#!/usr/bin/env python3
"""Concrete Coding Executor Adapter for the Codex CLI.

The configured CLI value supplies only the executable path. The adapter owns the Codex
invocation and never accepts command arguments from an ImplementationJob.

This adapter performs Coding execution only. It never touches Git (add/commit/push/branch),
never creates or merges a PR, and never writes Evidence -- those remain the exclusive
responsibility of manufacturing_git.py and manufacturing_runtime.py.
"""

from __future__ import annotations

import json
import os
import posixpath
import shutil
import subprocess
from pathlib import Path
from typing import Sequence

from manufacturing_executor import (
    CodingExecutor,
    ImplementationJob,
    ImplementationResult,
    STATUS_FAILURE,
    STATUS_SUCCESS,
    STATUS_UNKNOWN,
)

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
CLI_ENV_VAR = "MANUFACTURING_CODING_AGENT_CLI"
DEFAULT_CLI_COMMAND = "codex"


class CodingAgentAdapterError(ValueError):
    """Raised when the adapter is misconfigured (e.g. workspace outside the repository)."""


class SubprocessCodingAgentAdapter(CodingExecutor):
    """Adapter that executes an ImplementationJob through a fixed Codex invocation."""

    def __init__(
        self,
        *,
        cli_command: Sequence[str] | None = None,
        workspace_root: Path | None = None,
        repository_root: Path = REPOSITORY_ROOT,
        timeout_seconds: float = 600.0,
    ):
        self.cli_command = (cli_command[0],) if cli_command else None
        self.repository_root = repository_root.resolve()

        resolved_workspace = (workspace_root or self.repository_root).resolve()
        try:
            resolved_workspace.relative_to(self.repository_root)
        except ValueError as error:
            raise CodingAgentAdapterError(
                f"workspace_root must be inside the repository root: {resolved_workspace}"
            ) from error
        self.workspace_root = resolved_workspace
        self.timeout_seconds = timeout_seconds

    @classmethod
    def from_environment(
        cls,
        *,
        default_cli: str | None = DEFAULT_CLI_COMMAND,
        **kwargs: object,
    ) -> "SubprocessCodingAgentAdapter":
        """Build an adapter from the configured Codex executable, if available."""
        raw = os.environ.get(CLI_ENV_VAR, "").strip()
        cli = raw or default_cli
        cli_command = (cli,) if cli else None
        return cls(cli_command=cli_command, **kwargs)  # type: ignore[arg-type]

    def _cli_available(self) -> bool:
        if not self.cli_command:
            return False
        executable = self.cli_command[0]
        return Path(executable).is_file() or shutil.which(executable) is not None

    def execute(self, job: ImplementationJob) -> ImplementationResult:
        if not self._cli_available():
            return ImplementationResult(
                status=STATUS_FAILURE,
                fip=job.fip,
                run_id=job.run_id,
                reason="Coding Agent CLI is not connected/available in this environment",
                changed_files=(),
                summary="Coding Agent adapter has no configured/available CLI",
                unknown=False,
                retry_allowed=False,
                details={"adapter": "SubprocessCodingAgentAdapter", "available": False},
            )

        command = [
            self.cli_command[0],
            "exec",
            "-C",
            str(self.repository_root),
            "-s",
            "workspace-write",
            "--json",
            job.prompt,
        ]

        try:
            completed = subprocess.run(
                command,
                cwd=self.workspace_root,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            return ImplementationResult(
                status=STATUS_UNKNOWN,
                fip=job.fip,
                run_id=job.run_id,
                reason=f"Coding Agent process state is indeterminate: {error}",
                changed_files=(),
                summary="Coding Agent process could not be observed to completion",
                unknown=True,
                retry_allowed=False,
                details={"adapter": "SubprocessCodingAgentAdapter"},
            )

        changed_files, parse_error = self._parse_changed_files(completed.stdout)
        changed_files = self._normalize_changed_files(changed_files)
        details = {
            "adapter": "SubprocessCodingAgentAdapter",
            "returncode": completed.returncode,
            "stdout": completed.stdout[-4000:],
            "stderr": completed.stderr[-4000:],
        }

        if parse_error:
            return ImplementationResult(
                status=STATUS_UNKNOWN,
                fip=job.fip,
                run_id=job.run_id,
                reason=f"Coding Agent output could not be interpreted: {parse_error}",
                changed_files=(),
                summary="Coding Agent output was unparseable",
                unknown=True,
                retry_allowed=False,
                details=details,
            )

        unsafe_paths = [f for f in changed_files if self._is_unsafe_relative_path(f)]
        if unsafe_paths:
            return ImplementationResult(
                status=STATUS_FAILURE,
                fip=job.fip,
                run_id=job.run_id,
                reason=f"Coding Agent reported unsafe changed file paths: {unsafe_paths}",
                changed_files=(),
                summary="Unsafe changed_files rejected by adapter",
                unknown=False,
                retry_allowed=False,
                details=details,
            )

        if completed.returncode == 0:
            return ImplementationResult(
                status=STATUS_SUCCESS,
                fip=job.fip,
                run_id=job.run_id,
                reason="Coding Agent process completed successfully",
                changed_files=changed_files,
                summary="Coding Agent execution completed",
                unknown=False,
                retry_allowed=False,
                details=details,
            )

        return ImplementationResult(
            status=STATUS_FAILURE,
            fip=job.fip,
            run_id=job.run_id,
            reason=f"Coding Agent process exited with non-zero status {completed.returncode}",
            changed_files=(),
            summary="Coding Agent execution failed",
            unknown=False,
            retry_allowed=False,
            details=details,
        )

    @staticmethod
    def _is_unsafe_relative_path(file_path: str) -> bool:
        if file_path.startswith("/") or "\\" in file_path:
            return True
        normalized = posixpath.normpath(file_path)
        return normalized == ".." or normalized.startswith("../")

    def _normalize_changed_file_path(self, file_path: str) -> str:
        """Rewrite a workspace-contained absolute path as workspace-relative.

        Paths that are not POSIX-absolute (e.g. already relative, or Windows-style
        with a backslash) are returned unchanged. Absolute paths that resolve outside
        `workspace_root` are also returned unchanged so `_is_unsafe_relative_path`
        rejects them -- this method never widens what is considered safe.
        """
        if "\\" in file_path or not file_path.startswith("/"):
            return file_path
        try:
            relative = Path(file_path).resolve().relative_to(self.workspace_root)
        except ValueError:
            return file_path
        return relative.as_posix()

    def _normalize_changed_files(self, file_paths: Sequence[str]) -> tuple[str, ...]:
        """Normalize changed_files to workspace-relative paths and deduplicate."""
        normalized: list[str] = []
        seen: set[str] = set()
        for file_path in file_paths:
            candidate = self._normalize_changed_file_path(file_path)
            if candidate not in seen:
                seen.add(candidate)
                normalized.append(candidate)
        return tuple(normalized)

    @staticmethod
    def _extract_file_change_paths(item: object) -> list[str]:
        """Extract `changes[].path` from a Codex `item.completed` file_change item."""
        if not isinstance(item, dict) or item.get("type") != "file_change":
            return []
        changes = item.get("changes")
        if not isinstance(changes, list):
            return []
        paths: list[str] = []
        for change in changes:
            if isinstance(change, dict):
                path = change.get("path")
                if isinstance(path, str) and path.strip():
                    paths.append(path)
        return paths

    @staticmethod
    def _parse_changed_files(stdout: str) -> tuple[tuple[str, ...], str | None]:
        """Parse optional JSON/JSONL output for changed files.

        Recognizes two shapes: a top-level `changed_files` list (legacy/simple format),
        and Codex CLI `item.completed` events whose `item.type == "file_change"` carries
        a `changes` list of `{"path": ..., "kind": ...}` entries. Lines that are not valid
        JSON, or events that are not a recognized file_change, are ignored rather than
        treated as errors -- only a malformed `changed_files` value is treated as an
        unparseable/UNKNOWN result. Duplicate paths are removed while preserving the
        first-seen order.
        """
        text = (stdout or "").strip()
        if not text:
            return (), None
        documents: list[object] = []
        try:
            documents.append(json.loads(text))
        except json.JSONDecodeError:
            for line in text.splitlines():
                line = line.strip()
                if not line:
                    continue
                try:
                    documents.append(json.loads(line))
                except json.JSONDecodeError:
                    continue

        collected: list[str] = []
        seen: set[str] = set()

        for data in documents:
            if not isinstance(data, dict):
                continue

            if "changed_files" in data:
                files = data["changed_files"]
                if not isinstance(files, list) or not all(
                    isinstance(file_path, str) and file_path.strip() for file_path in files
                ):
                    return (), "changed_files must be a list of non-empty strings"
                for file_path in files:
                    if file_path not in seen:
                        seen.add(file_path)
                        collected.append(file_path)

            for path in SubprocessCodingAgentAdapter._extract_file_change_paths(data.get("item")):
                if path not in seen:
                    seen.add(path)
                    collected.append(path)

        return tuple(collected), None
