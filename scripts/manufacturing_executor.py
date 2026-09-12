#!/usr/bin/env python3
"""Manufacturing Minimal Coding Executor boundary and adapter abstraction."""

from __future__ import annotations

import abc
import argparse
import fnmatch
import json
import posixpath
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping, Sequence

STATUS_SUCCESS = "SUCCESS"
STATUS_FAILURE = "FAILURE"
STATUS_UNKNOWN = "UNKNOWN"
VALID_STATUSES = {STATUS_SUCCESS, STATUS_FAILURE, STATUS_UNKNOWN}

FORBIDDEN_MARKERS = (
    "architecture",
    "api",
    "database",
    "security",
    "safety",
)

DEFAULT_PROHIBITED_CHANGES = (
    "design",
    "architecture",
    "api_contract",
    "database",
    "security_safety",
    "phase_boundary",
    "fip_scope",
    "unknown_changes",
    "new_dependency",
)

DISALLOWED_JOB_FIELDS = {
    "command",
    "shell_command",
    "exec",
    "script",
    "git_command",
    "push",
    "pr",
    "merge",
    "commit",
}


class ExecutorValidationError(ValueError):
    """Raised when an ImplementationJob violates validation rules."""

    pass


class ExecutorSecurityError(ExecutorValidationError):
    """Raised when an ImplementationJob violates security or safety constraints."""

    pass


@dataclass(frozen=True)
class ImplementationJob:
    fip: str
    run_id: str
    source_of_truth: tuple[str, ...]
    allowed_paths: tuple[str, ...]
    prohibited_changes: tuple[str, ...] = DEFAULT_PROHIBITED_CHANGES
    context: Mapping[str, Any] | None = None
    prompt: str = ""

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> ImplementationJob:
        if not isinstance(data, Mapping):
            raise ExecutorValidationError("job must be a mapping object")

        # Security check: disallow arbitrary command / git execution fields in the job
        disallowed = [key for key in data if key in DISALLOWED_JOB_FIELDS]
        if disallowed:
            raise ExecutorSecurityError(
                f"job contains prohibited execution fields: {', '.join(sorted(disallowed))}"
            )

        fip = data.get("fip")
        if not isinstance(fip, str) or not fip.strip():
            raise ExecutorValidationError("fip must be a non-empty string")

        run_id = data.get("run_id")
        if not isinstance(run_id, str) or not run_id.strip():
            raise ExecutorValidationError("run_id must be a non-empty string")

        sot = data.get("source_of_truth")
        if not isinstance(sot, (list, tuple)) or not sot:
            raise ExecutorValidationError("source_of_truth must be a non-empty sequence")
        source_of_truth = tuple(str(item) for item in sot if isinstance(item, str) and item.strip())
        if len(source_of_truth) != len(sot):
            raise ExecutorValidationError("source_of_truth contains empty or non-string entries")

        paths = data.get("allowed_paths")
        if not isinstance(paths, (list, tuple)) or not paths:
            raise ExecutorValidationError("allowed_paths must be a non-empty sequence")
        allowed_paths = tuple(str(item) for item in paths if isinstance(item, str) and item.strip())
        if len(allowed_paths) != len(paths):
            raise ExecutorValidationError("allowed_paths contains empty or non-string entries")

        prohibited = data.get("prohibited_changes", DEFAULT_PROHIBITED_CHANGES)
        if not isinstance(prohibited, (list, tuple)) or not prohibited:
            raise ExecutorValidationError("prohibited_changes must be a non-empty sequence")
        prohibited_changes = tuple(
            str(item) for item in prohibited if isinstance(item, str) and item.strip()
        )
        if len(prohibited_changes) != len(prohibited):
            raise ExecutorValidationError("prohibited_changes contains empty or non-string entries")

        context = data.get("context")
        if context is not None:
            if not isinstance(context, Mapping):
                raise ExecutorValidationError("context must be a mapping if provided")
            context_disallowed = [k for k in context if k in DISALLOWED_JOB_FIELDS]
            if context_disallowed:
                raise ExecutorSecurityError(
                    f"job context contains prohibited execution fields: {', '.join(sorted(context_disallowed))}"
                )

        prompt = data.get("prompt", "")
        if not isinstance(prompt, str):
            raise ExecutorValidationError("prompt must be a string")

        return cls(
            fip=fip.strip(),
            run_id=run_id.strip(),
            source_of_truth=source_of_truth,
            allowed_paths=allowed_paths,
            prohibited_changes=prohibited_changes,
            context=context,
            prompt=prompt,
        )

    def to_dict(self) -> dict[str, Any]:
        result: dict[str, Any] = {
            "fip": self.fip,
            "run_id": self.run_id,
            "source_of_truth": list(self.source_of_truth),
            "allowed_paths": list(self.allowed_paths),
            "prohibited_changes": list(self.prohibited_changes),
            "prompt": self.prompt,
        }
        if self.context is not None:
            result["context"] = dict(self.context)
        return result


