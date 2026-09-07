# Final release verification

This record distinguishes the frozen analyzer from the delivery wrapper:

- Analyzer core: `v0.1.0` / `85ffebc096eb1b7d007270638ccffcecb62605b3`.
- Final project delivery: `v0.1.1` (annotated local tag created only after the checks below pass).
- Clean-checkout verified release candidate: `1307694` (`chore: prepare RetryLint v0.1.1 final delivery`).

## Verification record

| Check | Result |
|---|---|
| Java | Microsoft OpenJDK 21.0.12.1; Gradle launcher/daemon JVM 21.0.12.1 |
| Frozen production-core diff | PASS: `git diff v0.1.0 -- retrylint-cli/src/main` produced no output |
| Project tests | PASS: 106 total, 105 passed, 1 conditional Windows symlink test skipped, 0 failed |
| Clean test | PASS: `BUILD SUCCESSFUL` |
| Build | PASS: `BUILD SUCCESSFUL` |
| Distribution | `retrylint-cli-0.1.0.zip`, 7,583,782 bytes; built from the clean candidate |
| Distribution SHA-256 | `F2B31FB5ADFA7D9283CD5635B5BE146A14030647A7AE68BC642F299E62CE2B98` |
| Extracted-package smoke | PASS: version, validate, text/JSON analysis, safe/unsupported/invalid/threshold cases |
| Week 8 | PASS from unchanged historical evidence: predicted 27, ten observed `1/3/9/27` runs |
| Week 9 | PASS: 40 executed, 0 skipped, TP=30, FP=0, FN=0 |
| Week 10 | PASS in isolated checkout: four sizes, 80 measured analyses, all correct; historical evidence intact |
| Clean checkout | PASS: clean test, build, version, validate, text/JSON analysis, Week 9, Week 10, distribution, and extracted-package smoke |
| Documentation/links | PASS: required documents present and relative Markdown links resolve |
| Offline backup | PASS at final delivery: `release/artifacts/RetryLint-final-backup-v0.1.1.zip` is created and extraction-verified after the local tag |
| Demo backup | PASS under the documented fallback: packaged text/JSON outputs, complete example, figures, defense guide, and exact recording checklist are offline; manual video/screenshots remain owner action |

Historical Week 8–10 evidence is not replaced by final verification. The primary evidence remains linked through [`docs/EVIDENCE_INDEX.md`](../docs/EVIDENCE_INDEX.md).

## Definition of done

| Requirement | Result and evidence |
|---|---|
| Clean checkout builds with Gradle wrapper | PASS: isolated `clean test` and `build` |
| Supported YAML subset documented exactly | PASS: core contract and algorithms guide |
| Unknown/unsupported timing never becomes zero | PASS: explicit `Resolution`/completeness-gap tests and unsupported package smoke |
| 3/3/3 reports 27 with responsible path | PASS: complete-example text and JSON smoke |
| Adjacent timeout formula passes boundaries | PASS: RL002 unit/fixture suite |
| Non-idempotent/unknown operations receive correct findings | PASS: RL003 decision-table tests and complete-example smoke |
| Cycles are rejected clearly | PASS: topology tests and packaged cycle exit 2 |
| Text/JSON agree and exit codes are tested | PASS: unit suite and package smoke for exits 0, 1, and 2; internal-failure exit 3 remains unit-tested |
| Testbed observes 1/3/9/27 | PASS: unchanged Week 8 evidence contains ten matching runs |
| Mutation benchmark reproducible | PASS: isolated run executed 40, skipped 0, TP=30, FP=0, FN=0 |
| Scalability experiment reproducible | PASS: isolated run completed four sizes and retained 80 correct measurements |
| Limitations explicit | PASS: README, release notes, and limitations guide |
| Defense works without internet | PASS: distribution, example, outputs, evidence, figures, and defense material are included in the offline backup |

## Release state

The annotated local `v0.1.1` tag identifies this final delivery record. It is not pushed automatically. `v0.1.0` remains unchanged and continues to identify the evaluated analyzer core.
