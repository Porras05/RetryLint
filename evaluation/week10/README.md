# Week 10 scalability evaluation

This evaluation measures the frozen RetryLint 0.1.0 analyzer on deterministic sparse DAGs containing 10, 100, 500, and 1,000 operations. It does not modify or optimize the analyzer.

## Methodology

For each size, the generator creates four services, one root, a complete forward chain, and one additional fixed-seed forward edge from every operation that has at least two later operations. Therefore every graph is connected, acyclic, sparse, and has exactly `2N - 3` calls.

Every call uses a supported one-attempt retry and a one-second TimeLimiter. Every operation is idempotent. The expected result for every size is zero RL001/RL002/RL003 findings and zero completeness gaps: amplification remains 1x, adjacent one-attempt timeout windows equal their incoming budgets, and no call repeats its target.

The primary timing boundary is one in-process `RetryLintAnalyzer.analyze(Path)` call. It includes manifest and service YAML parsing, topology validation, configuration resolution, RL001, RL002, and RL003. It excludes project generation, Gradle configuration, JVM/process startup, CLI parsing, rendering, and correctness assertions. A separate untimed CLI preflight verifies exit code 0 and JSON finding/gap counts for each generated case.

Each size receives three unmeasured warm-ups followed by exactly 20 measured runs, as required by the implementation plan. Every measured value is retained. Median uses the average of the two center values for 20 runs. P95 uses nearest rank: `sorted[ceil(0.95 * n) - 1]`, which is the 19th sorted value when `n = 20`.

Generated projects are temporary inputs under `retrylint-cli/build/week10-cases/`; they are recreated deterministically and are not committed. The result JSON records each manifest's SHA-256 digest, fixed generation seed, graph counts, all raw nanosecond timings, statistics, correctness results, and execution environment. Memory is not measured because it is optional in the plan and would require a separate reliable JVM-memory methodology.

## Reproduce

From the repository root with `JAVA_HOME` pointing to Java 21:

```powershell
.\gradlew.bat :retrylint-cli:week10Scalability
```

The task regenerates all four projects, performs CLI correctness preflights, warms up, retains 80 measured runs, verifies every analysis result, and writes:

- `results/scalability-results.json`
- `results/summary.md`

Durations naturally vary across runs and machines. Reproducibility applies to graph structure, seeds, ordering, run counts, correctness expectations, and statistics calculation—not identical timing values.

## Manual verification

After running the task:

```powershell
Get-Content retrylint-cli/build/week10-cases/ops-10/retrylint.yml
Get-Content retrylint-cli/build/week10-cases/ops-100/retrylint.yml
Get-Item retrylint-cli/build/week10-cases/ops-500/retrylint.yml
Get-Item retrylint-cli/build/week10-cases/ops-1000/retrylint.yml

.\gradlew.bat :retrylint-cli:run --args="analyze retrylint-cli/build/week10-cases/ops-1000/retrylint.yml --format json --fail-on never"

Get-Content evaluation/week10/results/scalability-results.json
Get-Content evaluation/week10/results/summary.md
```

The JSON report is the primary evidence. The Markdown table is a concise view of the same raw measurements and derived statistics.
