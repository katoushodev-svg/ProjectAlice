#!/usr/bin/env python3
"""Generate a non-mutating commit/PR candidate after a manufacturing PASS."""

from __future__ import annotations

import argparse
import fnmatch
import json
import posixpath
import sys
from pathlib import Path
from typing import Any


REQUIRED_CHECKS = (
    "implementation",
    "focused_test",
    "analyze",
    "full_regression",
    "ai_review",
    "retest",
    "rereview",
)
REVIEW_CHECKS = ("ai_review", "rereview")
FORBIDDEN_MARKERS = (
    "architecture",
    "api",
    "database",
    "security",
    "safety",
)
HUMAN_GATE_FLAGS = (
    "design_change",
    "architecture_change",
    "api_contract_change",
    "db_design_change",
    "security_safety_change",
    "phase_boundary_change",
    "fip_scope_change",
    "new_dependency",
    "unknown_changes",
)
PROTECTED_BOUNDARY_FLAGS = {
    "design": "design_change",
    "architecture": "architecture_change",
    "api_contract": "api_contract_change",
    "database": "db_design_change",
    "security_safety": "security_safety_change",
    "phase_boundary": "phase_boundary_change",
    "fip_scope": "fip_scope_change",
    "new_dependency": "new_dependency",
    "unknown_changes": "unknown_changes",
}


class ManufacturingGateError(ValueError):
    pass


def _passed(checks: dict[str, Any], name: str) -> bool:
    value = checks.get(name)
    return (
        isinstance(value, dict)
        and value.get("status") == "PASS"
        and isinstance(value.get("summary"), str)
        and bool(value["summary"].strip())
    )


def _manifest_failures(fip: str, manifest: dict[str, Any]) -> list[str]:
    fip_manifest = manifest.get(fip)
    if not isinstance(fip_manifest, dict):
        return [f"no manifest exists for {fip}"]
    if fip_manifest.get("approval_status") != "Approved / Implementation Ready":
        return [f"{fip} is not Approved / Implementation Ready"]
    if not isinstance(fip_manifest.get("plan"), str):
        return ["manifest plan is invalid"]
    required_fields = (
        "prerequisites",
        "prohibited_changes",
        "required_checks",
        "review_policy",
        "run_budget",
    )
    missing = [field for field in required_fields if field not in fip_manifest]
    if missing:
        return ["manifest fields are missing: " + ", ".join(missing)]
    if not isinstance(fip_manifest["review_policy"], str) or not fip_manifest[
        "review_policy"
    ].strip():
        return ["manifest review_policy is invalid"]
    if not isinstance(fip_manifest["run_budget"], int) or fip_manifest["run_budget"] < 1:
        return ["manifest run_budget is invalid"]
    if not isinstance(fip_manifest["required_checks"], list) or not all(
        isinstance(item, str) for item in fip_manifest["required_checks"]
    ):
        return ["manifest required_checks are invalid"]
    if set(fip_manifest["required_checks"]) != set(REQUIRED_CHECKS):
        return ["manifest required_checks do not match the manufacturing gate"]
    if not isinstance(fip_manifest["prohibited_changes"], list) or not all(
        isinstance(item, str) for item in fip_manifest["prohibited_changes"]
    ):
        return ["manifest prohibited_changes are invalid"]
    if not set(fip_manifest["prohibited_changes"]).issuperset(PROTECTED_BOUNDARY_FLAGS):
        return ["manifest prohibited_changes do not cover required boundaries"]
    return []


def manufacturing_pass(record: dict[str, Any]) -> tuple[bool, list[str]]:
    failures: list[str] = []
    checks = record.get("checks")
    if not isinstance(checks, dict):
        failures.append("checks are missing")
        checks = {}

    for name in REQUIRED_CHECKS:
        if not _passed(checks, name):
            failures.append(f"{name} is not PASS")

    auto_fix = record.get("auto_fix")
    if not isinstance(auto_fix, dict):
        failures.append("auto_fix is missing")
    elif auto_fix.get("required") is not False and not _passed(checks, "retest"):
        failures.append("retest is required after auto-fix")

    if record.get("human_gate") is not False:
        failures.append("human_gate is present")

    return not failures, failures


def _changed_files_are_safe(record: dict[str, Any], manifest: dict[str, Any]) -> list[str]:
    changed_files = record.get("changed_files")
    if not isinstance(changed_files, list) or not changed_files:
        return ["changed_files are missing"]

    fip = record.get("fip")
    manifest_failures = _manifest_failures(fip, manifest)
    if manifest_failures:
        return manifest_failures
    fip_manifest = manifest.get(fip)

    allowed_paths = fip_manifest.get("allowed_paths", [])
    if not isinstance(allowed_paths, list) or not allowed_paths:
        return ["manifest allowed_paths are invalid"]
    if not all(isinstance(pattern, str) and pattern for pattern in allowed_paths):
        return ["manifest allowed_paths must contain strings"]

    failures: list[str] = []
    for file_path in changed_files:
        if not isinstance(file_path, str) or not file_path:
            failures.append("changed_files contains an invalid path")
            continue
        normalized_path = posixpath.normpath(file_path)
        if (
            file_path.startswith("/")
            or "\\" in file_path
            or normalized_path == ".."
            or normalized_path.startswith("../")
        ):
            failures.append(f"unsafe changed path: {file_path}")
            continue
        if not any(
            fnmatch.fnmatch(normalized_path, pattern) for pattern in allowed_paths
        ):
            failures.append(f"file is outside FIP scope: {file_path}")
        lowered = normalized_path.lower()
        if any(marker in lowered for marker in FORBIDDEN_MARKERS):
            failures.append(f"protected boundary path: {file_path}")
    return failures


