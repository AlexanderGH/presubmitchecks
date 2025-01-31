# LINT.DoNotSubmitIf

DoNotSubmitIf will ensure that the line following the comment will not contain the specified value.
This is useful for ensuring accidental debug or test values are not submitted.

## Supported Features

- ✅ Files
- ✅ Git Pre-Commit
- ✅ GitHub Actions
- ❌ Fixes

## Usage

### Syntax

```
// LINT.DoNotSubmitIf(value to look for)
line to search for the value and fail if it is present
```

### Usage Examples

The most common usage is to ensure some debug configuration or value is not accidentally submitted
to production.

```
// Check will fail if DEBUG = true
// LINT.DoNotSubmitIf(true)
static final boolean DEBUG = false;

// Multiple values are supported
// LINT.DoNotSubmitIf(ERROR)
// LINT.DoNotSubmitIf(WARNING)
val debugLogsAtLevel = LogLevels.DEBUG
```

## Configuration

- `severity`: The severity of the check.

### Example Configuration

```json
{
  "DoNotSubmitIf": {
    "severity": "ERROR"
  }
}
```
