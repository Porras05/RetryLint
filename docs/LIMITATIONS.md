# Limitations and threats to validity

## Limitations

- **Configuration subset.** RetryLint models only the frozen Resilience4j Retry and TimeLimiter subset. It does not model every Spring property, decorator, or Resilience4j module.
- **Synchronous DAG.** Calls are assumed synchronous and the manifest must be acyclic. Cyclic service behavior, asynchronous messaging, event-driven flows, queues, fan-out timing, and feedback loops are outside v0.1.0.
- **Static configuration.** Analysis uses the checked-in manifest and YAML values. Runtime overrides, profiles selected elsewhere, programmatic configuration, dynamic refresh, and request-specific changes can make deployment behavior differ.
- **Timing semantics.** Precise RL002 supports fixed waits and the default modeled aspect order. Exponential/randomized waits, interval functions, interval bi-functions, and custom aspect order create explicit completeness gaps rather than approximations.
- **Declared idempotency.** RL003 trusts the manifest's `idempotent`, `non-idempotent`, or `unknown` declaration; it does not infer semantic idempotency from source code or data stores.
- **Envelope, not traffic model.** RL001 calculates configured possible amplification. RetryLint has no request-volume, failure-probability, latency-distribution, queueing, load, or circuit-breaker interaction model, and does not predict how often the envelope occurs.
- **Bounded analysis.** Input, path, inheritance, and adjacent-pair limits protect deterministic execution. Hitting a rule work limit can produce a completeness gap instead of an exhaustive result.
- **Evaluation breadth.** Week 8 covers one controlled four-service architecture. Week 9 uses a small synthetic reviewed corpus. Week 10 uses four synthetic sparse DAG sizes, not production traces or dense/adversarial graph families.
- **Performance scope.** Week 10 reports one primary Windows/JVM environment and in-process latency only. JVM startup, CLI/Gradle overhead, deployment I/O, and precise memory usage were not measured.

## Threats to validity

### Construct validity

The three rules operationalize selected retry-architecture risks, not a complete definition of reliability. The Week 9 mutation operators represent selected configuration defects; its exact-event scoring can miss qualities not encoded by `(ruleId, severity, ordered callPath, targetOperation)`. Static envelopes and configured timeout windows are proxies for possible behavior, not observations of all runtime outcomes.

### Internal validity

Week 9 ground truth was manually reviewed within the project rather than by independent blinded assessors, so author bias or shared assumptions may remain. The Week 8 deterministic failure mode improves control but may conceal concurrency or transient-failure effects. Week 10 retains all measurements, yet JIT compilation, GC, operating-system scheduling, background load, and filesystem caching were not separately instrumented.

### External validity

One four-service testbed does not represent every microservice architecture or Resilience4j deployment. The mutation corpus is synthetic, and the scalability graphs are generated sparse DAGs with safe uniform policies. Results may differ for real heterogeneous systems, other framework versions, dense topologies, additional roots, asynchronous systems, other operating systems, and larger configurations.

### Conclusion validity

Week 8's repeated agreement supports the 27x prediction for that scenario, not a universal runtime guarantee. Week 9's precision/recall/F1 of 1.000 applies only to its 40-case controlled corpus. Week 10 has 20 measurements at each of four sizes on one main environment; this supports a descriptive latency table, not formal complexity or a universal capacity limit. No outlier was removed, but no confidence intervals or cross-machine replication were performed.
