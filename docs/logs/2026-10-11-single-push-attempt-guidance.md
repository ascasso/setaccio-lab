# One Push Attempt After Completed Commits

## Authorized

The project owner requested changing the repository guidance so that one push
is attempted after one or more commits. This replaces the previous per-session
branch-push gate with standing authorization for one normal push of the current
working branch to `origin` after each completed commit batch.

## Done

- Added the fifth Standing Work Loop step in `AGENTS.md`, including an explicit
  current-branch refspec, no tag following, upstream setup when needed, remote
  SHA verification on success, and failure reporting without automatic retry.
- Removed conflicting active push prohibitions from agent guidance and the
  historical plan's reusable dispatch/completion templates.
- Aligned README, environment guidance, deferred-work policy, the next-work
  plan, the historical plan's branch strategy, and the unreleased changelog.
- Preserved dated historical closeouts and the explicit `v0.1.0` tag-publication
  decision. The earlier cleanup-plan commit was pushed by the owner, as shown
  by the supplied shell result; this is a separate guidance change.

## Verification and Push Closeout

Ran
`./gradlew :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build --offline --no-daemon`:
`BUILD SUCCESSFUL` in 3 seconds, with one executed task and 18 up-to-date.
The core Spring-free classpath guard executed; existing provider-free tests
were up-to-date, not freshly rerun. No source or test change affects a
dedicated suite test task.

`git diff --check` passed. All 52 local Markdown links across the eight
changed/new files resolve. The new dated log is not ignored. A targeted search
found no conflicting active `do not push` or per-session branch-push gate in
the updated guidance; dated historical authorization and tag gates remain.

After the focused commit, one normal push of `feature/cleanup-mcp-plan` to
its matching `origin` branch will be attempted with tag following disabled.
The final response will report the attempt and remote verification result;
this entry does not claim a push succeeded before it occurs.

## Boundaries

No runtime code, dependency, test behavior, model/provider, MCP implementation,
or saved evidence changes. The new standing authorization covers the current
branch's completed commits only. It does not authorize automatic retries,
force-pushes, another branch target, tags, releases, PRs, merges, or promotion.
