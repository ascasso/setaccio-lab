# Thinking diagnostic role-capability enforcement

Commit `2b0b89c` ("fix: enforce thinking diagnostic role capabilities before
running", PR79 P1) fixed a validation gap in the thinking diagnostic's model
resolution and landed with source, test, and commit-message coverage but no
`CHANGELOG.md` entry and no dated log entry. This entry closes that Standing
Work Loop gap; it makes no code change and reruns nothing.

## What the fix does

`ThinkingDiagnosticRunner.resolveIdentities` records each resolved identity's
advertised thinking capability but, before this fix, never checked it against
the identity's assigned role. A subject that doesn't advertise thinking, a
control that does, or the same installed artifact resolved for both roles
would still pass through and allocate an evidence run — even though the
diagnostic's entire subject-versus-non-thinking-control comparison is only
valid when the subject advertises thinking, the control does not, and the two
are distinct artifacts.

`ThinkingDiagnosticModelInventory.requireDistinctRoleSatisfyingIdentities` now
runs immediately after both identities resolve and before any output
directory is allocated. It throws `ThinkingDiagnosticModelUnavailableException`
when the subject does not advertise thinking, when the control does, or when
subject and control resolve to the same digest, and throws
`IllegalArgumentException` when a role's identity is missing entirely.

## Verification

`ThinkingDiagnosticModelInventoryTest` (new, provider-free) covers all four
rejection cases and the accepting case. Ran
`./gradlew :setaccio-core:test :setaccio-lab:test :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build --rerun-tasks`;
all tasks completed successfully, offline, with no Ollama, Anthropic, or other
provider contacted. Ran `git diff --check`.

## What this does not do

No evidence directory was allocated, read, changed, reanalyzed, or published.
No model was pulled or invoked. No closed suite, closeout, or retained
evidence was touched — the thinking diagnostic itself has no formal evidence
on disk to affect. No release, tag, or push occurred.
