# RetryLint final project delivery v0.1.1

## Version scope

- **Analyzer core:** `v0.1.0`, frozen at commit `85ffebc096eb1b7d007270638ccffcecb62605b3` and unchanged since evaluation.
- **Final project delivery:** `v0.1.1`, containing the complete documentation, evaluation evidence, reproducible figures, packaged CLI, integrity record, and offline-delivery material.

The packaged command reports `RetryLint 0.1.0` because v0.1.1 does not change analyzer behavior or semantics.

## Included capabilities

- Kotlin/Java 21 command-line analyzer with `version`, `validate`, and `analyze`.
- RL001 retry-amplification path analysis using overflow-safe multiplication.
- RL002 adjacent timeout-budget analysis for the supported fixed-wait/default-order model.
- RL003 warnings/errors for retry-enabled calls to operations of unknown or non-idempotent status.
- Explicit completeness gaps when required semantics are missing or unsupported.
- Deterministic text and JSON reports with CI-oriented exit codes.
- Gradle-generated Windows and Unix distribution scripts with runtime dependencies.

## Evaluation evidence

- Week 8: the controlled four-service Resilience4j testbed matched the static 27x prediction with counters `1/3/9/27` in all 10 recorded repetitions. Source: [`evaluation/week8-results.json`](../evaluation/week8-results.json).
- Week 9: all 40 reviewed cases executed with TP=30, FP=0, FN=0 and precision/recall/F1=1.000 on that controlled corpus. Source: [`evaluation/week9/results/summary.json`](../evaluation/week9/results/summary.json).
- Week 10: all four generated sparse-DAG sizes completed with 80 retained measured analyses and no correctness failure. Timing values and environment are in [`evaluation/week10/results/scalability-results.json`](../evaluation/week10/results/scalability-results.json).

## Distribution

Build with:

```powershell
.\gradlew.bat :retrylint-cli:distZip
```

Artifact: `retrylint-cli-0.1.0.zip`. Verify it using [`SHA256SUMS.txt`](SHA256SUMS.txt). The archive is intended to be attached to a v0.1.1 GitHub release; publication and tag pushing are intentionally left to the repository owner.

## Known limitations

RetryLint v0.1.0 analyzes a declared static synchronous DAG and a restricted Resilience4j Retry/TimeLimiter subset. Idempotency is supplied by the manifest. Dynamic configuration, asynchronous/event-driven topologies, cycles, arbitrary wait strategies, traffic probabilities, queues, circuit-breaker interactions, and universal production-scale behavior are not modeled. See the complete [limitations and threats to validity](../docs/LIMITATIONS.md).
