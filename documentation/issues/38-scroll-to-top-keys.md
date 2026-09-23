# 38 — Some filter and order changes do not scroll the lists back to the top

**Severity:** inconsistent behaviour (all platforms) · **Area:** `:presentation` (`screens/songs/SongsScreen.kt`,
`screens/setlists/SetlistsScreen.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

## What the user sees

`presentation/CLAUDE.md` promises: "The two lists scroll back to the top through `ScrollToTopWhenChanged` whenever
their search (the query that is applied, so closing a search counts), sorting or filters change". Most of them do.
These do not:

- **Songs:** scrolled down the library, the sort and filter controls → "Show songs without chords" switched on or off.
  The list gains or loses every chordless song, and stays wherever the scroll position lands in the new contents.
- **Setlists:** scrolled down, the controls → a different order, or "Show archived" switched. The setlists reorder
  (or the archived ones appear at the end) under a list left in the middle.

## Cause

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt:252-256`:

```kotlin
    ScrollToTopWhenChanged(
        listState = listState,
        key = "$query|${userPreferences?.sortingMode?.name}|${songFilter.selectedTags.sorted()}|${userPreferences?.tagMatchMode?.name}|${songFilter.selectedLanguages.sorted()}|${userPreferences?.languageMatchMode?.name}",
        contents = songGroups,
    )
```

`shouldShowSongsWithoutChords` is a filter (`GetScreenDataUseCaseImpl.kt:164`,
`if (songListPreferences.shouldShowSongsWithoutChords) this else filter { it.hasChords }`) and a row of the same
controls (`Controls.kt:241`), but it is not in the key.

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt:259-264`:

```kotlin
    ScrollToTopWhenChanged(
        listState = listState,
        // A closed search narrows nothing whatever its field still holds, as on the songs screen.
        key = if (isSearchOpen) viewModel.setlistsSearch.textFieldState.text.toString() else "",
        contents = setlistsWithSongs,
    )
```

Only the search. The screen's two controls (`Controls.kt:197`, `:203`) — `setlistSortingMode` and
`shouldShowArchivedSetlists` — are the setlists' "sorting" and "filters".

## The change

Invoke the **`code-style`** skill before the first edit.

`SongsScreen.kt:254`, add the switch to the key:

```kotlin
        key = "$query|${userPreferences?.sortingMode?.name}|${userPreferences?.shouldShowSongsWithoutChords}|${songFilter.selectedTags.sorted()}|${userPreferences?.tagMatchMode?.name}|${songFilter.selectedLanguages.sorted()}|${userPreferences?.languageMatchMode?.name}",
```

`SetlistsScreen.kt:259-264` (`userPreferences` is already collected in `SetlistList`, `:197`):

```kotlin
    ScrollToTopWhenChanged(
        listState = listState,
        // A closed search narrows nothing whatever its field still holds, as on the songs screen. The order and the
        // archived setlists are the rest of what the screen's controls change about the list.
        key = "${if (isSearchOpen) viewModel.setlistsSearch.textFieldState.text.toString() else ""}|${userPreferences?.setlistSortingMode?.name}|${userPreferences?.shouldShowArchivedSetlists}",
        contents = setlistsWithSongs,
    )
```

The key is compared as a string and saved with `rememberSaveable` (`ListItemAnimation.kt:77`), so a longer string is
all this costs. The preference arrives a moment before the list it reorders; that is what `ScrollToTopWhenChanged`
already handles for the sorting mode (it holds the top by index across the contents that arrive afterwards,
`ListItemAnimation.kt:81-93`).

Nothing is changed for the pickers' lists (their own filters are sheet state that starts empty).

## Tests

- **No unit test is possible** (UI, untested by policy).
- Compile check: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs`

## Verification

Any platform; desktop is quickest (`./gradlew :app:desktop:run`), with a library of a hundred or so songs (the
fixtures) including some without chords, and a dozen setlists, a few archived.

1. Songs: scroll well down, open sort and filter, toggle "Show songs without chords".
   - **Before:** the list stays scrolled. **After:** it goes to the top, and the rows that stay do not jump before
     it does (no flash of the old position).
2. Setlists: scroll well down, change the order → top. Toggle "Show archived" → top.
3. Regressions: a search typed, cleared or closed still scrolls to top; a tag chip still does; tapping a song and
   coming back keeps the scroll position (the key has not changed).
4. Rotate on Android with a changed filter: the list does not scroll to top again (the key is saved).

## Docs

None: `presentation/CLAUDE.md` already says what this makes true.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongsScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`

## Depends on

Nothing. Plan 40 edits a comment in `SetlistsScreen.kt` (`:525-526`); different lines, either order.
