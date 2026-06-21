# 2026-06-21

## Dependency update

### Summary
- Upgraded Spring Boot to `4.1.0` across the Gradle build.
- Pinned `commons-codec` to `1.22.0` and `slf4j-api` to `2.0.18` across all subprojects.
- Kept `assertj-core` at `3.27.7`.

### Verification
- `./gradlew allDeps`
- `./gradlew :setaccio-core:build :setaccio-lab:build :setaccio-testcontainers:build`
