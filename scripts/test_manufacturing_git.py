import sys
import tempfile
import unittest
from pathlib import Path
from typing import Optional
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))

from manufacturing_git import (
    CommandResult,
    EvidenceCommitAuthorization,
    GitResultUnknown,
    ManufacturingGitAutomation,
    PushResult,
    RemoteVerificationResult,
)


def candidate() -> dict:
    return {
        "candidate_status": "READY",
        "branch": "fip-005/step-2-test-run",
        "base": "main",
        "changed_files": ["frontend/lib/conversation/application/state/reducer.dart"],
        "commit_candidate": {
            "message": "feat(fip-005): implement application state",
            "files": ["frontend/lib/conversation/application/state/reducer.dart"],
        },
        "pr_candidate": {
            "title": "[FIP-005] Manufacturing PASS",
            "body": "Manufacturing PASS evidence",
        },
    }


class FakeGit:
    def __init__(self, responses: dict[tuple[str, ...], CommandResult]):
        self.responses = responses
        self.commands: list[tuple[str, ...]] = []

    def __call__(self, arguments):
        command = tuple(arguments)
        self.commands.append(command)
        return self.responses.get(command, CommandResult(0))


class EvidenceFakeGit:
    def __init__(self, *, parent: str = "implementation-sha"):
        self.commands: list[tuple[str, ...]] = []
        self.parent = parent
        self.current_branch = "fip-005/evidence"
        self.status_output = "?? manufacturing/evidence/primary-001.json\n"
        self.head_reads = 0
        self.fail_command: tuple[str, ...] | None = None
        self.unknown_command: tuple[str, ...] | None = None

    def __call__(self, arguments):
        command = tuple(arguments)
        self.commands.append(command)
        if command == self.unknown_command:
            raise GitResultUnknown("result unknown")
        if command == self.fail_command:
            return CommandResult(1, stderr="command failed")
        if command == ("git", "branch", "--show-current"):
            return CommandResult(0, self.current_branch + "\n")
        if command == ("git", "rev-parse", "HEAD"):
            self.head_reads += 1
            return CommandResult(0, "implementation-sha\n" if self.head_reads == 1 else "evidence-sha\n")
        if command == ("git", "status", "--porcelain", "--untracked-files=all"):
            return CommandResult(0, self.status_output)
        if command == ("git", "rev-parse", "HEAD^"):
            return CommandResult(0, self.parent + "\n")
        if command[:4] == ("git", "show", "--format=", "--name-only"):
            return CommandResult(0, "manufacturing/evidence/primary-001.json\n")
        return CommandResult(0)


def evidence_authorization(**overrides: object) -> dict[str, object]:
    values: dict[str, object] = {
        "run_id": "run-001",
        "branch": "fip-005/evidence",
        "implementation_commit_sha": "implementation-sha",
        "primary_evidence_id": "primary-001",
        "primary_evidence_path": "manufacturing/evidence/primary-001.json",
        "primary_evidence_generation_confirmed": True,
        "push_not_performed": True,
        "unresolved_unknown_absent": True,
    }
    values.update(overrides)
    return values