@dataclass(frozen=True)
class ImplementationResult:
    status: str
    fip: str
    run_id: str
    reason: str
    changed_files: tuple[str, ...] = ()
    summary: str = ""
    unknown: bool = False
    retry_allowed: bool = False
    details: Mapping[str, Any] | None = None

    def to_dict(self) -> dict[str, Any]:
        res: dict[str, Any] = {
            "status": self.status,
            "fip": self.fip,
            "run_id": self.run_id,
            "reason": self.reason,
            "changed_files": list(self.changed_files),
            "summary": self.summary,
            "unknown": self.unknown,
            "retry_allowed": self.retry_allowed,
        }
        if self.details is not None:
            res["details"] = dict(self.details)
        return res


class CodingExecutor(abc.ABC):
    """Abstract adapter interface for a coding execution backend."""

    @abc.abstractmethod
    def execute(self, job: ImplementationJob) -> ImplementationResult:
        """Execute the job and return an ImplementationResult."""
        pass


class UnconnectedCodingExecutorAdapter(CodingExecutor):
    """Default unconnected adapter that reports clear FAILURE/UNAVAILABLE.

    Must not forge SUCCESS or report UNKNOWN.
    """

    def execute(self, job: ImplementationJob) -> ImplementationResult:
        return ImplementationResult(
            status=STATUS_FAILURE,
            fip=job.fip,
            run_id=job.run_id,
            reason="Coding Executor is not connected (no active adapter configured)",
            changed_files=(),
            summary="Coding Executor adapter is unconnected",
            unknown=False,
            retry_allowed=False,
            details={"adapter": "UnconnectedCodingExecutorAdapter", "available": False},
        )


def is_path_pattern_covered(job_pattern: str, manifest_pattern: str) -> bool:
    """Check whether a job path pattern is strictly covered by a manifest path pattern.

    Ensures that a Job cannot widen the permission scope beyond the Manifest.
    """
    if job_pattern == manifest_pattern:
        return True

    if manifest_pattern.endswith("/**"):
        base_dir = manifest_pattern[:-3]
        prefix = base_dir + "/"
        if job_pattern == base_dir or job_pattern.startswith(prefix):
            return True
        return False

    if manifest_pattern.endswith("/*"):
        base_dir = manifest_pattern[:-2]
        prefix = base_dir + "/"
        if job_pattern == base_dir:
            return True
        if job_pattern.startswith(prefix) and "/" not in job_pattern[len(prefix) :]:
            return True
        return False

    # Concrete file in manifest: job pattern must match exactly
    if not any(c in manifest_pattern for c in "*?["):
        return job_pattern == manifest_pattern

    # If job_pattern is a concrete path and matches manifest glob
    if not any(c in job_pattern for c in "*?["):
        return fnmatch.fnmatch(job_pattern, manifest_pattern)

    return False


