# 2026-10-24

## Existing 0.1.0 publication preparation

### Authorization and changes

The owner selected preparation of the existing 0.1.0 for publication, rather
than starting MCP work or cutting a new release.

- Prepared `docs/releases/0.1.0-notes.md` as the proposed GitHub release body.
- Prepared `docs/releases/0.1.0-publication.md` with the exact immutable target,
  explicit tag push and GitHub release commands, and final verification steps.
- Updated README, next-work plan, deferred-work index, and changelog to link
  the preparation and retain publication as pending approval.
- Checked tagged build configuration: version 0.1.0, Java 25, Gradle 9.7.1,
  Spring Boot 4.1.1, and Spring AI 2.0.1. Later wrapper changes are excluded.
- Live remote checks found no tags or GitHub releases on 2026-10-24.

### Verification scope

Exported `git archive v0.1.0` to a temporary directory and ran the exact tagged
source rather than substituting the current branch. The temporary copy contains
only tracked source and generated build output; it is not formal run evidence.
An initial Java 27 launch failed during Gradle buildSrc instrumentation.
Verification was repeated with the documented Java 25 launcher (GraalVM
25.0.4); normal Gradle cache access required sandbox escalation.

Verification succeeded on Java 25: all three module builds plus
`chatMatrixTest`, `toolCompatibilityTest`, `retrievalFixtureTest`,
`localEvaluationTest`, `localEvaluationBudgetTest`, `toolSearchSmokeTest`,
`visionMatrixTest`, and `thinkingDiagnosticTest`: BUILD SUCCESSFUL in 57s,
48 tasks executed. Current-branch three-module build also passed (18 tasks
up-to-date, core Spring-free guard executed). Local links in changed active
documents and release documents resolved; `git diff --check` passed.
Capability, environment, and test-plan runtime contracts remain unchanged.

### Boundaries

The existing annotated tag remains at
`016eef300764af4fa69898eba0eefb677a07b96f`. No tag push, GitHub release,
package publication, binary upload, branch promotion, merge, MCP work, live
provider invocation, or formal evidence read/write was performed. Source-only
publication is proposed. The preparation commit uses the standing one-attempt
current-branch push rule; that rule does not publish the release tag.
