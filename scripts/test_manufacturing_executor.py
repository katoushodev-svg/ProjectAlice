#!/usr/bin/env python3
"""Comprehensive unit tests for Manufacturing Minimal Coding Executor."""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from typing import Any, Mapping

sys.path.insert(0, str(Path(__file__).parent))

from manufacturing_executor import (  # noqa: E402
    CodingExecutor,
    ExecutorSecurityError,
    ExecutorValidationError,
    ImplementationJob,
    ImplementationResult,
    ManufacturingExecutor,
    STATUS_FAILURE,
    STATUS_SUCCESS,
    STATUS_UNKNOWN,
    UnconnectedCodingExecutorAdapter,
    is_path_pattern_covered,
    validate_paths_safety,
)

ROOT = Path(__file__).parents[1]
MANIFEST = json.loads((ROOT / "scripts/manufacturing_manifest.json").read_text(encoding="utf-8"))


def sample_fip005_job() -> dict[str, Any]:
    return {
        "fip": "FIP-005",
        "run_id": "run-fip005-001",
        "source_of_truth": [
            "docs/fip-005-application-state-plan.md",
            "docs/frontend-implementation-plan.md",
            "docs/project-alice-autonomous-manufacturing-pipeline.md",
        ],
        "allowed_paths": [
            "frontend/lib/conversation/application/**",
            "frontend/test/conversation/application/**",
        ],
        "prohibited_changes": [
            "design",
            "architecture",
            "api_contract",
            "database",
            "security_safety",
            "phase_boundary",
            "fip_scope",
            "unknown_changes",
            "new_dependency",
        ],
        "prompt": "Implement pure conversation state reducer and application models for FIP-005",
    }


class FakeSuccessAdapter(CodingExecutor):
    def __init__(self, changed_files: tuple[str, ...] | None = None):
        self.changed_files = changed_files or (
            "frontend/lib/conversation/application/state/reducer.dart",
            "frontend/test/conversation/application/state/reducer_test.dart",
        )
        self.called_with: ImplementationJob | None = None

    def execute(self, job: ImplementationJob) -> ImplementationResult:
        self.called_with = job
        return ImplementationResult(
            status=STATUS_SUCCESS,
            fip=job.fip,
            run_id=job.run_id,
            reason="All components generated according to Source of Truth",
            changed_files=self.changed_files,
            summary="Implemented conversation state reducer",
            unknown=False,
            retry_allowed=False,
        )


class FakeFailureAdapter(CodingExecutor):
    def __init__(self, reason: str = "Syntax generation failed", retry_allowed: bool = True):
        self.reason = reason
        self.retry_allowed = retry_allowed
        self.called_with: ImplementationJob | None = None

    def execute(self, job: ImplementationJob) -> ImplementationResult:
        self.called_with = job
        return ImplementationResult(
            status=STATUS_FAILURE,
            fip=job.fip,
            run_id=job.run_id,
            reason=self.reason,
            changed_files=(),
            summary="Coding executor failed deterministically",
            unknown=False,
            retry_allowed=self.retry_allowed,
        )


class FakeUnknownAdapter(CodingExecutor):
    def __init__(self, reason: str = "Process dropped connection / state indeterminate"):
        self.reason = reason
        self.called_with: ImplementationJob | None = None

    def execute(self, job: ImplementationJob) -> ImplementationResult:
        self.called_with = job
        return ImplementationResult(
            status=STATUS_UNKNOWN,
            fip=job.fip,
            run_id=job.run_id,
            reason=self.reason,
            changed_files=(),
            summary="Indeterminate execution state",
            unknown=True,
            retry_allowed=True,  # will be normalized to False!
        )


class FakeCrashingAdapter(CodingExecutor):
    def execute(self, job: ImplementationJob) -> ImplementationResult:
        raise RuntimeError("Subprocess crashed unexpectedly with SIGKILL")


