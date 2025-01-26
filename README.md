# Pre-Submit Checks for Repositories

## Supported Checks

- FileEndsInNewLine
- [IfChangedThenChange/IfThisThenThat](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/checks/IfChangeThenChangeChecker.md)
- [KeepSorted](presubmitchecks-core/src/main/kotlin/org/undermined/presubmitchecks/fixes/KeepSorted.md)

## Usage

### Git Pre-Commit

```shell
./gradlew :presubmitchecks-cli:installDist && git diff main | ./presubmitchecks-cli/build/install/presubmitchecks-cli/bin/presubmitchecks-cli git-pre-commit --diff - --fix
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

To automatically suggest fixes:

```yaml
permissions:
  contents: read
  pull-requests: write

jobs:
  checks:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.head_ref }}
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Run Presubmit Checks
        uses: 'AlexanderGH/presubmitchecks'
        id: presubmitchecks
        with:
          repo-token: ${{ secrets.GITHUB_TOKEN }}
          config-file: .github/presubmitchecks.json
          apply-fixes: "true"
      - uses: parkerbxyz/suggest-changes@v1
        if: steps.presubmitchecks.outputs.applied-fixes == '1'
        with:
          comment: 'Please commit the suggested changes.'
          event: 'COMMENT'
```

Or to automatically push fixes:

```yaml
name: Check PR
on:
  pull_request:
    branches:
      - main

permissions:
  contents: write

jobs:
  checks:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.head_ref }}
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Run Presubmit Checks
        uses: 'AlexanderGH/presubmitchecks'
        id: presubmitchecks
        with:
          repo-token: ${{ secrets.GITHUB_TOKEN }}
          config-file: .github/presubmitchecks.json
          apply-fixes: "true"
      - name: Commit Fixes
        if: steps.presubmitchecks.outputs.applied-fixes == '1'
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add .
          git commit -m "Presubmit Fixes"
          git push
        shell: bash
```

## Configuration
