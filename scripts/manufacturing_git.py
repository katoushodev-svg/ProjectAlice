"""Safely execute a Manufacturing PASS commit, push, and pull request."""

from __future__ import annotations

import re
import shutil
import subprocess
import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence


class GitSafetyError(ValueError):
    pass


class GitResultUnknown(RuntimeError):
    pass


@dataclass(frozen=True)
class CommandResult:
    returncode: int
    stdout: str = ""
    stderr: str = ""


@dataclass(frozen=True)
class AutomationResult:
    status: str
    reason: str
    branch: str | None = None
    branch_created: bool = False
    commit: bool = False
    push: bool = False
    pull_request: bool = False
    commit_hash: str | None = None
    pull_request_url: str | None = None


Runner = Callable[[Sequence[str]], CommandResult]


def _default_runner(arguments: Sequence[str]) -> CommandResult:
    try:
        completed = subprocess.run(
            list(arguments),
            cwd=Path.cwd(),
            check=False,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise GitResultUnknown(f"command result unknown: {' '.join(arguments)}") from error
    return CommandResult(completed.returncode, completed.stdout, completed.stderr)


class ManufacturingGitAutomation:
    def __init__(self, runner: Runner | None = None, gh_available: bool | None = None):
        self._runner = runner or _default_runner
        self._gh_available = shutil.which("gh") is not None if gh_available is None else gh_available

    def apply(
        self,
        candidate: dict,
        *,
        expected_remote: str,
        enabled: bool = False,
    ) -> AutomationResult:
        if not enabled:
            return AutomationResult(
                "HUMAN_GATE",
                "Git automation is disabled; explicit human enablement is required",
            )

        commit_hash: str | None = None
        push_verified = False
        branch: str | None = None
        branch_created = False
        try:
            self._preflight(candidate, expected_remote)
            branch = candidate["branch"]
            changed_files = candidate["changed_files"]
            if self._branch_exists(branch):
                raise GitSafetyError(f"branch collision: {branch}")
            else:
                self._run("git", "switch", "-c", branch)
                branch_created = True

            self._run("git", "add", "--", *changed_files)
            message = candidate["commit_candidate"]["message"]
            self._run("git", "commit", "-m", message)
            commit_hash = self._read("git", "rev-parse", "HEAD")
            self._run("git", "push", "--set-upstream", "origin", branch)
            self._verify_remote_branch(branch, commit_hash)
            push_verified = True

            if not self._gh_available:
                raise GitSafetyError("GitHub CLI is not installed; PR creation requires a human gate")
            pr = candidate["pr_candidate"]
            pr_url = self._read(
                "gh",
                "pr",
                "create",
                "--base",
                "main",
                "--head",
                branch,
                "--title",
                pr["title"],
                "--body",
                pr["body"],
            )
            if not re.search(r"https://github\.com/[^\s]+/pull/\d+", pr_url):
                raise GitResultUnknown("pull request creation result is unknown")
            return AutomationResult(
                "PASS",
                "commit, push, and pull request verified",
                branch=branch,
                branch_created=branch_created,
                commit=True,
                push=True,
                pull_request=True,
                commit_hash=commit_hash,
                pull_request_url=pr_url.strip(),
            )
        except GitResultUnknown as error:
            return AutomationResult(
                "HUMAN_GATE",
                str(error),
                branch=branch,
                branch_created=branch_created,
                commit=commit_hash is not None,
                push=push_verified,
                commit_hash=commit_hash,
            )
        except GitSafetyError as error:
            return AutomationResult(
                "HUMAN_GATE",
                str(error),
                branch=branch,
                branch_created=branch_created,
                commit=commit_hash is not None,
                push=push_verified,
                commit_hash=commit_hash,
            )

    def _preflight(self, candidate: dict, expected_remote: str) -> None:
        if candidate.get("candidate_status") != "READY":
            raise GitSafetyError("Manufacturing PASS candidate is required")
        branch = candidate.get("branch")
        if not isinstance(branch, str) or branch == "main" or not branch.startswith("fip-"):
            raise GitSafetyError("an isolated FIP branch is required")
        if candidate.get("base") != "main":
            raise GitSafetyError("PR base must be main")
        remote = self._read("git", "remote", "get-url", "origin")
        if remote != expected_remote:
            raise GitSafetyError("unexpected git remote")
        current_branch = self._read("git", "branch", "--show-current")
        if current_branch not in ("main", branch):
            raise GitSafetyError("unexpected current branch")
        changed_files = candidate.get("changed_files")
        if not isinstance(changed_files, list) or not changed_files:
            raise GitSafetyError("commit file list is empty")
        commit_files = candidate.get("commit_candidate", {}).get("files")
        if commit_files != changed_files:
            raise GitSafetyError("commit file list does not match the Manufacturing Record")
        status = self._run(
            "git", "status", "--porcelain", "--untracked-files=all"
        ).stdout
        actual_paths = {line[3:] for line in status.splitlines() if len(line) >= 4}
        if actual_paths != set(changed_files):
            raise GitSafetyError("worktree changes do not exactly match the Manufacturing Record")

    def _branch_exists(self, branch: str) -> bool:
        result = self._run_raw("git", "show-ref", "--verify", "--quiet", f"refs/heads/{branch}")
        if result.returncode not in (0, 1):
            raise GitSafetyError("unable to determine branch collision")
        return result.returncode == 0

    def _verify_remote_branch(self, branch: str, commit_hash: str) -> None:
        remote_hash = self._read("git", "ls-remote", "origin", f"refs/heads/{branch}")
        expected = f"{commit_hash}\trefs/heads/{branch}"
        if remote_hash != expected:
            raise GitResultUnknown("pushed commit could not be verified")

    def _read(self, *arguments: str) -> str:
        result = self._run(*arguments)
        return result.stdout.strip()

    def _run(self, *arguments: str) -> CommandResult:
        result = self._run_raw(*arguments)
        if result.returncode != 0:
            raise GitSafetyError(result.stderr.strip() or f"command failed: {' '.join(arguments)}")
        return result

    def _run_raw(self, *arguments: str) -> CommandResult:
        try:
            return self._runner(arguments)
        except GitResultUnknown:
            raise
        except Exception as error:
            raise GitResultUnknown(f"command result unknown: {' '.join(arguments)}") from error


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--expected-remote", required=True)
    parser.add_argument(
        "--execute",
        action="store_true",
        help="explicitly enable branch, commit, push, and PR operations",
    )
    args = parser.parse_args(argv)
    try:
        candidate = json.loads(args.candidate.read_text(encoding="utf-8"))
        result = ManufacturingGitAutomation().apply(
            candidate,
            expected_remote=args.expected_remote,
            enabled=args.execute,
        )
    except (OSError, TypeError, json.JSONDecodeError) as error:
        print(json.dumps({"status": "HUMAN_GATE", "reason": str(error)}))
        return 2
    print(json.dumps(result.__dict__, ensure_ascii=True, indent=2))
    return 0 if result.status == "PASS" else 2


if __name__ == "__main__":
    raise SystemExit(main())