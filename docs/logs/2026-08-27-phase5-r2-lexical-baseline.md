# Phase 5 R2 deterministic lexical baseline

## Pre-implementation method decision

R0 and R1 are committed and confirmed. Before implementing R2, the selected
baseline is a small corpus-aware exact-term coverage method in plain Java. BM25
is deliberately not selected: the confirmed twelve-document corpus does not
need length normalization or a new dependency to answer the registered first
retrieval question, while exact coverage keeps every score hand-calculable.

The locked version-one method is:

1. Lowercase with `Locale.ROOT` and extract ASCII alphanumeric tokens matching
   `[a-z0-9]+` from the exact validated query and document text.
2. Remove this fixed structural stop-word set:
   `a`, `an`, `and`, `are`, `as`, `at`, `be`, `because`, `been`, `before`,
   `being`, `both`, `but`, `by`, `can`, `did`, `do`, `does`, `during`, `each`,
   `for`, `from`, `had`, `has`, `have`, `how`, `in`, `into`, `is`, `it`, `its`,
   `may`, `no`, `not`, `of`, `on`, `one`, `only`, `or`, `other`, `s`, `should`,
   `so`, `than`, `that`, `the`, `their`, `them`, `then`, `there`, `these`,
   `they`, `this`, `those`, `through`, `to`, `under`, `up`, `was`, `were`,
   `what`, `when`, `where`, `which`, `while`, `who`, `why`, `will`, `with`, and
   `without`.
3. Preserve each query term once in first-occurrence order. Compute document
   frequency from the corresponding distinct document terms.
4. Retain query terms occurring in at most two corpus documents. Unseen query
   terms remain in the denominator. This removes the three-document fictional
   organization/topic boilerplate without pretending an absent term matched.
5. For each document, count retained query terms present in its exact text. A
   document qualifies only when it matches at least two terms and at least half
   of all retained query terms. Compare the half-coverage boundary by integers,
   not floating-point rounding.
6. Record the score as the exact rational `matchedTermCount /
   retainedQueryTermCount`. Rank by descending matched-term count, which is the
   same as descending coverage for one query, then by ascending stable
   `documentId`. An empty retained query returns no documents.

The retriever will record query ID and text; corpus ID, version, and SHA-256;
the complete locked parameters; retained query terms; and, for each ranked
hit, rank, document ID, content SHA-256, exact score numerator/denominator, and
matched terms. It will not record an answer or make a semantic relevance
judgment.

## Provider-free separation check

A disposable read-only term-count calculation against the confirmed catalog
showed one qualifying expected document for each of the twelve supported
fixtures. Their exact scores range from `2/4` through `3/3`; neither no-match
fixture qualifies any document. This calculation selects and checks the R2
method only. It is not formal R3 evidence, a retrieval-quality claim beyond the
locked fixtures, or a reason to change the confirmed human labels.

## Implementation and test boundary

R2 will add only plain Java types under the existing `retrieval` source set and
provider-free tests under `retrievalTest`. Tests must cover hand-calculated
rankings and score fractions, stable document-ID ties, stop words and document
frequency, the exact threshold boundaries, empty query, no match, confirmed
fixture behavior, immutable input/result collections, and repeatability.

No embedding, vector store, model call, answer generation, evaluator, formal
evidence writer, new dependency, credential, network access, Docker, or
Testcontainers behavior is authorized or needed. R3 remains a later slice.