def validate_record(record: dict[str, Any], manifest: dict[str, Any]) -> None:
    if not record.get("fip"):
        raise ManufacturingGateError("fip is required")
    if not record.get("run_id"):
        raise ManufacturingGateError("run_id is required")
    manifest_failures = _manifest_failures(record["fip"], manifest)
    if manifest_failures:
        raise ManufacturingGateError("; ".join(manifest_failures))
    if record.get("fip_approved") is not True:
        raise ManufacturingGateError("fip_approved must be true")
    if record.get("implementation_ready") is not True:
        raise ManufacturingGateError("implementation_ready must be true")
    fip_manifest = manifest.get(record["fip"])
    prerequisite_gates = record.get("prerequisite_gates")
    if (
        not isinstance(fip_manifest, dict)
        or not isinstance(prerequisite_gates, dict)
        or any(
            prerequisite_gates.get(prerequisite) != "PASS"
            for prerequisite in fip_manifest.get("prerequisites", [])
        )
    ):
        raise ManufacturingGateError("prerequisite FIP gates are incomplete")
    if record.get("base_branch") != "main":
        raise ManufacturingGateError("base_branch must be main")
    if not isinstance(record.get("branch"), str) or record["branch"] in ("", "main"):
        raise ManufacturingGateError("an isolated non-main branch is required")
    if record.get("commit_created") is not False:
        raise ManufacturingGateError("commit_created must be false")
    if record.get("push_performed") is not False:
        raise ManufacturingGateError("push_performed must be false")
    if record.get("merge_performed") is not False:
        raise ManufacturingGateError("merge_performed must be false")
    fip_manifest = manifest.get(record["fip"], {})
    auto_fix = record.get("auto_fix", {})
    if isinstance(fip_manifest, dict) and isinstance(auto_fix, dict):
        attempts = auto_fix.get("attempts", 0)
        if not isinstance(attempts, int) or attempts < 0 or attempts > fip_manifest.get(
            "run_budget", 0
        ):
            raise ManufacturingGateError("auto-fix attempts exceed the run budget")
    invalid_flags = [
        flag for flag in HUMAN_GATE_FLAGS if not isinstance(record.get(flag), bool)
    ]
    if invalid_flags:
        raise ManufacturingGateError(
            "human gate flags must be explicit booleans: " + ", ".join(invalid_flags)
        )
    active_flags = [
        flag
        for boundary, flag in PROTECTED_BOUNDARY_FLAGS.items()
        if boundary in fip_manifest["prohibited_changes"] and record[flag]
    ]
    if active_flags:
        raise ManufacturingGateError(
            "human gate required: " + ", ".join(active_flags)
        )
    checks = record.get("checks", {})
    if isinstance(checks, dict):
        invalid_reviews = []
        for name in REVIEW_CHECKS:
            review = checks.get(name)
            if (
                not isinstance(review, dict)
                or review.get("conclusion") != "PASS"
                or not isinstance(review.get("evidence"), str)
                or not review["evidence"].strip()
                or not isinstance(review.get("severity"), str)
                or not isinstance(review.get("allowed_scope"), str)
                or not isinstance(review.get("retest_required"), bool)
            ):
                invalid_reviews.append(name)
        if invalid_reviews:
            raise ManufacturingGateError(
                "review evidence is incomplete: " + ", ".join(invalid_reviews)
            )

    scope_failures = _changed_files_are_safe(record, manifest)
    if scope_failures:
        raise ManufacturingGateError("; ".join(scope_failures))

    passed, failures = manufacturing_pass(record)
    if not passed:
        raise ManufacturingGateError("; ".join(failures))


def build_candidate(record: dict[str, Any], manifest: dict[str, Any]) -> dict[str, Any]:
    validate_record(record, manifest)
    fip = record["fip"]
    changed_files = record["changed_files"]
    plan_name = posixpath.basename(manifest[fip]["plan"])
    purpose = plan_name.removeprefix(f"{fip.lower()}-").removesuffix("-plan.md").replace(
        "-", " "
    )
    checks = record["checks"]
    evidence_lines = [
        f"- {name}: {checks[name]['summary']}"
        for name in REQUIRED_CHECKS
    ]
    return {
        "candidate_status": "READY",
        "fip": fip,
        "run_id": record["run_id"],
        "branch": record["branch"],
        "base": "main",
        "changed_files": changed_files,
        "manufacturing_record": record,
        "commit_candidate": {
            "message": f"feat({fip.lower()}): implement {purpose}",
            "files": changed_files,
        },
        "pr_candidate": {
            "title": f"[{fip}] Manufacturing PASS candidate",
            "base": "main",
            "head": record["branch"],
            "body": "\n".join(
                [
                    f"FIP: {fip}",
                    "Manufacturing PASS: PASS",
                    f"Run: {record['run_id']}",
                    "Manufacturing Record: attached in candidate evidence",
                    "",
                    "Required checks:",
                    *evidence_lines,
                    f"- auto fix: {record['auto_fix']['summary']}",
                    f"- changed files: {', '.join(changed_files)}",
                    "- Human Gate: clear",
                    "- Auto-merge: disabled",
                ]
            ),
        },
        "actions_performed": {
            "commit": False,
            "push": False,
            "pull_request": False,
            "merge": False,
        },
    }


def _load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ManufacturingGateError(f"JSON object required: {path}")
    return value


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        candidate = build_candidate(_load_json(args.record), _load_json(args.manifest))
    except (OSError, TypeError, json.JSONDecodeError, ManufacturingGateError) as error:
        print(f"HUMAN GATE: {error}", file=sys.stderr)
        return 2

    rendered = json.dumps(candidate, ensure_ascii=True, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())