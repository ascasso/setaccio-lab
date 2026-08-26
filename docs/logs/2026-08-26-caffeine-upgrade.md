# Caffeine dependency upgrade

On 2026-08-26, the explicitly declared Caffeine dependency was upgraded from
`3.2.0` to `3.2.4` in the version catalog. The dependency remains explicit
because `setaccio-lab` directly uses Caffeine's API in `LabConfig`.

Verification:

- `./gradlew :setaccio-lab:dependencyInsight --dependency caffeine --configuration runtimeClasspath --no-daemon`
  resolved Caffeine `3.2.4`.
- `./gradlew clean build --no-daemon` passed across all modules and tests.
- `git diff --check` passed.

No provider, Docker, or remote service was used. No push was performed.
