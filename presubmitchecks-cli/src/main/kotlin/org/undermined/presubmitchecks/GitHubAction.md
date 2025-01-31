# GitHub Action Setup

## Run Checks

```yaml
name: Check PR
on:
  pull_request:
    types: [opened, reopened, synchronize, edited]
    branches:
      - main

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
        uses: 'AlexanderGH/presubmitchecks@main'
        id: presubmitchecks
        with:
          repo-token: ${{ secrets.GITHUB_TOKEN }}
```

## Generate Fixes

To run fixes on the checked out repo:

```yaml
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.head_ref }}
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Run Presubmit Checks
        uses: 'AlexanderGH/presubmitchecks@main'
        id: presubmitchecks
        with:
          repo-token: ${{ secrets.GITHUB_TOKEN }}
          config-file: .github/presubmitchecks.json
          apply-fixes: "true"
```

## Apply Fixes

To automatically suggest fixes:

```yaml
jobs:
  checks:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write

    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.head_ref }}
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Run Presubmit Checks
        uses: 'AlexanderGH/presubmitchecks@main'
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
jobs:
  checks:
    runs-on: ubuntu-latest
    permissions:
      contents: write

    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.head_ref }}
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Run Presubmit Checks
        uses: 'AlexanderGH/presubmitchecks@main'
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
