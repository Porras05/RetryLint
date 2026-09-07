# Manual demo recording and screenshot checklist

Automated screen recording is not part of the repository toolchain. Before the defense, record one short backup video and capture these five screenshots:

1. `bin\retrylint-cli.bat version` showing `RetryLint 0.1.0`.
2. Packaged text analysis showing RL001 27x and representative RL002/RL003 output.
3. [`evaluation/week8-results.json`](../../evaluation/week8-results.json) showing the `1/3/9/27` runtime counters.
4. [`evaluation/week9/results/summary.md`](../../evaluation/week9/results/summary.md) showing TP=30, FP=0, FN=0.
5. [`evaluation/week10/results/summary.md`](../../evaluation/week10/results/summary.md) showing all four scalability cases.

## Recording order

1. State that analyzer core v0.1.0 is frozen and final project delivery is v0.1.1.
2. Show the copied complete-example manifest and its three retry-owning calls.
3. Run packaged `version`, `validate`, text `analyze`, then JSON `analyze`.
4. Highlight RL001, RL002, RL003, and the distinction between findings and completeness gaps.
5. Show Week 8 runtime agreement, Week 9 controlled-corpus metrics, and Week 10 in-process scalability table.
6. Close with the principal limitations.

Expected command sequence from the extracted offline backup:

```powershell
.\retrylint-cli-0.1.0\bin\retrylint-cli.bat version
.\retrylint-cli-0.1.0\bin\retrylint-cli.bat validate .\demo\complete-example\retrylint.yml
.\retrylint-cli-0.1.0\bin\retrylint-cli.bat analyze .\demo\complete-example\retrylint.yml --format text --fail-on never
.\retrylint-cli-0.1.0\bin\retrylint-cli.bat analyze .\demo\complete-example\retrylint.yml --format json --fail-on never
```

Store the completed video/screenshots beside the backup archive; check them once with networking disabled. Do not include personal information, tokens, IDE secrets, or unrelated desktop windows.
