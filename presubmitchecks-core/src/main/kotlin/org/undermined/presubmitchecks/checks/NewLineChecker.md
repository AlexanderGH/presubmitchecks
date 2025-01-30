# New Line Checker

This checker verifies:

- That common text files end in a single new line character.
- Only \n (line feed) is used for new lines, not \r (carriage return).

## Supported Features

- ✅ Files
- ✅ Git Pre-Commit
- ✅ GitHub Actions
- ✅ Fixes

## Configuration

- `severity`: The severity of this check.

### Example Configuration

```json
{
  "NewLine": {
    "severity": "ERROR"
  }
}
```
