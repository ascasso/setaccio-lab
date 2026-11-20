# Spring dependency updates - 2026-11-19

The owner authorized updating the Spring dependencies. Updated the Spring Boot plugin and dependency catalog from 4.1.1 to 4.1.2 and Spring AI from 2.0.1 to 2.0.2. Updated current version references in the MCP plan and Testcontainers README; historical records remain unchanged.

- `./gradlew build --no-daemon`: passed.
- Provider-free `chatMatrixTest`, `localEvaluationBudgetTest`, `localEvaluationTest`, `retrievalFixtureTest`, `thinkingDiagnosticTest`, `toolCompatibilityTest`, `toolSearchSmokeTest`, and `visionMatrixTest`: passed.
- An initial build failed because an accidental text replacement changed SLF4J to a nonexistent version; it was restored to 2.0.18 before the successful checks.
- No live provider calls, formal runs, release, tag, or other dependency update is claimed or authorized.
