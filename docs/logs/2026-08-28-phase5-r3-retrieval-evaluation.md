# Phase 5 R3 retrieval-only evaluation and evidence

Phase 5 R3 is complete as a provider-free, retrieval-only lexical evidence
slice. It uses the approved version-one public corpus and the human-confirmed
version-one query catalog; it does not start Spring, call Ollama or any other
provider, use embeddings or a vector store, generate an answer, or invoke an
AI evaluator.

## Implementation

The dedicated `retrievalEvaluation` task runs the fourteen confirmed fixtures
sequentially through R2's locked `exact-distinct-query-coverage` method. Every
row is immediately repeated for a deterministic stability check. Its raw JSON
retains the fixture labels and query, corpus and catalog identities, complete
lexical parameters, ordered hit identities, ranks, exact score fields, matched
terms, and the complete public corpus text for every returned document.

New evidence is written non-overwriting to a dated directory directly under
ignored `build/retrieval-evaluation/`. The shared v1 manifest declares the raw
JSON and deterministic `SUMMARY.md` with SHA-256 integrity metadata. Dedicated
offline `retrievalEvaluationVerify`, `retrievalEvaluationReanalyze`, and
`retrievalEvaluationCompare` tasks reject unsafe paths, symlinks, unexpected
artifacts, manifest/raw/settings drift, changed corpus content, and summary
drift. Reanalysis may replace only a deterministic summary after the raw input
and manifest remain valid.

## Clean provider-free evidence

One fresh evidence run completed from clean commit
`5394f64cc975ff7c2bf61c839ed320a2d9930726`. Its ignored output directory is
`build/retrieval-evaluation/2026-08-28-r3-baseline/`; it is not a published
artifact.

For the exact corpus, confirmed labels, and locked lexical method, the saved
aggregate observations were:

| Retrieval-only measure | Result |
| --- | ---: |
| Expected supporting document retrieved | 12/12 matching fixtures |
| Expected supporting document in top 1 | 12/12 matching fixtures |
| Expected supporting document in top 3 | 12/12 matching fixtures |
| Fixtures retrieving a forbidden document | 0/12 matching fixtures |
| Correct no-match | 2/2 no-match fixtures |
| Rows stable across immediate repeat | 14/14 fixtures |

The saved run verified before and after deterministic reanalysis. A self-compare
of the verified run reported zero deltas across every metric and preserved
document ID. This is an algorithm-contract observation against the exact
human-confirmed fixtures. It makes no general retrieval-quality, semantic
relevance, answer-quality, embedding, model, or AI-evaluator claim.

## Verification

The following provider-free checks passed:

```text
./gradlew :setaccio-lab:retrievalFixtureTest --rerun-tasks --no-daemon
./gradlew :setaccio-lab:retrievalEvaluation --output-dir=build/retrieval-evaluation/2026-08-28-r3-baseline --no-daemon
./gradlew :setaccio-lab:retrievalEvaluationVerify --run-dir=build/retrieval-evaluation/2026-08-28-r3-baseline --no-daemon
./gradlew :setaccio-lab:retrievalEvaluationReanalyze --run-dir=build/retrieval-evaluation/2026-08-28-r3-baseline --no-daemon
./gradlew :setaccio-lab:retrievalEvaluationCompare --baseline-run-dir=build/retrieval-evaluation/2026-08-28-r3-baseline --candidate-run-dir=build/retrieval-evaluation/2026-08-28-r3-baseline --no-daemon
git diff --check
```

## Boundary and next gate

R4, embedding retrieval, remains later work. It requires a separately requested
embedding contract that records the installed model tag and full digest,
chunking, normalization, distance metric, and top-K settings before it writes
formal vector evidence. R3 does not authorize that next slice or convert this
lexical path into RAG.
