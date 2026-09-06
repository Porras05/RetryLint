# RetryLint v0.1 frozen core contract

The mandatory analyzer behavior described here is frozen after Week 7. Later
work may correct defects, improve documentation, and evaluate this behavior,
but it does not add mandatory analyzer features.

## Input contract

RetryLint accepts one UTF-8 `retrylint.yml` manifest (version `1`) containing
explicit services, operations, synchronous calls, analysis roots, amplification
thresholds, and an optional failure threshold. Every service references one
relative YAML configuration file below the manifest directory. Absolute paths,
normalized traversal, and symbolic-link components are rejected.

Service configuration is read as a Jackson tree. Only these Resilience4j areas
are interpreted:

- `resilience4j.retry.configs` and `.instances`;
- `maxAttempts` / `max-attempts`;
- `waitDuration` / `wait-duration`;
- `baseConfig` / `base-config`;
- `resilience4j.timelimiter.configs` and `.instances`;
- `timeoutDuration` / `timeout-duration`.

Supported durations are non-negative integer `ms`, `s`, or `m` values and
ISO-8601 durations. Retry wait timing becomes explicitly unsupported for
exponential backoff, randomized wait, `intervalFunction`, or
`intervalBiFunction`. Custom retry/time-limiter aspect order makes RL002 timing
unsupported. Unrelated Spring YAML is ignored. Profiles, placeholders,
`.properties`, arbitrary relaxed binding, and programmatic customization are
not supported.

## Analysis semantics

- **RL001** computes the maximum root-reachable product of effective
  `maxAttempts` values with `BigInteger`. Threshold equality triggers its
  configured severity; ties preserve manifest-derived topology/call order.
- **RL002** checks adjacent calls with
  `attempts × timeout + (attempts - 1) × fixedWait`. It reports only when the
  incoming timeout is strictly smaller. Unknown or unsupported timing creates
  a completeness gap and never becomes zero.
- **RL003** reports repeated unknown-idempotency targets as warnings and
  repeated non-idempotent targets as errors. One attempt and idempotent targets
  are safe. Findings say an operation *may be repeated*; they do not claim a
  runtime duplicate occurred.

Completeness gaps are separate from findings. An affected RL002 pair is skipped
without stopping supported RL002 pairs, RL001, or RL003.

## Resource limits

These limits are intentionally far above the four-service testbed while
bounding accidental or hostile work:

| Input/work item | Limit |
|---|---:|
| Manifest YAML | 2 MiB |
| Each service YAML | 2 MiB |
| Services | 256 |
| Operations | 10,000 |
| Calls | 20,000 |
| Analysis roots | 256 |
| Identifier/policy name | 256 characters |
| Project name | 256 characters |
| Relative configuration path | 1,024 characters |
| Named policies per configuration section | 2,048 |
| Named `baseConfig` inheritance | 8 levels |
| RL002 adjacent pairs per operation | 10,000 |
| RL002 adjacent pairs per analysis | 100,000 |

Cycle reconstruction is iterative, so a permitted deep cyclic graph produces a
diagnostic rather than consuming the JVM call stack. Limits fail clearly as
invalid input; RL002 work limits produce explicit completeness gaps.

## Frozen CLI and output

Commands are `retrylint validate`, `retrylint analyze`, and `retrylint version`.
Analyze options are `--format text|json`, `--output <file>`,
`--fail-on warning|error|never`, `--no-color`, and `--verbose`.

Text output contains project counts, ordered findings and their evidence,
completeness, and summary counts. JSON `schemaVersion` is `1` and contains
`project`, `findings`, `completenessGaps`, and `summary`. BigInteger evidence is
a decimal string, durations use ISO-8601 strings, and configuration evidence
uses manifest-relative paths. Findings sort by severity (error first), rule ID,
call path, then title. Gaps sort by rule ID, operation, call path, then reason.

Exit codes are frozen:

- `0`: analysis completed without meeting the selected threshold;
- `1`: analysis completed and a finding met the threshold;
- `2`: invalid input, topology, configuration, option, or output destination;
- `3`: unexpected internal failure.

## Explicit non-goals

The frozen core does not include RL004, source analysis, automatic call-graph
discovery, Spring profiles, placeholders, Kubernetes integration, circuit
breaker analysis, automatic repairs, SARIF, a web UI, or another retry
framework.
