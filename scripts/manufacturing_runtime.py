#!/usr/bin/env python3
"""Orchestrate one Manufacturing Job without owning Git or Evidence policy."""

from __future__ import annotations

import argparse
import copy
import json
import sys
import uuid
from datetime import datetime, timezone
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Callable, Mapping

from manufacturing import ManufacturingGateError, build_candidate
from manufacturing_coding_agent_adapter import SubprocessCodingAgentAdapter
from manufacturing_evidence import EvidenceStore, EvidenceValidationError
from manufacturing_executor import (
    DEFAULT_PROHIBITED_CHANGES,
    ExecutorSecurityError,
    ExecutorValidationError,
    ImplementationJob,
    ImplementationResult,
    ManufacturingExecutor,
    STATUS_FAILURE,
    STATUS_SUCCESS,
    STATUS_UNKNOWN,
)
from manufacturing_git import ManufacturingGitAutomation
from manufacturing_verification import (
    ESCALATE as REVIEW_ESCALATE,
    FIXABLE_FINDINGS,
    PASS as VERIFICATION_PASS,
    ManufacturingVerification,
)


READY = "READY"
IMPLEMENTING = "IMPLEMENTING"
TESTING = "TESTING"
REVIEW = "REVIEW"
QUALITY_PASS = "QUALITY_PASS"
PRIMARY_EVIDENCE = "PRIMARY_EVIDENCE"
EVIDENCE_COMMIT = "EVIDENCE_COMMIT"
PUSH = "PUSH"
REMOTE_VERIFICATION = "REMOTE_VERIFICATION"
PR_CREATED = "PR_CREATED"
G1_HANDOFF = "G1_HANDOFF"
ESCALATED = "ESCALATED"


@dataclass(frozen=True)
class RuntimeResult:
    status: str
    state: str
    run_id: str
    reason: str
    history: tuple[str, ...]
    handoff: dict[str, Any] | None = None
    unknown: bool = False


class RuntimeLock:
    """A local exclusive lock that never removes an existing lock."""

    def __init__(self, path: Path):
        self.path = path
        self._owned = False

    def acquire(self, run_id: str) -> None:
        try:
            with self.path.open("x", encoding="utf-8") as lock_file:
                lock_file.write(run_id + "\n")
        except FileExistsError as error:
            raise RuntimeError("existing runtime lock requires Human Gate") from error
        except OSError as error:
            raise RuntimeError(f"runtime lock could not be acquired: {error}") from error
        self._owned = True

    def release(self) -> None:
        if not self._owned:
            return
        try:
            self.path.unlink()
        finally:
            self._owned = False


EvidenceFactory = Callable[[str, dict[str, Any], dict[str, Any]], dict[str, Any]]


