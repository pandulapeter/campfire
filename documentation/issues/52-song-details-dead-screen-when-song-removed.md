# 52 · A song removed under an open details screen leaves it on a loading indicator forever

**Severity:** medium · **Area:** `:presentation` (`SongDetailsScreen`)

`SongDetailsScreen.kt:135–138, 299–307`: `songs = destination.songFileNames.mapNotNull { songsByFileName[it] }`.
When a sync run or a rescan removes the only song, `songs` becomes empty and the screen falls into the
`if (songs.isEmpty())` branch, whose comment assumes "the library has not been read yet" — a `DelayedLoadingIndicator`
under a title-less bar. Only the in-app `deleteSong` pops the screen. Issue 10's restored back stack can land here
too, when the song is gone after a relaunch.

## Fix

Tell the two cases apart with the ViewModel's `isLoading` (true while the library is being read):

```kotlin
val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
LaunchedEffect(songs.isEmpty(), isLoading) {
    // Every song this screen was opened on is gone from the library, and the library has been read: there is
    // nothing left to show, so the screen goes the way it would have if the song had been deleted from here.
    if (songs.isEmpty() && !isLoading) onBack()
}
```

Keep the loading branch for the genuine not-yet-read case. For a multi-song destination where *some* songs vanished,
the pager already shrinks; if the current page was the one removed, `pagerState.currentPage` is coerced by the pager
itself. Verify that a setlist whose last song is removed by sync pops back to the Setlists tab without the
navigation-generation workaround misfiring (see `nav3-interrupted-transition-pitfalls` in memory/`presentation/CLAUDE.md`).
