# Make Retry work for a failed layout, and say the right thing for an empty setlist

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, both `strings.xml`

## Problem

- Retry does `attempt++`, which re-reads the source. `PrintSource` is a data class, so the re-read value equals the old
  one, `chosenSource` does not change, `produceState` does not rerun and `layoutFailed` stays true: the button does
  nothing for the very failure it is shown under.
- A setlist with no songs shows "Select at least one song to export." though there is nothing to select.
- `print_load_failed` says "Could not prepare the preview." for a failed read as well.

## Fix

Add `attempt` to the `produceState` keys. For `source.songs.isEmpty()` show a new string ("This setlist has no songs
yet." / Hungarian) and no song list header. Keep one failure string but word it for both: "Could not prepare the PDF."

## Tests

None.

## Manual check

Open the sheet for an empty setlist. (A layout failure cannot be provoked by hand; read the code path.)
