# Figure provenance

Run the generator from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File docs/scripts/generate-figures.ps1
```

| Output | Source |
|---|---|
| `architecture.svg` | Static rendering of the frozen pipeline described in [`ARCHITECTURE.md`](../ARCHITECTURE.md) and production source |
| `week8-amplification.svg` | [`evaluation/week8-results.json`](../../evaluation/week8-results.json) |
| `week9-benchmark.svg` | [`evaluation/week9/results/summary.json`](../../evaluation/week9/results/summary.json) |
| `week10-scalability.svg` | [`evaluation/week10/results/scalability-results.json`](../../evaluation/week10/results/scalability-results.json) |

The script validates the Week 8 run/count consistency and reads every displayed quantitative value from JSON. Week 10 coordinates and labels are recalculated from the current raw timing evidence; timing values are not embedded in plotting code.
