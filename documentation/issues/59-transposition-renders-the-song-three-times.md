# 59 · One transposition tap parses and transposes the whole song two to three times

**Severity:** low-medium · **Area:** `:presentation` (`SongDetailsScreen.kt`, `SongDisplayControls.kt`)

`SongDetailsScreen.kt:162–164` runs `viewModel.renderSong(...)` on the full text just to read `.metadata.key` for the
app bar; :496 renders it again for the page; `SongDisplayControls.kt:94–96` a third time while the sheet is open.
`CampfireViewModel.renderKey(song, transposition, spelling)` (:816) already derives the key from the song's metadata
without the text.

## Fix

Replace both key-only renders with `viewModel.renderKey(currentSong, currentTransposition, chordSpelling)` (the
`Song` is at hand in both places). The full render stays where the page draws it. One caveat: `renderKey` uses the
`{key}` the *scan* read; a song whose key was edited in the editor is re-scanned on save (`saveSong` re-reads the
file), so the two agree.
