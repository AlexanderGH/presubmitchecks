# keep-sorted

See https://github.com/google/keep-sorted

## Differences from Google's Implementation

There's no guarantee that this implementation performs exactly the same way in all cases, especially
when combining many options in the same keep-sorted section.

Known Differences:

- `numeric=yes` is limited to numbers with 100 digits
- Support for a `template` option for pre-defined sets of options. Useful when needing to re-use the
  same options across many files. Templates are specified in the `KeepSorted` initialization.
- Support for a `maintain_suffix_order` post processor which takes a regex with 1 to 2 groups. The
  first match group is sticky to the sorted group position, while the second match group is sticky
  to the sorted group item itself. For example `maintain_suffix_order=(,)?(\s*(?://.*?|\/\*.*?|))`
  would keep commas in a consistent position. Using this option disables the default comma-handling
  behavior that Google's implementation has.

  <table border="0">
  <tr>
  <td>
  
  ```
  a = [

    1,
    3, // three
    2
  
  ]
  b = [

    1,
    3,
    2 // two

  ]
  ```
  
  </td>
  <td>
  
  ```diff
   a = [
  +  // keep-sorted start maintain_suffix_order=(,)?(\s*(?://.*?|\/\*.*?|))
     1,
     2,
     3 // three
  +  // keep-sorted end
   ]
   b = [
  +  // keep-sorted start maintain_suffix_order=(,)?(\s*(?://.*?|\/\*.*?|))
     1,
     2, // two
     3
  +  // keep-sorted end
   ]
  ```
  
  </td>
  </tr>
  </table>
