#!/usr/bin/env python3
"""Unit tests for the concrete Coding Executor Adapter (SubprocessCodingAgentAdapter)."""

from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))

from manufacturing_coding_agent_adapter import (  # noqa: E402
    CodingAgentAdapterError,
    SubprocessCodingAgentAdapter,
)
from manufacturing_executor import (  # noqa: E402
    ImplementationJob,
    STATUS_FAILURE,
    STATUS_SUCCESS,
    STATUS_UNKNOWN,
)

REPOSITORY_ROOT = Path(__file__).resolve().parents[1]


def sample_job() -> ImplementationJob:
    return ImplementationJob.from_dict(
        {
            "fip": "FIP-005",
            "run_id": "run-fip005-001",
            "source_of_truth": ["docs/fip-005-application-state-plan.md"],
            "allowed_paths": ["frontend/lib/conversation/application/**"],
            "prompt": "Implement pure conversation state reducer for FIP-005; rm -rf / ; $(malicious)",
        }
    )


class FakeCompleted:
    def __init__(self, returncode: int, stdout: str = "", stderr: str = ""):
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr


class TestCodingAgentCliConnectivity(unittest.TestCase):
    # 1. Coding Agent CLI未接続時の安全なFAILURE
    def test_unconnected_cli_returns_safe_failure(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=None)
        result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertFalse(result.unknown)
        self.assertFalse(result.retry_allowed)
        self.assertIn("not connected", result.reason)

    def test_nonexistent_cli_executable_returns_safe_failure(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=["/nonexistent/coding-agent-cli"])
        result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertFalse(result.unknown)
        self.assertFalse(result.retry_allowed)


class TestCodingAgentResultNormalization(unittest.TestCase):
    def _adapter(self) -> SubprocessCodingAgentAdapter:
        # sys.executable always exists; subprocess.run itself is mocked per-test.
        return SubprocessCodingAgentAdapter(cli_command=[sys.executable, "ignored"])

    # 2. SUCCESSの正規化
    def test_success_normalization(self):
        adapter = self._adapter()
        fake = FakeCompleted(
            0,
            stdout='{"changed_files": ["frontend/lib/conversation/application/state/reducer.dart"]}',
        )
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertFalse(result.unknown)
        self.assertIn(
            "frontend/lib/conversation/application/state/reducer.dart", result.changed_files
        )

    def test_codex_invocation_uses_repository_and_prompt_as_argv(self):
        adapter = self._adapter()
        fake = FakeCompleted(0)
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake) as mock_run:
            adapter.execute(sample_job())

        command = mock_run.call_args.args[0]
        self.assertEqual(
            command,
            [
                sys.executable,
                "exec",
                "-C",
                str(REPOSITORY_ROOT),
                "-s",
                "workspace-write",
                "--json",
                sample_job().prompt,
            ],
        )
        self.assertEqual(mock_run.call_args.kwargs["cwd"], adapter.workspace_root)
        self.assertNotIn("input", mock_run.call_args.kwargs)
        self.assertNotIn("shell", mock_run.call_args.kwargs)

    # 3. FAILUREの正規化
    def test_failure_normalization(self):
        adapter = self._adapter()
        fake = FakeCompleted(1, stdout="", stderr="build error")
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertFalse(result.unknown)
        self.assertFalse(result.retry_allowed)

    # 4. UNKNOWNの正規化 (timeout)
    def test_unknown_normalization_on_timeout(self):
        adapter = self._adapter()
        with patch(
            "manufacturing_coding_agent_adapter.subprocess.run",
            side_effect=subprocess.TimeoutExpired(cmd="coding-agent", timeout=1),
        ):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_UNKNOWN)
        self.assertTrue(result.unknown)
        # 5. UNKNOWN時にretryされない
        self.assertFalse(result.retry_allowed)

    def test_unknown_normalization_on_os_error(self):
        adapter = self._adapter()
        with patch(
            "manufacturing_coding_agent_adapter.subprocess.run", side_effect=OSError("process vanished")
        ):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_UNKNOWN)
        self.assertTrue(result.unknown)
        self.assertFalse(result.retry_allowed)

    def test_unknown_normalization_on_malformed_changed_files(self):
        adapter = self._adapter()
        fake = FakeCompleted(0, stdout='{"changed_files": [1, 2, 3]}')
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_UNKNOWN)
        self.assertTrue(result.unknown)
        self.assertFalse(result.retry_allowed)


