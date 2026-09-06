# RetryLint Week 8 testbed

This controlled testbed validates the frozen RetryLint `v0.1.0` analyzer
(commit `85ffebc`) against a real four-service Resilience4j system:

```text
checkout -> orders -> payments -> bank
```

The implementation is one Kotlin/Spring Boot application and one Docker image.
Compose starts that image four times and supplies each instance's service ID,
downstream URL, named retry policy, and deterministic failure mode.

## Prerequisites

- Java 21 and the checked-in Gradle wrapper
- Docker Desktop or Docker Engine
- Docker Compose v2 or later
- Git (used to verify the frozen `v0.1.0` analyzer before the experiment)
- Windows PowerShell 5.1 or PowerShell 7

The pinned runtime versions are Spring Boot `3.5.0`, Resilience4j `2.3.0`,
Kotlin `2.1.21`, and Java 21.

## Counter and endpoint contract

`POST /invoke` is the only counted business endpoint. Each service increments
its receive counter exactly once on endpoint entry, before any downstream call.
It accepts or creates `X-RetryLint-Trace-Id`, and the same value is propagated
on every downstream retry attempt.

The `testbed` Spring profile alone enables:

- `POST /test/reset` — synchronously clears total and per-trace counters;
- `GET /test/counters` — returns service ID, total, and all trace counts;
- `GET /test/counters?traceId=<id>` — also returns that trace's count.

Health and control requests never increment business counters. Counters are
thread-safe and in-memory, so container restart also clears them.

Bank supports `success`, `always-fail`, and `fail-first-n`. There are no random
faults and no custom retry loops; `DownstreamClient` obtains the configured
policy from Resilience4j's `RetryRegistry` and decorates the real HTTP call.

## Configuration identity

For the mandatory 27-call experiment, Compose directly bind-mounts these
frozen files—there is no runtime copy:

```text
retrylint-cli/src/test/resources/fixtures/complete-example/services/*/application.yml
```

RetryLint analyzes the sibling `complete-example/retrylint.yml`, which points
to those exact files. The 1/3/9 scenario manifests and their application files
live together under `testbed/configs/scenarios`; Compose mounts the same files
that each manifest names. Spring's additional configuration location adds only
testbed runtime settings around the analyzed Resilience4j values.

## Build and unit tests

From the repository root:

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

## Analyze the mandatory configuration

```powershell
.\gradlew.bat :retrylint-cli:run --args="analyze retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml --format text --fail-on never --no-color"
```

The RL001 evidence is `3 x 3 x 3 = 27`.

## One-command Week 8 verification

Start Docker, then run:

```powershell
.\testbed\scripts\verify-week8.ps1
```

The script uses bounded health polling, verifies the frozen analyzer's 27x
prediction, runs persistent-failure scenarios 1/3/9 once, runs 27 ten
independent times, verifies bank's deterministic third-call success behavior,
writes `evaluation/week8-results.json`, and stops Compose. It resets and checks
zero counters before every request and uses a fresh trace ID per run.

Expected persistent-failure counts are:

| Scenario | checkout | orders | payments | bank |
|---:|---:|---:|---:|---:|
| 1 | 1 | 1 | 1 | 1 |
| 3 (`1/1/3`) | 1 | 1 | 1 | 3 |
| 9 (`3/1/3`) | 1 | 3 | 3 | 9 |
| 27 (`3/3/3`) | 1 | 3 | 9 | 27 |

Use `-KeepRunning` to leave the final containers running.

## Individual commands

```powershell
.\testbed\scripts\start.ps1 -Scenario 27
.\testbed\scripts\reset.ps1
.\testbed\scripts\run-scenario.ps1 -Scenario 27
.\testbed\scripts\run-bank-third-success.ps1
.\testbed\scripts\stop.ps1
```

For 1, 3, or 9, start that scenario first and pass the same value to
`run-scenario.ps1`. Changing scenarios restarts the containers with checked-in
configuration; it never edits YAML or application code.

## Direct Compose commands

The scripts are the reproducible interface. For diagnosis, the equivalent main
startup is:

```powershell
$env:CONFIG_ROOT = "../retrylint-cli/src/test/resources/fixtures/complete-example/services"
docker compose -f testbed/compose.yml up -d --build
docker compose -f testbed/compose.yml ps
docker compose -f testbed/compose.yml down
```
