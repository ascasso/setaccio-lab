# setaccio-testcontainers

Optional Testcontainers-backed integration harness for `setaccio-lab`.

This module exists so Docker and Testcontainers support can evolve without becoming a requirement for the main lab application.

## Boundary

- `setaccio-testcontainers` may depend on `setaccio-lab`.
- `setaccio-lab` must not depend on `setaccio-testcontainers`.
- Root builds must not require Docker to be installed or running.
- Container-backed tests must stay behind an explicit task, profile, or property.

## Current state

This module is a skeleton. It declares Spring AI's Testcontainers support dependency but does not start any containers by default.

Future work should add a dedicated integration-test task before adding Docker-backed tests.
