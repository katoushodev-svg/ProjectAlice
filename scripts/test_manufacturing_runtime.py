import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from manufacturing_git import (  # noqa: E402
    EvidenceCommitResult,
    PullRequestResult,
    PushResult,
    RemoteVerificationResult,
)
from manufacturing_runtime import (  # noqa: E402
    EVIDENCE_COMMIT,
    ESCALATED,
    G1_HANDOFF,
    ManufacturingRuntime,
    PRIMARY_EVIDENCE,
    QUALITY_PASS,
    READY,
    REMOTE_VERIFICATION,
    REVIEW,
    TESTING,
)


ROOT = Path(__file__).parents[1]
MANIFEST = json.loads((ROOT / "scripts/manufacturing_manifest.json").read_text())


def passing_record() -> dict:
    checks = {
        name: {"status": "PASS", "summary": f"{name} passed"}
        for name in (
            "implementation", "focused_test", "analyze", "full_regression",
            "ai_review", "retest", "rereview",
        )
    }
    for name in ("ai_review", "rereview"):
        checks[name].update({
            "conclusion": "PASS", "evidence": "reviewed", "severity": "none",
            "allowed_scope": "manifest", "retest_required": False,
        })
    return {
        "fip": "FIP-005",
        "run_id": "template",
        "fip_approved": True,
        "implementation_ready": True,
        "prerequisite_gates": {f"FIP-00{number}": "PASS" for number in range(1, 5)},
        "branch": "fip-005/runtime-test",
        "base_branch": "main",
        "changed_files": ["frontend/lib/conversation/application/state/reducer.dart"],
        "checks": checks,
        "auto_fix": {"required": False, "attempts": 0, "summary": "not required"},
        "human_gate": False,
        "design_change": False, "architecture_change": False, "api_contract_change": False,
        "db_design_change": False, "security_safety_change": False, "phase_boundary_change": False,
        "fip_scope_change": False, "new_dependency": False, "unknown_changes": False,
        "commit_created": False, "push_performed": False, "merge_performed": False,
    }


def primary_evidence() -> dict:
    record = {
        "schema_version": "1", "evidence_id": "primary-runtime-001", "evidence_type": "PRIMARY",
        "fip_id": "FIP-005", "pipeline_step": "runtime", "run_id": "template",
        "created_at": "2026-09-12T00:00:00+09:00",
        "repository": {
            "repository": "katoushodev-svg/ProjectAlice", "branch": "fip-005/runtime-test",
            "implementation_commit_sha": "implementation-sha", "base_sha": "base-sha",
            "worktree_status": "clean", "changed_files": ["frontend/lib/conversation/application/state/reducer.dart"],
        },
        "manufacturing": {
            "manifest_path": "scripts/manufacturing_manifest.json",
            "approval_status": "Approved / Implementation Ready", "prerequisite_result": "PASS",
            "required_checks": ["implementation", "focused_test"],
            "allowed_paths": ["frontend/lib/conversation/application/**"],
            "prohibited_changes": ["architecture"], "manufacturing_result": "PASS",
            "manufacturing_reason": "all checks passed",
        },
        "verification": {
            name: {"status": "PASS", "summary": f"{name} passed"}
            for name in ("implementation", "focused_test", "analyze", "full_regression", "auto_fix", "retest")
        },
        "reviews": {
            name: {"conclusion": "PASS", "evidence": "reviewed", "reviewed_commit_sha": "implementation-sha"}
            for name in ("design_review", "implementation_review", "re_review")
        },
        "outcome": {"status": "PASS", "human_gate": False, "unknown": False, "stop_reason": "G1 handoff"},
    }
    return record


class FakeEvidenceStore:
    def __init__(self, directory: Path, status: str = "CREATED"):
        self.directory = directory
        self.status = status
        self.writes = []

    def write(self, record):
        self.writes.append(record)
        if self.status != "CREATED":
            from manufacturing_evidence import EvidenceWriteResult
            return EvidenceWriteResult(self.status, None, "forced result")
        path = self.directory / "manufacturing/evidence" / f"{record['evidence_id']}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(record), encoding="utf-8")
        from manufacturing_evidence import EvidenceWriteResult
        return EvidenceWriteResult("CREATED", path, "created")


class FakeGit:
    def __init__(self, *, evidence="SUCCESS", push="SUCCESS", remote="SUCCESS", pr="SUCCESS"):
        self.evidence_status = evidence
        self.push_status = push
        self.remote_status = remote
        self.pr_status = pr
        self.calls = []

    def commit_evidence(self, *args):
        self.calls.append("commit_evidence")
        return EvidenceCommitResult(self.evidence_status, "commit_evidence", "forced", "run", "fip-005/runtime-test", "implementation-sha", "evidence-sha", "manufacturing/evidence/primary-runtime-001.json")

    def push(self, *args, **kwargs):
        self.calls.append("push")
        return PushResult(self.push_status, "push", "forced", "run", "fip-005/runtime-test", "evidence-sha")

    def verify_remote(self, *args, **kwargs):
        self.calls.append("verify_remote")
        return RemoteVerificationResult(self.remote_status, "verify_remote", "forced", "run", "fip-005/runtime-test", "fip-005/runtime-test", "evidence-sha", "fip-005/runtime-test", "evidence-sha")

    def create_pull_request(self, *args, **kwargs):
        self.calls.append("create_pull_request")
        return PullRequestResult(self.pr_status, "create_pull_request", "forced", "run", "fip-005/runtime-test", "https://github.com/example/repo/pull/1")


