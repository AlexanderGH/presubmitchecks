# IfChangeThenChange / IfThisThenThat Checker

IfChangeThenChange (aka IfThisThenThat) allows you to request that if a block of content is modified, another block of content is also modified.

**Note: Replace `` `LINT `` with `LINT` in all examples (we use `` `LINT `` to avoid triggering the checks in this file).**

## Syntax

Blocks start with `LINT.IfChange` and end with `LINT.ThenChange(target)`. Each directive must
be on its own line, usually as comments. The `target` is composed of an optional file (if absent:
the current file) and an  optional label (if absent: the entire file), though at least one must be
specified. Blocks may be nested, but start and end directives must be balanced.

```
// Anonymous
`LINT.IfChange
// Labeled
`LINT.IfChange(my-block-name)

// Relative path to another file.
// Any change in the other file fulfills the requriement.
`LINT.ThenChange(relative/path/to/file.txt)
// Another file relative to the repository root.
// Any change in the other file fulfills the requriement.
`LINT.ThenChange(//from/repio/root/file.txt)
// A block in the same file.
// Only changes in in that block fulfill the requirement.
`LINT.ThenChange(:other-block-name)
// You can also combine the two.
// Only changes in a block names "other-block-name" in that file.txt fulfill the requirement.
`LINT.ThenChange(file.txt:other-block-name)
```

## Examples

The most common usage is to ensure if one file is modified, another one is too. This helps ensure
some related data or lists stay in sync.

```
foo.txt:
// `LINT.IfChange
this you modify  line, you'll need to also modify bar.txt
// `LINT.ThenChange(bar.txt)

bar.txt:
// `LINT.IfChange
this you modify  line, you'll need to also modify foo.txt
// `LINT.ThenChange(foo.txt)
```

## Skipping

To skip this check, add `NO_IFTT=[reason for skipping]` to your changelist/PR description.

## See Also

- https://www.chromium.org/chromium-os/developer-library/guides/development/keep-files-in-sync/
- https://fuchsia.dev/fuchsia-src/development/source_code/presubmit_checks#ifthisthenthat