class TestManufacturingExecutor(unittest.TestCase):
    def setUp(self):
        self.manifest = MANIFEST

    # 1. 正常なImplementation Jobを受け取れる
    def test_accepts_valid_implementation_job(self):
        job_data = sample_fip005_job()
        job = ImplementationJob.from_dict(job_data)
        self.assertEqual(job.fip, "FIP-005")
        self.assertEqual(job.run_id, "run-fip005-001")
        self.assertIn("docs/fip-005-application-state-plan.md", job.source_of_truth)
        self.assertIn("frontend/lib/conversation/application/**", job.allowed_paths)

        adapter = FakeSuccessAdapter()
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(job)
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertFalse(result.unknown)
        self.assertEqual(adapter.called_with, job)

    # 2. FIP不一致を拒否する
    def test_rejects_unapproved_or_mismatched_fip(self):
        job_data = sample_fip005_job()
        job_data["fip"] = "FIP-999"
        executor = ManufacturingExecutor(manifest=self.manifest)
        with self.assertRaises(ExecutorValidationError) as ctx:
            executor.execute_job(job_data)
        self.assertIn("FIP-999 is not present in manifest", str(ctx.exception))

    # 3. 必須Source of Truth不足を拒否する
    def test_rejects_missing_required_source_of_truth(self):
        job_data = sample_fip005_job()
        job_data["source_of_truth"] = ["docs/frontend-implementation-plan.md"]  # missing fip-005 plan
        executor = ManufacturingExecutor(manifest=self.manifest)
        with self.assertRaises(ExecutorValidationError) as ctx:
            executor.execute_job(job_data)
        self.assertIn("missing required plan", str(ctx.exception))

        job_data_empty = sample_fip005_job()
        job_data_empty["source_of_truth"] = []
        with self.assertRaises(ExecutorValidationError):
            executor.execute_job(job_data_empty)

    # 4. allowed_paths不足・逸脱を拒否する
    def test_rejects_missing_or_invalid_allowed_paths(self):
        job_data_empty = sample_fip005_job()
        job_data_empty["allowed_paths"] = []
        executor = ManufacturingExecutor(manifest=self.manifest)
        with self.assertRaises(ExecutorValidationError):
            executor.execute_job(job_data_empty)

        job_data_outside = sample_fip005_job()
        job_data_outside["allowed_paths"] = ["backend/security/**"]
        with self.assertRaises(ExecutorSecurityError) as ctx:
            executor.execute_job(job_data_outside)
        self.assertIn("not permitted by manifest", str(ctx.exception))

    # 4b. FINDING-001: Manifestより広いallowed_paths patternの指定を拒否する
    def test_rejects_wider_allowed_paths_pattern_than_manifest(self):
        executor = ManufacturingExecutor(manifest=self.manifest)
        for wider_pattern in ("frontend/**", "frontend/lib/**", "**", "*"):
            job_data = sample_fip005_job()
            job_data["allowed_paths"] = [wider_pattern]
            with self.assertRaises(ExecutorSecurityError) as ctx:
                executor.execute_job(job_data)
            self.assertIn("not permitted by manifest", str(ctx.exception))

    # 4c. FINDING-001: Manifestと完全一致する既存FIP-005 pathsはPASS
    def test_accepts_exact_match_allowed_paths_from_manifest(self):
        job_data = sample_fip005_job()
        job_data["allowed_paths"] = [
            "frontend/lib/conversation/application/**",
            "frontend/test/conversation/application/**",
        ]
        adapter = FakeSuccessAdapter()
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(job_data)
        self.assertEqual(result.status, STATUS_SUCCESS)

    # 4d. FINDING-001: Manifest範囲内の適切なsub-path patternはPASS
    def test_accepts_narrower_subpath_allowed_paths(self):
        job_data = sample_fip005_job()
        job_data["allowed_paths"] = [
            "frontend/lib/conversation/application/state/**",
            "frontend/lib/conversation/application/state/reducer.dart",
            "frontend/test/conversation/application/state/**",
        ]
        adapter = FakeSuccessAdapter()
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(job_data)
        self.assertEqual(result.status, STATUS_SUCCESS)

    # 5. Coding Executor SUCCESSを正しく返す
    def test_returns_coding_executor_success(self):
        adapter = FakeSuccessAdapter()
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(sample_fip005_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertFalse(result.unknown)
        self.assertFalse(result.retry_allowed)
        self.assertGreater(len(result.changed_files), 0)

    # 6. Coding Executor FAILUREを正しく返す
    def test_returns_coding_executor_failure(self):
        adapter = FakeFailureAdapter(reason="Type check failed in generated code", retry_allowed=True)
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(sample_fip005_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertFalse(result.unknown)
        self.assertTrue(result.retry_allowed)
        self.assertIn("Type check failed", result.reason)

    # 7. Coding Executor UNKNOWNを正しく返す
    def test_returns_coding_executor_unknown(self):
        adapter = FakeUnknownAdapter(reason="Connection reset peer during execution")
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(sample_fip005_job())
        self.assertEqual(result.status, STATUS_UNKNOWN)
        self.assertTrue(result.unknown)
        # 8. UNKNOWNを自動再実行しない
        self.assertFalse(result.retry_allowed)

    # 8. UNKNOWNを自動再実行しない (Adapter crash also yields non-retryable UNKNOWN)
    def test_adapter_crash_returns_unknown_and_disallows_retry(self):
        adapter = FakeCrashingAdapter()
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(sample_fip005_job())
        self.assertEqual(result.status, STATUS_UNKNOWN)
        self.assertTrue(result.unknown)
        self.assertFalse(result.retry_allowed)
        self.assertIn("Subprocess crashed unexpectedly", result.reason)

    # 9. ExecutorがGit操作を行わない
    def test_executor_does_not_perform_git_operations(self):
        executor_module = sys.modules["manufacturing_executor"]
        self.assertFalse(hasattr(executor_module, "git"))
        self.assertFalse(hasattr(executor_module, "ManufacturingGitAutomation"))
        # Execute job and verify no git repositories are manipulated
        adapter = FakeSuccessAdapter()
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(sample_fip005_job())
        self.assertEqual(result.status, STATUS_SUCCESS)

    # 10. ExecutorがEvidence操作を行わない
    def test_executor_does_not_perform_evidence_operations(self):
        executor_module = sys.modules["manufacturing_executor"]
        self.assertFalse(hasattr(executor_module, "EvidenceStore"))
        self.assertFalse(hasattr(executor_module, "write_evidence"))

    # 11. ExecutorがPR/Mergeを行わない
    def test_executor_does_not_perform_pr_or_merge(self):
        executor_module = sys.modules["manufacturing_executor"]
        self.assertFalse(hasattr(executor_module, "create_pull_request"))
        self.assertFalse(hasattr(executor_module, "merge"))

    # 12. 未接続Coding Executorを成功扱いしない
    def test_unconnected_coding_executor_returns_failure(self):
        unconnected_adapter = UnconnectedCodingExecutorAdapter()
        executor = ManufacturingExecutor(adapter=unconnected_adapter, manifest=self.manifest)
        result = executor.execute_job(sample_fip005_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertFalse(result.unknown)
        self.assertFalse(result.retry_allowed)
        self.assertIn("not connected", result.reason)

    # Security: Arbitrary shell command injection rejection
    def test_rejects_arbitrary_shell_or_git_command_in_job(self):
        for prohibited_field in ("command", "shell_command", "exec", "script", "git_command", "push", "pr", "merge"):
            job_data = sample_fip005_job()
            job_data[prohibited_field] = "rm -rf /"
            executor = ManufacturingExecutor(manifest=self.manifest)
            with self.assertRaises(ExecutorSecurityError):
                executor.execute_job(job_data)

    # Security: Path traversal rejection
    def test_rejects_path_traversal_in_job(self):
        job_data = sample_fip005_job()
        job_data["allowed_paths"] = ["../../etc/**"]
        executor = ManufacturingExecutor(manifest=self.manifest)
        with self.assertRaises(ExecutorSecurityError):
            executor.execute_job(job_data)

        job_data_sot = sample_fip005_job()
        job_data_sot["source_of_truth"] = ["../secret.md"]
        with self.assertRaises(ExecutorSecurityError):
            executor.execute_job(job_data_sot)

    # Safety: Changed files violation rejected even if adapter says SUCCESS
    def test_rejects_changed_files_outside_allowed_paths(self):
        adapter = FakeSuccessAdapter(changed_files=(
            "frontend/lib/conversation/application/state/reducer.dart",
            "frontend/lib/security/key_store.dart",  # outside allowed paths & protected marker!
        ))
        executor = ManufacturingExecutor(adapter=adapter, manifest=self.manifest)
        result = executor.execute_job(sample_fip005_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertFalse(result.unknown)
        self.assertIn("unauthorized file changes", result.reason)


class TestPathSafety(unittest.TestCase):
    def test_is_path_pattern_covered_rules(self):
        ma = "frontend/lib/conversation/application/**"
        # Exact match
        self.assertTrue(is_path_pattern_covered("frontend/lib/conversation/application/**", ma))
        # Subdirectories / Subpaths
        self.assertTrue(is_path_pattern_covered("frontend/lib/conversation/application/state/**", ma))
        self.assertTrue(is_path_pattern_covered("frontend/lib/conversation/application/state/*.dart", ma))
        self.assertTrue(is_path_pattern_covered("frontend/lib/conversation/application/state/reducer.dart", ma))
        self.assertTrue(is_path_pattern_covered("frontend/lib/conversation/application", ma))

        # Wider patterns must be rejected
        self.assertFalse(is_path_pattern_covered("frontend/**", ma))
        self.assertFalse(is_path_pattern_covered("frontend/lib/**", ma))
        self.assertFalse(is_path_pattern_covered("**", ma))
        self.assertFalse(is_path_pattern_covered("*", ma))
        self.assertFalse(is_path_pattern_covered("backend/**", ma))

    def test_validate_paths_safety_rules(self):
        allowed = ["frontend/lib/conversation/application/**"]
        # Valid
        self.assertEqual(validate_paths_safety(["frontend/lib/conversation/application/state.dart"], allowed), [])
        # Absolute path
        self.assertTrue(len(validate_paths_safety(["/etc/passwd"], allowed)) > 0)
        # Directory traversal
        self.assertTrue(len(validate_paths_safety(["../outside.dart"], allowed)) > 0)
        # Outside scope
        self.assertTrue(len(validate_paths_safety(["frontend/lib/other/state.dart"], allowed)) > 0)
        # Protected marker
        self.assertTrue(len(validate_paths_safety(["frontend/lib/conversation/application/security.dart"], allowed)) > 0)


if __name__ == "__main__":
    unittest.main()
