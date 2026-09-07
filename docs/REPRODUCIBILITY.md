# Reproducibility guide

These instructions assume a clean checkout on Windows and commands run from the repository root. Source fixtures live under `src/test/resources`; do not edit copies under `build/resources/test` because Gradle regenerates them.

## Prerequisites

- Git
- Microsoft OpenJDK 21 or another compatible Java 21 distribution
- the repository's Gradle wrapper (`gradlew.bat`)
- PowerShell plus Docker Desktop with Docker Compose for Week 8 only

Confirm Java before starting:

```powershell
java -version
.\gradlew.bat --version
```

Both should report Java 21 for this project. A separate system Gradle installation is unnecessary.

## Build and test the frozen analyzer

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

Verify that post-freeze work did not change production analyzer code:

```powershell
git diff v0.1.0 -- retrylint-cli/src/main
```

The last command should print nothing.

## Validate and analyze the complete example

```powershell
.\gradlew.bat :retrylint-cli:run --args="validate retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml"
.\gradlew.bat :retrylint-cli:run --args="analyze retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml --format text --fail-on never"
.\gradlew.bat :retrylint-cli:run --args="analyze retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml --format json --fail-on never"
```

The source manifest declares the service configuration paths. RetryLint resolves them relative to that manifest and reads each source `application.yml` directly.

## Week 8: runtime validation

Start Docker Desktop, ensure the required ports are free, then run:

```powershell
powershell -ExecutionPolicy Bypass -File testbed/scripts/verify-week8.ps1
```

This is the expensive integration experiment: it builds/starts the four-service Compose system, runs the scenarios, and writes an evidence result. Read [the testbed guide](../testbed/README.md) before running it. The checked-in result is [`evaluation/week8-results.json`](../evaluation/week8-results.json). Week 11 does not require rerunning Docker because it changes no executable testbed code.

## Week 9: mutation benchmark

```powershell
.\gradlew.bat :retrylint-cli:week9Benchmark
```

Expected invariant: 40 executed, 0 skipped, TP=30, FP=0, FN=0. Results are deterministic and written under `evaluation/week9/results/`; a second unchanged run should be byte-identical.

## Week 10: scalability

```powershell
.\gradlew.bat :retrylint-cli:week10Scalability
```

Expected invariants are four successful generated sizes (10, 100, 500, and 1000 operations), 3 excluded warm-ups and 20 retained measurements per size, and zero findings/gaps for the safe inputs. Raw timings naturally differ by machine and run. The task rewrites `evaluation/week10/results/`, so use a clean branch/worktree if you want to compare new timings without replacing the checked-in primary evidence. Full boundaries and generated input locations are in the [Week 10 guide](../evaluation/week10/README.md).

## Regenerate documentation figures

```powershell
powershell -ExecutionPolicy Bypass -File docs/scripts/generate-figures.ps1
```

The script reads the checked-in Week 8, Week 9, and Week 10 JSON and deterministically writes SVGs under `docs/figures/`. It does not run the experiments or alter evidence.

## Evidence checks

```powershell
Get-Content evaluation/week8-results.json
Get-Content evaluation/week9/results/summary.json
Get-Content evaluation/week10/results/scalability-results.json
git status --short
```

Use the [evidence index](EVIDENCE_INDEX.md) to trace each report claim. Build output under `build/` and IntelliJ metadata are not evidence sources.
