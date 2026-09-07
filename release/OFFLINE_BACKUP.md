# Offline backup and restore

The final backup archive is generated locally as `release/artifacts/RetryLint-final-backup-v0.1.1.zip`. The directory is ignored by Git because it contains rebuildable binary delivery artifacts.

## Required contents

- `RetryLint-v0.1.1.bundle`: complete Git history and local release tags.
- `RetryLint-v0.1.1-source.zip`: tracked source at the final tag, including Gradle wrapper, documentation, evaluation evidence, and testbed.
- `retrylint-cli-0.1.0.zip`: independently runnable analyzer distribution.
- `SHA256SUMS.txt`, release notes, and final verification record.
- `demo/complete-example/`: the manifest and service configurations used offline.
- `demo/complete-example-text.txt` and `demo/complete-example.json`.
- `demo/RECORDING_CHECKLIST.md` and the defense guide.

Rebuildable `.gradle`, `build`, IDE, Kotlin daemon, Docker, and temporary benchmark directories are excluded. No secrets or machine-specific absolute paths are included.

## Offline restore check

1. Extract the final backup on an offline Java 21 machine.
2. Verify the CLI ZIP against `SHA256SUMS.txt`.
3. Extract `retrylint-cli-0.1.0.zip`.
4. Run `bin\retrylint-cli.bat version` and confirm `RetryLint 0.1.0`.
5. Run `validate` and text/JSON `analyze` against `demo\complete-example\retrylint.yml`.
6. Open the recorded Week 8–10 evidence, figures, and defense guide without network access.

The Git bundle can recreate the source repository with `git clone RetryLint-v0.1.1.bundle RetryLint` when Git is available.