class RuntimeTest(unittest.TestCase):
    def job(self):
        return {
            "fip": "FIP-005", "record": passing_record(), "primary_evidence": primary_evidence(),
            "preflight": {"status": "PASS", "branch": "fip-005/runtime-test", "worktree_status": "clean", "origin": "origin"},
        }

    def runtime(self, git=None, evidence=None, directory=None):
        directory = directory or Path(tempfile.mkdtemp())
        return ManufacturingRuntime(
            MANIFEST, git_layer=git or FakeGit(), evidence_store=evidence or FakeEvidenceStore(directory),
            lock_path=directory / ".runtime.lock",
        ), directory

    def test_full_lifecycle_reaches_g1_handoff_with_one_run_id(self):
        git = FakeGit()
        runtime, _ = self.runtime(git=git)
        result = runtime.run(self.job())
        self.assertEqual(result.state, G1_HANDOFF)
        self.assertEqual(result.history[0], READY)
        self.assertEqual(result.history[-1], G1_HANDOFF)
        self.assertEqual(git.calls, ["commit_evidence", "push", "verify_remote", "create_pull_request"])
        self.assertEqual(result.handoff["run_id"], result.run_id)
        self.assertIsNone(result.handoff["g1_decision"])

    def test_lifecycle_states_are_ordered(self):
        runtime, _ = self.runtime()
        result = runtime.run(self.job())
        expected = [READY, "IMPLEMENTING", TESTING, REVIEW, QUALITY_PASS, PRIMARY_EVIDENCE, EVIDENCE_COMMIT, "PUSH", REMOTE_VERIFICATION, "PR_CREATED", G1_HANDOFF]
        self.assertEqual(list(result.history), expected)

    def test_failure_gates_stop_follow_up_operations(self):
        for stage, expected_calls in (("evidence", ["commit_evidence"]), ("push", ["commit_evidence", "push"]), ("remote", ["commit_evidence", "push", "verify_remote"]), ("pr", ["commit_evidence", "push", "verify_remote", "create_pull_request"])):
            git = FakeGit(**{stage: "FAILURE"})
            runtime, _ = self.runtime(git=git)
            result = runtime.run(self.job())
            self.assertEqual(result.state, ESCALATED)
            self.assertNotIn("PR_CREATED", result.history)
            self.assertEqual(git.calls, expected_calls)

    def test_unknown_results_escalate_without_retry(self):
        for stage in ("evidence", "push", "remote", "pr"):
            git = FakeGit(**({} if stage == "evidence" else {stage: "UNKNOWN"}))
            evidence = FakeEvidenceStore(Path(tempfile.mkdtemp()), "UNKNOWN") if stage == "evidence" else None
            runtime, _ = self.runtime(git=git, evidence=evidence)
            result = runtime.run(self.job())
            self.assertTrue(result.unknown)
            self.assertEqual(result.state, ESCALATED)
            if stage == "pr":
                self.assertNotIn("PR_CREATED", result.history)
            expected_call = {
                "evidence": "commit_evidence",
                "push": "push",
                "remote": "verify_remote",
                "pr": "create_pull_request",
            }[stage]
            self.assertEqual(git.calls.count(expected_call), 0 if stage == "evidence" else 1)

    def test_lock_competition_is_human_gate_and_not_removed(self):
        with tempfile.TemporaryDirectory() as directory:
            lock = Path(directory) / ".runtime.lock"
            lock.write_text("existing\n", encoding="utf-8")
            runtime, _ = self.runtime(directory=Path(directory))
            result = runtime.run(self.job())
            self.assertEqual(result.state, ESCALATED)
            self.assertTrue(lock.exists())

    def test_main_and_dirty_preflight_are_rejected(self):
        runtime, _ = self.runtime()
        job = self.job()
        job["record"]["branch"] = "main"
        job["preflight"]["branch"] = "main"
        result = runtime.run(job)
        self.assertEqual(result.state, ESCALATED)

        runtime, _ = self.runtime()
        job = self.job()
        job["preflight"]["worktree_status"] = "dirty"
        result = runtime.run(job)
        self.assertEqual(result.state, ESCALATED)

    def test_runtime_does_not_call_apply_or_direct_git(self):
        git = FakeGit()
        runtime, _ = self.runtime(git=git)
        runtime.run(self.job())
        self.assertNotIn("apply", git.calls)


if __name__ == "__main__":
    unittest.main()