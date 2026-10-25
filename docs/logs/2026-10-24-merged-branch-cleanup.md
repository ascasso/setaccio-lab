# 2026-10-24

## Merged-branch cleanup

### Authorization and work

The owner requested the remaining cleanup listed in `docs/NEXT-WORK-PLAN.md`.
After refreshing origin, all seven named local branches passed
`git merge-base --is-ancestor <branch> origin/develop`; none was used by a
worktree. The develop worktree at `/Users/ascasso/IdeaProjects/agy` was left
untouched, as were develop, master, and the current cleanup branch.

Removed with `git branch -d`:

| Branch | Previous tip |
| --- | --- |
| feature/anthropic-portability | b783596 |
| feature/chat-evidence-reuse | ddec792 |
| feature/local-fact-checking-evaluation | 724436b |
| feature/vision-evidence-lifecycle | 16b0e1a |
| feature/vision-prompt-experiment | a473211 |
| plan | 918d42e |
| feature/tool-compatibility-plan | 84367f0 |

Six corresponding remote branches were already absent. Removed only the
remaining remote `feature/tool-compatibility-plan` with a normal explicit
branch deletion. Updated README, next-work plan, deferred-work index, and
changelog. Capability, environment, and test-plan contracts are unchanged.

### Verification

- Refreshed `origin/develop`: `a2f7754b72ec1acf53bbfa8bea4302c6077680da`.
- Local branch listing and live `git ls-remote --heads origin` confirmed all
  seven names absent; only develop, master, and the cleanup branch remain.
- `./gradlew :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build`:
  BUILD SUCCESSFUL; 18 tasks up-to-date and the Spring-free guard executed.
- `git diff --check` passed.

### Boundaries

No tag publication, release, merge, promotion, MCP implementation, model run,
provider invocation, or evidence modification was performed. The Ollama setup
guide remains an independent follow-up. M0 is next; its implementation slices
are not started by this cleanup. The standing work loop covers the focused
record commit and one current-branch push attempt, separately from the
explicitly requested obsolete-branch deletion.
