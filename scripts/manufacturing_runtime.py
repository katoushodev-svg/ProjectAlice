#!/usr/bin/env python3
"""Orchestrate one Manufacturing Job without owning Git or Evidence policy."""

from __future__ import annotations

import argparse
import copy
import json
import sys
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Callable, Mapping

from manufacturing import ManufacturingGateError, build_candidate
from manufacturing_evidence import EvidenceStore, EvidenceValidationError
from manufacturing_git import ManufacturingGitAutomation


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
        git_layer: Any | None = None,
        evidence_store: EvidenceStore | None = None,
        lock_path: Path = Path("manufacturing/.runtime.lock"),
        evidence_factory: EvidenceFactory | None = None,
    ):
        self.manifest = dict(manifest)
        self.git_layer = git_layer or ManufacturingGitAutomation()
        self.evidence_store = evidence_store or EvidenceStore(Path("manufacturing/evidence"))
        self.lock_path = lock_path
        self.evidence_factory = evidence_factory

    def run(self, job: Mapping[str, Any]) -> RuntimeResult:
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
    if args.record is None:
        print(json.dumps({
            "status": "HUMAN_GATE",
            "state": ESCALATED,
            "run_id": run_id,
            "reason": "a prepared Manufacturing Job payload is required",
        }, ensure_ascii=True))
        return 10
    try:
        payload = _load_object(args.record)
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