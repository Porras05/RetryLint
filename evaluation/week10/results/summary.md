# RetryLint Week 10 scalability results

Frozen analyzer: 0.1.0 (v0.1.0, `ad29876202710e4ee20bb22426eb00c25e2c1a87`)

Measurement boundary: in-process RetryLintAnalyzer.analyze(Path): includes manifest/service YAML parsing, topology validation, configuration resolution, RL001, RL002, and RL003; excludes case generation, Gradle, JVM/process startup, CLI parsing, correctness assertions, and rendering

Warm-up: 3 runs per size. Measured: 20 runs per size. Every measured duration is retained in the JSON evidence.

| Operations | Calls | Services | Roots | Runs | Min ms | Median ms | Mean ms | P95 ms | Max ms | Findings/Gaps | Result |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 10 | 17 | 4 | 1 | 20 | 3.422 | 4.590 | 5.032 | 7.889 | 8.352 | 0/0 | success |
| 100 | 197 | 4 | 1 | 20 | 6.736 | 10.213 | 10.935 | 15.053 | 17.655 | 0/0 | success |
| 500 | 997 | 4 | 1 | 20 | 10.876 | 12.192 | 12.876 | 16.201 | 17.533 | 0/0 | success |
| 1000 | 1997 | 4 | 1 | 20 | 20.409 | 23.834 | 25.234 | 33.389 | 34.423 | 0/0 | success |

## Median scaling ratios

- 100 / 10 operations: 2.225x
- 500 / 100 operations: 1.194x
- 1000 / 500 operations: 1.955x

## Interpretation

Median growth remained below operation-count growth at every step. This is consistent with practical scaling for these sparse inputs, but four empirical points do not establish formal complexity. The largest retained max/median spread was the 10-operation case: 8.352 ms, 1.820x its median. It had no correctness failure; GC/scheduler telemetry was not collected, so its cause is unknown.

These measurements describe one environment and are not a production-scale performance claim. No outlier was removed. Memory usage was not measured because the plan makes it optional and reliable JVM memory attribution would require a separate methodology.

P95 convention: nearest-rank p95: sorted[ceil(0.95 * n) - 1]; median averages the two center values for even n
