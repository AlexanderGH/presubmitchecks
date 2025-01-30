# Pre-Submit Checks for Repositories

A single extensible tool for linting, fixing and formatting your code in several
contexts (manual, git pre-commit, GitHub Action).

## Supported Checks

- [Content Patterns](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/ContentPatternChecker.md)
- [LINT.IfChange](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/IfChangeThenChangeChecker.md)
- [Keep Sorted](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/KeepSortedChecker.md)
- [New Line](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/NewLineChecker.md)
- [Valid JSON](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/ValidJsonChecker.md)

## Usage

Install the checker CLI using:

```shell
./gradlew :presubmitchecks-cli:installDist
```

### Check Files

```shell
./presubmitchecks-cli/build/install/presubmitchecks-cli/bin/presubmitchecks-cli files **.json '!**/dist/**''
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
        uses: 'AlexanderGH/presubmitchecks@main'
        id: presubmitchecks
        with:
          repo-token: ${{ secrets.GITHUB_TOKEN }}
```

See [GitHub Action Setup](presubmitchecks-cli/src/main/kotlin/org/undermined/presubmitchecks/GitHubAction.md) for
more examples.

## Configuration
