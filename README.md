# RetryLint

RetryLint is a Kotlin command-line analyzer for composed Resilience4j retry and
TimeLimiter configuration across an explicit synchronous service topology.

The mandatory analyzer and CLI behavior is documented in
[`docs/CORE_CONTRACT_V0.1.md`](docs/CORE_CONTRACT_V0.1.md) and frozen for the
v0.1.0 release.

## Five-minute example

Build and validate the bundled example from PowerShell:

```powershell
.\gradlew.bat build
.\gradlew.bat :retrylint-cli:run --args="validate retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml"
```

Analyze it as a human-readable report:

```powershell
.\gradlew.bat :retrylint-cli:run --args="analyze retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml --format text --no-color"
```

Or produce JSON suitable for another tool:

```powershell
.\gradlew.bat :retrylint-cli:run --args="analyze retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml --format json --fail-on never"
```

The available analysis controls are:

```text
--format text|json
--output <file>
--fail-on warning|error|never
--no-color
--verbose
```

Exit code `0` means analysis completed without meeting the selected failure
threshold, `1` means a finding met it, `2` means invalid input prevented the
requested analysis, and `3` means an unexpected internal failure.

## Four-service testbed

Week 8 adds a controlled Spring Boot + Resilience4j experiment that runs one
parameterized image as checkout, orders, payments, and bank. See
[`testbed/README.md`](testbed/README.md) for the exact build, analyzer,
1/3/9/27, ten-run, reset, and cleanup commands.
