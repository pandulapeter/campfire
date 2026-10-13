# Give every Fill in of the song details lookup an identity of its own, so that a second one equal to the first still resets the form

**Kind:** bug  ·  **Severity:** low–medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/DialogType.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongMetadataDialog.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongDetailsReview.kt` (or a new small file beside it for the pure hand-over), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongDetailsReviewTest.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/DialogHostTest.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/DialogStackTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`

## Problem

The Edit song details form (`SongMetadataDialog.kt`) keeps its fields in a saveable state keyed by the draft it was handed:

```kotlin
var values by rememberSaveable(dialog.song.fileName, dialog.draft, stateSaver = songMetadataSaver) { mutableStateOf(dialog.draft ?: offeredValues) }
```

The song details lookup hands its result back by replacing the form under it with an equal-shaped `DialogType.SongMetadata` (`CampfireViewModel.kt`):

```kotlin
fun fillSongMetadata(dialog: DialogType.SongDetailsLookup, draft: Map<ChordProMetadataFields.Field, String>) = dialogHost.updateUnderlyingDialog(
    DialogType.SongMetadata(song = dialog.song, values = dialog.values, target = dialog.target, draft = draft),
)
```

`DialogType.SongMetadata` is a data class (`song`, `values`, `target`, `draft`), so a hand-over whose draft equals the previous one is the same dialog: `updateUnderlyingDialog` writes an equal list, the `rememberSaveable` key does not change, and the form keeps what the user typed since.

Reproduction: open Edit song details, open the lookup, pick a recording, Fill in (the form now holds draft D1, album "Nimrod" among it). Clear the Album field by hand. Open the lookup again (it is handed the form's current values, album blank), pick the same recording: the album row is offered and ticked by default ("ticked where the form holds nothing"), and `dialog.draft.withSongDetails(rows, tickedFields)` (`SongDetailsLookupSheet.kt`) produces D1 again. Fill in: the lookup slides away and the Album field is still empty. The suggestion the user ticked is lost without a word, and Save writes the cleared album.

The form stays composed under the lookup (`CampfireDialogs.kt` composes every open dialog in `key(dialog.slotKey)`), which is why its state survives and is not re-initialised.

## Fix

Give each hand-over an identity the form can key on, independent of what the draft holds:

1. `DialogType.SongMetadata` gets `val fillCount: Int = 0` (a data class field; the "no defaulted parameters" rule of the root `CLAUDE.md` is about Koin constructors and does not apply), documented as "how many times the song details lookup has filled this form in, so that a fill equal to the one before still puts its draft back into the fields".
2. `DialogType.SongDetailsLookup` carries the form's count: `val formFillCount: Int`. `CampfireViewModel.showSongDetailsLookup` passes `formFillCount = dialog.fillCount`.
3. The hand-over is a small pure function next to `withSongDetails`, so that it can be tested, e.g. in `SongDetailsReview.kt`:

   ```kotlin
   /** The metadata form [this] lookup was put up over, holding [draft], as a hand-over of its own (see [DialogType.SongMetadata.fillCount]). */
   internal fun DialogType.SongDetailsLookup.filledForm(draft: Map<Field, String>) = DialogType.SongMetadata(
       song = song,
       values = values,
       target = target,
       draft = draft,
       fillCount = formFillCount + 1,
   )
   ```

   and `fillSongMetadata` becomes `dialogHost.updateUnderlyingDialog(dialog.filledForm(draft))`.
4. In `SongMetadataDialog.kt` key the state on the count instead of the draft:

   ```kotlin
   var values by rememberSaveable(dialog.song.fileName, dialog.fillCount, stateSaver = songMetadataSaver) { mutableStateOf(dialog.draft ?: offeredValues) }
   ```

Saving and restoring are unaffected: `SavedDialog.kt` maps `DialogType.SongMetadata` to `savedSongEdit(SavedDialog.SongEdit.Kind.METADATA)`, which keeps neither the draft nor the new count, and a restored process reopens the form from the file through `showSongMetadataDialog` (count 0, draft null), as it does today. `slotKey` (`DialogStack.kt`) is the sheet's kind and target, so the replaced form keeps its slot.

Alternative considered: keying on a fresh `Uuid` per hand-over. It works too, but a counter keeps `DialogType` equality meaningful in tests and is no larger.

Update `ui/dialogs/CLAUDE.md`, where it says Fill in puts "a new `DialogType.SongMetadata.draft` the form resets its fields to": say that every Fill in is a new hand-over (`fillCount`), so the fields are reset to it even where it equals the one before.

## Tests

In `SongDetailsReviewTest.kt`, over the pure hand-over (the `updateUnderlyingDialog` half in `DialogHostTest.kt`):

- `two fills with the same draft are two hand-overs`: build a form `DialogType.SongMetadata(song, values = emptyMap(), target = file)`, a lookup with `formFillCount = form.fillCount`, `val first = lookup.filledForm(draft)`; then a lookup put up over `first` (`formFillCount = first.fillCount`) and `val second = it.filledForm(draft)` with the same `draft`. Assert `first != second`, `first.draft == second.draft`, and that `updateUnderlyingDialog(second)` over `[info, first, lookup]` leaves `second` in the form's place.

Adjust the existing `DialogType.SongDetailsLookup(...)` constructions in `DialogHostTest.kt` (`the lookup fills the form under it in place, and closing it uncovers that form`) and `DialogStackTest.kt` for the new `formFillCount` parameter.

## Manual check

On any platform with the "Online song details" switch on: open a song's Edit song details, open the lookup, pick a recording with an album, Fill in. Clear the Album field by hand, open the lookup again, pick the same recording, leave its Album box ticked, and Fill in. The Album field must show the album again.
