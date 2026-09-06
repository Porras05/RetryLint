# Week 9 mutant review table

| Case | Seed | Mutation(s) | Expected status | Expected findings | Observed RetryLint | Observed baseline | Classification | Reviewed |
|---|---|---|---|---|---|---|---|---|
| mut01-one-call-attempts | seed01 | increase-attempts-one-call | valid | RL001/warning:frontend-to-backend:backend.fetch | RL001/warning:frontend-to-backend:backend.fetch | none | matched | true |
| mut02-consecutive-attempts | seed02 | increase-attempts-consecutive-calls | valid | RL001/warning:edge-to-core→core-to-store:store.read | RL001/warning:edge-to-core→core-to-store:store.read | none | matched | true |
| mut03-branch-attempts | seed03 | increase-attempts-one-call | valid | RL001/warning:gateway-to-catalog:catalog.search | RL001/warning:gateway-to-catalog:catalog.search | none | matched | true |
| mut04-multi-root-attempts | seed04 | increase-attempts-one-call | valid | RL001/warning:apiA-to-ledger:ledger.write | RL001/warning:apiA-to-ledger:ledger.write | none | matched | true |
| mut05-three-layer-attempts | seed07 | increase-attempts-consecutive-calls | valid | RL001/error:a-to-b→b-to-c→c-to-d:d.finish | RL001/error:a-to-b→b-to-c→c-to-d:d.finish | none | matched | true |
| mut06-diamond-attempts | seed09 | increase-attempts-consecutive-calls | valid | RL001/warning:root-to-b→b-to-end:end.read | RL001/warning:root-to-b→b-to-end:end.read | none | matched | true |
| mut07-reduce-incoming-timeout | seed02 | reduce-incoming-timeout | valid | RL002/warning:edge-to-core→core-to-store:store.read | RL002/warning:edge-to-core→core-to-store:store.read | none | matched | true |
| mut08-increase-downstream-timeout | seed02 | increase-downstream-timeout | valid | RL002/warning:edge-to-core→core-to-store:store.read | RL002/warning:edge-to-core→core-to-store:store.read | none | matched | true |
| mut09-increase-fixed-wait | seed06 | increase-fixed-wait | valid | RL002/warning:caller-to-middle→middle-to-target:target.read | RL002/warning:caller-to-middle→middle-to-target:target.read | none | matched | true |
| mut10-reduce-middle-budget | seed07 | reduce-incoming-timeout | valid | RL002/warning:a-to-b→b-to-c:c.step | RL002/warning:a-to-b→b-to-c:c.step | none | matched | true |
| mut11-diamond-downstream-timeout | seed09 | increase-downstream-timeout | valid | RL002/warning:root-to-b→b-to-end:end.read | RL002/warning:root-to-b→b-to-end:end.read | none | matched | true |
| mut12-timeout-boundary | seed10 | reduce-incoming-timeout | valid | RL002/warning:client-to-worker→worker-to-database:database.read | RL002/warning:client-to-worker→worker-to-database:database.read | none | matched | true |
| mut13-idempotency-unknown | seed01 | idempotent-to-unknown | valid | RL003/warning:frontend-to-backend:backend.fetch | RL003/warning:frontend-to-backend:backend.fetch | none | matched | true |
| mut14-idempotency-non-idempotent | seed01 | idempotent-to-non-idempotent | valid | RL003/error:frontend-to-backend:backend.fetch | RL003/error:frontend-to-backend:backend.fetch | none | matched | true |
| mut15-branch-idempotency-unknown | seed03 | idempotent-to-unknown | valid | RL003/warning:gateway-to-catalog:catalog.search | RL003/warning:gateway-to-catalog:catalog.search | none | matched | true |
| mut16-shared-non-idempotent | seed04 | idempotent-to-non-idempotent | valid | RL003/error:apiA-to-ledger:ledger.write<br>RL003/error:apiB-to-ledger:ledger.write | RL003/error:apiA-to-ledger:ledger.write<br>RL003/error:apiB-to-ledger:ledger.write | none | matched | true |
| mut17-inherited-idempotency-unknown | seed06 | idempotent-to-unknown | valid | RL003/warning:middle-to-target:target.read | RL003/warning:middle-to-target:target.read | none | matched | true |
| mut18-mixed-key-non-idempotent | seed10 | idempotent-to-non-idempotent | valid | RL003/error:worker-to-database:database.read | RL003/error:worker-to-database:database.read | none | matched | true |
| mut19-amplification-and-unknown | seed02 | increase-attempts-consecutive-calls, idempotent-to-unknown | valid | RL001/warning:edge-to-core→core-to-store:store.read<br>RL003/warning:core-to-store:store.read | RL001/warning:edge-to-core→core-to-store:store.read<br>RL003/warning:core-to-store:store.read | none | matched | true |
| mut20-amplification-and-unsafe | seed07 | increase-attempts-consecutive-calls, idempotent-to-non-idempotent | valid | RL001/warning:a-to-b→b-to-c:c.step<br>RL003/error:b-to-c:c.step | RL001/warning:a-to-b→b-to-c:c.step<br>RL003/error:b-to-c:c.step | none | matched | true |
| mut21-timeout-and-unknown | seed09 | reduce-incoming-timeout, idempotent-to-unknown | valid | RL002/warning:root-to-b→b-to-end:end.read<br>RL003/warning:b-to-end:end.read<br>RL003/warning:c-to-end:end.read | RL002/warning:root-to-b→b-to-end:end.read<br>RL003/warning:b-to-end:end.read<br>RL003/warning:c-to-end:end.read | none | matched | true |
| mut22-amplification-and-non-idempotent | seed03 | increase-attempts-one-call, idempotent-to-non-idempotent | valid | RL001/warning:gateway-to-catalog:catalog.search<br>RL003/error:gateway-to-catalog:catalog.search | RL001/warning:gateway-to-catalog:catalog.search<br>RL003/error:gateway-to-catalog:catalog.search | none | matched | true |
| mut23-timeout-and-non-idempotent | seed10 | increase-downstream-timeout, idempotent-to-non-idempotent | valid | RL002/error:client-to-worker→worker-to-database:database.read<br>RL003/error:worker-to-database:database.read | RL002/error:client-to-worker→worker-to-database:database.read<br>RL003/error:worker-to-database:database.read | none | matched | true |
| mut24-single-attempt-non-idempotent | seed05 | idempotent-to-non-idempotent | valid | none | valid | none | matched | true |
| mut25-missing-retry-policy | seed01 | reference-missing-retry-policy | invalid | none | invalid | none | expected-rejection | true |
| mut26-missing-time-limiter-policy | seed02 | reference-missing-time-limiter-policy | invalid | none | invalid | none | expected-rejection | true |
| mut27-graph-cycle | seed09 | introduce-graph-cycle | invalid | none | invalid | none | expected-rejection | true |
| mut28-exponential-wait | seed02 | enable-exponential-wait | partially-analyzable | none | partially-analyzable | unsupported-local-retry-timing:core | expected-partial | true |
| mut29-randomized-wait | seed06 | enable-randomized-wait | partially-analyzable | none | partially-analyzable | unsupported-local-retry-timing:middle | expected-partial | true |
| mut30-exponential-linear-four | seed07 | enable-exponential-wait | partially-analyzable | none | partially-analyzable | unsupported-local-retry-timing:b | expected-partial | true |
