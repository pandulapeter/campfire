# 55 · The languages dialog loses its ticks on rotation

**Severity:** medium-low (dropped input, no destructive write) · **Area:** `:presentation` (`Dialogs.kt`, `SongLanguagesDialog`)

`Dialogs.kt:775`: `selectedCodes` is a plain `remember(dialog.song.fileName)` (the `query` next to it is
`rememberSaveable`). Tick languages → rotate → Done writes the song's original languages.

## Fix

`var selectedCodes by rememberSaveable(dialog.song.fileName, stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(dialog.song.languages.toSet()) }`.
Same pattern as issue 04.
