"""Verification boundary for Manufacturing jobs.

Only this module starts verification commands.  It deliberately contains no Git,
Evidence, PR, or merge operation.
"""
from __future__ import annotations

import json
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Sequence

PASS = "PASS"
FAIL = "FAIL"
UNKNOWN = "UNKNOWN"
FIXABLE_FINDINGS = "FIXABLE_FINDINGS"
ESCALATE = "ESCALATE"


@dataclass(frozen=True)
class CheckResult:
    status: str
    summary: str


@dataclass(frozen=True)
class ReviewResult:
    conclusion: str
    evidence: str
    severity: str
    allowed_scope: str
    retest_required: bool


Runner = Callable[[Sequence[str], Path, float], subprocess.CompletedProcess[str]]


def _run(command: Sequence[str], cwd: Path, timeout: float) -> subprocess.CompletedProcess[str]:
    return subprocess.run(list(command), cwd=cwd, text=True, capture_output=True,
                          timeout=timeout, check=False)


class ManufacturingVerification:
    def __init__(self, repository_root: Path | None = None, *, runner: Runner = _run,
                 timeout_seconds: float = 900.0, codex_command: str = "codex"):
        self.root = (repository_root or Path(__file__).resolve().parents[1]).resolve()
        self.runner = runner
        self.timeout_seconds = timeout_seconds
        self.codex_command = codex_command

    def focused_test(self) -> CheckResult:
        return self._command("focused_test", ("flutter", "test", "test/conversation/presentation"), self.root / "frontend")

    def analyze(self) -> CheckResult:
        return self._command("analyze", ("flutter", "analyze"), self.root / "frontend")

    def full_regression(self) -> CheckResult:
        flutter = self._command("full_regression", ("flutter", "test"), self.root / "frontend")
        if flutter.status != PASS:
            return flutter
        return self._command("full_regression", ("python3", "-m", "unittest", "discover", "scripts", "-p", "test_*.py"), self.root)

    def review(self, prompt: str) -> ReviewResult:
        if not prompt or not isinstance(prompt, str) or shutil.which(self.codex_command) is None:
            return ReviewResult(ESCALATE, "Codex read-only review is unavailable", "unknown", "unknown", False)
        try:
            completed = self.runner((self.codex_command, "exec", "-C", str(self.root), "-s", "read-only", "--json", prompt), self.root, self.timeout_seconds)
        except (OSError, subprocess.TimeoutExpired) as error:
            return ReviewResult(ESCALATE, f"review state is unknown: {error}", "unknown", "unknown", False)
        if completed.returncode != 0:
            return ReviewResult(ESCALATE, "read-only review command failed", "unknown", "unknown", False)
        return self._parse_review(completed.stdout)

    def _command(self, name: str, command: Sequence[str], cwd: Path) -> CheckResult:
        try:
            completed = self.runner(command, cwd, self.timeout_seconds)
        except (OSError, subprocess.TimeoutExpired) as error:
            return CheckResult(UNKNOWN, f"{name} state is unknown: {error}")
        if completed.returncode:
            return CheckResult(FAIL, f"{name} failed (exit {completed.returncode})")
        return CheckResult(PASS, f"{name} passed")

    @staticmethod
    def _parse_review(output: str) -> ReviewResult:
        # Codex --json can be JSONL.  Exactly one final, complete conclusion is
        # required; arbitrary text and contradictory conclusions fail closed.
        values: list[dict[str, Any]] = []
        try:
            parsed = json.loads(output)
            values = [parsed] if isinstance(parsed, dict) else []
        except json.JSONDecodeError:
            try:
                values = [json.loads(line) for line in output.splitlines() if line.strip()]
            except json.JSONDecodeError:
                return ReviewResult(ESCALATE, "malformed review output", "unknown", "unknown", False)
        conclusions = []
        for value in values:
            if not isinstance(value, dict):
                return ReviewResult(ESCALATE, "malformed review output", "unknown", "unknown", False)
            candidate = value
            # Codex --json emits event objects. Only an agent final/message text
            # containing one complete JSON object is a review result.
            item = value.get("item")
            if isinstance(item, dict) and item.get("type") in {"agent_message", "message"}:
                text = item.get("text") or item.get("content")
                if isinstance(text, str):
                    if '"conclusion"' not in text:
                        continue
                    try:
                        candidate = json.loads(text)
                    except json.JSONDecodeError:
                        return ReviewResult(ESCALATE, "malformed structured review text", "unknown", "unknown", False)
            if isinstance(candidate, dict) and candidate.get("conclusion") in {PASS, FIXABLE_FINDINGS, ESCALATE}:
                conclusions.append(candidate)
        if not conclusions:
            return ReviewResult(ESCALATE, "review conclusion is missing or ambiguous", "unknown", "unknown", False)
        value = conclusions[-1]
        evidence = value.get("evidence")
        severity = value.get("severity", "none")
        scope = value.get("allowed_scope", "manifest")
        retest = value.get("retest_required", value["conclusion"] == FIXABLE_FINDINGS)
        if not isinstance(evidence, str) or not evidence.strip() or not isinstance(severity, str) or not isinstance(scope, str) or not isinstance(retest, bool):
            return ReviewResult(ESCALATE, "review conclusion is incomplete", "unknown", "unknown", False)
        return ReviewResult(value["conclusion"], evidence, severity, scope, retest)
