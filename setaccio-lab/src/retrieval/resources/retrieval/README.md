# Public retrieval corpus contract, version 1

The `corpus-v1/` resource defines the Phase 5 R0 corpus contract. It contains
only short, fictional, repository-authored Markdown documents. It contains no
personal data, imported articles, private Setaccio material, answers,
embeddings, model output, or relevance judgments.

The version-one corpus root is `retrieval/corpus-v1/` and must contain exactly:

- `catalog.json`;
- `catalog.sha256`, a single lowercase SHA-256 line for the exact bytes of
  `catalog.json`; and
- `documents/<documentId>.md` for every catalog entry, with no additional
  files or directories.

`catalog.json` has this exact schema. Unknown JSON fields and duplicate JSON
keys are rejected.

```json
{
  "schemaVersion": 1,
  "catalogId": "public-safe-retrieval-corpus",
  "catalogVersion": 1,
  "privacyReviewState": "PENDING_HUMAN_REVIEW | APPROVED_PUBLIC_SAFE",
  "documents": [
    {
      "documentId": "lowercase-hyphen-id",
      "relativePath": "documents/lowercase-hyphen-id.md",
      "contentSha256": "64 lowercase hexadecimal characters",
      "title": "Short public-safe title",
      "topic": "lowercase-hyphen-topic",
      "sourceType": "REPOSITORY_AUTHORED",
      "privacyReviewState": "PENDING_HUMAN_REVIEW | APPROVED_PUBLIC_SAFE"
    }
  ]
}
```

The catalog has 12--20 documents. Document IDs and relative paths are unique;
document SHA-256 values must also be unique. Every document path is exactly
`documents/<documentId>.md`, every document is a regular non-symbolic file,
and its exact UTF-8 bytes must match `contentSha256`. Markdown uses LF line
endings and ends with one LF. The catalog review state and every document
review state must agree.

A status of `PENDING_HUMAN_REVIEW` permits contract validation but prevents
formal retrieval use. A human must change the catalog and every document to
`APPROVED_PUBLIC_SAFE` after reviewing the text and its provenance. The project
owner completed that review for corpus version 1 on 2026-08-27. The approved
catalog and every document now carry `APPROVED_PUBLIC_SAFE`, and the catalog's
new pinned SHA-256 captures that reviewed state. Future content changes require
a new catalog version, updated document and catalog digests, and a new human
public-safety review; they must not alter saved evidence from an earlier
version.

## Query fixture contract, version 1

The `query-fixtures-v1/` resource defines the Phase 5 R1 retrieval-only
questions. It contains exactly `catalog.json` and `catalog.sha256`. The query
catalog is bound to the exact approved corpus catalog ID, version, and SHA-256;
changing the corpus makes the query catalog invalid until a new reviewed query
version is created.

Each fixture has this exact schema:

```json
{
  "caseId": "stable-lowercase-hyphen-id",
  "query": "One retrieval question?",
  "expectedSupportingDocumentIds": ["required-best-support-id"],
  "allowedSupportingDocumentIds": ["required-best-support-id"],
  "forbiddenDocumentIds": ["explicit-distractor-id"],
  "expectedNoMatch": false,
  "humanReviewState": "PENDING_HUMAN_REVIEW | CONFIRMED"
}
```

`expectedSupportingDocumentIds` identifies the document that must be retrieved
for a matching version-one case. `allowedSupportingDocumentIds` is the complete
set that may count as supporting and must include every expected ID.
`forbiddenDocumentIds` records explicit distractors and must be disjoint from
the allow-list; an unlisted document is not accepted as support merely because
it is not an explicit distractor. Every ID must link to the bound corpus.

Version 1 has twelve matching fixtures in corpus-document order, so every
document is the expected support exactly once. It also has two topical no-match
fixtures. A no-match fixture has empty expected and allowed lists and records
all twelve corpus documents as forbidden. The catalog contains no expected
answer text: it labels retrieval support only.

The query catalog and every fixture are initially
`PENDING_HUMAN_REVIEW`. This permits schema and linkage validation, while
`loadConfirmed` rejects formal use until a human confirms every query and its
expected, allowed, forbidden, and no-match judgment against the pinned corpus.
Confirmation changes every query review state to `CONFIRMED` and repins the
query catalog digest in one reviewable source change.

The project owner confirmed all fourteen version-one judgments on 2026-08-27,
bound to pre-confirmation catalog SHA-256
`e50e22266a1806ab94034f985248a51bb6428b27292feb2e99956af2606c8204`.
The catalog and every fixture now carry `CONFIRMED`; the resulting catalog
SHA-256 is
`ced4a31b13542a47d171a88879400fe649a0de985eeecd4ca58fea4feefb59b5`.
Any query or relevance-label change requires a new digest and new human review.