class TestCodingAgentSecurityBoundaries(unittest.TestCase):
    # 6. Jobから任意shell commandを実行できない
    def test_job_cannot_inject_arbitrary_shell_command(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=[sys.executable, "-c", "pass"])
        fake = FakeCompleted(0, stdout="")
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake) as mock_run:
            adapter.execute(sample_job())
        called_args = mock_run.call_args
        executed_command = called_args.args[0] if called_args.args else called_args.kwargs["args"]
        # The prompt remains one argv element even when it contains shell metacharacters.
        self.assertEqual(
            list(executed_command),
            [
                sys.executable,
                "exec",
                "-C",
                str(REPOSITORY_ROOT),
                "-s",
                "workspace-write",
                "--json",
                sample_job().prompt,
            ],
        )
        self.assertNotIn("shell", called_args.kwargs)
        self.assertIn("rm -rf", executed_command[-1])

    # 7. Git操作をAdapterが行わない / 8. PR・mergeをAdapterが行わない (static source check)
    def test_adapter_source_contains_no_git_or_pr_operations(self):
        source = Path(
            REPOSITORY_ROOT / "scripts" / "manufacturing_coding_agent_adapter.py"
        ).read_text(encoding="utf-8")
        forbidden_snippets = (
            "git add",
            "git commit",
            "git push",
            "gh pr create",
            "git merge",
            "git switch",
            "import manufacturing_git",
            "ManufacturingGitAutomation",
        )
        for snippet in forbidden_snippets:
            self.assertNotIn(snippet, source)

    def test_adapter_does_not_import_evidence_layer(self):
        source = Path(
            REPOSITORY_ROOT / "scripts" / "manufacturing_coding_agent_adapter.py"
        ).read_text(encoding="utf-8")
        self.assertNotIn("EvidenceStore", source)
        self.assertNotIn("manufacturing_evidence", source)

    # 9. Repository外workspaceを拒否
    def test_rejects_workspace_outside_repository(self):
        with self.assertRaises(CodingAgentAdapterError):
            SubprocessCodingAgentAdapter(workspace_root=Path("/tmp/outside-repo"))

    def test_accepts_workspace_inside_repository(self):
        adapter = SubprocessCodingAgentAdapter(workspace_root=REPOSITORY_ROOT / "frontend")
        self.assertEqual(adapter.workspace_root, (REPOSITORY_ROOT / "frontend").resolve())

    # 10. changed_filesの安全性検証
    def test_rejects_unsafe_absolute_changed_file(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=[sys.executable])
        fake = FakeCompleted(0, stdout='{"changed_files": ["/etc/passwd"]}')
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertEqual(result.changed_files, ())

    def test_rejects_unsafe_traversal_changed_file(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=[sys.executable])
        fake = FakeCompleted(0, stdout='{"changed_files": ["../outside.dart"]}')
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertEqual(result.changed_files, ())


class TestCodexJsonlChangedFilesParsing(unittest.TestCase):
    """Codex `codex exec --json` JSONL file_change event parsing."""

    # Test 1: 既存トップレベル changed_files が取得できる
    def test_top_level_changed_files_still_parsed(self):
        files, error = SubprocessCodingAgentAdapter._parse_changed_files(
            '{"changed_files": ["README.md"]}'
        )
        self.assertIsNone(error)
        self.assertEqual(files, ("README.md",))

    # Test 2: JSONL item.type=file_change から changes[].path を取得できる
    def test_file_change_event_changes_paths_extracted(self):
        line = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "README.md", "kind": "update"}],
                },
            }
        )
        files, error = SubprocessCodingAgentAdapter._parse_changed_files(line)
        self.assertIsNone(error)
        self.assertEqual(files, ("README.md",))

    # Test 3: 複数file_changeイベントから複数ファイルを取得できる
    def test_multiple_file_change_events_extract_multiple_files(self):
        line1 = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "README.md", "kind": "update"}],
                },
            }
        )
        line2 = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "docs/mvp.md", "kind": "add"}],
                },
            }
        )
        files, error = SubprocessCodingAgentAdapter._parse_changed_files(
            "\n".join([line1, line2])
        )
        self.assertIsNone(error)
        self.assertEqual(files, ("README.md", "docs/mvp.md"))

    # Test 4: 重複pathが除去される
    def test_duplicate_paths_removed(self):
        line1 = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "README.md", "kind": "update"}],
                },
            }
        )
        line2 = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "README.md", "kind": "update"}],
                },
            }
        )
        files, error = SubprocessCodingAgentAdapter._parse_changed_files(
            "\n".join([line1, line2])
        )
        self.assertIsNone(error)
        self.assertEqual(files, ("README.md",))

    # Test 8: file_change以外のイベントを無視する
    def test_non_file_change_items_ignored(self):
        line1 = json.dumps({"type": "item.completed", "item": {"type": "agent_message"}})
        line2 = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "README.md", "kind": "update"}],
                },
            }
        )
        files, error = SubprocessCodingAgentAdapter._parse_changed_files(
            "\n".join([line1, line2])
        )
        self.assertIsNone(error)
        self.assertEqual(files, ("README.md",))

    # Test 9: file_changeイベントにchangesがない場合の既存安全動作
    def test_file_change_without_changes_key_is_safe_noop(self):
        line = json.dumps({"type": "item.completed", "item": {"type": "file_change"}})
        files, error = SubprocessCodingAgentAdapter._parse_changed_files(line)
        self.assertIsNone(error)
        self.assertEqual(files, ())

    # Test 5: absolute pathを拒否する
    def test_rejects_absolute_path_from_file_change(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=[sys.executable])
        line = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "/etc/passwd", "kind": "update"}],
                },
            }
        )
        fake = FakeCompleted(0, stdout=line)
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertEqual(result.changed_files, ())

    # Test 6: ../ traversalを拒否する
    def test_rejects_traversal_path_from_file_change(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=[sys.executable])
        line = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "../outside.md", "kind": "update"}],
                },
            }
        )
        fake = FakeCompleted(0, stdout=line)
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertEqual(result.changed_files, ())

    # Test 7: Windows pathを拒否する
    def test_rejects_windows_path_from_file_change(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=[sys.executable])
        line = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "C:\\Windows\\README.md", "kind": "update"}],
                },
            }
        )
        fake = FakeCompleted(0, stdout=line)
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertEqual(result.changed_files, ())

    # Test 10: SUCCESS + changed_filesが正しくResultへ入る
    def test_success_with_file_change_changed_files_in_result(self):
        adapter = SubprocessCodingAgentAdapter(cli_command=[sys.executable])
        line = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": "README.md", "kind": "update"}],
                },
            }
        )
        fake = FakeCompleted(0, stdout=line)
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertEqual(result.changed_files, ("README.md",))


