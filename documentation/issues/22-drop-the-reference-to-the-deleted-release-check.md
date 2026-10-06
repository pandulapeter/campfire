# Drop the reference to the deleted release-check document from the document fixtures' README

**Kind:** docs · **Severity:** low · **Platforms:** none
**Files:** `data/source/local/implementation/src/desktopTest/resources/document/README.md`

## Problem

`data/source/local/implementation/src/desktopTest/resources/document/README.md` ends:

```
available. Platform picker/drop/share and snackbar behavior is checked separately in
`documentation/release-check.md`.
```

That file never existed at that path and `documentation/testing/release-check.md` was deleted in `b0e98cfa8`; there is
no release-check document in the repository any more.

## Fix

Replace the sentence with: `Platform picker/drop/share and snackbar behavior is checked by hand before a release.`
keeping the line wrapping of the paragraph.

## Tests

None.

## Manual check

None.
