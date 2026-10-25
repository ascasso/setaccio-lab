# Cleanup and MCP Planning Sequence

## Authorized

The project owner requested a fresh branch and tracked documentation of the
next work: cleanup first, then planning for MCP. A later message requested a
read-only assessment of a proposed embedding endpoint/matrix. This change
completes branch creation and documentation; it does not implement that
embedding proposal or MCP.

## Done

- Created `feature/cleanup-mcp-plan` from `origin/develop` at
  `a2f7754b72ec1acf53bbfa8bea4302c6077680da`.
- Added `docs/NEXT-WORK-PLAN.md` with completed documentation cleanup, pending
  merged-branch removal and tag-publication items, the separate Ollama setup
  follow-up, and the test-first MCP sequence.
- Corrected `docs/DEFERRED-WORK.md`'s release row and outdated three-phase
  wording. Appended later promotion status to the release log, preserving its
  original authorization.
- Aligned README, capabilities, environment, test plan, the closed historical
  plan, agent guidance, and the unreleased changelog entry.
- Checked the embedding proposal against current code: the Spring AI
  `OllamaApi` embedding adapter and `retrievalEmbedding` offline lifecycle
  already exist; an interactive embedding endpoint and generic `EmbeddingModel`
  service would be separate additions. Recorded the useful process points and
  the reasons it does not replace the selected MCP direction.

## Verification

GitHub API reads confirmed PR #81/#82 promotion and PR #84's wrapper promotion.
The current `develop` and `master` commit trees match; their different commit
histories are not outstanding content. `git branch --merged origin/develop`
confirmed the branch candidates are contained in the integration branch.
`git rev-parse 'v0.1.0^{commit}'` resolved the local release tag to `016eef3`,
and the GitHub matching-refs API returned no matching tag. These are dated
status observations, to be refreshed before later ref changes.

Ran
`./gradlew :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build --offline --no-daemon`:
`BUILD SUCCESSFUL` in 4 seconds, with one executed task and 18 up-to-date.
The core Spring-free classpath guard executed; default provider-free test
tasks were up-to-date rather than freshly rerun. No runtime code or test
changed, so no dedicated suite test task was affected.

`git diff --check` passed. Checked all 64 local Markdown links across the
11 changed/new files; all targets exist. The two new Markdown paths are not
ignored. Only the intended documentation files are included in the commit.

## Boundaries

No source, dependency, endpoint, model, protocol, default lifecycle, or saved
evidence changed. No live Ollama/remote-provider call, model pull, branch
deletion, tag creation/movement/publication, push, PR, merge, release, or Docker
action was performed. Retained evidence was not reverified or reanalyzed by
this change; the existing closeouts and waivers stand.
