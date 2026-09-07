# Defense guide

## Five-minute demonstration

Prepare the repository and terminal before presenting; keep the checked-in Week 8-10 evidence open in editor tabs so the demo does not depend on Docker or a performance rerun.

1. **0:00-0:35 — Problem and input.** Show [`complete-example/retrylint.yml`](../retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml). Point out services, operations, synchronous calls, roots, and idempotency.
2. **0:35-1:00 — Configuration ownership.** Show the three caller-side Retry policies in the adjacent service `application.yml` files and explain that each permits three total attempts.
3. **1:00-1:40 — Live analyzer.** Run:

   ```powershell
   .\gradlew.bat :retrylint-cli:run --args="analyze retrylint-cli/src/test/resources/fixtures/complete-example/retrylint.yml --format text --fail-on never"
   ```

4. **1:40-2:20 — Rules.** Point to RL001's `3 x 3 x 3 = 27` envelope, RL002's adjacent timeout comparison, and RL003's “may be repeated” warning/error. Emphasize possible behavior, not observed duplication.
5. **2:20-3:05 — Runtime evidence.** Open [`week8-results.json`](../evaluation/week8-results.json) and show that every recorded run observed checkout/orders/payments/bank counts `1/3/9/27` under the controlled persistent failure.
6. **3:05-3:50 — Detection evidence.** Open the [Week 9 summary](../evaluation/week9/results/summary.md): 40 executed, TP=30, FP=0, FN=0 on the reviewed corpus. State the corpus qualification aloud.
7. **3:50-4:30 — Scalability evidence.** Open the [Week 10 summary](../evaluation/week10/results/summary.md), show the four successful sizes and current median/p95 columns, and identify the in-process measurement boundary.
8. **4:30-5:00 — Boundaries.** Close with explicit completeness gaps and the main limits: static synchronous DAG, supported Resilience4j subset, manifest-declared idempotency, and no probability/traffic model.

Fallback: if the live invocation is unavailable, show the command and use the checked-in evidence. Do not rebuild Docker containers during the five-minute sequence.

## Likely questions and evidence-based answers

### What problem does RetryLint solve?

It exposes retry risks that emerge across service boundaries: multiplied attempts, incompatible adjacent timeout budgets, and retries of operations that may not be safe to repeat.

### Why static analysis?

The relevant configuration and declared topology can be checked before deployment, deterministically and without waiting for a rare runtime failure. Static analysis gives a possible envelope, not the frequency of occurrence.

### Why model the system as a graph?

Operations and synchronous calls provide the missing cross-service structure needed to compose caller-local policies along paths and around adjacent call pairs.

### Why must the graph be a DAG?

The frozen algorithm uses topological traversal and finite path composition. Cycles would require explicit recurrence/termination semantics that v0.1.0 does not claim to model, so validation rejects a concrete cycle.

### What exactly does RL001 calculate?

It calculates the maximum product of resolved total attempts along any path reachable from a declared root and reconstructs the responsible path.

### Why use `BigInteger`?

Retry products can grow rapidly with path length. Arbitrary-precision multiplication avoids silent integer overflow and preserves threshold comparisons.

### What does Resilience4j `maxAttempts` mean?

It includes the initial invocation, so `maxAttempts=3` permits one initial call plus up to two retries. A contract test verifies this against the pinned library.

### Why does RL002 use its formula?

Under the supported fixed-wait/default-order model, every attempt may consume its TimeLimiter timeout and every gap between attempts may consume the fixed wait: `Attempts x Timeout + (Attempts - 1) x Wait`.

### Why is timeout equality safe?

RL002's incompatibility condition is strictly `incoming < retryWindow`. At equality, the configured incoming budget is not smaller than the modeled window, though real scheduling overhead is outside this static formula.

### Why is idempotency important?

A retry may repeat effects after an uncertain response. Manifest-declared non-idempotency therefore raises RL003 to error; unknown idempotency is warning; known idempotency suppresses the rule.

### What is the difference between warning and error?

Severity expresses the rule's configured or semantic confidence/risk boundary. For RL001 it comes from manifest thresholds; for RL002 a non-idempotent target is error; for RL003 non-idempotent is error and unknown is warning.

### What is a completeness gap?

It is an explicit record that a specific condition could not be evaluated safely because required semantics were missing, unsupported, overflowed, or bounded by work limits. It is not a finding.

### Why not approximate unsupported timing?

Substituting a guessed fixed or zero wait could hide or fabricate RL002 findings. RetryLint preserves uncertainty and continues unaffected analyses.

### How was the 27x prediction validated?

The Week 8 Docker Compose testbed used four real Spring Boot/Resilience4j services and persistent downstream failure. Ten recorded runs matched the static `1 -> 3 -> 9 -> 27` counters.

### How was Week 9 ground truth established?

Each seed/mutant has a reviewed `truth.json`; reviewers compare overlays with the parent and independently check validity, retry arithmetic, timeout pairs, idempotency, and expected completeness. It was not generated from RetryLint output, though independent external review remains a validity limitation.

### What do TP, FP, and FN mean here?

They compare exact `(ruleId, severity, ordered callPath, targetOperation)` events for supported valid cases. Invalid rejections and completeness gaps are classified separately.

### Does precision and recall of 1.000 mean RetryLint is perfect?

No. It means there were no false results on this controlled 40-case reviewed corpus. It does not establish accuracy on unrepresented systems or defects.

### What does the baseline show?

It fairly checks what local configuration alone can reveal. Without topology it can report local parse/unsupported issues, but cannot derive the three architecture-level rules.

### Why is Week 10 not a Big-O proof?

It contains four empirical sparse-DAG sizes on one primary environment. The results describe those runs; they do not mathematically establish asymptotic complexity or generalize to every graph family.

### Why use warm-ups and exclude JVM startup?

Warm-ups reduce first-use/JIT effects within the chosen boundary. Excluding startup isolates analyzer work—parsing through rule execution—from Gradle, process launch, CLI parsing, and rendering.

### What are the main limitations?

The largest are the supported Resilience4j subset, synchronous-DAG assumption, static inputs, declared rather than inferred idempotency, absence of traffic/probability/queue/circuit-breaker models, and controlled synthetic evaluation scope.

### What would you implement next?

After the frozen study, candidates include independently reviewed real-world corpora, source-assisted topology/idempotency discovery, additional explicitly modeled retry strategies, asynchronous semantics, cross-machine performance replication, and IDE/CI integrations. Each requires a new contract rather than silently expanding v0.1.0.

## Evidence shortcuts

- [Frozen contract](CORE_CONTRACT_V0.1.md)
- [Algorithms](ALGORITHMS.md)
- [Evaluation](EVALUATION.md)
- [Limitations and threats](LIMITATIONS.md)
- [Claim-level evidence index](EVIDENCE_INDEX.md)
