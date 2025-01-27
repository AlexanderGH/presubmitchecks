# GitHub Action Setup

## Generate Fixes

## Apply Fixes

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
