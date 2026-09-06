# Project Alice Copilot Instructions

Project Alice is Design First. Repository design documents are the Source of Truth.

## Credit-Efficient Context Policy

- Read only the current FIP prompt and its **Primary Source** before editing.
- Never preload `docs/`, "all relevant canonical documents", all requirements, all reviews, or all design files.
- Do not reread Phase 0 / Cross-Phase review documents merely to confirm authorization.
- Read an additional design document only when a specific implementation question cannot be answered by the Primary Source.
- When additional context is required, open only the exact document/section cited by the Primary Source and relevant to that question. Prefer repository search/find over reading a whole large document.
- Do not copy formal design content into implementation prompts or source comments. Keep one Source of Truth.

## Authorization and Gate Semantics

- Invoking a `.github/prompts/fip-XXX.prompt.md` file explicitly authorizes **that FIP only**.
- Invocation also attests that the previous FIP gate was accepted by the user. Do not spend context rereading historical gate/review documents to prove it again.
- If the working tree or implementation clearly contradicts that attestation (for example, a required prior implementation is absent or existing tests are already failing for an unrelated reason), stop and report the concrete contradiction.
- A current FIP gate is PASS only when its required implementation is complete, its required checks pass, its Acceptance Criteria self-review passes, and no Stop Condition remains.
- Never start the next FIP unless a separate prompt explicitly authorizes it.

## Implementation Rules

- Before editing, run `git status --short`.
- Do not read the full repository diff by default.
- If existing changes affect files relevant to the current FIP, inspect only those paths with a scoped `git diff -- <path>`.
- Ignore unrelated existing changes unless they create a concrete conflict.
- Implement only the currently authorized FIP and its explicit Acceptance Criteria.
- Do not infer, invent, redesign, or expand scope.
- Do not add speculative abstractions, unauthorized dependencies, or later-phase work.
- Do not change formal design documents to fit implementation.
- Ordinary implementation defects may be fixed autonomously.
- No commit or push unless explicitly instructed by the user.

## Deterministic Test Policy

- If the Primary Source specifies exact validation commands, run those exact commands.
- Otherwise, from `frontend/`:
  1. format only changed Dart files with `dart format <changed-dart-files>`;
  2. run `flutter analyze`;
  3. during fix loops, run only the changed/affected test files;
  4. for the final gate, run full `flutter test`.
- Do not repeatedly run full regression tests inside ordinary fix loops unless a focused test cannot validate the fix.
- Do not run unrelated platform, backend, Android, web, or integration commands unless the Primary Source explicitly requires them.

## Stop Conditions

Stop and report instead of guessing when resolution requires any of the following:

- architecture change;
- API contract change;
- security model change;
- database design change;
- permission or approval model change;
- new or upgraded dependency not authorized by the Primary Source;
- FIP scope expansion or phase-boundary change;
- modification of formal design to make implementation pass;
- contradiction between Source-of-Truth documents;
- implementation-affecting ambiguity that cannot be resolved by the Primary Source or one specifically cited supporting section;
- destructive Git operation.

## Cross-Phase Invariants

- Conversation History is not Personal Memory.
- Memory does not grant Permission or Approval.
- Goal is not Permission.
- Tool selection is not Approval.
- A connector or API failure must never silently escalate into a more powerful PC, browser, or agent mechanism.
- Credentials must never become Conversation History or Personal Memory.
- Preserve the approved Project Alice visual hierarchy; do not reinterpret the product as a generic SaaS/dashboard.
