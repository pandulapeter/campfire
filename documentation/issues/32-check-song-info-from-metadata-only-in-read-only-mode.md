# Ask whether the current song has anything for the About the song sheet only in read only mode, and from its metadata alone

**Kind:** performance (paging)  ·  **Severity:** low–medium  ·  **Platforms:** all
**Lane:** U  ·  **Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (only
`songMetadataOf` and a new function next to it; lane S edits other parts of this file),
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`

## Problem

Every time the pager's current page changes, the song details screen fully parses the current song on the main
thread. At 491c4254a (`SongDetailsScreen.kt:306-312`):

```kotlin
val currentSongText = currentSong?.let { songTexts[it.fileName] }
…
val openCurrentSongInfo = currentSong?.let { song ->
    val hasSongInfo = remember(currentSongText) { currentSongText?.let(viewModel::songMetadataOf)?.hasSongInfo == true }
    if (currentSongText == null || (isReadOnly && !hasSongInfo)) null else { { viewModel.showDialog(CampfireViewModel.DialogType.SongInfo(song)) } }
}
```

with `CampfireViewModel.kt:2019`:

```kotlin
fun songMetadataOf(text: String): ChordProMetadata = parseChordPro(text).metadata
```

`parseChordPro` is `ChordProParser.parse`. It builds every block of the song and then normalizes the notation of the
whole song, only for the metadata to be kept. `currentSong` is `songs.getOrNull(pagerState.currentPage)`, which flips
in the middle of a swipe, so the parse lands on a frame of the swipe animation. The result is only used when
`isReadOnly` is true. Outside read only mode the sheet is offered whenever the text is there, but `remember` runs
the parse anyway, because the condition reads `hasSongInfo` only after it has been computed.

Measured on the desktop JVM (warm), full parse against `ChordProParser.parseMetadata` for small, medium and large
songs:

- `parse`: 51 µs, 310 µs, 1.46 ms.
- `parseMetadata`: 16 µs, 81 µs, 360 µs.

A low-end device is 8–15× slower, so the full parse costs up to 4–20 ms in the middle of a swipe for a long song, on
every page change, read only or not.

`hasSongInfo` (`SongMetadata.kt:176-181`) reads only album, year, composer, lyricist, duration, tags, languages and
links. Notation normalization changes none of these. A throwaway check while writing this plan found
`parseMetadata(text)` and `parse(text).metadata` equal in those eight fields over:

- text with a body, a chorus and directives repeated after the body;
- `{start_of_abc}` holding directives, and `{new_song}`;
- commented, indented and selector-suffixed directives and a `{start_of_tab}`;
- an empty text.

## Fix

1. In `SongDetailsScreen.kt`, compute it only in read only mode. Hoist it out of the `let` and key it on both:

   ```kotlin
   val hasSongInfo = remember(currentSongText, isReadOnly) {
       isReadOnly && currentSongText?.let(viewModel::hasSongInfo) == true
   }
   val openCurrentSongInfo = currentSong?.let { song ->
       if (currentSongText == null || (isReadOnly && !hasSongInfo)) null else { { viewModel.showDialog(…) } }
   }
   ```

   Keep the comment above it, and add that outside read only mode nothing needs to be read.
2. In `CampfireViewModel.kt`, next to `songMetadataOf`, add
   `fun hasSongInfo(text: String): Boolean = ChordProParser.parseMetadata(text).hasSongInfo`. `hasSongInfo` is
   `internal` in `:presentation`'s `SongMetadata.kt`, so it is reachable from there. The view model already uses
   `:chordpro` directly where a use case would only forward a call (`editorSummaryCache()` builds a
   `ChordProSummaryCache`). If the reviewer insists on a use case, the alternative is a
   `ParseChordProMetadataUseCase` in `:domain:api`/`:implementation` that forwards to `parseMetadata`. Lane S owns
   `:domain`, so it would be a new file there, named in this plan's commit, plus a `domain/api/CLAUDE.md` line.
   Recommended: the direct call. Leave `songMetadataOf` and its other caller, `Dialogs.kt:2466` (the sheet reads it
   once when it opens and wants the key, which is notation-normalized), unchanged.

A further option is to have the current page report `model.song.metadata.hasSongInfo` upward, since its
`SongLyricsModel` is already parsed off the main thread. That would cost nothing at all, but the title's tap target
would then depend on a model that arrives a frame later. The metadata-only scan, in read only mode only, is enough.

## Tests

In `chordpro/src/commonTest/.../ChordProParserTest.kt`, add a test that `ChordProParser.parseMetadata(text)` equals
`ChordProParser.parse(text).metadata` in `album`, `year`, `composer`, `lyricist`, `duration`, `tags`, `languages`
and `links`. Use the fixtures listed above: a body with a chorus and directives after it, `{meta: …}` forms of each
field, an `{start_of_abc}` environment holding directives, `{new_song}` followed by a second header, commented
(`# {tag: x}`), indented and selector-suffixed (`{tag-guitar: x}`) directives, a `{start_of_tab}` block, empty
values (`{year:}`), and an empty text. This pins the equivalence the read only check now relies on. Run
`./gradlew :chordpro:desktopTest`.

## Manual check

1. Turn on Read only in Settings → Features. Open a setlist whose songs differ: one with an album or tags, one with
   nothing but lyrics.
2. Swipe between them. The title of the song with info opens About the song when the song is at its top. The bare
   song's title does not, and just scrolls to top.
3. Turn Read only off. Every song's title opens the sheet again.
4. On a low-end phone, swiping through long songs no longer stutters at the moment the page changes.
