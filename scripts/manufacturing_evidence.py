"""Validate and append immutable manufacturing evidence records."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


RESULT_STATUSES = {"PASS", "FAIL", "NOT_RUN", "UNKNOWN"}
REVIEW_CONCLUSIONS = {"PASS", "FIXABLE_FINDINGS", "ESCALATE"}
EVIDENCE_TYPES = {"PRIMARY", "SUPPLEMENTARY"}
SUPPLEMENTARY_ANOMALY_TYPES = {
    "push_success", "push_failure", "push_unknown",
    "remote_verification_success", "remote_verification_failure", "remote_verification_unknown",
    "pull_request_success", "pull_request_failure", "pull_request_unknown",
    "crash", "evidence_write_unknown", "evidence_verify_unknown", "test_unknown",
    "review_unknown", "human_gate",
}
REQUIRED_CHECKS = (
    "implementation",
    "focused_test",
    "analyze",
    "full_regression",
    "auto_fix",
    "retest",
)
REQUIRED_REVIEWS = ("design_review", "implementation_review", "re_review")
DEFAULT_EVIDENCE_DIRECTORY = Path("manufacturing/evidence")
REQUIRED_IDENTITY = (
    "evidence_id",
    "evidence_type",
    "fip_id",
    "pipeline_step",
    "run_id",
    "created_at",
)
REQUIRED_REPOSITORY = (
    "repository",
    "branch",
    "implementation_commit_sha",
    "base_sha",
    "worktree_status",
    "changed_files",
)
FORBIDDEN_FIELDS = {
    "credential", "credentials", "secret", "secrets", "token", "tokens",
    "conversation", "conversation_body", "raw_response", "raw_provider_response",
    "provider_response", "execution_authority", "permission", "approval_authority",
}


class EvidenceValidationError(ValueError):
    pass


class EvidenceConflictError(EvidenceValidationError):
    pass


@dataclass(frozen=True)
class EvidenceWriteResult:
    status: str
    path: Path | None = None
    reason: str = ""


def _require_mapping(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise EvidenceValidationError(f"{name} must be an object")
    return value


def _require_non_empty_string(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise EvidenceValidationError(f"{name} must be a non-empty string")
    return value


def _validate_result(value: Any, name: str) -> None:
    result = _require_mapping(value, name)
    status = result.get("status")
    if status not in RESULT_STATUSES:
        raise EvidenceValidationError(f"{name}.status is invalid")
    _require_non_empty_string(result.get("summary"), f"{name}.summary")


def _validate_review(value: Any, name: str) -> None:
    review = _require_mapping(value, name)
    if review.get("conclusion") not in REVIEW_CONCLUSIONS:
        raise EvidenceValidationError(f"{name}.conclusion is invalid")
    _require_non_empty_string(review.get("evidence"), f"{name}.evidence")
    _require_non_empty_string(review.get("reviewed_commit_sha"), f"{name}.reviewed_commit_sha")


def _reject_forbidden_fields(value: Any) -> None:
    if isinstance(value, dict):
        for field, nested in value.items():
            if field.lower() in FORBIDDEN_FIELDS:
                raise EvidenceValidationError(f"{field} is not permitted in Evidence")
            _reject_forbidden_fields(nested)
    elif isinstance(value, list):
        for item in value:
            _reject_forbidden_fields(item)


def _validate_outcome(value: Any) -> None:
    outcome = _require_mapping(value, "outcome")
    if outcome.get("status") not in RESULT_STATUSES:
        raise EvidenceValidationError("outcome.status is invalid")
    if not isinstance(outcome.get("human_gate"), bool) or not isinstance(
        outcome.get("unknown"), bool
    ):
        raise EvidenceValidationError("outcome.human_gate and outcome.unknown must be booleans")
    _require_non_empty_string(outcome.get("stop_reason"), "outcome.stop_reason")
    if outcome["unknown"] != (outcome["status"] == "UNKNOWN"):
        raise EvidenceValidationError("outcome.unknown must match outcome.status")
    if outcome["status"] == "UNKNOWN" and not outcome["human_gate"]:
        raise EvidenceValidationError("UNKNOWN requires human_gate")


def _validate_primary(record: dict[str, Any]) -> None:
    allowed_fields = {
        "evidence_id", "evidence_type", "fip_id", "pipeline_step", "run_id", "created_at",
        "schema_version", "repository", "manufacturing", "verification", "reviews", "outcome",
    }
    unexpected = set(record) - allowed_fields
    if unexpected:
        raise EvidenceValidationError(f"PRIMARY fields are not permitted: {sorted(unexpected)}")
    _require_non_empty_string(record.get("schema_version"), "schema_version")
    if record["schema_version"] != "1":
        raise EvidenceValidationError("schema_version must be '1' for PRIMARY Evidence")

    repository = _require_mapping(record.get("repository"), "repository")
    for field in REQUIRED_REPOSITORY:
        if field == "changed_files":
            if not isinstance(repository.get(field), list) or not all(
                isinstance(item, str) and item for item in repository[field]
            ):
                raise EvidenceValidationError("repository.changed_files must be a list of strings")
        else:
            _require_non_empty_string(repository.get(field), f"repository.{field}")

    manufacturing = _require_mapping(record.get("manufacturing"), "manufacturing")
    for field in (
        "manifest_path", "approval_status", "prerequisite_result", "allowed_paths",
        "prohibited_changes", "manufacturing_result", "manufacturing_reason",
    ):
        if field in {"allowed_paths", "prohibited_changes"}:
            value = manufacturing.get(field)
            if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
                raise EvidenceValidationError(f"manufacturing.{field} must be a list of strings")
        else:
            _require_non_empty_string(manufacturing.get(field), f"manufacturing.{field}")
    required_checks = manufacturing.get("required_checks")
    if not isinstance(required_checks, list) or not all(
        isinstance(item, str) and item for item in required_checks
    ):
        raise EvidenceValidationError("manufacturing.required_checks must be a list of strings")

    verification = _require_mapping(record.get("verification"), "verification")
    for name in REQUIRED_CHECKS:
        _validate_result(verification.get(name), f"verification.{name}")
    reviews = _require_mapping(record.get("reviews"), "reviews")
    for name in REQUIRED_REVIEWS:
        _validate_review(reviews.get(name), f"reviews.{name}")
    _validate_outcome(record.get("outcome"))


def _validate_supplementary(record: dict[str, Any]) -> None:
    allowed_fields = {
        "evidence_id", "evidence_type", "fip_id", "pipeline_step", "run_id",
        "repository", "implementation_commit_sha", "created_at", "anomaly_type", "status", "reason",
        "unknown_details", "human_gate_details",
    }
    unexpected = set(record) - allowed_fields
    if unexpected:
        raise EvidenceValidationError(
            f"SUPPLEMENTARY fields are not permitted: {sorted(unexpected)}"
        )
    _require_non_empty_string(record.get("repository"), "repository")
    _require_non_empty_string(record.get("implementation_commit_sha"), "implementation_commit_sha")
    if record.get("anomaly_type") not in SUPPLEMENTARY_ANOMALY_TYPES:
        raise EvidenceValidationError("anomaly_type is invalid")
    if record.get("status") not in RESULT_STATUSES:
        raise EvidenceValidationError("status is invalid")
    _require_non_empty_string(record.get("reason"), "reason")
    _require_non_empty_string(record.get("unknown_details"), "unknown_details")
    _require_non_empty_string(record.get("human_gate_details"), "human_gate_details")


def validate_record(record: dict[str, Any]) -> None:
    if not isinstance(record, dict):
        raise EvidenceValidationError("Evidence Record must be an object")
    for field in REQUIRED_IDENTITY:
        _require_non_empty_string(record.get(field), field)
    if record["evidence_type"] == "PRIMARY":
        _validate_primary(record)
    elif record["evidence_type"] == "SUPPLEMENTARY":
        _validate_supplementary(record)
    else:
        raise EvidenceValidationError("evidence_type is invalid")
    _reject_forbidden_fields(record)


def _canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"))


def idempotency_key(record: dict[str, Any]) -> tuple[str, str, str, str]:
    validate_record(record)
    repository_value = record.get("repository", {})
    if isinstance(repository_value, dict):
        repository = repository_value.get("repository", "")
        commit_sha = repository_value.get("implementation_commit_sha", "")
    else:
        repository = repository_value
        commit_sha = record.get("implementation_commit_sha", "")
    return (repository, record["pipeline_step"], record["run_id"], commit_sha)


class EvidenceStore:
    """Append-only JSON store. It never performs Git or G1 operations."""

    def __init__(self, directory: Path):
        self.directory = directory

    def write(self, record: dict[str, Any]) -> EvidenceWriteResult:
        validate_record(record)
        evidence_id = record["evidence_id"]
        if Path(evidence_id).name != evidence_id or evidence_id in {".", ".."}:
            raise EvidenceValidationError("evidence_id must be a simple filename-safe value")
        path = self.directory / f"{evidence_id}.json"
        if record["evidence_type"] == "SUPPLEMENTARY":
            return self._write_supplementary(path, record)

        key = idempotency_key(record)
        existing_records = self._records()
        for existing_path, existing in existing_records:
            if idempotency_key(existing) != key:
                continue
            if _canonical_json(existing) == _canonical_json(record):
                return EvidenceWriteResult("DUPLICATE", existing_path, "same key and content")
            return EvidenceWriteResult("UNKNOWN", existing_path, "same key with different content")
        self.directory.mkdir(parents=True, exist_ok=True)
        try:
            self._create_exclusive(path, record)
        except FileExistsError:
            try:
                existing = self._load(path)
            except EvidenceValidationError:
                return EvidenceWriteResult("UNKNOWN", path, "existing Evidence could not be verified")
            if _canonical_json(existing) == _canonical_json(record):
                return EvidenceWriteResult("DUPLICATE", path, "same key and content")
            return EvidenceWriteResult("UNKNOWN", path, "same key with different content")
        return EvidenceWriteResult("CREATED", path, "new immutable record")

    def _write_supplementary(
        self, path: Path, record: dict[str, Any]
    ) -> EvidenceWriteResult:
        self.directory.mkdir(parents=True, exist_ok=True)
        try:
            self._create_exclusive(path, record)
        except FileExistsError:
            try:
                existing = self._load(path)
            except EvidenceValidationError:
                return EvidenceWriteResult("UNKNOWN", path, "existing Evidence could not be verified")
            if _canonical_json(existing) == _canonical_json(record):
                return EvidenceWriteResult("DUPLICATE", path, "same evidence_id and content")
            return EvidenceWriteResult("UNKNOWN", path, "same evidence_id with different content")
        return EvidenceWriteResult("CREATED", path, "new immutable supplementary record")

    @staticmethod
    def _create_exclusive(path: Path, record: dict[str, Any]) -> None:
        with path.open("x", encoding="utf-8") as evidence_file:
            json.dump(record, evidence_file, ensure_ascii=True, indent=2)
            evidence_file.write("\n")

    def _records(self) -> list[tuple[Path, dict[str, Any]]]:
        if not self.directory.exists():
            return []
        records = []
        for path in sorted(self.directory.glob("*.json")):
            records.append((path, self._load(path)))
        return records

    @staticmethod
    def _load(path: Path) -> dict[str, Any]:
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise EvidenceValidationError(f"unable to read Evidence: {path}") from error
        validate_record(value)
        return value


def load_record(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise EvidenceValidationError(f"invalid Evidence JSON: {path}") from error
    validate_record(value)
    return value


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record", type=Path, required=True)
    parser.add_argument("--directory", type=Path, default=DEFAULT_EVIDENCE_DIRECTORY)
    args = parser.parse_args(argv)
    try:
        result = EvidenceStore(args.directory).write(load_record(args.record))
    except EvidenceValidationError as error:
        print(json.dumps({"status": "UNKNOWN", "reason": str(error)}, ensure_ascii=True))
        return 2
    print(json.dumps(result.__dict__, ensure_ascii=True, default=str, indent=2))
    return 0 if result.status in {"CREATED", "DUPLICATE"} else 2


if __name__ == "__main__":
    raise SystemExit(main())