class TestAbsolutePathNormalization(unittest.TestCase):
    """Codex 0.154.0 may report workspace-relative changes as absolute paths."""

    def setUp(self):
        self.workspace = REPOSITORY_ROOT / "frontend"

    def _adapter(self, workspace_root: Path | None = None) -> SubprocessCodingAgentAdapter:
        return SubprocessCodingAgentAdapter(
            cli_command=[sys.executable],
            workspace_root=workspace_root or self.workspace,
        )

    # Test 1: workspace内absolute path -> relative pathへ正常化
    def test_workspace_absolute_path_normalized_to_relative(self):
        adapter = self._adapter()
        absolute_path = str(self.workspace / "README.md")
        fake = FakeCompleted(0, stdout=json.dumps({"changed_files": [absolute_path]}))
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertEqual(result.changed_files, ("README.md",))

    # Test 2: workspace内nested absolute path -> 正常化
    def test_workspace_nested_absolute_path_normalized_to_relative(self):
        adapter = self._adapter()
        absolute_path = str(self.workspace / "lib" / "foo.dart")
        fake = FakeCompleted(0, stdout=json.dumps({"changed_files": [absolute_path]}))
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertEqual(result.changed_files, ("lib/foo.dart",))

    # Test 3: workspace外absolute path -> reject
    def test_outside_workspace_absolute_path_rejected(self):
        adapter = self._adapter()
        fake = FakeCompleted(0, stdout=json.dumps({"changed_files": ["/private/tmp/other/README.md"]}))
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertEqual(result.changed_files, ())

    # Test 4: absolute path traversal -> reject
    def test_absolute_path_traversal_rejected(self):
        adapter = self._adapter()
        traversal_path = str(self.workspace / ".." / ".." / "etc" / "passwd")
        fake = FakeCompleted(0, stdout=json.dumps({"changed_files": [traversal_path]}))
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertEqual(result.changed_files, ())

    # Test 5: relative path -> existing behavior unchanged
    def test_relative_path_behavior_unchanged(self):
        adapter = self._adapter()
        fake = FakeCompleted(0, stdout=json.dumps({"changed_files": ["README.md"]}))
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertEqual(result.changed_files, ("README.md",))

    # Test 6: duplicate normalized path -> deduplicate
    def test_duplicate_normalized_path_deduplicated(self):
        adapter = self._adapter()
        absolute_path = str(self.workspace / "README.md")
        fake = FakeCompleted(
            0, stdout=json.dumps({"changed_files": [absolute_path, "README.md"]})
        )
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertEqual(result.changed_files, ("README.md",))

    # Test 7: mixed relative + absolute paths -> relative pathへ統一
    def test_mixed_relative_and_absolute_paths_unified(self):
        adapter = self._adapter()
        absolute_path = str(self.workspace / "lib" / "foo.dart")
        fake = FakeCompleted(
            0, stdout=json.dumps({"changed_files": ["README.md", absolute_path]})
        )
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertEqual(result.changed_files, ("README.md", "lib/foo.dart"))

    # Test 8: Windows backslash -> reject
    def test_windows_backslash_path_rejected(self):
        adapter = self._adapter()
        fake = FakeCompleted(0, stdout=json.dumps({"changed_files": ["C:\\temp\\README.md"]}))
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_FAILURE)
        self.assertEqual(result.changed_files, ())

    # Test 9: file_change event absolute path also normalized (not special-cased)
    def test_file_change_event_absolute_path_normalized(self):
        adapter = self._adapter()
        absolute_path = str(self.workspace / "README.md")
        line = json.dumps(
            {
                "type": "item.completed",
                "item": {
                    "type": "file_change",
                    "changes": [{"path": absolute_path, "kind": "update"}],
                },
            }
        )
        fake = FakeCompleted(0, stdout=line)
        with patch("manufacturing_coding_agent_adapter.subprocess.run", return_value=fake):
            result = adapter.execute(sample_job())
        self.assertEqual(result.status, STATUS_SUCCESS)
        self.assertEqual(result.changed_files, ("README.md",))


if __name__ == "__main__":
    unittest.main()
