"""Safely execute a Manufacturing PASS commit, push, and pull request."""

from __future__ import annotations

import re
import shutil
import subprocess
import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Mapping, Sequence


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


@dataclass(frozen=True)
class EvidenceCommitAuthorization:
    run_id: str
    branch: str
    implementation_commit_sha: str
    primary_evidence_id: str
    primary_evidence_path: str
    primary_evidence_generation_confirmed: bool
    push_not_performed: bool
    unresolved_unknown_absent: bool


@dataclass(frozen=True)
class EvidenceCommitResult:
    status: str
    operation: str
    reason: str
    run_id: str | None = None
    branch: str | None = None
    implementation_commit_sha: str | None = None
    evidence_commit_sha: str | None = None
    committed_path: str | None = None


@dataclass(frozen=True)
class PushResult:
    status: str
    operation: str
    reason: str
    run_id: str
    branch: str
    pushed_commit_sha: str | None = None


@dataclass(frozen=True)
class RemoteVerificationResult:
    status: str
    operation: str
    reason: str
    run_id: str
    branch: str
    expected_branch: str
    expected_commit_sha: str
    remote_branch: str | None = None
    remote_commit_sha: str | None = None


@dataclass(frozen=True)
class PullRequestResult:
    status: str
    operation: str
    reason: str
    run_id: str
    branch: str
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

    def commit_evidence(
        self,
        authorization: EvidenceCommitAuthorization | Mapping[str, object],
        evidence_paths: Sequence[str],
        branch: str,
        implementation_commit_sha: str,
        message: str,
    ) -> EvidenceCommitResult:
        operation = "commit_evidence"
        values: dict[str, object] = {}
        try:
            values = self._validate_evidence_authorization(authorization)
            run_id = self._required_string(values, "run_id")
            authorized_branch = self._required_string(values, "branch")
            authorized_sha = self._required_string(values, "implementation_commit_sha")
            evidence_id = self._required_string(values, "primary_evidence_id")
            authorized_path = self._required_string(values, "primary_evidence_path")
            self._validate_evidence_preconditions(
                values,
                evidence_paths,
                branch,
                implementation_commit_sha,
                authorized_branch,
                authorized_sha,
                evidence_id,
                authorized_path,
                message,
            )

            current_branch = self._read_required("git", "branch", "--show-current")
            if current_branch != branch:
                raise GitSafetyError("current branch does not match the Evidence authorization")
            current_sha = self._read_required("git", "rev-parse", "HEAD")
            if current_sha != implementation_commit_sha:
                raise GitSafetyError("current HEAD does not match the Implementation Commit")
            status = self._run(
                "git", "status", "--porcelain", "--untracked-files=all"
            ).stdout
            actual_paths = {line[3:] for line in status.splitlines() if len(line) >= 4}
            if actual_paths != {authorized_path}:
                raise GitSafetyError("worktree changes do not exactly match Primary Evidence")

            self._run("git", "add", "--", authorized_path)
            self._run("git", "commit", "-m", message)
            evidence_commit_sha = self._read_required("git", "rev-parse", "HEAD")
            parent_sha = self._read_required("git", "rev-parse", "HEAD^")
            if parent_sha != implementation_commit_sha:
                raise GitResultUnknown("Evidence Commit parent could not be verified")
            committed_paths = self._read_commit_paths(evidence_commit_sha)
            if committed_paths != {authorized_path}:
                raise GitResultUnknown("Evidence Commit paths could not be verified")
            return EvidenceCommitResult(
                "SUCCESS",
                operation,
                "Primary Evidence committed and verified",
                run_id=run_id,
                branch=branch,
                implementation_commit_sha=implementation_commit_sha,
                evidence_commit_sha=evidence_commit_sha,
                committed_path=authorized_path,
            )
        except GitResultUnknown as error:
            return EvidenceCommitResult(
                "UNKNOWN",
                operation,
                str(error),
                run_id=values.get("run_id") if isinstance(values.get("run_id"), str) else None,
                branch=branch,
                implementation_commit_sha=implementation_commit_sha,
                committed_path=values.get("primary_evidence_path")
                if isinstance(values.get("primary_evidence_path"), str)
                else None,
            )
        except GitSafetyError as error:
            return EvidenceCommitResult(
                "FAILURE",
                operation,
                str(error),
                run_id=values.get("run_id") if isinstance(values.get("run_id"), str) else None,
                branch=branch,
                implementation_commit_sha=implementation_commit_sha,
                committed_path=values.get("primary_evidence_path")
                if isinstance(values.get("primary_evidence_path"), str)
                else None,
            )

    def push(
        self,
        run_id: str,
        branch: str,
        expected_commit_sha: str,
        *,
        evidence_commit_completed: bool,
        unresolved_unknown_absent: bool,
        remote: str = "origin",
    ) -> PushResult:
        operation = "push"
        try:
            self._validate_run_identity(run_id, branch, expected_commit_sha)
            self._validate_isolated_branch(branch)
            if evidence_commit_completed is not True:
                raise GitSafetyError("Evidence Commit must succeed before Push")
            if unresolved_unknown_absent is not True:
                raise GitSafetyError("unresolved UNKNOWN state blocks Push")
            if not remote or remote.startswith("-"):
                raise GitSafetyError("remote is invalid")

            current_branch = self._read_required("git", "branch", "--show-current")
            if current_branch != branch:
                raise GitSafetyError("current branch does not match Push target")
            current_sha = self._read_required("git", "rev-parse", "HEAD")
            if current_sha != expected_commit_sha:
                raise GitSafetyError("current HEAD does not match expected commit")
            self._run("git", "push", "--set-upstream", remote, branch)
            pushed_sha = self._read_required("git", "rev-parse", "HEAD")
            if pushed_sha != expected_commit_sha:
                raise GitResultUnknown("pushed commit SHA could not be verified")
            return PushResult(
                "SUCCESS", operation, "Push completed and commit SHA verified",
                run_id, branch, pushed_sha,
            )
        except GitResultUnknown as error:
            return PushResult("UNKNOWN", operation, str(error), run_id, branch)
        except GitSafetyError as error:
            return PushResult("FAILURE", operation, str(error), run_id, branch)

    def verify_remote(
        self,
        push_result: PushResult,
        *,
        expected_branch: str,
        expected_commit_sha: str,
        remote: str = "origin",
    ) -> RemoteVerificationResult:
        operation = "verify_remote"
        run_id = push_result.run_id
        branch = push_result.branch
        try:
            self._validate_run_identity(run_id, branch, expected_commit_sha)
            self._validate_isolated_branch(expected_branch)
            if push_result.status != "SUCCESS":
                raise GitSafetyError("Remote Verification requires successful Push")
            if push_result.branch != expected_branch:
                raise GitSafetyError("Push branch does not match expected branch")
            if push_result.pushed_commit_sha != expected_commit_sha:
                raise GitSafetyError("Push commit does not match expected commit")
            if not remote or remote.startswith("-"):
                raise GitSafetyError("remote is invalid")

            output = self._read("git", "ls-remote", remote, f"refs/heads/{expected_branch}")
            fields = output.split()
            if len(fields) != 2 or not fields[1].startswith("refs/heads/"):
                raise GitResultUnknown("remote branch state could not be parsed")
            remote_commit_sha = fields[0]
            remote_branch = fields[1].removeprefix("refs/heads/")
            if not remote_commit_sha or not remote_branch:
                raise GitResultUnknown("remote branch state is incomplete")
            if remote_branch != expected_branch or remote_commit_sha != expected_commit_sha:
                return RemoteVerificationResult(
                    "FAILURE", operation, "remote branch or commit does not match",
                    run_id, branch, expected_branch, expected_commit_sha,
                    remote_branch, remote_commit_sha,
                )
            return RemoteVerificationResult(
                "SUCCESS", operation, "remote branch and commit verified",
                run_id, branch, expected_branch, expected_commit_sha,
                remote_branch, remote_commit_sha,
            )
        except GitResultUnknown as error:
            return RemoteVerificationResult(
                "UNKNOWN", operation, str(error), run_id, branch,
                expected_branch, expected_commit_sha,
            )
        except GitSafetyError as error:
            return RemoteVerificationResult(
                "FAILURE", operation, str(error), run_id, branch,
                expected_branch, expected_commit_sha,
            )

    def create_pull_request(
        self,
        verification_result: RemoteVerificationResult,
        *,
        title: str,
        body: str,
        base: str = "main",
    ) -> PullRequestResult:
        operation = "create_pull_request"
        run_id = verification_result.run_id
        branch = verification_result.branch
        try:
            self._validate_isolated_branch(branch)
            if verification_result.status != "SUCCESS":
                raise GitSafetyError("Pull Request requires successful Remote Verification")
            if verification_result.expected_branch != branch:
                raise GitSafetyError("verified branch does not match Pull Request head")
            if base != "main":
                raise GitSafetyError("Pull Request base must be main")
            if not title or not body:
                raise GitSafetyError("Pull Request title and body are required")
            if not self._gh_available:
                raise GitSafetyError("GitHub CLI is not available")
            output = self._read(
                "gh", "pr", "create", "--base", base, "--head", branch,
                "--title", title, "--body", body,
            )
            match = re.search(r"https://github\.com/[^\s]+/pull/\d+", output)
            if not match:
                raise GitResultUnknown("Pull Request URL could not be verified")
            return PullRequestResult(
                "SUCCESS", operation, "Pull Request created and URL verified",
                run_id, branch, match.group(0),
            )
        except GitResultUnknown as error:
            return PullRequestResult("UNKNOWN", operation, str(error), run_id, branch)
        except GitSafetyError as error:
            return PullRequestResult("FAILURE", operation, str(error), run_id, branch)

    @staticmethod
    def _validate_run_identity(run_id: str, branch: str, commit_sha: str) -> None:
        if not isinstance(run_id, str) or not run_id:
            raise GitSafetyError("run_id is required")
        if not isinstance(branch, str) or not branch:
            raise GitSafetyError("branch is required")
        if not isinstance(commit_sha, str) or not commit_sha:
            raise GitSafetyError("commit SHA is required")

    @staticmethod
    def _validate_isolated_branch(branch: str) -> None:
        if not branch.startswith("fip-") or branch == "main":
            raise GitSafetyError("an isolated FIP branch is required")

    @staticmethod
    def _validate_evidence_authorization(
        authorization: EvidenceCommitAuthorization | Mapping[str, object],
    ) -> dict[str, object]:
        if isinstance(authorization, EvidenceCommitAuthorization):
            return {
                "run_id": authorization.run_id,
                "branch": authorization.branch,
                "implementation_commit_sha": authorization.implementation_commit_sha,
                "primary_evidence_id": authorization.primary_evidence_id,
                "primary_evidence_path": authorization.primary_evidence_path,
                "primary_evidence_generation_confirmed": authorization.primary_evidence_generation_confirmed,
                "push_not_performed": authorization.push_not_performed,
                "unresolved_unknown_absent": authorization.unresolved_unknown_absent,
            }
        if not isinstance(authorization, Mapping):
            raise GitSafetyError("Evidence authorization must be an object")
        required = (
            "run_id",
            "branch",
            "implementation_commit_sha",
            "primary_evidence_id",
            "primary_evidence_path",
            "primary_evidence_generation_confirmed",
            "push_not_performed",
            "unresolved_unknown_absent",
        )
        missing = [field for field in required if field not in authorization]
        if missing:
            raise GitSafetyError("Evidence authorization fields are missing: " + ", ".join(missing))
        return {field: authorization[field] for field in required}

    @staticmethod
    def _required_string(values: Mapping[str, object], field: str) -> str:
        value = values.get(field)
        if not isinstance(value, str) or not value:
            raise GitSafetyError(f"Evidence authorization field is invalid: {field}")
        return value

    def _validate_evidence_preconditions(
        self,
        values: Mapping[str, object],
        evidence_paths: Sequence[str],
        branch: str,
        implementation_commit_sha: str,
        authorized_branch: str,
        authorized_sha: str,
        evidence_id: str,
        authorized_path: str,
        message: str,
    ) -> None:
        booleans = (
            "primary_evidence_generation_confirmed",
            "push_not_performed",
            "unresolved_unknown_absent",
        )
        if any(values.get(field) is not True for field in booleans):
            raise GitSafetyError("Evidence authorization preconditions are not satisfied")
        if branch != authorized_branch or implementation_commit_sha != authorized_sha:
            raise GitSafetyError("Evidence authorization does not match the requested Git state")
        if not branch.startswith("fip-") or branch == "main":
            raise GitSafetyError("an isolated FIP branch is required")
        if not evidence_id or Path(evidence_id).name != evidence_id or evidence_id in {".", ".."}:
            raise GitSafetyError("Primary Evidence ID is invalid")
        expected_path = f"manufacturing/evidence/{evidence_id}.json"
        if authorized_path != expected_path:
            raise GitSafetyError("Primary Evidence path does not match its Evidence ID")
        if isinstance(evidence_paths, (str, bytes)) or list(evidence_paths) != [authorized_path]:
            raise GitSafetyError("exactly the authorized Primary Evidence must be staged")
        if message != f"manufacturing: add evidence {evidence_id}":
            raise GitSafetyError("Evidence Commit message is invalid")
        self._validate_evidence_path(authorized_path)

    @staticmethod
    def _validate_evidence_path(path: str) -> None:
        path_value = Path(path)
        if path_value.is_absolute() or ".git" in path_value.parts or ".." in path_value.parts:
            raise GitSafetyError("Primary Evidence path is unsafe")
        if path != path_value.as_posix() or not path.startswith("manufacturing/evidence/"):
            raise GitSafetyError("Primary Evidence path is outside the Evidence directory")
        root = Path.cwd().resolve()
        target = root / path
        current = root
        for component in path_value.parts:
            current /= component
            if current.is_symlink():
                raise GitSafetyError("Primary Evidence path contains a symlink")
        resolved_target = target.resolve(strict=False)
        try:
            resolved_target.relative_to(root)
        except ValueError as error:
            raise GitSafetyError("Primary Evidence path escapes the repository") from error
        if resolved_target.parent != root / "manufacturing" / "evidence":
            raise GitSafetyError("Primary Evidence path escapes the Evidence directory")
        if not target.is_file():
            raise GitSafetyError("Primary Evidence file does not exist")

    def _read_required(self, *arguments: str) -> str:
        value = self._read(*arguments)
        if not value:
            raise GitResultUnknown(f"command result could not be verified: {' '.join(arguments)}")
        return value

    def _read_commit_paths(self, commit_sha: str) -> set[str]:
        output = self._read("git", "show", "--format=", "--name-only", commit_sha)
        return {line for line in output.splitlines() if line}

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