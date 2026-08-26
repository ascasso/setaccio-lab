# Remove duplicate setaccio-core wrappers

On 2026-08-26, the redundant `setaccio-core/gradlew` and
`setaccio-core/gradlew.bat` scripts were removed. The repository root wrapper
is the canonical Gradle entry point, and the module-level scripts had no
matching module-level wrapper JAR or properties.

Verification:

- `./gradlew :setaccio-core:clean :setaccio-core:build --no-daemon` passed.
- `./gradlew clean build --no-daemon` passed across all modules and tests.
- `git diff --check` passed.

No push was performed.
