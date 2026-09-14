# 14 · `resolve()` writes the conflict copy before the upload that may fail

**Severity:** low (an extra ` (3)` copy in a race) · **Area:** `:data:repository:implementation` (`SyncEngine.resolve`)

## Cause

`SyncEngine.resolve` (:251–255) writes `name (2).cho` locally, then uploads the local version; if that upload returns
`Conflict` the outcome is `hasUnresolvedConflict = true` with no index entries, but the copy is already on disk. The
second pass sees `name (2).cho` as new-local (uploads it) and plans `name.cho` as `Resolve` again → downloads the
remote again → writes `name (3).cho`. Two devices resolving the same file at once end with (2) and (3) each.

## Fix

Reorder: upload the local version with `operation.revision` **first**. Only on `Written` write the copy locally and
upload it under its free name. On `Conflict`, return `OperationOutcome(hasUnresolvedConflict = true)` with nothing
written. The KDoc's promise ("both devices end with both versions") still holds; the copy just waits for the pass in
which it is actually the losing version. Update the KDoc sentence about ordering and the "a conflict copy has to be
on disk before the file it was made from is overwritten" note in `apply` (still true for the *download* group).
