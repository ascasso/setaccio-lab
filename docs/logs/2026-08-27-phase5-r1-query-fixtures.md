# Phase 5 R1 query fixture catalog

## R0 approval gate

On 2026-08-27, the project owner stated that they had reviewed the Phase 5 R0
retrieval contract, catalog, and all twelve documents, and approved corpus
version 1 as `APPROVED_PUBLIC_SAFE`. The catalog and all document review states
were changed together. The reviewed catalog SHA-256 is
`2c3f72f153cfb097caeef73ae210a66265af054b585b1b2a292162f289087b9d`;
the document text and its existing per-document digests did not change.

## R1 implementation

The new `public-safe-retrieval-query-fixtures` catalog version 1 is bound to
that exact approved corpus ID, version, and SHA-256. Its tracked pre-confirmation
SHA-256 is
`539103668e04045cf7d3b95e7b30916fb7ff5308a23b9ce29ae08d292f47ef29`.

The ordered catalog contains twelve matching questions, one per corpus
document in corpus order, plus two topical no-match questions. Each matching
fixture identifies one required document, includes it in the complete support
allow-list, and identifies the other two documents in the same topic group as
explicit distractors. Each no-match fixture has empty expected and allowed
lists and forbids all twelve documents. No fixture contains an expected answer.

The provider-free loader requires the exact approved corpus, query-catalog
digest integrity, strict JSON with no unknown or duplicate fields, stable
unique case IDs, complete document-ID linkage, disjoint allowed/forbidden
labels, exactly two no-match fixtures, and exact ordered one-time coverage of
all corpus documents. Formal loading additionally requires actual-human truth
confirmation of the catalog and every fixture.

## Human content review packet

The following are the exact staged relevance judgments. `Allowed` currently
equals `Expected`; `Forbidden` names the deliberate same-topic distractors.
For each no-match case, every corpus document is forbidden.

| Case ID | Query | Expected / allowed | Forbidden |
|---|---|---|---|
| `garden-compost-accepted-materials` | Which items does Harbor Garden accept in its shared compost bins? | `garden-compost-basics` | `garden-tool-shed`, `garden-water-schedule` |
| `garden-shed-tool-inventory` | Which tools are stored in the Harbor Garden shared shed? | `garden-tool-shed` | `garden-compost-basics`, `garden-water-schedule` |
| `garden-rain-watering-schedule` | How does rainfall affect Harbor Garden's scheduled watering round? | `garden-water-schedule` | `garden-compost-basics`, `garden-tool-shed` |
| `library-borrowing-renewal-condition` | When may a Riverside Library borrower renew an item? | `library-borrowing-rules` | `library-study-room`, `library-workshop-calendar` |
| `library-study-room-equipment` | What equipment is available in the Riverside Library quiet study room? | `library-study-room` | `library-borrowing-rules`, `library-workshop-calendar` |
| `library-repair-workshop-exclusions` | Which items will the Riverside Library repair workshop not accept? | `library-workshop-calendar` | `library-borrowing-rules`, `library-study-room` |
| `trail-dune-shortcut-closure` | Why is the North Shore Trail dune shortcut closed? | `trail-access-notice` | `trail-bird-observation`, `trail-weather-guidance` |
| `trail-bird-observation-rules` | What rules apply when visitors observe birds at the North Shore Trail marsh? | `trail-bird-observation` | `trail-access-notice`, `trail-weather-guidance` |
| `trail-weather-closure-alerts` | Which weather alerts close the North Shore Trail? | `trail-weather-guidance` | `trail-access-notice`, `trail-bird-observation` |
| `workshop-bicycle-safety-inspection` | What does the Open Wheel Workshop inspect during its bicycle safety check? | `workshop-bike-check` | `workshop-membership`, `workshop-route-map` |
| `workshop-first-visit-membership` | Does a first visit to the Open Wheel Workshop require membership? | `workshop-membership` | `workshop-bike-check`, `workshop-route-map` |
| `workshop-route-map-limit` | What limitation applies to the Open Wheel Workshop's printed route map? | `workshop-route-map` | `workshop-bike-check`, `workshop-membership` |
| `no-match-library-home-delivery` | Does Riverside Library deliver borrowed books to a reader's home? | none; expected no match | all twelve corpus documents |
| `no-match-trail-overnight-camping` | Where may visitors camp overnight along the North Shore Trail? | none; expected no match | all twelve corpus documents |

The catalog and every fixture remain `PENDING_HUMAN_REVIEW`. This is an
agent-authored review packet, not a human confirmation. R2 remains blocked
until the project owner reviews the exact table against corpus version 1 and
explicitly confirms or corrects all fourteen relevance judgments. That later
confirmation must change every state to `CONFIRMED` and repin the catalog
digest; it must not be inferred from the earlier corpus public-safety approval.

## Boundary and verification

This slice adds no lexical retriever, embedding, vector store, answer text,
answer generation, evaluator, model call, formal evidence, dependency,
credential, Docker, or Testcontainers behavior. No Ollama or remote provider
was contacted.

The provider-free verification command passed:

```text
./gradlew :setaccio-lab:retrievalFixtureTest --rerun-tasks --no-daemon
```

The tests cover the approved corpus gate, pinned query order and digest,
complete document linkage and coverage, two no-match cases, pending-review
rejection, confirmed-state acceptance on a disposable copy, corpus-binding and
digest drift, duplicate cases and IDs, required fields, and strict rejection of
unknown, duplicate, or answer-bearing JSON fields. `git diff --check` also
passed.
