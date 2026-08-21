# Offline Tool Compatibility Prompt Comparison

- Baseline run: `report-baseline`
- Candidate run: `report-candidate`
- Prompt conditions: `untreated` → `prompted`
- Shared Git commit: `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa`
- Paired schedule: `tool-compatibility-paired-execution` version `1` (`e18f7d3a6c0701e4c2e84dddf92c1ff0b3824ed03bd0b2f359c8904443c434de`)
- Protocol: 8 case(s) × 2 repetition(s) = 16 paired row(s).

This report contains deterministic paired evidence only. It does not declare an overall winner, aggregate score, prompt-adoption decision, or human interpretation.

## Contract and behavioral transitions

| Case | Repetition | Contract | Required tools | Forbidden tools | Exact calls | Semantic arguments | Final response | Visible reasoning |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `arithmetic-add` | 1 | fail → pass | newly selected `lab_add_numbers` | n/a (no forbidden tools) | newly matched | #1 `lab_add_numbers`: newly matched | newly present | introduced |
| `arithmetic-add` | 2 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_add_numbers`: unchanged matched | unchanged present | unchanged absent |
| `fixed-utc-time` | 1 | pass → fail | newly missed `lab_fixed_utc_now` | n/a (no forbidden tools) | newly mismatched | #1 `lab_fixed_utc_now`: newly mismatched | newly empty | removed |
| `fixed-utc-time` | 2 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_fixed_utc_now`: unchanged matched | unchanged present | unchanged absent |
| `fixed-zone-time` | 1 | pass → fail | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_fixed_time_for_zone`: newly mismatched | unchanged present | unchanged absent |
| `fixed-zone-time` | 2 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_fixed_time_for_zone`: unchanged matched | unchanged present | unchanged absent |
| `catalog-lookup` | 1 | unchanged fail | none | n/a (no forbidden tools) | unchanged mismatched | #1 `lab_lookup_catalog_item`: not reached → not reached | unchanged empty | unchanged absent |
| `catalog-lookup` | 2 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_lookup_catalog_item`: unchanged matched | unchanged present | unchanged absent |
| `catalog-multi-step` | 1 | pass → fail | newly missed `lab_list_catalog_items` | n/a (no forbidden tools) | newly mismatched | #1 `lab_lookup_catalog_item`: unchanged matched<br>#2 `lab_list_catalog_items`: newly mismatched | newly empty | unchanged absent |
| `catalog-multi-step` | 2 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_lookup_catalog_item`: unchanged matched<br>#2 `lab_list_catalog_items`: unchanged matched | unchanged present | unchanged absent |
| `catalog-no-match` | 1 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_lookup_catalog_item`: unchanged matched | unchanged present | unchanged absent |
| `catalog-no-match` | 2 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_lookup_catalog_item`: unchanged matched | unchanged present | unchanged absent |
| `no-applicable-domain-tool` | 1 | pass → fail | n/a (no required tools) | newly selected `lab_add_numbers` | newly mismatched | n/a (no expected calls) | unchanged present | removed |
| `no-applicable-domain-tool` | 2 | unchanged pass | n/a (no required tools) | none | unchanged matched | n/a (no expected calls) | unchanged present | unchanged absent |
| `deterministic-tool-failure` | 1 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_fail_fixture`: unchanged matched | unchanged present | unchanged absent |
| `deterministic-tool-failure` | 2 | unchanged pass | none | n/a (no forbidden tools) | unchanged matched | #1 `lab_fail_fixture`: unchanged matched | unchanged present | unchanged absent |

## Provider and resource deltas

| Case | Repetition | Provider turns and later failures | Output-limit states | Aggregate completion tokens | Row latency |
| --- | --- | --- | --- | --- | --- |
| `arithmetic-add` | 1 | 1 → 2 turn(s); later failures none → none | #1 unobservable → unobservable; #2 absent → not reached; row aggregate not reached | n/a → 2 (delta n/a) | 35 ms → 10 ms (-25 ms) |
| `arithmetic-add` | 2 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 90 ms → 90 ms (0 ms) |
| `fixed-utc-time` | 1 | 2 → 1 turn(s); later failures none → none | #1 unobservable → unobservable; #2 not reached → absent; row aggregate not reached | 2 → n/a (delta n/a) | 20 ms → 45 ms (+25 ms) |
| `fixed-utc-time` | 2 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 100 ms → 100 ms (0 ms) |
| `fixed-zone-time` | 1 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 30 ms → 33 ms (+3 ms) |
| `fixed-zone-time` | 2 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 110 ms → 110 ms (0 ms) |
| `catalog-lookup` | 1 | unchanged: 1 turn(s); later failures none | unchanged: #1 unobservable; row aggregate not reached | n/a → n/a (delta n/a) | 45 ms → 45 ms (0 ms) |
| `catalog-lookup` | 2 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 120 ms → 120 ms (0 ms) |
| `catalog-multi-step` | 1 | 3 → 2 turn(s); later failures none → #2 | #1 not reached → not reached; #2 reached → unobservable; #3 not reached → absent; row aggregate reached → not reached | 516 → 2 (delta -514) | 50 ms → 55 ms (+5 ms) |
| `catalog-multi-step` | 2 | unchanged: 3 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; #3 not reached; row aggregate not reached | 6 → 6 (delta 0) | 130 ms → 130 ms (0 ms) |
| `catalog-no-match` | 1 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 60 ms → 60 ms (0 ms) |
| `catalog-no-match` | 2 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 140 ms → 140 ms (0 ms) |
| `no-applicable-domain-tool` | 1 | 1 → 2 turn(s); later failures none → none | #1 not reached → not reached; #2 absent → not reached; row aggregate not reached | 2 → 4 (delta +2) | 70 ms → 75 ms (+5 ms) |
| `no-applicable-domain-tool` | 2 | unchanged: 1 turn(s); later failures none | unchanged: #1 not reached; row aggregate not reached | 2 → 2 (delta 0) | 150 ms → 150 ms (0 ms) |
| `deterministic-tool-failure` | 1 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 80 ms → 80 ms (0 ms) |
| `deterministic-tool-failure` | 2 | unchanged: 2 turn(s); later failures none | unchanged: #1 not reached; #2 not reached; row aggregate not reached | 4 → 4 (delta 0) | 160 ms → 160 ms (0 ms) |

Provider-turn and output-limit fields preserve observed evidence states; `n/a` means the corresponding completion-token value was unavailable.
