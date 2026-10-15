# Reopen the New song sheet of a pasted or shared text after process death when the text is small

**Challenged:** amended — dropped the iOS `isFromShare` flag: `SavedStateStore` is restored only by Android ("Empty on
every real start, and on the platforms that have no such thing as a process being restored"), so iOS, the desktop, and
the web never reopen a `SavedDialog` and plan 03's re-offered share is the only iOS restore (no two sheets); the limit is
50 000 characters of text (`LONG_DOCUMENT_LENGTH`), not 100 KiB, since the back stack (≤ 100 000 JSON characters) and
the editor field (≤ 50 000 characters) already share the same Bundle as UTF-16; the saved variant holds only `text` and
`isConverted`, the rest is recomputed from the text by the same function that built the form; typed values are not
promised beyond what `NewSong` gets.

**Kind:** feature (user decision)  ·  **Severity:** low  ·  **Platforms:** Android only (the only platform whose saved
state survives process death; the case is a user looking up the artist in another app)
**Depends on:** plan 01 (same lane, S, after 02): it reshapes how the form is shown and keys its `rememberSaveable`.
Plan 03 (lane I) needs nothing from this plan.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialog.kt`,
`.../ui/dialogs/SavedDialogRestorer.kt`, `.../ui/state/importing/NewSongFromText.kt`, `.../ui/dialogs/DialogType.kt`
(`NewSongFromText` KDoc), `.../ui/dialogs/CLAUDE.md`, `.../ui/navigation/CLAUDE.md` (the saved state bullet),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SavedDialogTest.kt`

## Problem

`DialogType.NewSongFromText` has no `SavedDialog` variant (`toSavedDialog` ends in `else -> null`), so after Android
ends the process the sheet is gone. For a share this loses the text: the Activity does not handle its intent again on a
restore (`CampfireMainActivity.onCreate`: `if (savedInstanceState == null && …)`). The KDoc's reason, "the clipboard
still holds it", holds only for a paste. The user decided (33rd review) to restore it for small texts.

Only Android is concerned: `SavedStateStore` is backed by the view model's `SavedStateHandle`, and its KDoc says it is
"Empty on every real start, and on the platforms that have no such thing as a process being restored" (iOS, desktop,
web all start fresh; that is why the editor has its on-disk draft). On iOS, plan 03 keeps an unanswered share in the
inbox and offers it again, which is that platform's restore.

## Fix

- **`SavedDialog.NewSongFromText(val text: String, val isConverted: Boolean)`**, `@Serializable` like its siblings.
  Not `values` or `hasChords`: both are what `ChordProParser.summarize(text)` says, and `newSongFromTextDialogOf`
  computes them that way at HEAD:

  ```kotlin
  val summary = ChordProParser.summarize(song.text)
  return DialogType.NewSongFromText(
      text = song.text,
      values = SONG_METADATA_FIELDS.associateWith { ChordProMetadataFields.valueOf(summary.metadata, it).orEmpty() },
      isConverted = song.isConverted,
      hasChords = summary.hasChords,
  )
  ```

  Pull that into `internal fun newSongFromTextDialog(text: String, isConverted: Boolean)` in `NewSongFromText.kt`,
  used by `newSongFromTextDialogOf` and by the restorer, so the reopened form offers exactly the values it did (which
  also keeps plan 01's `rememberSaveable(songFileName, offeredValues, …)` key equal), and the saved state carries the
  text alone.
- **`toSavedDialog`**: `is DialogType.NewSongFromText -> if (text.length <= MAX_SAVED_TEXT_LENGTH) SavedDialog.NewSongFromText(text, isConverted) else null`,
  with `MAX_SAVED_TEXT_LENGTH = LONG_DOCUMENT_LENGTH` (50 000 characters, `screens/songEditor/EditorTextReplacement.kt`)
  or its own constant of the same value, commented with the budget: the saved state crosses to the system in one
  transaction of about a megabyte for the whole process, written as UTF-16, and already holds the back stack (up to
  `MAX_SAVED_BACK_STACK_LENGTH = 100_000` JSON characters, ~200 KB) and an editor's text (`EditorFieldSaver`, up to
  `LONG_DOCUMENT_LENGTH`, ~100 KB) — an editor can be on the stack under the form, plan 01 waits only for one with
  unsaved text. 50 000 characters (~100 KB once escaped) keeps the sum near 400 KB; any real chord sheet is far below
  it. Larger texts stay unrestored, as today. The form stacks over nothing, so a null here leaves nothing under it to
  fall back to.
- **`SavedDialogRestorer.reopen`**: `is SavedDialog.NewSongFromText -> dialogHost.showDialog(newSongFromTextDialog(saved.text, saved.isConverted))`.
  It needs no song or setlist; it still goes through the existing waits (library read, `visibleDialog == null`, top
  screen unchanged). Under plan 01 the restored form is not a queue request, and needs none: Create calls
  `songFilesController.createSong(values = values, text = dialog.text)`, which uses only the dialog's text and the
  form's values (the name is numbered on a collision, as for any New song), and dismissing calls `dismissSheet`. A
  text shared while it is up waits in plan 01's queue for `dialog == null`, as behind any other restored sheet; What's
  new and the restorer race through `visibleDialog` exactly as for the other `SavedDialog`s.
- **Typed values**: nothing to add. `NewSongDialog` is the same composable `NewSong` uses, in the same slot mechanism,
  so what the user typed comes back exactly when it does for `NewSong`, otherwise the offered values do. (A caution for
  whoever verifies this on a device: the slot is `key(dialog.slotKey)` with `slotKey = this::class`, and a `KClass`'s
  hash code is identity-based, so the composite key `rememberSaveable` saves under may differ in a new process; if so,
  no restored form sheet gets its typed values back. That is not this plan's to fix; report it.)
- **Docs**: the `NewSongFromText` KDoc ("Not reopened in a restored process …") becomes "reopened in a restored process
  when its text is at most `MAX_SAVED_TEXT_LENGTH` characters; the text is saved with it". `SavedDialog`'s KDoc ("by
  kind and file names only") gains the exception. `ui/dialogs/CLAUDE.md`'s first bullet: "(not `NewSongFromText`, whose
  text lives only in the dialog)" → "(`NewSongFromText` too, its text saved with it, when it is at most 50 000
  characters)". `ui/navigation/CLAUDE.md`'s "the open form sheet (`SavedDialog`: kind and file names, …)" → "kind and
  file names, or a small pasted text".

## Tests

In `SavedDialogTest`: a `NewSongFromText` at the limit is saved by `savedDialogOf` with its text and `isConverted`, and
`newSongFromTextDialog(text, isConverted)` gives back a dialog equal to the original (values and `hasChords` included);
one character over the limit gives null; and, with the file's restorer fakes, a saved `NewSongFromText` is reopened once
the library is read.
