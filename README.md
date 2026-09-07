# RetryLint

RetryLint 0.1.0 is a deterministic Kotlin CLI that finds cross-service retry risks which are difficult to see from one service configuration alone. It combines an explicit synchronous call topology (`retrylint.yml`) with a supported subset of each caller service's Resilience4j Retry and TimeLimiter configuration.

The analyzer reports:

| Rule | Risk |
|---|---|
| RL001 | Multiplicative retry amplification along a call path |
| RL002 | An incoming timeout that is smaller than a downstream retry window |
| RL003 | Retries that may repeat an operation declared non-idempotent or of unknown idempotency |

RetryLint is intentionally bounded: it analyzes static synchronous DAGs, not runtime traffic, probabilities, queues, circuit breakers, or arbitrary Resilience4j behavior. See the [frozen v0.1.0 contract](docs/CORE_CONTRACT_V0.1.md) and [limitations](docs/LIMITATIONS.md).

## Prerequisites

- Java 21 (`JAVA_HOME` must point to a Java 21 installation)
- the checked-in Gradle wrapper; no system Gradle installation is required
- Docker Desktop with Docker Compose and PowerShell only for the Week 8 runtime experiment

## Quick start

From the repository root on Windows:

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

Validate topology and configuration paths without running the rules:

```powershell
.\gradlew.bat :retrylint-cli:run --args="validate retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml"
```

Analyze the complete example:

```powershell
.\gradlew.bat :retrylint-cli:run --args="analyze retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml --format text --fail-on never"
```

Its three retry layers each permit three attempts, so RL001 reports the configured invocation envelope `3 x 3 x 3 = 27`. This is a possible retry amplification, not a claim that 27 duplicate business operations actually occurred.

Use `--format text` for human-readable output or `--format json` for structured output. `--fail-on error` returns exit code 1 for error findings, `--fail-on warning` for warnings or errors, and `--fail-on never` keeps successful analysis at exit code 0. Invalid input or configuration returns exit code 2; unexpected internal failure returns 3. The manifest's `analysis.failOn` is used when the command-line option is omitted.

## Evidence and reproduction

| Evaluation | Recorded result | Reproduce |
|---|---|---|
| Week 8 runtime validation | Static 27x prediction matched bank count 27 in all 10 recorded repetitions | `powershell -ExecutionPolicy Bypass -File testbed/scripts/verify-week8.ps1` |
| Week 9 mutation benchmark | 40 cases; TP=30, FP=0, FN=0 on the controlled reviewed corpus | `.\gradlew.bat :retrylint-cli:week9Benchmark` |
| Week 10 scalability | All generated 10/100/500/1000-operation sparse DAGs completed successfully | `.\gradlew.bat :retrylint-cli:week10Scalability` |

The numbers above link to primary evidence and their scope in [Evaluation](docs/EVALUATION.md). For an auditable claim-to-file map, see the [Evidence index](docs/EVIDENCE_INDEX.md). Week 10 timings vary by execution, so the documentation reports the checked-in JSON rather than promising identical latency.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Algorithms and supported configuration](docs/ALGORITHMS.md)
- [Evaluation and results](docs/EVALUATION.md)
- [Limitations and threats to validity](docs/LIMITATIONS.md)
- [Reproducibility guide](docs/REPRODUCIBILITY.md)
- [Evidence index](docs/EVIDENCE_INDEX.md)
- [Five-minute demo and defense guide](docs/DEFENSE_GUIDE.md)
- [Week 8 testbed](testbed/README.md)
