# 53 · `presentation/CLAUDE.md` says `visibleDialog` is written only through `setVisibleDialog`, but two writes bypass it

**Severity:** docs (all platforms; no user-visible effect. A module doc states as an invariant something two
deliberate code paths do not follow, and the next direct write would be added relying on the doc) · **Area:**
`presentation/CLAUDE.md`

## Symptom
The `ui/CampfireViewModel.kt` bullet of `presentation/CLAUDE.md` says: "`visibleDialog` is written through
`setVisibleDialog` alone, which is where the work parked behind a dialog — the import plan behind the conflicts
question, the exit behind the unsaved changes one — is dropped whenever that dialog stops being the one on screen."
Two writes do not go through it.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`:
- `:1436`, in `import()`: `while (!_visibleDialog.compareAndSet(expect = null, update = question)) { ... }`, commented
  at `:1432-1434` ("The question goes up past setVisibleDialog, which is right since it only ever replaces no dialog,
  and the pending import is only set once it is up").
- `:1545`, in `createSetlist`: `_visibleDialog.compareAndSet(null, DialogType.SongPicker(setlist))`, commented at
  `:1544` ("Not through setVisibleDialog: this only ever replaces no dialog at all, behind which nothing is parked.").

Every other write is `setVisibleDialog` (`:1788-1792`). Both bypasses are correct: replacing no dialog, they have
nothing parked to drop, and `import()` must set `pendingImport` after its question is up, which `setVisibleDialog`
would clear.

## Fix
Docs only, below.

## Tests
None.

## Verify
Read the new sentence against `CampfireViewModel.kt:1432-1437`, `:1544-1545` and `:1788-1792`.

## Docs
`presentation/CLAUDE.md`, the `ui/CampfireViewModel.kt` bullet: replace "`visibleDialog` is written through
`setVisibleDialog` alone, which is where the work parked behind a dialog — the import plan behind the conflicts
question, the exit behind the unsaved changes one — is dropped whenever that dialog stops being the one on screen."
with "`visibleDialog` is written through `setVisibleDialog`, which is where the work parked behind a dialog — the
import plan behind the conflicts question, the exit behind the unsaved changes one — is dropped whenever that dialog
stops being the one on screen. The two exceptions only ever replace no dialog, so there is nothing parked to drop:
the import's conflicts question and the song picker opened after a new setlist are each a compare-and-set against
null, the first because the import it belongs to is only parked once the question is up."

## Touches
- `presentation/CLAUDE.md`

## Depends on
Nothing.
