# Content Pattern Checker

This checker allows you to specify issues by looking for patterns in titles, descriptions,
filenames, changed lines or all lines in a file.

## Supported Features

- ✅ Files
- ✅ Git Pre-Commit
- ✅ GitHub Actions
- ❌ Fixes

## Configuration

- `severity`: The default severity for this check.
- `patterns[]`: A list of patterns to detect.
- `canned[]`: A list of canned check ids. Canned checks are defined
  [in this file](../../../../../resources/contentpatternchecker.canned.json).

Pattern Options:

- `patterns[].id`: A unique id for this pattern.
- `patterns[].message`: A message to show if this pattern is found.
- `patterns[].title`: The title for the message to show if this pattern is found.
- `patterns[].severity`: The severity of this pattern. Overrides the default severity.
- `patterns[].matches`: How to match this pattern.
- `patterns[].matches.changeTargets[]`: Match the target of the change, e.g. `["+^main$"]` to match
    the main branch. *
- `patterns[].matches.files[]`: Match the file's path relative from to the repository root. *
- `patterns[].matches.patterns[]`: The regex patterns to look for. **
- `patterns[].matches.excludes[]`: If the pattern was found, ignore it if any of these patterns match. **
- `patterns[].matches.context[]`: A list of contexts that `patterns` and `excludes` apply to. ***

**\* Context (`changeTargets`, `files`) Matching Syntax**

If the list of matchers for a context is empty, it is assumed to match.
Matchers for a context are evaluated in order, and toggle the match state based on the syntax below.

- `+regex`: Will match if the regex is found in the context.
- `-regex`: Will not match if the regex matches.
- `!regex`: Will match if the regex does not match.

**\*\* Value (`patterns`, `excludes`) Matching Syntax**

These use standard RE2 regexps.

**\*\*\* Context Options**

- `cl:title`: Matches the PR title.
- `cl:description`: Matches the PR description.
- `file:name`: Matches the file path.
- `file:line:added`: Matches any added (or modified) line.
- `file:line:removed`: Matches any removed line.
- `file:line:any`: Matches any line.

### Configuration Examples

```json
{
  "ContentPattern": {
    "severity": "WARNING",
    "patterns": [
      {
        "id": "DoNotSubmit",
        "message": "The change should not be submitted/merged.",
        "severity": "ERROR",
        "matches": {
          "changeTargets": [
            "+^main$"
          ],
          "patterns": [
            "(?i)DO NOT (SUBMIT|COMMIT|PUSH|MERGE)"
          ],
          "context": [
            "cl:title",
            "cl:description",
            "file:line:any"
          ]
        }
      }
    ]
  }
}
```

See [our presubmitchecks.json](../../../../../../../../.github/presubmitchecks.json) for more
examples such as:

- Preventing empty titles or descriptions
- Requiring conventional commit messages
- Requiring spaces not tabs
- Detecting trailing whitespace
- Detecting long lines
- Detecting gendered language
- Detecting non-inclusive language
