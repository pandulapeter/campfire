# Keep one huge song from freezing the app or running it out of memory

**Kind:** performance  ·  **Severity:** high (on a phone: a crash)  ·  **Platforms:** all, worst on Android and iOS
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt, presentation/src/commonMain/composeResources/values/strings.xml, presentation/src/commonMain/composeResources/values-hu/strings.xml, presentation/CLAUDE.md

## Problem
Measured on 2026-09-28 on the desktop debug build with a library of 3000 songs (the numbers are in
`documentation/issues/README.md`): everything is fine except one file. Opening a 2.85 MB song of 60 000 lines froze
the UI thread for 7.5 s on a Mac (all four `jstack`s taken during the freeze are in Skia paragraph layout under
`SongLyrics.kt`) and took the process from 400 MB to 3.4 to 4.4 GB of resident memory, most of which it kept after
leaving the song. A phone has a heap of a few hundred MB, so the same file is an out-of-memory crash there, and a
file a tenth that size is a multi-second freeze with the "app not responding" dialog behind it.

Why: the song details screen composes the **whole** song at once. `SongSectionsLayout` (`SongLyrics.kt:272` and
`:1251`) lays out every section to fit them into columns, every chorded line is measured on the UI thread to pad the
lyrics under the chords (`SongLyrics.kt:1842`, `:1940`, the width cache at `:1141`), and each line keeps its
`TextLayoutResult`. That is the right design for a song, whose few hundred lines fit a few pages, and the wrong one
for a file that is not one: a whole songbook concatenated into one `.cho`, a Word document saved as `.cho`, a log
file dropped into the folder. Nothing stops such a file from arriving: the import, the sync engine and the scan all
take a text file of up to 8 MiB (`ImportLimits.MAX_TEXT_FILE_SIZE`, `ImportLimits.kt:24`, meant for a collection of
songs in one file, which the import splits), the editor opens it (a single `TextFieldState`, which copes), and the
details screen is one tap away from the list. A user with an old phone who taps it once loses the app and, without
crash reporting, nobody hears of it.

For comparison, the other pathological files were fine: one 20 000-character line without spaces opened in 25 ms, a
0-byte song and a 500-song setlist in under 20 ms, and the 3000-song list itself in 2 s from process start.

## Fix
Bound what the details screen lays out, rather than making the sheet layout lazy (which would undo the column
fitting and the pager that the screen is built around):

1. A budget in `:chordpro` or `:presentation`: `SongLyricsLimits.MAX_LINES` (about 3000) and `MAX_CHARACTERS`
   (about 200 KB), several times the largest real song (a long tab file is under 1000 lines and 60 KB).
2. In the model preparation that `SongLyrics` runs on `Dispatchers.Default` (`SongLyrics.kt:1539`, `prepare`), when
   the parsed song exceeds the budget, keep only the sections up to the limit and mark the model as truncated. The
   screen then draws the sections it has and a final card saying the song is too long to be shown whole, with a
   button that opens the editor (which shows all of it). The text of the card is a new string in both `strings.xml`
   files. Performance mode, the transposer and the pager work on the truncated model unchanged.
3. Make sure the truncation happens before any measurement, and that the metadata scan (`ChordProParser.summarize`)
   already reads such a file without holding more than it needs; it does today (the list showed the song without a
   stall).
4. Document the budget in `presentation/CLAUDE.md` next to the description of the details screen.

Optional and separate: lower `MAX_TEXT_FILE_SIZE` for a *single* song read by the details screen and the editor to
1 MiB while keeping 8 MiB for the import of a collection; it is a second guard, and the first is what matters.

## Verification
- A `:presentation` `desktopTest` for the preparation: a song of `MAX_LINES + 1000` lines prepares to a model of at
  most `MAX_LINES` lines flagged truncated, one under the limit is untouched, and the truncation cuts at a section
  boundary.
- Manual, desktop: drop a 3 MB `.cho` into the library and open it: the screen appears within a second, memory stays
  under 1 GB, the last card offers the editor. Then on the Android emulator with a 2 GB RAM profile: no crash.

## Conflicts
`SongLyrics.kt` and `SongDetailsScreen.kt`; plan 05 touches one line of the latter.
