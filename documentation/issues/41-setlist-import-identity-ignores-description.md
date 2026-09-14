# 41 · A setlist whose only change is its description is "already there" on import

**Severity:** low · **Area:** `:domain:implementation` (`PrepareImportUseCaseImpl.holdsTheSameAs`)

`PrepareImportUseCaseImpl.kt:130` compares title, `isArchived` and entries. A setlist exported after only its
description changed is `IDENTICAL` on the other device and disregarded, so the description never arrives by import
(sync compares bytes and is unaffected).

## Fix

Add `description == other.description` to `holdsTheSameAs` and mention the description in the KDoc above
`planSetlists` (it lists what counts and why).
