import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from manufacturing_git import CommandResult, ManufacturingGitAutomation


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


if __name__ == "__main__":
    unittest.main()