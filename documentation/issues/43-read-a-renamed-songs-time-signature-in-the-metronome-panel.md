# Read the time signature of a song being renamed in the metronome panel, as the click already does

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomePanel.kt

## Problem

`MetronomePanel.kt:97-100` (8ee010b36):
```kotlin
val timeSignature = when (val context = viewModel.metronomeContext) {
    MetronomeContext.Standalone -> settings.timeSignatureOrDefault
    is MetronomeContext.Song -> songsByFileName[context.songFileName].timeSignatureOrDefault
}
```
During **Update file name** the library drops the old name before the back stack (and so `metronomeContext`) is
rewritten; for that window `updateSongFileName` holds the song in `songsBeingRenamed`. The click's own pattern reads it
(`CampfireViewModel.kt:1383-1391`: `songOf = { songs[it] ?: renaming[it] }`), and so does the pager
(`SongDetailsScreen`'s `songs`), but the panel does not: `songsByFileName[old]` is null, `null.timeSignatureOrDefault`
is 4/4. A song in 3/4 with the panel up shows four beat blocks and then three again once the rename lands, and a beat
tapped in that window is stored by `withBeatLevels` under 4/4 instead of 3/4.

## Fix

In `MetronomePanel`, collect `viewModel.songsBeingRenamed` (`StateFlow<Map<String, Song>>`, `CampfireViewModel.kt:661`)
next to `songsByFileName`, and resolve
`(songsByFileName[context.songFileName] ?: songsBeingRenamed[context.songFileName]).timeSignatureOrDefault` — the same
fallback `currentMetronomePattern`'s `songOf` uses. Extend the comment above it: a song being renamed is still the
click's song, and its bar is still its own.

## Tests

None worth adding: a map lookup with a fallback.

## Manual check

Open a song with `{time: 3/4}` whose file name no longer matches its header (so **Update file name** is offered), show
the metronome panel and take Update file name: the beat row stays at three blocks throughout.
