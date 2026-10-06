# Show the text as written in the editor's preview, without the library's transposition

**Kind:** ux  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt,
presentation/CLAUDE.md, CLAUDE.md
**Challenged:** amended — the file's own `{transpose}` directive is part of the text as written and stays applied (`renderSong` adds `parsed.metadata.transpose` itself); only the reader's library override goes. The "all agree" claim and the doc wording now say so.

## Problem

`SongEditorScreen.kt:567` (8ee010b36), the preview pane:
```kotlin
SongPreview(
    ...
    transposition = transpositions[destination.fileName, null],
```
with `val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()` at `:333`, its only use in the file.
`Transpositions.get(fileName, null)` is the library override (`UserPreferences.transpositions`), and `SongPreview`
passes it into `viewModel.renderSong(…)` (`:849`), which transposes the chords and the key of the playing line.

Scenario: open a song from the library, step Transposition to +2 (stored as the library override), open Edit song file.
The field shows `[C]`; the editor's own stepper (`TextTranspositionControls`, `key = summary.metadata.key`) says C; the
preview right beside them shows D chords and its playing line names D. Tapping the editor's +1 rewrites the text to C#
and the preview shows D#. Opened from a setlist, the editor still applies the *library* override and ignores the
setlist's own, so its preview matches neither the screen it came from nor the text.

The docs say the preview shows what is being written: root CLAUDE.md, "everywhere but in the editor's preview, which
says all four whatever the switches, since it shows what is being written", and "as does the editor's preview, where the
text being typed is what says them"; presentation/CLAUDE.md says the editor stepper "rewrites the file rather than
changing how it is read: there is no amount". A reader's transposition is the same kind of reading choice as the Chords
switch, which the preview deliberately ignores (`SongPreview`'s comment: "this preview is here to show what is being
written").

## Fix

This is a product choice (see Decisions); the options:

- **A (recommended): the text as written.** Pass `transposition = 0` to `SongPreview` and delete the
  `transpositions` collection at `:333`. Do **not** touch `CampfireViewModel.renderSong`: it adds the file's own
  `{transpose}` (`parsed.metadata.transpose + transposition`), and that directive is part of what is being written, so
  the preview keeps applying it, exactly as the song details screen does. For every song without a `{transpose}`
  directive the preview, the field and the stepper then name the same chords and the same key; a song that carries one
  is previewed moved by what it says, which is the file's own instruction rather than a reader's choice. Add to the songEditor section of presentation/CLAUDE.md (next to the stepper paragraph): the preview shows
  the text as written — none of the reader's transposition, whether the song was opened from the library or a setlist
  (the file's own `{transpose}` still applies, being part of the text) — as it ignores the Chords and Metronome switches. Add "and in the key it is written in" to the root CLAUDE.md sentence "everywhere but in
  the editor's preview, which says all four whatever the switches, since it shows what is being written".
- **B: the text as it will be read where it was opened from.** Would need the editor destination to carry the setlist it
  was opened from (`CampfireDestination.SongEditor` has only `fileName`), pass `transpositions[fileName,
  setlistFileName]`, and a hint in the preview that it is transposed — otherwise the stepper and the preview keep
  disagreeing. More code, and it contradicts the documented "shows what is being written".

Drop nothing else: `SongPreview`'s `transposition` parameter can stay (always 0) or be removed together with
`latestTransposition`; removing it is cleaner — `inputsOf(text, spelling)` then passes `transposition = 0` to
`SongLyricsInputs`.

## Tests

None: a composable argument; nothing pure changes.

## Manual check

Open a song with chords from the Songs list, set Transposition to +2, open Edit song file: the preview's chords and
playing line say the same key as the field and the stepper. Step +1 in the editor: field, stepper and preview all move
together by one. Open the same song from a setlist with a different transposition and edit it: the preview again shows
the text as written.
