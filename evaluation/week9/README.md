# Week 9 mutation benchmark

This directory contains the reviewed RetryLint 0.1.0 mutation benchmark. It is evaluation data and infrastructure only; it does not alter the frozen analyzer core.

## Corpus and review

`benchmark.json` is the authoritative ordered index. It names exactly 10 safe seeds and 30 mutants. Every indexed directory has a `truth.json` with its parent seed, mutation operator(s), expected input status, expected findings, expected completeness gaps, rationale, and an explicit review marker and note.

Each mutant stores only its changed files under `overlay/`. Review consists of comparing those files with the named seed, then independently checking retry multiplication, adjacent timeout arithmetic, target idempotency, topology/config validity, and expected completeness. Expected findings are not generated from RetryLint output.

The runner rejects duplicate IDs, missing indexed cases, unreviewed truth, unknown parent seeds, count drift, unexpected case directories, and a production-core difference from `v0.1.0`. All cases are executed in index order; the normal skip count is zero.

## Scoring

One positive event is identified by:

`(ruleId, severity, ordered callPath, targetOperation)`

For supported valid cases, exact matching events are TP, unexpected events are FP, and missing expected events are FN. Invalid inputs and partially analyzable inputs are counted separately and excluded from TP/FP/FN. An expected rejection is not an FN, and a completeness gap is not a finding. When a metric denominator is zero, JSON records `null` and Markdown displays `n/a`.

Any FP or FN must have a checked-in `falseResultExplanation`; otherwise the runner fails. Truth is not relabeled merely to improve metrics.

## Local-only baseline

The baseline reads only each service ID and configuration path, then parses that service's Resilience4j retry and TimeLimiter subset independently. It reports malformed local configuration and supported-contract limitations such as exponential or randomized wait. It does not read calls or operation relationships, multiply retries across services, compare adjacent timeout budgets, or combine retry behavior with target idempotency. Consequently, it emits no architecture-level RL001/RL002/RL003 findings. This is a comparison of information available with and without topology, not a claim that Resilience4j promises these analyses.

## Reproduce

From the repository root on Windows:

```powershell
.\gradlew.bat :retrylint-cli:week9Benchmark
```

Run the same command twice and compare the four files under `results/`. They intentionally contain no timestamps or checkout-specific absolute paths, so unchanged inputs and code produce byte-identical output.

Primary evidence:

- `results/raw-results.json`: every case's truth snapshot, normalized CLI stdout/stderr, exit code, baseline result, observed events, and classification.
- `results/summary.json`: machine-readable counts and metrics.
- `results/summary.md`: concise human-readable metrics and limitations.
- `results/mutant-table.md`: all 30 mutants without filtering.