def validate_paths_safety(changed_files: Sequence[str], allowed_paths: Sequence[str]) -> list[str]:
    """Validate that changed_files conform strictly to allowed_paths and contains no boundary escapes."""
    failures: list[str] = []
    if not isinstance(changed_files, (list, tuple)):
        return ["changed_files must be a sequence"]

    for file_path in changed_files:
        if not isinstance(file_path, str) or not file_path.strip():
            failures.append("changed_files contains an invalid path")
            continue
        normalized_path = posixpath.normpath(file_path)
        if (
            file_path.startswith("/")
            or "\\" in file_path
            or normalized_path == ".."
            or normalized_path.startswith("../")
        ):
            failures.append(f"unsafe changed path: {file_path}")
            continue
        if not any(
            fnmatch.fnmatch(normalized_path, pattern) for pattern in allowed_paths
        ):
            failures.append(f"file is outside allowed_paths: {file_path}")
        lowered = normalized_path.lower()
        if any(marker in lowered for marker in FORBIDDEN_MARKERS):
            failures.append(f"protected boundary path: {file_path}")
    return failures


class ManufacturingExecutor:
    """Manufacturing Pipeline dedicated Minimal Coding Executor.

    Coordinates Job validation, adapter dispatch, and result normalization
    without performing any Git, Evidence, PR, or shell command operations.
    """

    def __init__(
        self,
        *,
        adapter: CodingExecutor | None = None,
        manifest: Mapping[str, Any] | None = None,
    ):
        self.adapter = adapter or UnconnectedCodingExecutorAdapter()
        self.manifest = dict(manifest) if manifest is not None else None

    def validate_job(self, job: ImplementationJob) -> None:
        """Validate the ImplementationJob against internal safety rules and optional manifest."""
        if not isinstance(job, ImplementationJob):
            raise ExecutorValidationError("job must be an ImplementationJob instance")

        if not job.fip or not job.fip.strip():
            raise ExecutorValidationError("fip is required")
        if not job.run_id or not job.run_id.strip():
            raise ExecutorValidationError("run_id is required")
        if not job.source_of_truth:
            raise ExecutorValidationError("source_of_truth is required")
        if not job.allowed_paths:
            raise ExecutorValidationError("allowed_paths is required")
        if not job.prohibited_changes:
            raise ExecutorValidationError("prohibited_changes is required")

        # Validate path patterns in allowed_paths
        for pattern in job.allowed_paths:
            if not isinstance(pattern, str) or not pattern.strip():
                raise ExecutorValidationError("allowed_paths pattern must be a non-empty string")
            norm = posixpath.normpath(pattern)
            if pattern.startswith("/") or "\\" in pattern or norm == ".." or norm.startswith("../"):
                raise ExecutorSecurityError(f"unsafe allowed_paths pattern: {pattern}")

        # Validate Source of Truth paths
        for doc_path in job.source_of_truth:
            if not isinstance(doc_path, str) or not doc_path.strip():
                raise ExecutorValidationError("source_of_truth entry must be a non-empty string")
            norm = posixpath.normpath(doc_path)
            if doc_path.startswith("/") or "\\" in doc_path or norm == ".." or norm.startswith("../"):
                raise ExecutorSecurityError(f"unsafe source_of_truth path: {doc_path}")

        # If manifest is present, enforce strict manifest alignment
        if self.manifest is not None:
            fip_manifest = self.manifest.get(job.fip)
            if not isinstance(fip_manifest, dict):
                raise ExecutorValidationError(f"FIP {job.fip} is not present in manifest")

            if fip_manifest.get("approval_status") != "Approved / Implementation Ready":
                raise ExecutorValidationError(
                    f"{job.fip} is not 'Approved / Implementation Ready' in manifest"
                )

            manifest_plan = fip_manifest.get("plan")
            if manifest_plan and manifest_plan not in job.source_of_truth:
                raise ExecutorValidationError(
                    f"source_of_truth is missing required plan: {manifest_plan}"
                )

            manifest_allowed = fip_manifest.get("allowed_paths", [])
            for p in job.allowed_paths:
                # Every path pattern in job must be strictly covered by manifest allowed_paths
                if not any(
                    is_path_pattern_covered(p, ma)
                    for ma in manifest_allowed
                ):
                    raise ExecutorSecurityError(
                        f"allowed_paths entry '{p}' is not permitted by manifest"
                    )

    def execute_job(self, job: ImplementationJob | Mapping[str, Any]) -> ImplementationResult:
        """Process an ImplementationJob through validation, adapter dispatch, and normalization."""
        # 1. Parse / construct Job
        if isinstance(job, Mapping):
            job_obj = ImplementationJob.from_dict(job)
        elif isinstance(job, ImplementationJob):
            job_obj = job
        else:
            raise ExecutorValidationError(f"job must be ImplementationJob or mapping, got {type(job)}")

        # 2. Validate Job
        self.validate_job(job_obj)

        # 3. Call Adapter
        try:
            raw_result = self.adapter.execute(job_obj)
        except Exception as error:
            # Catch crashes / unexpected exceptions and return UNKNOWN
            # Crucial: retry_allowed is False on UNKNOWN
            return ImplementationResult(
                status=STATUS_UNKNOWN,
                fip=job_obj.fip,
                run_id=job_obj.run_id,
                reason=f"Coding Executor crashed or encountered unexpected error: {error}",
                changed_files=(),
                summary="Executor runtime crash",
                unknown=True,
                retry_allowed=False,
            )

        # 4. Normalize Result
        normalized = self._normalize_result(job_obj, raw_result)

        # 5. Security & Safety: verify changed_files strictly within allowed_paths
        if normalized.status == STATUS_SUCCESS:
            path_failures = validate_paths_safety(normalized.changed_files, job_obj.allowed_paths)
            if path_failures:
                return ImplementationResult(
                    status=STATUS_FAILURE,
                    fip=job_obj.fip,
                    run_id=job_obj.run_id,
                    reason=f"Implementation produced unauthorized file changes: {'; '.join(path_failures)}",
                    changed_files=(),
                    summary="Scope violation in changed files",
                    unknown=False,
                    retry_allowed=False,
                )

        return normalized

    def _normalize_result(
        self, job: ImplementationJob, result: ImplementationResult | Any
    ) -> ImplementationResult:
        if not isinstance(result, ImplementationResult):
            return ImplementationResult(
                status=STATUS_UNKNOWN,
                fip=job.fip,
                run_id=job.run_id,
                reason=f"Adapter returned non-ImplementationResult: {type(result)}",
                changed_files=(),
                summary="Invalid result type from adapter",
                unknown=True,
                retry_allowed=False,
            )

        status = result.status if result.status in VALID_STATUSES else STATUS_UNKNOWN
        is_unknown = (status == STATUS_UNKNOWN) or result.unknown
        if is_unknown:
            status = STATUS_UNKNOWN

        changed_files = tuple(
            str(f) for f in result.changed_files if isinstance(f, str) and f.strip()
        )

        # UNKNOWN must never be automatically retried
        retry_allowed = False if is_unknown else bool(result.retry_allowed)

        return ImplementationResult(
            status=status,
            fip=job.fip,
            run_id=job.run_id,
            reason=result.reason or ("Completed successfully" if status == STATUS_SUCCESS else "Execution failed"),
            changed_files=changed_files,
            summary=result.summary or f"Execution result: {status}",
            unknown=is_unknown,
            retry_allowed=retry_allowed,
            details=result.details,
        )


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Manufacturing Coding Executor CLI")
    parser.add_argument("--job", type=str, help="Path to JSON job file or JSON string")
    parser.add_argument("--manifest", type=str, default="scripts/manufacturing_manifest.json")
    args = parser.parse_args(argv)

    if not args.job:
        job_data = json.load(sys.stdin)
    else:
        job_path = Path(args.job)
        if job_path.is_file():
            job_data = json.loads(job_path.read_text(encoding="utf-8"))
        else:
            job_data = json.loads(args.job)

    manifest_data = None
    manifest_path = Path(args.manifest)
    if manifest_path.is_file():
        manifest_data = json.loads(manifest_path.read_text(encoding="utf-8"))

    executor = ManufacturingExecutor(manifest=manifest_data)
    result = executor.execute_job(job_data)
    print(json.dumps(result.to_dict(), indent=2))
    return 0 if result.status == STATUS_SUCCESS else 1


if __name__ == "__main__":
    sys.exit(main())
