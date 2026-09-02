# Local Embedding Retrieval

- Raw result: `retrieval-embedding-results.json`
- Raw SHA-256: `aa56b1add65bdd9506676170f33df3fedd580f6d16b9dc48b9a80b04362b716a`
- Git commit: `4c13b4ac24f4b0d39497f662b9df0c930169f35f`
- Evidence status: `clean-baseline candidate`
- Protocol version: `1`
- Provider / endpoint category: `ollama` / `loopback-local`
- Embedding model: requested `qwen3-embedding:0.6b`, effective `qwen3-embedding:0.6b`, Ollama digest `ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d`
- Corpus: `public-safe-retrieval-corpus` version `1` (`2c3f72f153cfb097caeef73ae210a66265af054b585b1b2a292162f289087b9d`)
- Query catalog: `public-safe-retrieval-query-fixtures` version `1` (`ced4a31b13542a47d171a88879400fe649a0de985eeecd4ca58fea4feefb59b5`)
- Inputs: 12 documents + 14 queries in one explicit batch.
- Vector dimension: `1024`
- Chunking: `whole-document-v1`; normalization: `unit-l2-v1`
- Ranking: `cosine-descending-document-id`; top K: `5`
- Attempt policy: exactly 1; timeout: 120000 ms
- Pull strategy: `never`

## Retained evidence

The ignored raw artifact retains every normalized document and query vector, their exact corpus/query SHA-256 identities, model identity, provider timing metadata, and the deterministic top-K document IDs, ranks, content digests, and cosine scores for every confirmed query.

## Interpretation boundary

This records one local embedding-generation and ranking configuration. It does not set a support threshold, score no-match behavior, generate answers, establish semantic relevance, or compare models or retrieval methods.
