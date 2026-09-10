import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from manufacturing import ManufacturingGateError, build_candidate


ROOT = Path(__file__).parents[1]
MANIFEST = json.loads(
    (ROOT / "scripts/manufacturing_manifest.json").read_text(encoding="utf-8")
)


def passing_record() -> dict:
    return {
        "fip": "FIP-005",
        "run_id": "step-2-test-run",
        "fip_approved": True,
        "implementation_ready": True,
        "prerequisite_gates": {
            "FIP-001": "PASS",
            "FIP-002": "PASS",
            "FIP-003": "PASS",
            "FIP-004": "PASS",
        },
        "branch": "fip-005/step-2-test-run",
        "base_branch": "main",
        "changed_files": ["frontend/lib/conversation/application/state/reducer.dart"],
        "checks": {
            name: {"status": "PASS", "summary": f"{name} passed"}
            for name in (
                "implementation",
                "focused_test",
                "analyze",
                "full_regression",
                "ai_review",
                "retest",
                "rereview",
            )
        },
        "auto_fix": {"required": False, "attempts": 0, "summary": "not required"},
        "human_gate": False,
        "design_change": False,
        "architecture_change": False,
        "api_contract_change": False,
        "db_design_change": False,
        "security_safety_change": False,
        "phase_boundary_change": False,
        "fip_scope_change": False,
        "new_dependency": False,
        "unknown_changes": False,
        "commit_created": False,
        "push_performed": False,
        "merge_performed": False,
    }


def add_review_evidence(record: dict) -> dict:
    for name in ("ai_review", "rereview"):
        record["checks"][name].update(
            {
                "conclusion": "PASS",
                "evidence": "reviewed implementation and test record",
                "severity": "none",
                "allowed_scope": "FIP-005 manifest paths",
                "retest_required": False,
            }
        )
    return record


class ManufacturingCandidateTest(unittest.TestCase):
    def test_pass_generates_commit_and_pr_candidates_without_git_actions(self):
        candidate = build_candidate(add_review_evidence(passing_record()), MANIFEST)

        self.assertEqual(candidate["candidate_status"], "READY")
        self.assertEqual(candidate["base"], "main")
        self.assertEqual(candidate["pr_candidate"]["base"], "main")
        self.assertFalse(any(candidate["actions_performed"].values()))
        self.assertIn("Manufacturing Record", candidate["pr_candidate"]["body"])

    def test_test_failure_does_not_progress(self):
        record = add_review_evidence(passing_record())
        record["checks"]["focused_test"]["status"] = "FAIL"

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_review_failure_does_not_progress(self):
        record = add_review_evidence(passing_record())
        record["checks"]["ai_review"]["status"] = "FAIL"

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_human_gate_does_not_progress(self):
        record = add_review_evidence(passing_record())
        record["human_gate"] = True

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_out_of_scope_change_does_not_progress(self):
        record = add_review_evidence(passing_record())
        record["changed_files"].append("docs/security-design.md")

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_main_branch_cannot_generate_candidate(self):
        record = add_review_evidence(passing_record())
        record["branch"] = "main"

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_auto_fix_requires_retest_and_rereview(self):
        record = add_review_evidence(passing_record())
        record["auto_fix"] = {"required": True, "summary": "local fix"}
        record["checks"]["retest"]["status"] = "FAIL"

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_boundary_change_requires_human_gate(self):
        record = add_review_evidence(passing_record())
        record["architecture_change"] = True

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_unknown_change_requires_human_gate(self):
        record = add_review_evidence(passing_record())
        record["unknown_changes"] = True

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_path_traversal_requires_human_gate(self):
        record = add_review_evidence(passing_record())
        record["changed_files"] = [
            "frontend/lib/conversation/application/../../../../other/file.py"
        ]

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_missing_boundary_flag_requires_human_gate(self):
        record = add_review_evidence(passing_record())
        del record["security_safety_change"]

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_invalid_boundary_flag_requires_human_gate(self):
        record = add_review_evidence(passing_record())
        record["new_dependency"] = "false"

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_unapproved_fip_does_not_progress(self):
        record = add_review_evidence(passing_record())
        record["fip_approved"] = False

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_invalid_manifest_paths_do_not_progress(self):
        record = add_review_evidence(passing_record())
        invalid_manifest = {"FIP-005": {"allowed_paths": ["valid/**", 42]}}

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, invalid_manifest)

    def test_incomplete_review_evidence_does_not_progress(self):
        record = passing_record()
        add_review_evidence(record)
        del record["checks"]["ai_review"]["evidence"]

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_non_string_branch_does_not_progress(self):
        record = add_review_evidence(passing_record())
        record["branch"] = []

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_auto_fix_budget_is_enforced(self):
        record = add_review_evidence(passing_record())
        record["auto_fix"]["attempts"] = 3

        with self.assertRaises(ManufacturingGateError):
            build_candidate(record, MANIFEST)

    def test_record_is_preserved_in_candidate(self):
        record = add_review_evidence(passing_record())
        candidate = build_candidate(record, MANIFEST)

        self.assertEqual(candidate["manufacturing_record"], record)
        self.assertEqual(candidate["changed_files"], record["changed_files"])


if __name__ == "__main__":
    unittest.main()