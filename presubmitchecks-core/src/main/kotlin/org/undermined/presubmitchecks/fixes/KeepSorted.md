# keep-sorted

See https://github.com/google/keep-sorted

## Differences from Google's Implementation

There's no guarantee that this implementation performs exactly the same way in all cases, especially
when combining many options in the same keep-sorted section.

Known Differences:

- `numeric=yes` is limited to numbers with 100 digits
- Support for a `template` option for pre-defined sets of options. Useful when needing to re-use the
  same options across many files.