## Deterministic lexical baseline, version 1

R2 adds one plain-Java exact-term coverage baseline. It lowercases with
`Locale.ROOT`, extracts ASCII alphanumeric terms, removes the fixed
`english-structural-v1` stop-word set, keeps distinct query terms in first-use
order, and ignores query terms found in more than two corpus documents. Unseen
query terms remain in the score denominator.

A document qualifies after matching at least two retained query terms and at
least half of all retained query terms. Its exact score is
`matchedTermCount / retainedQueryTermCount`. Ranking uses descending matched
count, which is equivalent to descending coverage for a single query, then
ascending stable document ID. Empty retained queries return no documents.

Each result retains query ID and text; corpus ID, version, and digest; all
method parameters; retained query terms; and each hit's rank, document ID,
content digest, exact score fields, and matched terms. The confirmed v1 fixture
tests pin one expected hit for each of twelve supported queries and no hit for
both no-match queries.

## Retrieval-only evaluation and saved evidence, version 1

R3 runs the exact confirmed catalog sequentially through the locked lexical
baseline. It immediately repeats every row and records whether the two results
are identical. The raw result retains each fixture's human-confirmed labels,
full lexical result, and the complete text of every returned public corpus
document alongside its ID, rank, SHA-256, exact score fields, and matched terms.

`retrievalEvaluation` writes a new dated directory directly under
`local/evidence/retrieval-evaluation/`. The manifest declares only the raw JSON and
deterministic `SUMMARY.md`; all writes are non-overwriting. The offline
`retrievalEvaluationVerify`, `retrievalEvaluationReanalyze`, and
`retrievalEvaluationCompare` tasks require saved directories under that same
root and never start Spring, call a provider, or contact a network service.

The R3 metrics are expected-support retrieval, top 1, top 3, forbidden-document
retrieval, correct no-match, and immediate-repeat stability. They are
deterministic observations against the exact human-confirmed labels, not answer
quality, general semantic relevance, embedding performance, or an AI-evaluator
judgment.

R3 stops before embeddings, answer generation, `RelevancyEvaluator`, or any
provider call.

## Local embedding retrieval and saved evidence, version 1

R4 adds one explicit, opt-in local Ollama embedding boundary. It is not part
of the default build or test lifecycle. The generation task requires all of
the following at invocation time: a loopback-only Ollama URL, one already
installed embedding-model tag, a positive top-K value, and one new dated
directory directly under `local/evidence/retrieval-embedding/`. It first requires a
clean full Git commit, validates the approved corpus and confirmed query
catalog, resolves the exact installed tag and full digest from the local
inventory, and atomically reserves the output directory before sending one
batch containing all twelve document texts followed by all fourteen query texts
to Spring AI's direct `/api/embed` boundary. It never pulls a model, retries a
request, starts Spring, or accepts a remote endpoint.

