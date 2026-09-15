import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from manufacturing_verification import ESCALATE, FIXABLE_FINDINGS, PASS, UNKNOWN, ManufacturingVerification


class VerificationTest(unittest.TestCase):
    def test_commands_pass_and_fail_closed(self):
        calls = []
        def runner(command, cwd, timeout):
            calls.append(tuple(command))
            return subprocess.CompletedProcess(command, 0, "", "")
        verification = ManufacturingVerification(Path.cwd(), runner=runner, codex_command=sys.executable)
        self.assertEqual(verification.focused_test().status, PASS)
        self.assertEqual(verification.analyze().status, PASS)
        self.assertEqual(verification.full_regression().status, PASS)
        self.assertIn(("flutter", "test", "test/conversation/presentation"), calls)

    def test_timeout_is_unknown(self):
        def runner(command, cwd, timeout): raise subprocess.TimeoutExpired(command, timeout)
        self.assertEqual(ManufacturingVerification(Path.cwd(), runner=runner).analyze().status, UNKNOWN)

    def test_review_json_and_malformed_output(self):
        def runner(command, cwd, timeout):
            return subprocess.CompletedProcess(command, 0, '{"conclusion":"FIXABLE_FINDINGS","evidence":"fix x","severity":"low","allowed_scope":"manifest","retest_required":true}', "")
        review = ManufacturingVerification(Path.cwd(), runner=runner, codex_command=sys.executable).review("review")
        self.assertEqual(review.conclusion, FIXABLE_FINDINGS)
        self.assertEqual(ManufacturingVerification._parse_review("not-json").conclusion, ESCALATE)
        self.assertEqual(ManufacturingVerification._parse_review('{"conclusion":"UNKNOWN"}').conclusion, ESCALATE)

    def test_codex_event_jsonl_uses_last_structured_result_only(self):
        review = '{"conclusion":"PASS","evidence":"scope checked","severity":"none","allowed_scope":"manifest","retest_required":false}'
        fixed = '{"conclusion":"FIXABLE_FINDINGS","evidence":"fix","severity":"low","allowed_scope":"manifest","retest_required":true}'
        event = '{"type":"item.completed","item":{"type":"agent_message","text":' + __import__("json").dumps(review) + '}}'
        final = '{"type":"item.completed","item":{"type":"agent_message","text":' + __import__("json").dumps(fixed) + '}}'
        self.assertEqual(ManufacturingVerification._parse_review('{"type":"thread.started"}\n' + event + '\n' + final).conclusion, FIXABLE_FINDINGS)
        self.assertEqual(ManufacturingVerification._parse_review('{"type":"item.completed","item":{"type":"agent_message","text":"Review result is PASS"}}').conclusion, ESCALATE)
        self.assertEqual(ManufacturingVerification._parse_review('{"type":"item.completed","item":{"type":"agent_message","text":"{\\\"conclusion\\\": bad}"}}').conclusion, ESCALATE)


if __name__ == "__main__": unittest.main()
