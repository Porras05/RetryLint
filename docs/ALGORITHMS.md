# Algorithms and configuration model

## RL001: retry amplification

RL001 starts every manifest analysis root at multiplier 1 and traverses the validated DAG in topological order. For every reachable call, it multiplies the best multiplier at the source operation by that call's resolved `maxAttempts`. It retains the greatest multiplier reaching each operation, then reconstructs the responsible call path and configuration files through predecessor links.

The canonical path has three calls with three attempts each:

```text
3 x 3 x 3 = 27
```

This is a configured retry amplification or possible invocation envelope. It does not assert that 27 duplicate business operations occurred. Multiplication uses `BigInteger`, so long paths do not silently overflow. If equal candidates reach an operation, the first path encountered in deterministic topological and outgoing-call order remains selected. The maximum reachable path becomes RL001 only when its multiplier reaches the manifest warning or error threshold; equality with a threshold triggers that severity.

## RL002: timeout-budget incompatibility

For each incoming/outgoing call pair around a middle operation, RL002 computes the downstream configured worst-case retry window:

```text
RetryWindow = Attempts x Timeout + (Attempts - 1) x Wait
```

For the canonical Week 5 case:

```text
3 x 400 ms + 2 x 50 ms = 1300 ms
incoming timeout             =  800 ms
possible overrun             =  500 ms
```

`incoming timeout < RetryWindow` produces a finding. Equality is safe because the incoming budget is not smaller than the calculated window. A non-idempotent downstream target raises the finding to error; other idempotency states produce warning. The formula assumes fixed wait and the supported default Retry-wrapping-TimeLimiter order. It is a configuration-envelope comparison, not a latency prediction.

If a required timeout, fixed wait, or aspect order is missing, unknown, unsupported, outside duration arithmetic, or skipped by a work limit, RL002 emits a `CompletenessGap` for that affected condition rather than inventing a value. Other rules still run.

## RL003: unsafe retry

RL003 evaluates each call's resolved attempts against the target operation's manifest-declared idempotency.

| Attempts | Target idempotency | Result |
|---:|---|---|
| 1 | any | none |
| >1 | idempotent | none |
| >1 | unknown | warning |
| >1 | non-idempotent | error |

Messages say the target **may be repeated** or the call **may invoke** it several times. RetryLint does not claim that a duplicate actually occurred.

## Finding versus completeness gap

A `Finding` is a supported architectural risk calculated from known inputs. A `CompletenessGap` records that RetryLint could not safely evaluate a particular condition. Examples include exponential or randomized wait, `intervalFunction`, `intervalBiFunction`, custom retry/time-limiter aspect order, missing timing policy, duration overflow, and bounded-work limits. Gaps are reported separately from findings and are not scored as findings.

## Supported Resilience4j model

RetryLint reads only `resilience4j.retry` and `resilience4j.timelimiter` from each service configuration as a Jackson tree. Unrelated Spring configuration is ignored.

Resolution is deterministic and ordered:

1. pinned Resilience4j-supported defaults;
2. `configs.default` overrides;
3. recursively referenced named `baseConfig` values;
4. instance-specific overrides.

The supported defaults are Retry `maxAttempts=3`, Retry `waitDuration=500ms`, and TimeLimiter `timeoutDuration=1s`. The resolver accepts the supported camelCase and kebab-case forms: `maxAttempts`/`max-attempts`, `waitDuration`/`wait-duration`, `baseConfig`/`base-config`, and `timeoutDuration`/`timeout-duration`. It parses `ms`, `s`, `m`, and ISO-8601 durations as defined by the frozen contract. Unknown bases and inheritance cycles are errors; inheritance depth is bounded.

Each call resolves Retry and TimeLimiter names in the **caller service's** configuration, never the target's configuration. An omitted retry reference means one attempt with zero wait; an omitted TimeLimiter remains absent. Exponential/randomized waits, interval functions, and custom aspect order are preserved as unsupported knowledge for precise RL002 rather than approximated.

Contract tests in [`Resilience4jContractTest.kt`](../retrylint-cli/src/test/kotlin/retrylint/config/Resilience4jContractTest.kt) exercise the pinned Resilience4j 2.3.0 dependencies declared in [`retrylint-cli/build.gradle.kts`](../retrylint-cli/build.gradle.kts). They verify defaults, that `maxAttempts` includes the initial invocation, fixed wait behavior, TimeLimiter timeout, and representative derived/base-configuration assumptions. RetryLint does not claim to model the complete Spring Boot or Resilience4j configuration surface.
