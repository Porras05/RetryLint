# Final release verification

This record distinguishes the frozen analyzer from the delivery wrapper:

- Analyzer core: `v0.1.0` / `85ffebc096eb1b7d007270638ccffcecb62605b3`.
- Final project delivery: `v0.1.1` (annotated local tag created only after the checks below pass).
- Verified source candidate before release-record changes: `ab49e476bfada3b6f0112df73139c443a6251b4c`.

## Verification record

| Check | Result |
|---|---|
| Java | Microsoft OpenJDK 21.0.12.1; Gradle launcher/daemon JVM 21.0.12.1 |
| Frozen production-core diff | PASS: `git diff v0.1.0 -- retrylint-cli/src/main` produced no output |
| Project tests | PASS: 106 total, 105 passed, 1 conditional Windows symlink test skipped, 0 failed |
| Clean test | PASS: `BUILD SUCCESSFUL` |
| Build | PASS: `BUILD SUCCESSFUL` |
| Distribution | `retrylint-cli-0.1.0.zip`, 7,583,759 bytes |
| Distribution SHA-256 | `B1E8F3FF7FA8B21EC9EA6DD2741DE6D8081E1A34E96A03AC3D449E7BE379AC28` |
| Extracted-package smoke | PASS: version, validate, text/JSON analysis, safe/unsupported/invalid/threshold cases |
| Week 8 | PASS from unchanged historical evidence: predicted 27, ten observed `1/3/9/27` runs |
| Week 9 | PASS: 40 executed, 0 skipped, TP=30, FP=0, FN=0 |
| Week 10 | Historical evidence intact; clean-checkout reproduction must pass four sizes/80 measured analyses |
| Clean checkout | Pending final-candidate commit verification |
| Documentation/links | Pending final audit |
| Offline backup | Pending creation after the local release tag |
| Demo backup | Text/JSON outputs and recording checklist prepared; manual video/screenshots remain owner action |

Historical Week 8–10 evidence is not replaced by final verification. The primary evidence remains linked through [`docs/EVIDENCE_INDEX.md`](../docs/EVIDENCE_INDEX.md).