def with_primary_evidence(test_method):
    def wrapped(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence_path = Path(directory) / "manufacturing/evidence/primary-001.json"
            evidence_path.parent.mkdir(parents=True)
            evidence_path.write_text("{}\n", encoding="utf-8")
            with mock.patch("manufacturing_git.Path.cwd", return_value=Path(directory)):
                test_method(self, Path(directory))

    return wrapped


def passing_git(gh: bool = True) -> tuple[FakeGit, ManufacturingGitAutomation]:
    branch = candidate()["branch"]
    fake = FakeGit(
        {
            ("git", "remote", "get-url", "origin"): CommandResult(
                0, "https://github.com/katoushodev-svg/ProjectAlice.git\n"
            ),
            ("git", "branch", "--show-current"): CommandResult(0, "main\n"),
            ("git", "status", "--porcelain", "--untracked-files=all"): CommandResult(
                0, " M frontend/lib/conversation/application/state/reducer.dart\n"
            ),
            ("git", "show-ref", "--verify", "--quiet", f"refs/heads/{branch}"): CommandResult(1),
            ("git", "rev-parse", "HEAD"): CommandResult(0, "abc123\n"),
            ("git", "ls-remote", "origin", f"refs/heads/{branch}"): CommandResult(
                0, "abc123\trefs/heads/" + branch + "\n"
            ),
            (
                "gh",
                "pr",
                "create",
                "--base",
                "main",
                "--head",
                branch,
                "--title",
                "[FIP-005] Manufacturing PASS",
                "--body",
                "Manufacturing PASS evidence",
            ): CommandResult(0, "https://github.com/katoushodev-svg/ProjectAlice/pull/42\n"),
        }
    )
    return fake, ManufacturingGitAutomation(fake, gh_available=gh)


class ManufacturingGitSafetyTest(unittest.TestCase):
    remote = "https://github.com/katoushodev-svg/ProjectAlice.git"

    def test_disabled_automation_performs_no_git_action(self):
        fake, automation = passing_git()

        result = automation.apply(candidate(), expected_remote=self.remote)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertEqual(fake.commands, [])

    def test_pass_commits_pushes_and_creates_pr_without_merge(self):
        fake, automation = passing_git()

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "PASS")
        self.assertEqual(result.branch, candidate()["branch"])
        self.assertTrue(result.branch_created)
        self.assertTrue(result.commit)
        self.assertTrue(result.push)
        self.assertTrue(result.pull_request)
        self.assertFalse(any(command[:3] == ("gh", "pr", "merge") for command in fake.commands))
        self.assertIn(("git", "push", "--set-upstream", "origin", candidate()["branch"]), fake.commands)

    def test_main_candidate_is_rejected(self):
        fake, automation = passing_git()
        record = candidate()
        record["branch"] = "main"

        result = automation.apply(record, expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertIsNone(result.branch)
        self.assertFalse(result.branch_created)
        self.assertIn("isolated FIP branch", result.reason)
        self.assertNotIn(("git", "commit", "-m", record["commit_candidate"]["message"]), fake.commands)

    def test_unknown_changes_are_rejected(self):
        fake, automation = passing_git()
        fake.responses[("git", "status", "--porcelain", "--untracked-files=all")] = CommandResult(
            0,
            " M frontend/lib/conversation/application/state/reducer.dart\n?? docs/unknown.md\n",
        )

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertIn("exactly match", result.reason)

    def test_branch_collision_is_rejected(self):
        fake, automation = passing_git()
        branch = candidate()["branch"]
        fake.responses[("git", "show-ref", "--verify", "--quiet", f"refs/heads/{branch}")] = CommandResult(0)
        fake.responses[("git", "branch", "--show-current")] = CommandResult(0, "main\n")

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertIn("branch collision", result.reason)

    def test_unexpected_remote_is_rejected(self):
        fake, automation = passing_git()
        fake.responses[("git", "remote", "get-url", "origin")] = CommandResult(
            0, "https://example.invalid/repository.git\n"
        )

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertIn("unexpected git remote", result.reason)

    def test_missing_gh_stops_after_push_without_claiming_pr_success(self):
        fake, automation = passing_git(gh=False)

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertEqual(result.branch, candidate()["branch"])
        self.assertTrue(result.branch_created)
        self.assertTrue(result.commit)
        self.assertTrue(result.push)
        self.assertFalse(result.pull_request)
        self.assertIn("GitHub CLI", result.reason)

    def test_commit_failure_after_branch_creation_keeps_branch_and_avoids_destructive_git(self):
        fake, automation = passing_git()
        fake.responses[("git", "commit", "-m", candidate()["commit_candidate"]["message"])] = CommandResult(
            1, stderr="commit failed"
        )

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertEqual(result.branch, candidate()["branch"])
        self.assertTrue(result.branch_created)
        self.assertFalse(any(command[:3] == ("git", "branch", "-D") for command in fake.commands))
        self.assertFalse(any(command[:2] == ("git", "reset") for command in fake.commands))
        self.assertFalse(any(command[0:3] == ("git", "push", "--force") for command in fake.commands))

    def test_push_verification_rejects_other_branch_or_hash(self):
        fake, automation = passing_git()
        fake.responses[("git", "ls-remote", "origin", f"refs/heads/{candidate()['branch']}")] = CommandResult(
            0, "other-hash\trefs/heads/other-branch\n"
        )

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertTrue(result.commit)
        self.assertFalse(result.push)
        self.assertTrue(result.branch_created)

    def test_correct_ls_remote_value_verifies_push(self):
        fake, automation = passing_git(gh=False)

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertTrue(result.push)
        self.assertTrue(result.branch_created)

    def test_pr_creation_failure_after_push_keeps_commit_and_push_state(self):
        fake, automation = passing_git()
        pr_command = (
            "gh",
            "pr",
            "create",
            "--base",
            "main",
            "--head",
            candidate()["branch"],
            "--title",
            "[FIP-005] Manufacturing PASS",
            "--body",
            "Manufacturing PASS evidence",
        )
        fake.responses[pr_command] = CommandResult(1, stderr="PR creation failed")

        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertTrue(result.commit)
        self.assertTrue(result.push)
        self.assertFalse(result.pull_request)
        self.assertEqual(result.commit_hash, "abc123")
        self.assertTrue(result.branch_created)

    def test_unknown_command_result_is_not_success(self):
        _, _ = passing_git()

        def unknown(_arguments):
            raise RuntimeError("transport ended")

        automation = ManufacturingGitAutomation(unknown, gh_available=True)
        result = automation.apply(candidate(), expected_remote=self.remote, enabled=True)

        self.assertEqual(result.status, "HUMAN_GATE")
        self.assertIn("unknown", result.reason)


class EvidenceCommitSafetyTest(unittest.TestCase):
    branch = "fip-005/evidence"
    message = "manufacturing: add evidence primary-001"

    @with_primary_evidence
    def test_valid_evidence_commit_succeeds_and_verifies_parent_and_path(self, _directory):
        fake = EvidenceFakeGit()
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )

        self.assertEqual(result.status, "SUCCESS")
        self.assertEqual(result.evidence_commit_sha, "evidence-sha")
        self.assertEqual(result.committed_path, "manufacturing/evidence/primary-001.json")
        self.assertIn(
            ("git", "add", "--", "manufacturing/evidence/primary-001.json"),
            fake.commands,
        )
        self.assertNotIn(("git", "add", "."), fake.commands)
        self.assertNotIn(("git", "add", "-A"), fake.commands)
        self.assertFalse(any(command[0] == "git" and command[1] == "push" for command in fake.commands))
        self.assertFalse(any(command[0] == "gh" for command in fake.commands))

    @with_primary_evidence
    def test_authorization_fields_and_boolean_preconditions_are_required(self, _directory):
        fake = EvidenceFakeGit()
        for field, value in (
            ("branch", "fip-005/other"),
            ("implementation_commit_sha", "other-sha"),
            ("primary_evidence_generation_confirmed", False),
            ("push_not_performed", False),
            ("unresolved_unknown_absent", False),
        ):
            values = evidence_authorization(**{field: value})
            result = ManufacturingGitAutomation(fake).commit_evidence(
                values,
                ["manufacturing/evidence/primary-001.json"],
                self.branch,
                "implementation-sha",
                self.message,
            )
            self.assertEqual(result.status, "FAILURE")
        missing = evidence_authorization()
        del missing["run_id"]
        result = ManufacturingGitAutomation(fake).commit_evidence(
            missing,
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "commit" for command in fake.commands))

    @with_primary_evidence
    def test_path_policy_rejects_non_primary_targets(self, _directory):
        variants = (
            ["manufacturing/evidence/.gitkeep"],
            ["manufacturing/evidence/"],
            ["git add ."],
            ["/tmp/primary-001.json"],
            ["manufacturing/evidence/../other.json"],
            ["outside/primary-001.json"],
            [".git/primary-001.json"],
        )
        for paths in variants:
            fake = EvidenceFakeGit()
            result = ManufacturingGitAutomation(fake).commit_evidence(
                evidence_authorization(), paths, self.branch, "implementation-sha", self.message
            )
            self.assertEqual(result.status, "FAILURE", paths)
            self.assertFalse(any(command[1] == "add" for command in fake.commands))

    @with_primary_evidence
    def test_path_must_match_authorization(self, _directory):
        fake = EvidenceFakeGit()
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(primary_evidence_path="manufacturing/evidence/other.json"),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "add" for command in fake.commands))

    @with_primary_evidence
    def test_git_state_and_parent_are_required(self, _directory):
        fake = EvidenceFakeGit(parent="other-sha")
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "UNKNOWN")
        self.assertIn(("git", "rev-parse", "HEAD^"), fake.commands)

        fake = EvidenceFakeGit()
        fake.current_branch = "main"
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "commit" for command in fake.commands))

    @with_primary_evidence
    def test_failure_and_unknown_are_fail_closed_without_follow_up_operations(self, _directory):
        fake = EvidenceFakeGit()
        fake.fail_command = ("git", "commit", "-m", self.message)
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertIsNone(result.evidence_commit_sha)
        self.assertFalse(any(command[1] == "push" for command in fake.commands))

        fake = EvidenceFakeGit()
        fake.unknown_command = ("git", "commit", "-m", self.message)
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "UNKNOWN")
        self.assertFalse(any(command[1] == "push" for command in fake.commands))

    @with_primary_evidence
    def test_sha_tree_and_parent_verification_unknown_is_not_success(self, _directory):
        for unknown_command in (
            ("git", "rev-parse", "HEAD^"),
            ("git", "show", "--format=", "--name-only", "evidence-sha"),
        ):
            fake = EvidenceFakeGit()
            fake.unknown_command = unknown_command
            result = ManufacturingGitAutomation(fake).commit_evidence(
                evidence_authorization(),
                ["manufacturing/evidence/primary-001.json"],
                self.branch,
                "implementation-sha",
                self.message,
            )
            self.assertEqual(result.status, "UNKNOWN")
            self.assertIsNone(result.evidence_commit_sha)

    @with_primary_evidence
    def test_unrelated_worktree_change_is_rejected(self, _directory):
        fake = EvidenceFakeGit()
        fake.status_output += "?? manufacturing/evidence/stale.json\n"
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "commit" for command in fake.commands))

    @with_primary_evidence
    def test_existing_evidence_commit_state_is_rejected_without_duplicate_commit(self, _directory):
        fake = EvidenceFakeGit()
        fake.head_reads = 1
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertIn("current HEAD", result.reason)
        self.assertFalse(any(command[1] == "add" for command in fake.commands))
        self.assertFalse(any(command[1] == "commit" for command in fake.commands))

    @with_primary_evidence
    def test_symlink_escape_is_rejected(self, directory):
        evidence_path = directory / "manufacturing/evidence/primary-001.json"
        evidence_path.unlink()
        outside = directory / "outside.json"
        outside.write_text("{}\n", encoding="utf-8")
        evidence_path.symlink_to(outside)
        fake = EvidenceFakeGit()
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "add" for command in fake.commands))

    @with_primary_evidence
    def test_evidence_directory_symlink_is_rejected(self, directory):
        evidence_directory = directory / "manufacturing/evidence"
        evidence_directory.joinpath("primary-001.json").unlink()
        evidence_directory.rmdir()
        outside = directory / "outside-evidence"
        outside.mkdir()
        outside.joinpath("primary-001.json").write_text("{}\n", encoding="utf-8")
        evidence_directory.symlink_to(outside, target_is_directory=True)
        fake = EvidenceFakeGit()
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "add" for command in fake.commands))
        self.assertFalse(any(command[1] == "commit" for command in fake.commands))

    @with_primary_evidence
    def test_ancestor_symlink_escape_is_rejected(self, directory):
        manufacturing_directory = directory / "manufacturing"
        manufacturing_directory.joinpath("evidence/primary-001.json").unlink()
        manufacturing_directory.joinpath("evidence").rmdir()
        manufacturing_directory.rmdir()
        outside = directory / "outside-manufacturing/evidence"
        outside.mkdir(parents=True)
        outside.joinpath("primary-001.json").write_text("{}\n", encoding="utf-8")
        manufacturing_directory.symlink_to(outside.parent, target_is_directory=True)
        fake = EvidenceFakeGit()
        result = ManufacturingGitAutomation(fake).commit_evidence(
            evidence_authorization(),
            ["manufacturing/evidence/primary-001.json"],
            self.branch,
            "implementation-sha",
            self.message,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "add" for command in fake.commands))
        self.assertFalse(any(command[1] == "commit" for command in fake.commands))


