# 30 · Dropbox paths are case-insensitive; the engine keys files case-sensitively

**Severity:** low (legacy or hand-named files only) · **Area:** `:data:repository:implementation` (`SyncEngine`)

`toRemoteFiles` reports `entry.name` as Dropbox stores it. A local `Song.cho` where the remote has `song.cho` are two
keys to the planner; the `add`-mode upload of `Song.cho` comes back `path/conflict` → `Conflict` → an unresolved
conflict that the second pass repeats, and the run ends quietly with nothing moved for that file.

## Fix

In `SyncEngine.synchronize`, before planning, fold remote names onto the local spelling where they differ only by
case: build `localNamesByFolded = local.associateBy { it.key.copy(name = it.key.name.lowercase()) }` and map each
remote `SyncKey` to the local key when a folded match exists and the exact one does not. Dropbox addresses the same
file under either spelling, so every later request with the local name still hits it. Add a `SyncPlannerTest`-style
unit test for the folding helper (make it an `internal fun` in the engine file). Note in the KDoc that this is a
Dropbox property surfacing in the engine on purpose: it keeps the provider's contract to "a flat folder of names".
