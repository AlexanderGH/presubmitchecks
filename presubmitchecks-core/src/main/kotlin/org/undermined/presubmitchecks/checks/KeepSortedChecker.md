# Keep Sorted Checker

Detects that any `keep-sorted` blocks are sorted.

## Supported Features

- ✅ Files
- ✅ Git Pre-Commit
- ✅ GitHub Actions
- ✅ Fixes

## Usage

See [KeepSorted](../fixes/KeepSorted.md).

## Configuration

- `severity`: The severity of the check.
- `templates`: A `map<string, string>` object of `template id` to `configuration`, e.g.
    `"a_is_last": "prefix_order=,a case=no"`. These can then be referenced as aliases, e.g.
    `# keep-sorted start template=a_is_last` is the same as
    `# keep-sorted start prefix_order=,a case=no`.

### Example Configuration

```json
{
  "KeepSorted": {
    "severity": "ERROR",
    "templates": {
      "gradle-dependencies": "block=yes case=no by_regex=(api|implementation|testImplementation)\"?\\([^)]\\) numeric=yes prefix_order=api,implementation,testImplementation,androidTestImplementation"
    }
  }
}
```
