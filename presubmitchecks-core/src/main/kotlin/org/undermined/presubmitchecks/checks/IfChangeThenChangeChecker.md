# LINT.IfChange / IfChangeThenChange / IfThisThenThat Checker

IfChangeThenChange (aka IfThisThenThat or LINT.IfChange) allows you to request that if a block of
content is modified, another block of content is also modified.

## Supported Features

- ❌ Files
- ✅ Git Pre-Commit
- ✅ GitHub Actions
- ❌ Fixes

## Usage

### Syntax

Blocks start with `LINT.IfChange` and end with `LINT.ThenChange(target)`. Each directive must
be on its own line, usually as comments. The `target` is composed of an optional file (if absent:
the current file) and an  optional label (if absent: the entire file), though at least one must be
specified. Blocks may be nested, but start and end directives must be balanced.

```
// Special syntax to disable processing this lint in the rest of the file.
// Useful for using the check string in things like documentation.
// LINT.IfChange(@ignore)

// Anonymous Block
LINT.IfChange
// Labeled Block
// Note: Label must match [A-Za-z-]+
LINT.IfChange(my-block-name)

// Relative path to another file.
// Any change in the other file fulfills the requirement.
LINT.ThenChange(relative/path/to/file.txt)
// Another file relative to the repository root.
// Any change in the other file fulfills the requirement.
LINT.ThenChange(//from/repo/root/file.txt)
// A block in the same file.
// Only changes in a block name "other-block-name" in the same file fulfill the requirement.
LINT.ThenChange(:other-block-name)
// You can also combine the two.
// Only changes in a block named "other-block-name" in file.txt fulfill the requirement.
LINT.ThenChange(file.txt:other-block-name)
```

### Usage Examples

The most common usage is to ensure if one file is modified, another one is too. This helps ensure
some related data or lists stay in sync.

```
foo.txt:
// LINT.IfChange
this you modify  line, you'll need to also modify bar.txt
// LINT.ThenChange(bar.txt)

bar.txt:
// LINT.IfChange
this you modify  line, you'll need to also modify foo.txt
// LINT.ThenChange(foo.txt)
```

### Skipping

To skip this check, add `NO_IFTT=[reason for skipping]` to your changelist/PR description.

## Configuration

- `severity`: The severity of the check.

### Example Configuration

```json
{
  "IfChangeThenChange": {
    "severity": "ERROR"
  }
}
```

## See Also

- https://www.chromium.org/chromium-os/developer-library/guides/development/keep-files-in-sync/
- https://fuchsia.dev/fuchsia-src/development/source_code/presubmit_checks#ifthisthenthat
