# 39 — The pager bar's "2 / 3" disagrees with the setlist's own numbering when an entry's file is missing

**Severity:** confusing numbers (all platforms) · **Area:** `:presentation`
(`screens/songDetails/SongDetailsScreen.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

**Decided by the orchestrator (a default the user may override):** the pager bar shows the song's place in the
setlist and the setlist's number of entries, the way the setlist's rows number themselves, rather than documenting
the difference.

## What the user sees

A setlist of four entries whose second file is missing (deleted outside the app, or not yet synced here). The Setlists
screen numbers the rows 1, 2 (the missing one), 3, 4 — deliberately, "so the numbers a setlist is read by are its own
order". Open song number 3 from there: the pager bar under the lyrics says "Setlist · 2 / 3". The band calls out
"number three", and the phone says two of three.

## Cause

The rows are numbered by slot, missing entries included
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt:378`,
`text = (index + 1).toString()`, fed by `SetlistWithSongs.Entry.index`, `CampfireViewModel.kt:636-641`).

The pager can only page through the songs that are there (`CampfireViewModel.kt:985-995`):

```kotlin
    /**
     * The pager can only page through the songs that are actually there, so the index is taken from those rather
     * than from the position of the row in the setlist, which also counts the entries whose file is missing.
     */
    fun openSongInSetlist(setlistWithSongs: SetlistWithSongs, song: Song) = openSongDetails(
        CampfireDestination.SongDetails(
            songFileNames = setlistWithSongs.songs.map { it.fileName },
```

and the bar labels the page by the pager's own count
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt:456-466`):

```kotlin
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { page ->
                Text(
                    text = stringResource(Res.string.song_details_song_position, page + 1, pageCount),
```

Paging through present songs only is right (there is nothing to show for a missing file); labelling them with the
page count is what disagrees with the setlist.

## The change

Invoke the **`code-style`** skill before the first edit.

Label each page with its entry's place in the setlist and the setlist's entry count, looked up from `setlists` (the
whole library's setlists, archived ones included, which the screen already collects at `:131`). Where any page cannot
be placed — the setlist is gone, or the library has not caught up with a rename yet — fall back to the page count for
every page rather than mixing two numberings.

In `SongDetailsScreen`, replace `:183`:

```kotlin
    val setlist = destination.setlistFileName?.let { fileName -> setlists.firstOrNull { it.fileName == fileName } }
    val setlistTitle = setlist?.title
    // Each page's place in the setlist, the missing files included, which is what the setlist's rows are numbered by:
    // a setlist is read by its own order, and "2 / 3" over the song its screen calls number 3 would read as a mistake.
    // The pages only count themselves where one of them cannot be placed - the setlist is gone, or the library has not
    // caught up with a change to it yet - so that two numberings are never mixed in one bar.
    val setlistSlots = remember(setlist, songs) {
        setlist?.let {
            val slotByPage = songs.map { song -> setlist.entries.indexOfFirst { entry -> entry.songFileName == song.fileName } }
            if (slotByPage.none { slot -> slot < 0 }) SetlistSlots(slotByPage = slotByPage, entryCount = setlist.entries.size) else null
        }
    }
```

pass it to the bar (`:382-389`):

```kotlin
            SongPagerControls(
                setlistTitle = setlistTitle,
                setlistSlots = setlistSlots,
                currentPage = pagerState.currentPage,
                ...
```

and in `SongPagerControls` (`:401-478`) add the parameter and use it in the label:

```kotlin
 * @param setlistSlots Where each page sits in the setlist, which is what the label numbers it by; null where the
 *   pages are numbered by themselves.
 ...
    setlistSlots: SetlistSlots?,
 ...
            ) { page ->
                val position = setlistSlots?.slotByPage?.getOrNull(page)
                Text(
                    text = if (position != null) {
                        stringResource(Res.string.song_details_song_position, position + 1, setlistSlots.entryCount)
                    } else {
                        stringResource(Res.string.song_details_song_position, page + 1, pageCount)
                    },
```

with, at the bottom of the file:

```kotlin
/** Where each page of a setlist's pager sits in the setlist, see [SongPagerControls]. */
private class SetlistSlots(
    val slotByPage: List<Int>,
    val entryCount: Int,
)
```

The previous / next buttons keep deciding by pages (`targetPage`, `pageCount`): stepping skips a missing entry, which is
the only thing it can do. The same song appearing twice in one setlist is not something the app creates
(`addSongToSetlist` and the picker refuse duplicates); `indexOfFirst` would give both pages the first slot.

## Tests

- **No unit test is possible** (UI, untested by policy).
- Compile check: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs`

## Verification

1. Desktop (`./gradlew :app:desktop:run`). Make a setlist of four songs A, B, C, D. Quit, move B's `.cho` out of
   `library/songs/`, start again. The Setlists screen shows 1 A, 2 (missing), 3 C, 4 D.
2. Open C.
   - **Before:** "Setlist · 2 / 3". **After:** "Setlist · 3 / 4".
3. Next → D: "4 / 4", and Next is disabled. Previous twice → A: "1 / 4" (B is skipped, as before).
4. A setlist with nothing missing: unchanged numbers ("2 / 4" on the second song).
5. Delete the setlist file while its song is open (with the window in the background; the resume rescans): the bar
   loses the title and counts pages again ("2 / 3"), rather than showing a slot of a setlist that is gone.

## Docs

- `presentation/CLAUDE.md`, the `ui/CampfireViewModel.kt` bullet says each entry "carries the place it has in its
  setlist, the missing files included, so the numbers a setlist is read by are its own order". Add to the
  `screens/songDetails/SongKeyboardShortcuts.kt` bullet (the one about the pager bar's paging) or after it: "The pager
  bar numbers a song by its place in the setlist and counts the setlist's entries, missing files included, as the
  setlist's rows do; the pages themselves are the songs that are there, so stepping skips a missing entry."

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`
- `presentation/CLAUDE.md`

## Depends on

After 29 in lane D (both edit `SongDetailsScreen.kt`, different blocks). Plan 17 (lane B) may introduce a case / NFC
folded lookup of setlist entries against the library; if it does, the `entry.songFileName == song.fileName` here must
use the same resolver, or a folded match counts as "cannot be placed" and the bar falls back to page numbers.
