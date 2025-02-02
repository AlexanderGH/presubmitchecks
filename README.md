# Pre-Submit Checks for Repositories

A single extensible tool for linting, fixing and formatting your code in several
contexts (manual, git pre-commit, GitHub Action).

## Supported Checks

- [Content Patterns](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/ContentPatternChecker.md)
- [LINT.DoNotSubmitIf](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/DoNotSubmitIfChecker.md)
- [LINT.IfChange](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/IfChangeThenChangeChecker.md)
- [Keep Sorted](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/KeepSortedChecker.md)
- [New Line](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/NewLineChecker.md)
- [Valid JSON](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/ValidJsonChecker.md)

## Usage

Install the checker CLI:

```shell
# From Source
git clone https://github.com/AlexanderGH/presubmitchecks.git
cd presubmitchecks
./gradlew :presubmitchecks-cli:installDist
./presubmitchecks-cli/build/install/presubmitchecks-cli/bin/presubmitchecks-cli

# Download Release Artifact presubmitchecks-cli-standalone.jar
wget https://github.com/AlexanderGH/presubmitchecks/releases/download/<release>/presubmitchecks-cli-standalone.jar
java -jar presubmitchecks-cli-standalone.jar

# Download Release Artifact presubmitchecks-cli.zip
wget https://github.com/AlexanderGH/presubmitchecks/releases/download/<release>/presubmitchecks-cli.zip
unzip presubmitchecks-cli.zip
./presubmitchecks-cli/bin/presubmitchecks-cli
```

You can verify the integrity and provenance of an artifact using its associated cryptographically
signed attestations (not available for pre-release/SNAPSHOT artifacts):

```shell
gh attestation verify presubmitchecks-cli-standalone.jar
gh attestation verify presubmitchecks-cli.zip
```

### Check Files

```shell
./presubmitchecks-cli files '**.json' '!**/dist/**'
```

### Git Pre-Commit

```shell
# Automatic diff:
./presubmitchecks-cli git-pre-commit

# From stdin:
git diff main | ./presubmitchecks-cli git-pre-commit --diff -

# From a file:
./presubmitchecks-cli git-pre-commit --diff changes.diff
```

### GitHub Action

To run the presubmit checks as part of a GitHub Action `pull_request` trigger:

```shell
./presubmitchecks-cli github-action
```

Or, use our pre-configured GitHub Action:

```yaml
    steps:
      - name: Run Presubmit Checks
        uses: 'AlexanderGH/presubmitchecks@main'
        id: presubmitchecks
        with:
          repo-token: ${{ secrets.GITHUB_TOKEN }}
```

See [GitHub Action Setup](presubmitchecks-cli/src/main/kotlin/org/undermined/presubmitchecks/GitHubAction.md) for
more examples.

### Fixes

By default, `presubmitchecks` operates in report-only mode and will only list issues. In order for
it to make changes that attempt to fix issues, you must pass `--fix` to the CLI or the `apply-fixes`
input for the GitHub Action.

## Configuration

A configuration file can be specified with `--config`, e.g. `--config .github/presubmitchecks.json`.
When using the GitHub action, it can be specified using the `config-file` input.

The default configuration can be found
[here](presubmitchecks-core/src/main/resources/presubmitchecks.defaults.json).

### Example Configuration

```json5
{
  "checkerConfigs": {
    // One of more checker configurations can be set here.
  }
}
```