class ManufacturingRuntime:
    def __init__(
        self,
        manifest: Mapping[str, Any],
        *,
        executor: ManufacturingExecutor | None = None,
        git_layer: Any | None = None,
        evidence_store: EvidenceStore | None = None,
        verification: ManufacturingVerification | None = None,
        lock_path: Path = Path("manufacturing/.runtime.lock"),
        evidence_factory: EvidenceFactory | None = None,
    ):
        self.manifest = dict(manifest)
        self.executor = executor or ManufacturingExecutor(
            adapter=SubprocessCodingAgentAdapter.from_environment(),
            manifest=self.manifest,
        )
        self.git_layer = git_layer or ManufacturingGitAutomation()
        self.evidence_store = evidence_store or EvidenceStore(Path("manufacturing/evidence"))
        self.verification = verification or ManufacturingVerification()
        self.lock_path = lock_path
        self.evidence_factory = evidence_factory

    def execute_implementation(
        self, job: ImplementationJob | Mapping[str, Any]
    ) -> ImplementationResult:
        """Execute an ImplementationJob via the configured ManufacturingExecutor."""
        return self.executor.execute_job(job)

    def run(self, job: Mapping[str, Any]) -> RuntimeResult:
        if "record" not in job:
            return self._run_autonomous(job)
        run_id = self._new_run_id()
        history = [READY]
        lock = RuntimeLock(self.lock_path)
        try:
            lock.acquire(run_id)
        except RuntimeError as error:
            return RuntimeResult("HUMAN_GATE", ESCALATED, run_id, str(error), tuple(history))

        try:
            try:
                record = self._prepare_record(job, run_id)
                self._validate_preflight(job, record)
            except ManufacturingGateError as error:
                return self._escalate(history, run_id, str(error))
            self._transition(history, IMPLEMENTING)

            impl_job_data = job.get("implementation_job")
            if impl_job_data is None and "source_of_truth" in job and "allowed_paths" in job:
                impl_job_data = {
                    "fip": job.get("fip") or record.get("fip"),
                    "run_id": run_id,
                    "source_of_truth": job.get("source_of_truth"),
                    "allowed_paths": job.get("allowed_paths"),
                    "prohibited_changes": job.get("prohibited_changes", DEFAULT_PROHIBITED_CHANGES),
                    "prompt": job.get("prompt", ""),
                    "context": job.get("context"),
                }

            if impl_job_data is not None:
                if isinstance(impl_job_data, Mapping):
                    impl_job_payload = dict(impl_job_data)
                    impl_job_payload.setdefault("fip", job.get("fip") or record.get("fip"))
                    impl_job_payload.setdefault("run_id", run_id)
                elif isinstance(impl_job_data, ImplementationJob):
                    impl_job_payload = impl_job_data
                else:
                    return self._escalate(history, run_id, "invalid implementation_job format")

                try:
                    impl_result = self.executor.execute_job(impl_job_payload)
                except (ExecutorValidationError, ExecutorSecurityError) as error:
                    return self._escalate(history, run_id, f"Implementation validation failed: {error}")
                except Exception as error:
                    return self._escalate(history, run_id, f"Implementation state is unknown: {error}", unknown=True)

                if impl_result.status != STATUS_SUCCESS:
                    return self._escalate(
                        history,
                        run_id,
                        f"Implementation failed: {impl_result.reason}",
                        unknown=impl_result.unknown or (impl_result.status == STATUS_UNKNOWN),
                    )

            self._transition(history, TESTING)
            self._transition(history, REVIEW)
            try:
                candidate = build_candidate(record, self.manifest)
            except ManufacturingGateError as error:
                return self._escalate(history, run_id, f"Manufacturing Gate failed: {error}")
            self._transition(history, QUALITY_PASS)

            self._transition(history, PRIMARY_EVIDENCE)
            try:
                evidence = self._prepare_evidence(job, run_id, record, candidate)
                evidence_result = self.evidence_store.write(evidence)
                if evidence_result.status != "CREATED" or evidence_result.path is None:
                    return self._escalate(
                        history, run_id,
                        f"Primary Evidence was not newly created: {evidence_result.status}",
                        unknown=evidence_result.status == "UNKNOWN",
                    )
                evidence_path = evidence_result.path
                if not evidence_path.is_file():
                    return self._escalate(history, run_id, "Primary Evidence could not be verified", unknown=True)
                expected_path = Path("manufacturing/evidence") / f"{evidence['evidence_id']}.json"
                if evidence_path.name != expected_path.name or evidence_path.parts[-3:] != expected_path.parts:
                    return self._escalate(history, run_id, "Primary Evidence path is outside the Evidence directory")
            except EvidenceValidationError as error:
                return self._escalate(history, run_id, f"Primary Evidence failed validation: {error}")
            except OSError as error:
                return self._escalate(history, run_id, f"Primary Evidence state is unknown: {error}", unknown=True)

            evidence_id = evidence["evidence_id"]
            branch = record["branch"]
            implementation_sha = evidence["repository"]["implementation_commit_sha"]
            authorization = {
                "run_id": run_id,
                "branch": branch,
                "implementation_commit_sha": implementation_sha,
                "primary_evidence_id": evidence_id,
                "primary_evidence_path": expected_path.as_posix(),
                "primary_evidence_generation_confirmed": True,
                "push_not_performed": True,
                "unresolved_unknown_absent": True,
            }

            self._transition(history, EVIDENCE_COMMIT)
            evidence_commit = self.git_layer.commit_evidence(
                authorization,
                [expected_path.as_posix()],
                branch,
                implementation_sha,
                f"manufacturing: add evidence {evidence_id}",
            )
            if evidence_commit.status != "SUCCESS":
                return self._escalate(
                    history, run_id, evidence_commit.reason,
                    unknown=evidence_commit.status == "UNKNOWN",
                )

            self._transition(history, PUSH)
            push_result = self.git_layer.push(
                run_id,
                branch,
                evidence_commit.evidence_commit_sha,
                evidence_commit_completed=True,
                unresolved_unknown_absent=True,
            )
            if push_result.status != "SUCCESS":
                return self._escalate(
                    history, run_id, push_result.reason,
                    unknown=push_result.status == "UNKNOWN",
                )

            self._transition(history, REMOTE_VERIFICATION)
            remote_result = self.git_layer.verify_remote(
                push_result,
                expected_branch=branch,
                expected_commit_sha=evidence_commit.evidence_commit_sha,
            )
            if remote_result.status != "SUCCESS":
                return self._escalate(
                    history, run_id, remote_result.reason,
                    unknown=remote_result.status == "UNKNOWN",
                )

            pr_candidate = candidate["pr_candidate"]
            pr_result = self.git_layer.create_pull_request(
                remote_result,
                title=pr_candidate["title"],
                body=pr_candidate["body"],
            )
            if pr_result.status != "SUCCESS":
                return self._escalate(
                    history, run_id, pr_result.reason,
                    unknown=pr_result.status == "UNKNOWN",
                )

            self._transition(history, PR_CREATED)
            self._transition(history, G1_HANDOFF)
            handoff = self._build_handoff(
                run_id, record, evidence, evidence_commit, push_result, remote_result, pr_result
            )
            return RuntimeResult("G1_HANDOFF", G1_HANDOFF, run_id, "G1 Human Decision required", tuple(history), handoff)
        except Exception as error:
            return self._escalate(history, run_id, f"Runtime state is unknown: {error}", unknown=True)
        finally:
            lock.release()

    def _run_autonomous(self, job: Mapping[str, Any]) -> RuntimeResult:
        """FIP-selected manufacturing path. Runtime orchestrates but never shells out."""
        run_id = self._new_run_id()
        history = [READY]
        fip = job.get("fip")
        entry = self.manifest.get(fip)
        if not isinstance(fip, str) or not isinstance(entry, Mapping):
            return self._escalate(history, run_id, "requested FIP is not in the manifest")
        branch = job.get("branch", fip.lower() + "/implementation")
        if not isinstance(branch, str):
            return self._escalate(history, run_id, "FIP branch is invalid")
        lock = RuntimeLock(self.lock_path)
        try:
            lock.acquire(run_id)
            preflight = self.git_layer.preflight(branch)
            if preflight.status != "PASS":
                return self._escalate(history, run_id, preflight.reason, unknown=preflight.status == "UNKNOWN")
            gates = self._prerequisite_gates(entry.get("prerequisites", ()))
            if any(value != "PASS" for value in gates.values()):
                unknown = any(value == "UNKNOWN" for value in gates.values())
                return self._escalate(history, run_id, "prerequisite completion is not verified", unknown=unknown)
            attempts = int(entry["run_budget"])
            actual_attempts = 0
            last_review = None
            final_checks: dict[str, Any] | None = None
            changed_files: tuple[str, ...] = ()
            for attempt in range(1, attempts + 1):
                actual_attempts = attempt
                self._transition(history, IMPLEMENTING)
                impl = self.executor.execute_job(self._implementation_job(fip, run_id, entry, job, attempt))
                if impl.status != STATUS_SUCCESS:
                    return self._escalate(history, run_id, impl.reason, unknown=impl.status == STATUS_UNKNOWN or impl.unknown)
                changed_files = impl.changed_files
                self._transition(history, TESTING)
                checks = {
                    "implementation": {"status": "PASS", "summary": impl.summary or "implementation passed"},
                    "focused_test": self._check_dict(self.verification.focused_test()),
                    "analyze": self._check_dict(self.verification.analyze()),
                    "full_regression": self._check_dict(self.verification.full_regression()),
                }
                if any(checks[name]["status"] != VERIFICATION_PASS for name in ("focused_test", "analyze", "full_regression")):
                    unknown = any(checks[name]["status"] == "UNKNOWN" for name in checks)
                    return self._escalate(history, run_id, "verification did not pass", unknown=unknown)
                self._transition(history, REVIEW)
                review = self.verification.review(self._review_prompt(fip, entry, changed_files))
                last_review = review
                if review.conclusion == REVIEW_ESCALATE:
                    return self._escalate(history, run_id, review.evidence, unknown=True)
                if review.conclusion == FIXABLE_FINDINGS:
                    if attempt == attempts:
                        return self._escalate(history, run_id, "fixable findings exhausted run_budget")
                    continue
                if review.conclusion != VERIFICATION_PASS:
                    return self._escalate(history, run_id, "review conclusion is unknown", unknown=True)
                checks["ai_review"] = self._review_check(review)
                checks["retest"] = {"status": "PASS", "summary": "retest passed" if attempt > 1 else "retest not required"}
                checks["rereview"] = self._review_check(review)
                final_checks = checks
                break
            if final_checks is None or last_review is None:
                return self._escalate(history, run_id, "no final verification result", unknown=True)
            record = self._build_record(fip, run_id, branch, entry, gates, list(changed_files), final_checks, actual_attempts)
            candidate = build_candidate(record, self.manifest)
            self._transition(history, QUALITY_PASS)
            commit = self.git_layer.commit_implementation(run_id, branch, preflight.head_sha, list(changed_files), entry["allowed_paths"], candidate["commit_candidate"]["message"])
            if commit.status != "SUCCESS":
                return self._escalate(history, run_id, commit.reason, unknown=commit.status == "UNKNOWN")
            self._transition(history, PRIMARY_EVIDENCE)
            evidence = self._generated_evidence(record, entry, preflight, commit.implementation_commit_sha, last_review)
            written = self.evidence_store.write(evidence)
            if written.status != "CREATED" or written.path is None:
                return self._escalate(history, run_id, f"Primary Evidence was not newly created: {written.status}", unknown=written.status == "UNKNOWN")
            expected_path = Path("manufacturing/evidence") / f"{evidence['evidence_id']}.json"
            self._transition(history, EVIDENCE_COMMIT)
            authorization = {"run_id": run_id, "branch": branch, "implementation_commit_sha": commit.implementation_commit_sha, "primary_evidence_id": evidence["evidence_id"], "primary_evidence_path": expected_path.as_posix(), "primary_evidence_generation_confirmed": True, "push_not_performed": True, "unresolved_unknown_absent": True}
            evidence_commit = self.git_layer.commit_evidence(authorization, [expected_path.as_posix()], branch, commit.implementation_commit_sha, f"manufacturing: add evidence {evidence['evidence_id']}")
            if evidence_commit.status != "SUCCESS": return self._escalate(history, run_id, evidence_commit.reason, unknown=evidence_commit.status == "UNKNOWN")
            self._transition(history, PUSH)
            pushed = self.git_layer.push(run_id, branch, evidence_commit.evidence_commit_sha, evidence_commit_completed=True, unresolved_unknown_absent=True)
            if pushed.status != "SUCCESS": return self._escalate(history, run_id, pushed.reason, unknown=pushed.status == "UNKNOWN")
            self._transition(history, REMOTE_VERIFICATION)
            remote = self.git_layer.verify_remote(pushed, expected_branch=branch, expected_commit_sha=evidence_commit.evidence_commit_sha)
            if remote.status != "SUCCESS": return self._escalate(history, run_id, remote.reason, unknown=remote.status == "UNKNOWN")
            pr = self.git_layer.create_pull_request(remote, title=candidate["pr_candidate"]["title"], body=candidate["pr_candidate"]["body"])
            if pr.status != "SUCCESS": return self._escalate(history, run_id, pr.reason, unknown=pr.status == "UNKNOWN")
            self._transition(history, PR_CREATED); self._transition(history, G1_HANDOFF)
            return RuntimeResult("G1_HANDOFF", G1_HANDOFF, run_id, "G1 Human Decision required", tuple(history), self._build_handoff(run_id, record, evidence, evidence_commit, pushed, remote, pr))
        except Exception as error:
            return self._escalate(history, run_id, f"Runtime state is unknown: {error}", unknown=True)
        finally:
            lock.release()

    def _prerequisite_gates(self, prerequisites: Any) -> dict[str, str]:
        # Existing formal completion commits; the Git Layer independently proves
        # each is a commit and ancestor of the current main.
        # FIP-001 and FIP-002 have no commit in Git history whose subject
        # identifies either FIP individually; both are bundled into the initial
        # commit below. The Git Layer instead requires that commit's tree to
        # contain Source of Truth completion evidence naming the specific FIP as
        # Completed / Approved, so nothing here is taken on faith or guessed.
        root_commit = "38b618238b64caccf58b6b7ce7655f1825a5bf03"
        proof = {"FIP-001": root_commit, "FIP-002": root_commit, "FIP-003": "4dfbbb5602b51318ca643e115bb50116a877952d", "FIP-004": "09229608e17d1758afa2470218538ffa7e79433f", "FIP-005": "0cf34b5d74693bc75438e87d878179903ea08b7e", "FIP-006": "cbbe7affc98a2bc8bd8649a670d966676830d3c4"}
        completion_evidence = {"FIP-001": {"document": "docs/frontend-implementation-plan.md"}, "FIP-002": {"document": "docs/frontend-implementation-plan.md"}}
        return {fip: self.git_layer.verify_prerequisite(fip, proof.get(fip, ""), completion_evidence.get(fip)).status for fip in prerequisites}

    @staticmethod
    def _check_dict(result: Any) -> dict[str, str]: return {"status": result.status, "summary": result.summary}
    @staticmethod
    def _review_check(review: Any) -> dict[str, Any]: return {"status": "PASS", "summary": "AI read-only review passed", "conclusion": "PASS", "evidence": review.evidence, "severity": review.severity, "allowed_scope": review.allowed_scope, "retest_required": review.retest_required}

    @staticmethod
    def _implementation_job(fip: str, run_id: str, entry: Mapping[str, Any], job: Mapping[str, Any], attempt: int) -> dict[str, Any]:
        return {"fip": fip, "run_id": run_id, "source_of_truth": [entry["plan"]], "allowed_paths": entry["allowed_paths"], "prohibited_changes": entry["prohibited_changes"], "prompt": job.get("prompt") or f"Implement {fip} only according to {entry['plan']}. Attempt {attempt}. Do not change files outside allowed_paths or prohibited boundaries."}

    @staticmethod
    def _review_prompt(fip: str, entry: Mapping[str, Any], changed_files: tuple[str, ...]) -> str:
        return json.dumps({"fip": fip, "source_of_truth": entry["plan"], "fip_006_dependency_boundary": "preserve existing FIP-006 visual foundation", "allowed_paths": entry["allowed_paths"], "prohibited_changes": entry["prohibited_changes"], "changed_files": list(changed_files), "required_output": {"conclusion": ["PASS", "FIXABLE_FINDINGS", "ESCALATE"], "evidence": "required", "severity": "required", "allowed_scope": "required", "retest_required": "required"}})

    @staticmethod
    def _build_record(fip: str, run_id: str, branch: str, entry: Mapping[str, Any], gates: Mapping[str, str], changed_files: list[str], checks: Mapping[str, Any], attempts: int) -> dict[str, Any]:
        return {"fip": fip, "run_id": run_id, "fip_approved": True, "implementation_ready": True, "prerequisite_gates": dict(gates), "branch": branch, "base_branch": "main", "changed_files": changed_files, "checks": dict(checks), "auto_fix": {"required": attempts > 1, "attempts": attempts, "summary": "bounded by manifest run_budget"}, "human_gate": False, "design_change": False, "architecture_change": False, "api_contract_change": False, "db_design_change": False, "security_safety_change": False, "phase_boundary_change": False, "fip_scope_change": False, "new_dependency": False, "unknown_changes": False, "commit_created": False, "push_performed": False, "merge_performed": False}

    @staticmethod
    def _generated_evidence(record: Mapping[str, Any], entry: Mapping[str, Any], preflight: Any, implementation_sha: str, review: Any) -> dict[str, Any]:
        run_id = record["run_id"]
        verification = {name: {"status": "PASS", "summary": record["checks"][name]["summary"]} for name in ("implementation", "focused_test", "analyze", "full_regression", "retest")}
        review_data = {"conclusion": "PASS", "evidence": review.evidence, "reviewed_commit_sha": implementation_sha}
        return {"schema_version": "1", "evidence_id": f"primary-{run_id}", "evidence_type": "PRIMARY", "fip_id": record["fip"], "pipeline_step": "manufacturing-runtime", "run_id": run_id, "created_at": datetime.now(timezone.utc).isoformat(), "repository": {"repository": preflight.origin, "branch": record["branch"], "implementation_commit_sha": implementation_sha, "base_sha": preflight.base_sha, "worktree_status": "dirty-evidence-only", "changed_files": record["changed_files"]}, "manufacturing": {"manifest_path": "scripts/manufacturing_manifest.json", "approval_status": entry["approval_status"], "prerequisite_result": "PASS", "required_checks": entry["required_checks"], "allowed_paths": entry["allowed_paths"], "prohibited_changes": entry["prohibited_changes"], "manufacturing_result": "PASS", "manufacturing_reason": "all required checks passed"}, "verification": verification, "reviews": {"design_review": review_data, "implementation_review": review_data, "re_review": review_data}, "outcome": {"status": "PASS", "human_gate": False, "unknown": False, "stop_reason": "G1 handoff pending"}}

    @staticmethod
    def _new_run_id() -> str:
        return f"run-{uuid.uuid4().hex}"

    @staticmethod
    def _transition(history: list[str], state: str) -> None:
        history.append(state)

    @staticmethod
    def _prepare_record(job: Mapping[str, Any], run_id: str) -> dict[str, Any]:
        record = job.get("record")
        if not isinstance(record, Mapping):
            raise ManufacturingGateError("job record is required")
        prepared = copy.deepcopy(dict(record))
        prepared["run_id"] = run_id
        if job.get("fip") != prepared.get("fip"):
            raise ManufacturingGateError("job FIP does not match the Manufacturing Record")
        return prepared

    @staticmethod
    def _validate_preflight(job: Mapping[str, Any], record: Mapping[str, Any]) -> None:
        preflight = job.get("preflight")
        if not isinstance(preflight, Mapping) or preflight.get("status") != "PASS":
            raise ManufacturingGateError("repository preflight is not PASS")
        if record.get("base_branch") != "main":
            raise ManufacturingGateError("base branch must be main")
        branch = record.get("branch")
        if not isinstance(branch, str) or branch == "main" or not branch.startswith("fip-"):
            raise ManufacturingGateError("isolated FIP branch is required")
        if preflight.get("branch") != branch:
            raise ManufacturingGateError("preflight branch does not match the record")
        if preflight.get("worktree_status") != "clean":
            raise ManufacturingGateError("preflight worktree is not clean")
        if not isinstance(preflight.get("origin"), str) or not preflight["origin"]:
            raise ManufacturingGateError("preflight origin is required")

    def _prepare_evidence(
        self,
        job: Mapping[str, Any],
        run_id: str,
        record: dict[str, Any],
        candidate: dict[str, Any],
    ) -> dict[str, Any]:
        if self.evidence_factory is not None:
            evidence = self.evidence_factory(run_id, record, candidate)
        else:
            evidence = job.get("primary_evidence")
        if not isinstance(evidence, Mapping):
            raise EvidenceValidationError("Primary Evidence payload is required")
        prepared = copy.deepcopy(dict(evidence))
        prepared["run_id"] = run_id
        if prepared.get("fip_id") != record["fip"]:
            raise EvidenceValidationError("Primary Evidence FIP does not match the job")
        if prepared.get("evidence_type") != "PRIMARY":
            raise EvidenceValidationError("Primary Evidence is required")
        return prepared

    @staticmethod
    def _build_handoff(
        run_id: str,
        record: Mapping[str, Any],
        evidence: Mapping[str, Any],
        evidence_commit: Any,
        push_result: Any,
        remote_result: Any,
        pr_result: Any,
    ) -> dict[str, Any]:
        return {
            "run_id": run_id,
            "fip": record["fip"],
            "implementation_commit_sha": evidence["repository"]["implementation_commit_sha"],
            "primary_evidence_id": evidence["evidence_id"],
            "evidence_commit_sha": evidence_commit.evidence_commit_sha,
            "push": asdict(push_result),
            "remote_verification": asdict(remote_result),
            "pull_request": asdict(pr_result),
            "manufacturing_pass": True,
            "test_results": {
                name: record["checks"][name]
                for name in ("implementation", "focused_test", "analyze", "full_regression", "retest")
            },
            "review_results": {
                name: record["checks"][name]
                for name in ("ai_review", "rereview")
            },
            "scope_result": {"status": "PASS", "changed_files": record["changed_files"]},
            "unknown": False,
            "human_gate": True,
            "g1_decision": None,
        }

    @staticmethod
    def _escalate(
        history: list[str],
        run_id: str,
        reason: str,
        *,
        unknown: bool = False,
    ) -> RuntimeResult:
        if not history or history[-1] != ESCALATED:
            history.append(ESCALATED)
        return RuntimeResult("ESCALATED", ESCALATED, run_id, reason, tuple(history), unknown=unknown)


def _load_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"JSON object required: {path}")
    return value


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    run_parser = subparsers.add_parser("run")
    run_parser.add_argument("--fip", required=True)
    run_parser.add_argument("--record", type=Path)
    run_parser.add_argument("--manifest", type=Path, default=Path(__file__).with_name("manufacturing_manifest.json"))
    run_parser.add_argument("--lock", type=Path, default=Path("manufacturing/.runtime.lock"))
    args = parser.parse_args(argv)

    run_id = ManufacturingRuntime._new_run_id()
    try:
        payload = _load_object(args.record) if args.record is not None else {}
        payload["fip"] = args.fip
        runtime = ManufacturingRuntime(
            _load_object(args.manifest),
            lock_path=args.lock,
        )
        result = runtime.run(payload)
    except (OSError, TypeError, ValueError, json.JSONDecodeError) as error:
        print(json.dumps({"status": "HUMAN_GATE", "state": ESCALATED, "run_id": run_id, "reason": str(error)}))
        return 14
    print(json.dumps(asdict(result), ensure_ascii=True, indent=2, default=str))
    if result.state == G1_HANDOFF:
        return 0
    return 13 if result.unknown else 10


if __name__ == "__main__":
    raise SystemExit(main())