The formal model tag, full digest, and top K are intentionally supplied and
locked by the clean preflight immediately before any generation. The task
requires the selected installed model to advertise Ollama's `embedding`
capability in `ollama show <tag>`; an `embedding length` field is dimensional
metadata, not proof that the model accepts `/api/embed`. Ollama's embedding API
accepts either one input or an ordered input batch and returns one unit-length
vector per input. See the official [embedding capability documentation](https://docs.ollama.com/capabilities/embeddings).
The task records the full
digest before the request, rejects a response whose effective model differs
from that identity, and rechecks the installed identity/digest after the
request before writing evidence. If the request or post-request check fails,
the reservation remains as non-reusable diagnostic state rather than allowing
the path to be reused; it is not completed evidence. This is an operational
identity control, not a
semantic-quality, capability, performance, or model-selection claim. The formal
configuration also locks `whole-document-v1` chunking, `unit-l2-v1`
normalization, `cosine-descending-document-id` ranking, one batch, one attempt,
and a two-minute request timeout.

The runner writes only under ignored `local/evidence/retrieval-embedding/`, retaining a
non-overwriting raw JSON result, shared-v1 manifest, and deterministic
`SUMMARY.md`. Raw evidence retains the provider and endpoint category,
requested/effective model and full digest, corpus/query identities, provider
timing metadata when returned, every normalized document/query vector, and
every deterministic top-K rank, document identity, content digest, and cosine
score. The summary deliberately excludes vectors. Its provider-free
`retrievalEmbeddingVerify` and `retrievalEmbeddingReanalyze` tasks check the
saved layout, artifact hashes, model/settings/corpus identities, vector
dimensions and normalization, and recomputed rankings without starting Spring
or contacting Ollama.

An embedding-enabled local `/api/embed` service is a prerequisite for a formal
run. If that endpoint is unavailable or rejects the selected installed model,
the task fails before creating evidence and does not retry, pull, substitute a
model, or treat the failure as a retrieval result. R4 preserves rankings for a
later retrieval flow but sets no support threshold, scores no-match fixtures,
generates no answer, makes no semantic-relevance claim, and does not invoke
`RelevancyEvaluator`.

## Local answer generation and saved evidence, version 1

R5 adds one explicit, opt-in local answer stage. It is not part of the default
build or test lifecycle, and it does not re-run retrieval. The
`retrievalAnswerMatrix` task requires a loopback-only Ollama URL, one explicit
already-installed answer-model tag, explicit maximum output tokens, seed, and
timeout, a verified clean-baseline R3 saved run, and a new dated directory
directly under `local/evidence/retrieval-answer/`. Before reserving output it verifies
the R3 evidence against the current approved corpus and confirmed catalog,
requires its clean source baseline, loads the tracked
`retrieval-grounded-answer-v1` prompt, resolves the requested/effective answer
model and full digest from local inventory, and locks temperature `0.0`, one
attempt, and pull strategy `never`.

The runner sends one sequential answer request for every preserved R3 row. Each
raw R5 row retains the exact original R3 row—including complete returned public
document text, document IDs, ranks, SHA-256 values, lexical score fields, and
fixture labels—together with the complete rendered prompt, prompt/model
identity, raw answer, safe provider response identifier, available usage,
latency, and classified invocation outcome. It observes exact `NO_SUPPORT`
abstention and bracketed document-ID reference syntax. It does not decide
whether an assertion is supported: raw answer/source text is retained and the
assessment is `NOT_ASSESSED` until a separately scoped evaluator or human
review. A correct-looking citation does not prove semantic support.

The answer run writes only a non-overwriting raw JSON result, shared-v1
manifest, and deterministic `SUMMARY.md` under ignored
`local/evidence/retrieval-answer/`. `retrievalAnswerVerify` and
`retrievalAnswerReanalyze` operate offline; they validate source-row
preservation, prompt/model/settings identity, reference/abstention
recomputation, hashes, layout, and summary drift without starting Spring or
contacting Ollama. If invocation, post-run model-identity, or clean-baseline
checks fail after reservation, the path remains a non-reusable diagnostic
marker rather than a completed run. R5 does not score retrieval, claim answer
correctness or relevance, or rank a model.

## Local relevancy evaluation and saved evidence, version 1

R6 adds one explicit, opt-in Spring AI `RelevancyEvaluator` stage over a
verified R5 answer run. It is not part of the default build or test lifecycle,
does not rerun retrieval or answer generation, and rejects any row without
actual retrieved documents before it can call the evaluator. The
`retrievalRelevancyMatrix` task requires a loopback-only Ollama URL, one
explicit already-installed evaluator-model tag, explicit maximum output tokens,
seed, and timeout, a verified clean-baseline R5 saved run, and a new dated
directory directly under `local/evidence/retrieval-relevancy/`. It locks the tracked
`retrieval-relevancy-evaluator-v1` prompt, requested/effective evaluator model
and full digest, temperature `0.0`, one attempt, and pull strategy `never`
before reserving output.

Each raw R6 row retains its exact R5 row and therefore the complete R3-derived
document text, identities, ranks, content SHA-256 values, lexical
observations, and fixture labels. An attempted row records the evaluator
prompt/model identity, raw evaluator response, Spring evaluator pass/score,
normalized `YES`/`NO` verdict, response metadata, available usage, latency,
and failure classification. It separately retains the deterministic retrieval
expectation, an explicit self-evaluation flag, `NOT_REVIEWED` human support,
and `NOT_ASSESSED` answer correctness. Rows with missing context or a failed or
empty R5 answer are explicitly not attempted.

The evaluator run writes only a non-overwriting raw JSON result, shared-v1
manifest, and deterministic `SUMMARY.md` under ignored
`local/evidence/retrieval-relevancy/`. `retrievalRelevancyVerify` and
`retrievalRelevancyReanalyze` operate offline. An AI evaluator is not ground
truth: R6 does not turn a verdict into an expectation match, make a human
support or answer-correctness claim, merge outcomes into a score, rank a model,
or select a model.