class StagedGitApiTest(unittest.TestCase):
    branch = "fip-005/step-2-test-run"
    sha = "evidence-sha"

    def push_fake(self, *, push_result: Optional[CommandResult] = None) -> FakeGit:
        return FakeGit(
            {
                ("git", "branch", "--show-current"): CommandResult(0, self.branch + "\n"),
                ("git", "rev-parse", "HEAD"): CommandResult(0, self.sha + "\n"),
                ("git", "push", "--set-upstream", "origin", self.branch): push_result
                or CommandResult(0),
            }
        )

    def verify_fake(self, output: str = "evidence-sha\trefs/heads/fip-005/step-2-test-run\n") -> FakeGit:
        return FakeGit({("git", "ls-remote", "origin", f"refs/heads/{self.branch}"): CommandResult(0, output)})

    def test_push_validates_evidence_commit_and_head(self):
        fake = self.push_fake()
        result = ManufacturingGitAutomation(fake).push(
            "run-001", self.branch, self.sha,
            evidence_commit_completed=True,
            unresolved_unknown_absent=True,
        )
        self.assertEqual(result.status, "SUCCESS")
        self.assertEqual(result.pushed_commit_sha, self.sha)
        self.assertIn(("git", "push", "--set-upstream", "origin", self.branch), fake.commands)
        self.assertFalse(any("--force" in command for command in fake.commands))

    def test_push_rejects_main_and_branch_or_sha_mismatch(self):
        fake = self.push_fake()
        result = ManufacturingGitAutomation(fake).push(
            "run-001", "main", self.sha,
            evidence_commit_completed=True,
            unresolved_unknown_absent=True,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "push" for command in fake.commands))

        fake = self.push_fake()
        result = ManufacturingGitAutomation(fake).push(
            "run-001", "fip-005/other", self.sha,
            evidence_commit_completed=True,
            unresolved_unknown_absent=True,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "push" for command in fake.commands))

        fake = self.push_fake()
        result = ManufacturingGitAutomation(fake).push(
            "run-001", self.branch, "other-sha",
            evidence_commit_completed=True,
            unresolved_unknown_absent=True,
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertFalse(any(command[1] == "push" for command in fake.commands))

    def test_push_rejects_incomplete_evidence_or_unknown_state(self):
        for evidence_complete, unknown_absent in ((False, True), (True, False)):
            fake = self.push_fake()
            result = ManufacturingGitAutomation(fake).push(
                "run-001", self.branch, self.sha,
                evidence_commit_completed=evidence_complete,
                unresolved_unknown_absent=unknown_absent,
            )
            self.assertEqual(result.status, "FAILURE")
            self.assertFalse(any(command[1] == "push" for command in fake.commands))

    def test_push_failure_and_unknown_are_fail_closed(self):
        fake = self.push_fake(push_result=CommandResult(1, stderr="push rejected"))
        result = ManufacturingGitAutomation(fake).push(
            "run-001", self.branch, self.sha,
            evidence_commit_completed=True,
            unresolved_unknown_absent=True,
        )
        self.assertEqual(result.status, "FAILURE")

        def unknown(arguments):
            command = tuple(arguments)
            if command[1] == "push":
                raise GitResultUnknown("network uncertainty")
            if command[1:3] == ("branch", "--show-current"):
                return CommandResult(0, self.branch + "\n")
            return CommandResult(0, self.sha + "\n")

        result = ManufacturingGitAutomation(unknown).push(
            "run-001", self.branch, self.sha,
            evidence_commit_completed=True,
            unresolved_unknown_absent=True,
        )
        self.assertEqual(result.status, "UNKNOWN")

    def test_remote_verification_requires_successful_push_and_matching_state(self):
        push = PushResult("SUCCESS", "push", "ok", "run-001", self.branch, self.sha)
        fake = self.verify_fake()
        result = ManufacturingGitAutomation(fake).verify_remote(
            push, expected_branch=self.branch, expected_commit_sha=self.sha
        )
        self.assertEqual(result.status, "SUCCESS")
        self.assertEqual(result.remote_branch, self.branch)
        self.assertEqual(result.remote_commit_sha, self.sha)

        failed_push = PushResult("FAILURE", "push", "failed", "run-001", self.branch)
        fake = self.verify_fake()
        result = ManufacturingGitAutomation(fake).verify_remote(
            failed_push, expected_branch=self.branch, expected_commit_sha=self.sha
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertEqual(fake.commands, [])

    def test_remote_verification_rejects_branch_sha_mismatch_and_unknown(self):
        push = PushResult("SUCCESS", "push", "ok", "run-001", self.branch, self.sha)
        for output in (
            "evidence-sha\trefs/heads/other-branch\n",
            "other-sha\trefs/heads/fip-005/step-2-test-run\n",
        ):
            result = ManufacturingGitAutomation(self.verify_fake(output)).verify_remote(
                push, expected_branch=self.branch, expected_commit_sha=self.sha
            )
            self.assertEqual(result.status, "FAILURE")

        def unknown(_arguments):
            raise GitResultUnknown("remote state uncertain")

        result = ManufacturingGitAutomation(unknown).verify_remote(
            push, expected_branch=self.branch, expected_commit_sha=self.sha
        )
        self.assertEqual(result.status, "UNKNOWN")

    def test_pull_request_requires_remote_verification_and_never_merges(self):
        verification = RemoteVerificationResult(
            "SUCCESS", "verify_remote", "ok", "run-001", self.branch,
            self.branch, self.sha, self.branch, self.sha,
        )
        fake = FakeGit(
            {
                (
                    "gh", "pr", "create", "--base", "main", "--head", self.branch,
                    "--title", "[FIP-005] Manufacturing PASS", "--body", "handoff",
                ): CommandResult(0, "https://github.com/example/repo/pull/42\n")
            }
        )
        result = ManufacturingGitAutomation(fake, gh_available=True).create_pull_request(
            verification, title="[FIP-005] Manufacturing PASS", body="handoff"
        )
        self.assertEqual(result.status, "SUCCESS")
        self.assertEqual(result.pull_request_url, "https://github.com/example/repo/pull/42")
        self.assertFalse(any(command[1:3] == ("pr", "merge") for command in fake.commands))

        failed = RemoteVerificationResult(
            "FAILURE", "verify_remote", "mismatch", "run-001", self.branch,
            self.branch, self.sha,
        )
        fake = FakeGit({})
        result = ManufacturingGitAutomation(fake, gh_available=True).create_pull_request(
            failed, title="title", body="body"
        )
        self.assertEqual(result.status, "FAILURE")
        self.assertEqual(fake.commands, [])

    def test_pull_request_failure_and_unknown_are_not_success(self):
        verification = RemoteVerificationResult(
            "SUCCESS", "verify_remote", "ok", "run-001", self.branch,
            self.branch, self.sha, self.branch, self.sha,
        )
        command = (
            "gh", "pr", "create", "--base", "main", "--head", self.branch,
            "--title", "title", "--body", "body",
        )
        fake = FakeGit({command: CommandResult(1, stderr="PR failed")})
        result = ManufacturingGitAutomation(fake, gh_available=True).create_pull_request(
            verification, title="title", body="body"
        )
        self.assertEqual(result.status, "FAILURE")

        def unknown(_arguments):
            raise GitResultUnknown("PR result uncertain")

        result = ManufacturingGitAutomation(unknown, gh_available=True).create_pull_request(
            verification, title="title", body="body"
        )
        self.assertEqual(result.status, "UNKNOWN")


if __name__ == "__main__":
    unittest.main()