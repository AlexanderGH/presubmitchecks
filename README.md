# Pre-Submit Checks for Repositories

## Supported Checks

- [Content Patterns](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/ContentPatternChecker.md)
- [IfChangeThenChange/IfThisThenThat](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/IfChangeThenChangeChecker.md)
- [KeepSorted](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/KeepSortedChecker.md)
- [NewLine](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/NewLineChecker.md)
- [ValidJson](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/ValidJsonChecker.md)

## Usage

Install the checker CLI using:

```shell
./gradlew :presubmitchecks-cli:installDist
```

### Check Files

```shell
./presubmitchecks-cli/build/install/presubmitchecks-cli/bin/presubmitchecks-cli files **.json
```

### Git Pre-Commit

```shell
# Automatic diff:
./presubmitchecks-cli/build/install/presubmitchecks-cli/bin/presubmitchecks-cli git-pre-commit

# From stdin:
git diff main | ./presubmitchecks-cli/build/install/presubmitchecks-cli/bin/presubmitchecks-cli git-pre-commit --diff -

# From a file:
./presubmitchecks-cli/build/install/presubmitchecks-cli/bin/presubmitchecks-cli git-pre-commit --diff changes.diff
```

### GitHub Action

Add it to your PR workflow using:

```yaml
    steps:
      - name: Run Presubmit Checks
        uses: 'AlexanderGH/presubmitchecks'
        id: presubmitchecks
        with:
          repo-token: ${{ secrets.GITHUB_TOKEN }}
```

See [GitHub Action Setup](presubmitchecks-cli/src/main/kotlin/org/undermined/presubmitchecks/GitHubAction.md) for
more examples.

## Configuration
