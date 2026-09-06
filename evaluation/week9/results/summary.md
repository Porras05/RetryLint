# RetryLint Week 9 mutation benchmark

- RetryLint: 0.1.0
- Corpus: 10 safe seeds + 30 mutants = 40 cases
- Executed/skipped: 40/0
- Valid / invalid / partial: 34 / 3 / 3
- Scoring key: `(ruleId, severity, ordered callPath, targetOperation)`
- TP / FP / FN: 30 / 0 / 0
- Precision / recall / F1: 1.000 / 1.000 / 1.000
- Baseline local issues: 3; architecture-level findings: none by design

## Per-rule metrics

| Rule | TP | FP | FN | Precision | Recall | F1 |
|---|---:|---:|---:|---:|---:|---:|
| RL001 | 9 | 0 | 0 | 1.000 | 1.000 | 1.000 |
| RL002 | 8 | 0 | 0 | 1.000 | 1.000 | 1.000 |
| RL003 | 13 | 0 | 0 | 1.000 | 1.000 | 1.000 |

## Mutation operators

- enable-exponential-wait: 2
- enable-randomized-wait: 1
- idempotent-to-non-idempotent: 7
- idempotent-to-unknown: 5
- increase-attempts-consecutive-calls: 5
- increase-attempts-one-call: 4
- increase-downstream-timeout: 3
- increase-fixed-wait: 1
- introduce-graph-cycle: 1
- reduce-incoming-timeout: 4
- reference-missing-retry-policy: 1
- reference-missing-time-limiter-policy: 1

## False results

None.

Invalid/rejected and partially analyzed cases are reported separately and do not enter TP/FP/FN. Completeness gaps are not findings. Undefined zero-denominator metrics are `null` in JSON and `n/a` here.
