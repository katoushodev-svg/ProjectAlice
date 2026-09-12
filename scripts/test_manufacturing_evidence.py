import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from manufacturing_evidence import (  # noqa: E402
    EvidenceStore,
    EvidenceValidationError,
    idempotency_key,
    validate_record,
)


def valid_record() -> dict:
    return {
        "schema_version": "1",
        "evidence_id": "evidence-001",
        "evidence_type": "PRIMARY",
        "fip_id": "FIP-005",
        "pipeline_step": "step-2.6-b-evidence",
        "run_id": "run-001",
        "created_at": "2026-09-11T00:00:00+09:00",
        "repository": {
            "repository": "katoushodev-svg/ProjectAlice",
            "branch": "fip-005/evidence",
            "implementation_commit_sha": "abc123",
            "base_sha": "def456",
            "worktree_status": "clean",
            "changed_files": ["frontend/lib/example.dart"],
        },
        "manufacturing": {
            "manifest_path": "scripts/manufacturing_manifest.json",
            "approval_status": "Approved / Implementation Ready",
            "prerequisite_result": "PASS",
            "required_checks": ["implementation", "focused_test"],
            "allowed_paths": ["frontend/lib/conversation/application/**"],
            "prohibited_changes": ["architecture", "new_dependency"],
            "manufacturing_result": "PASS",
            "manufacturing_reason": "all required checks passed",
        },
        "verification": {
            name: {"status": "PASS", "summary": f"{name} passed"}
            for name in ("implementation", "focused_test", "analyze", "full_regression", "auto_fix", "retest")
        },
        "reviews": {
            name: {
                "conclusion": "PASS",
                "evidence": f"{name} reviewed",
                "reviewed_commit_sha": "abc123",
            }
            for name in ("design_review", "implementation_review", "re_review")
        },
        "outcome": {
            "status": "PASS",
            "human_gate": False,
            "unknown": False,
            "stop_reason": "recorded for human G1 review",
        },
    }


def supplementary_record(evidence_id: str, anomaly_type: str, status: str) -> dict:
    return {
        "evidence_id": evidence_id,
        "evidence_type": "SUPPLEMENTARY",
        "fip_id": "FIP-005",
        "pipeline_step": "step-2.6-c-evidence",
        "run_id": "run-001",
        "repository": "katoushodev-svg/ProjectAlice",
        "implementation_commit_sha": "abc123",
        "created_at": "2026-09-11T00:01:00+09:00",
        "anomaly_type": anomaly_type,
        "status": status,
        "reason": "recorded after Primary Evidence creation",
        "unknown_details": "none",
        "human_gate_details": "none",
    }


class EvidenceValidationTest(unittest.TestCase):
    def test_valid_record_round_trips(self):
        record = valid_record()
        validate_record(record)
        encoded = json.dumps(record, sort_keys=True)
        self.assertEqual(json.loads(encoded), record)

    def test_required_and_enum_validation(self):
        record = valid_record()
        del record["repository"]["base_sha"]
        with self.assertRaises(EvidenceValidationError):
            validate_record(record)

        record = valid_record()
        record["verification"]["focused_test"]["status"] = "SUCCESS"
        with self.assertRaises(EvidenceValidationError):
            validate_record(record)

        record = valid_record()
        record["reviews"]["re_review"]["conclusion"] = "PASS_WITH_GATE"
        with self.assertRaises(EvidenceValidationError):
            validate_record(record)

    def test_unknown_requires_human_gate_and_is_not_pass(self):
        record = valid_record()
        record["outcome"] = {
            "status": "UNKNOWN",
            "human_gate": True,
            "unknown": True,
            "stop_reason": "result unavailable",
        }
        validate_record(record)
        self.assertNotEqual(record["outcome"]["status"], "PASS")

    def test_schema_version_is_fixed(self):
        record = valid_record()
        record["schema_version"] = "2"
        with self.assertRaises(EvidenceValidationError):
            validate_record(record)

    def test_primary_has_no_git_follow_up_fields(self):
        record = valid_record()
        record["git"] = {"push_verified": True}
        with self.assertRaises(EvidenceValidationError):
            validate_record(record)

    def test_sensitive_and_authority_data_is_rejected(self):
        for field in ("token", "conversation", "raw_provider_response", "execution_authority"):
            record = valid_record()
            record[field] = "must not be persisted"
            with self.assertRaises(EvidenceValidationError):
                validate_record(record)


