# Validate only the Song defaults fields that are shown and changed, so an out-of-range value in the file cannot block Save

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongPlayingDialog.kt` (`SongPlayingDialog`, `isValidSongPlayingDraft`),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongPlayingDraftTest.kt`,
`presentation/CLAUDE.md` (the Song defaults sheet description, one clause)

Lane D: apply before 34 and 38, which edit other parts of `SongPlayingDialog.kt`.

## Problem

The sheet is offered the file's values (`CampfireViewModel.showSongPlayingDialog`):

```kotlin
ChordProMetadataFields.Field.CAPO to metadata.capo?.toString().orEmpty(),
ChordProMetadataFields.Field.TEMPO to ChordProTempo.parse(metadata.tempo)?.toString().orEmpty(),
```

`ChordProParser` keeps any integer capo (`"capo" -> value.toIntOrNull()?.let { capo = it }`, so `-1` and `14` too) and
`ChordProTempo.parse` any positive number (`320`). The sheet then validates every field, whether it is shown or touched:

```kotlin
val isValid = isValidSongPlayingDraft(capo = values[Field.CAPO].orEmpty(), tempo = values[Field.TEMPO].orEmpty())
...
enabled = isValid && SONG_PLAYING_FIELDS.any { values[it].orEmpty().trim() != offeredValues[it].orEmpty().trim() },
```

So a song whose file says `{tempo: 320}` or `{capo: 14}` (imported, synced or hand-edited — the importer and the editor do
not clamp them) cannot have *any* field saved from the sheet:

- with the Metronome switched off the tempo field is not composed at all (`if (shouldShowTempo)`), and with the Chords
  off neither is the capo field, so the value that blocks Save is invisible and nothing explains the disabled button;
- with the field shown, the user who only wanted to change the key has to fix a number they never touched first.

`setSongPlaying` writes only the changed fields, so validating an untouched value protects nothing.

## Fix

Validate a number only where it is shown and differs from the offered value. Change the helper to take what decides
that, and keep it pure:

```kotlin
/**
 * Whether the sheet's typed numbers can be written: each field that is shown and was changed is empty or a whole number
 * within its range. A value the file already held is not the sheet's to judge - only the changed fields are written -
 * and a hidden one cannot have been changed.
 */
internal fun isValidSongPlayingDraft(
    capo: String,
    tempo: String,
    offeredCapo: String = "",
    offeredTempo: String = "",
    isCapoShown: Boolean = true,
    isTempoShown: Boolean = true,
) = (!isCapoShown || capo.trim() == offeredCapo.trim() || isValidSongPlayingNumber(capo, Song.CAPO_RANGE)) &&
    (!isTempoShown || tempo.trim() == offeredTempo.trim() || isValidSongPlayingNumber(tempo, MetronomePattern.BPM_RANGE))
```

and call it with `offeredValues[Field.CAPO].orEmpty()`, `offeredValues[Field.TEMPO].orEmpty()`, `shouldShowChords` and
`shouldShowTempo`. Keep the field's own `isError` as it is, so an untouched out-of-range value is still drawn as one (it
tells the user why the click plays something else), only no longer blocking Save. Add to the Song defaults paragraph of
`presentation/CLAUDE.md`: a value the file already holds outside a range is shown as an error but does not keep the
other fields from being saved.

## Tests

Extend `SongPlayingDraftTest`:
- `capo = "14", offeredCapo = "14"` (untouched) → valid; `capo = "13", offeredCapo = "14"` → invalid;
- `tempo = "320", offeredTempo = "320", isTempoShown = false` → valid; same with `isTempoShown = true` → valid (untouched);
- `tempo = "301", offeredTempo = ""` → invalid (typed); `capo = "-1", offeredCapo = "-1"` → valid.
Existing tests keep passing through the defaults.

## Manual check

Put `{tempo: 320}` and `{capo: 14}` into a song's text in the editor and save. Open Edit song defaults: both fields show
their range error, and changing only the key enables Save and writes only the key. Switch the Metronome off in
Settings → Features, open the sheet again and change the capo to 3: Save is enabled and the file's `{tempo: 320}` is
left as it was.
