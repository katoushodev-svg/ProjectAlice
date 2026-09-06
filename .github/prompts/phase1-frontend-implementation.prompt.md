# Phase 1 Frontend — Single-FIP Execution Lifecycle

This file defines the reusable lifecycle for **one explicitly invoked FIP**. It is not authorization to execute multiple FIPs.

## Context Budget

1. Apply the repository Copilot instructions already provided by the workspace.
2. Read the invoking FIP prompt.
3. Read the FIP prompt's Primary Source.
4. Do **not** preload any other design/review document.
5. Open a supporting document only when a concrete unresolved implementation question requires it, and only the exact cited section needed.

## Lifecycle

1. Treat invocation of the FIP prompt as authorization for that FIP only and as user attestation that the previous gate was accepted.
2. Inspect relevant existing source/tests, run `git status --short`, and inspect only FIP-relevant existing changes with scoped `git diff -- <path>` when needed.
3. From the Primary Source, identify exact scope, expected/allowed files, Acceptance Criteria, prohibited work, dependencies, and any exact validation commands.
4. Implement only the current FIP.
5. Fix ordinary implementation defects autonomously using focused checks.
6. Apply the Deterministic Test Policy from the repository Copilot instructions already provided by the workspace.
7. Self-review against the Primary Source Acceptance Criteria and architecture/dependency/security boundaries actually relevant to this FIP.
8. Report:
   - files created/modified;
   - commands/tests executed and results;
   - Acceptance Criteria: PASS/FAIL;
   - boundary review: PASS/FAIL;
   - unresolved blockers, if any;
   - concise `git diff`/`git status` summary.
9. Stop. Do not start another FIP, commit, or push.

## Gate Result

`PASS` requires all of the following:

- current FIP implementation complete;
- required validation commands pass;
- Acceptance Criteria self-review passes;
- no Stop Condition remains.

Otherwise report `BLOCKED` with the smallest concrete reason and the exact Source-of-Truth location involved.