class EvidenceStoreTest(unittest.TestCase):
    def test_create_duplicate_and_mismatch_are_append_only(self):
        with tempfile.TemporaryDirectory() as directory:
            store = EvidenceStore(Path(directory))
            record = valid_record()
            self.assertEqual(store.write(record).status, "CREATED")
            self.assertEqual(store.write(copy.deepcopy(record)).status, "DUPLICATE")

            changed = copy.deepcopy(record)
            changed["verification"]["focused_test"]["summary"] = "different result"
            self.assertEqual(store.write(changed).status, "UNKNOWN")
            self.assertEqual(len(list(Path(directory).glob("*.json"))), 1)

    def test_different_run_or_commit_is_a_distinct_key(self):
        first = valid_record()
        second = copy.deepcopy(first)
        second["evidence_id"] = "evidence-002"
        second["run_id"] = "run-002"
        self.assertNotEqual(idempotency_key(first), idempotency_key(second))

        with tempfile.TemporaryDirectory() as directory:
            store = EvidenceStore(Path(directory))
            self.assertEqual(store.write(first).status, "CREATED")
            self.assertEqual(store.write(second).status, "CREATED")

    def test_existing_file_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            store = EvidenceStore(Path(directory))
            record = valid_record()
            store.write(record)
            changed = copy.deepcopy(record)
            changed["outcome"]["stop_reason"] = "changed"
            self.assertEqual(store.write(changed).status, "UNKNOWN")
            persisted = json.loads((Path(directory) / "evidence-001.json").read_text())
            self.assertEqual(persisted["outcome"]["stop_reason"], "recorded for human G1 review")

    def test_exclusive_creation_rejects_existing_file_without_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            store = EvidenceStore(Path(directory))
            record = valid_record()
            path = Path(directory) / "evidence-001.json"
            path.write_text(json.dumps(record), encoding="utf-8")

            self.assertEqual(store.write(copy.deepcopy(record)).status, "DUPLICATE")
            self.assertEqual(json.loads(path.read_text()), record)

    def test_creation_race_with_same_content_is_duplicate(self):
        class RacingStore(EvidenceStore):
            @staticmethod
            def _create_exclusive(path, record):
                path.write_text(json.dumps(record), encoding="utf-8")
                raise FileExistsError(path)

        with tempfile.TemporaryDirectory() as directory:
            record = valid_record()
            result = RacingStore(Path(directory)).write(record)
            self.assertEqual(result.status, "DUPLICATE")

    def test_creation_race_with_different_content_is_unknown(self):
        class RacingStore(EvidenceStore):
            @staticmethod
            def _create_exclusive(path, record):
                competing = copy.deepcopy(record)
                competing["outcome"]["stop_reason"] = "competing record"
                path.write_text(json.dumps(competing), encoding="utf-8")
                raise FileExistsError(path)

        with tempfile.TemporaryDirectory() as directory:
            record = valid_record()
            result = RacingStore(Path(directory)).write(record)
            self.assertEqual(result.status, "UNKNOWN")
            persisted = json.loads((Path(directory) / "evidence-001.json").read_text())
            self.assertEqual(persisted["outcome"]["stop_reason"], "competing record")

    def test_incomplete_key_is_rejected(self):
        record = valid_record()
        del record["repository"]["implementation_commit_sha"]
        with self.assertRaises(EvidenceValidationError):
            idempotency_key(record)


class SupplementaryEvidenceTest(unittest.TestCase):
    def test_supplementary_results_and_multiple_events_are_append_only(self):
        with tempfile.TemporaryDirectory() as directory:
            store = EvidenceStore(Path(directory))
            events = (
                supplementary_record("supp-push", "push_success", "PASS"),
                supplementary_record("supp-remote", "remote_verification_success", "PASS"),
                supplementary_record("supp-pr", "pull_request_success", "PASS"),
                supplementary_record("supp-failure", "push_failure", "FAIL"),
                supplementary_record("supp-unknown", "push_unknown", "UNKNOWN"),
            )
            for event in events:
                self.assertEqual(store.write(event).status, "CREATED")
            self.assertEqual(len(list(Path(directory).glob("*.json"))), len(events))
            self.assertEqual(store.write(copy.deepcopy(events[0])).status, "DUPLICATE")
            changed = copy.deepcopy(events[0])
            changed["reason"] = "different content"
            self.assertEqual(store.write(changed).status, "UNKNOWN")

    def test_unknown_is_not_normalized_to_success_or_failure(self):
        event = supplementary_record("supp-unknown", "test_unknown", "UNKNOWN")
        validate_record(event)
        self.assertEqual(event["status"], "UNKNOWN")

    def test_supplementary_requires_complete_relation_and_status(self):
        event = supplementary_record("supp-invalid", "human_gate", "UNKNOWN")
        del event["implementation_commit_sha"]
        with self.assertRaises(EvidenceValidationError):
            validate_record(event)


if __name__ == "__main__":
    unittest.main()