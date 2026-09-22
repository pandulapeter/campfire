# 08 · With no songs in the library the Setlists tab hides every setlist behind "Your library is empty", buttons and all

**Severity:** wrong behaviour / stuck state (all platforms; likely: a fresh installation whose two demo songs are
deleted is enough, and a setlist created there vanishes the moment it is made) · **Area:** `:presentation`
(`screens/setlists/SetlistsScreen.kt`: `SetlistList`; `CampfireViewModel.kt`: `libraryPlaceholder`;
`components/ListItems.kt`: `ListPlaceholder`)

**Decided (by the user, 2026-09-22): A.** The question was what the Setlists tab shows while setlists exist and the library holds no songs. Options:
- **A (recommended)** — the setlists, always. The library's empty state is the Songs tab's business; this tab shows it
  nowhere. Empty setlists keep their "Add songs" row, whose picker says the library is empty. Smallest change, and
  every setlist stays reachable (Edit, Archive, Export, Delete).
- **B** — the setlists, with the library's "Your library is empty" state as one extra item above them, carrying its
  three buttons (Create a song, Import files, Add demo songs) as it does on the Songs tab. More UI, keeps the
  invitation to fill the library where the user is looking.
- **C** — keep hiding the setlists, but give the empty state on this tab its buttons and take the "New" menu out of the
  bar while it is up. The setlists stay unreachable until a song exists, which is the part this plan is about, so it
  only fixes the dead end, not the disappearance.

The Fix below is written for **A**; B and C are sketched at its end.

## Symptom
1. Fresh installation (the two demo songs and the demo setlist are planted). Songs tab → delete both demo songs.
   Deleting a song takes it out of every setlist (`DeleteSongUseCaseImpl`) but leaves the setlist, now empty.
2. Setlists tab.

The screen says "Your library is empty — Create a song, import .cho files and zip archives, or add the demo songs."
with **no buttons under it**; the demo setlist is not shown, so it cannot be edited, archived, exported or deleted
from anywhere in the app. The app bar meanwhile still has its "New" (+) menu, which the empty states otherwise take
away.

Worse, from there: + → "Create setlist" → name it → Create. The setlist is written and the screen goes on saying the
library is empty; the setlist the user just made is nowhere. The same with an import of a lone `.setlist.json`, or a
sync run that brings setlists down to a device whose library is empty, or one that deletes every song but not the
setlists.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt:263-268`:

```kotlin
// The setlists come first: they are what this screen is about. The library only speaks up once there are
// setlists to fill, since without it the rows of every setlist would be missing rather than the setlists
// themselves. ...
val placeholder = setlistsPlaceholder ?: libraryPlaceholder
```

`setlistsPlaceholder` (`CampfireViewModel.kt:589-596`) is null as soon as there is a setlist to list, and
`libraryPlaceholder` (`:534-536`) is `NO_SONGS` whenever `unfilteredSongs` is empty, so in exactly the state "setlists
exist, songs do not" the placeholder branch wins and `setlistsWithSongs.forEach` is never reached. The placeholder is
drawn by `ListPlaceholder` (`components/ListItems.kt:670-681`), whose `NO_SONGS` buttons exist only when `onNewSong`
is passed, and `SetlistList` passes `onNewSetlist` and `onImport` but never `onNewSong` — so the state has no buttons.
The "New" menu's visibility is decided from `setlistsPlaceholder` alone (`SetlistsScreen.kt:137`), which is null, so
the menu stays; a setlist made through it lands in the hidden list.

The precedence dates from 58c3e40d (and the order before it was the same); the comment's reasoning — that without it
"the rows of every setlist would be missing" — does not hold since deleting a song removes it from every setlist: the
setlists are simply empty, and an empty setlist already has its own row offering to fill it.

## Fix (option A)
1. `SetlistsScreen.kt`, `SetlistList`: drop `libraryPlaceholder` (the `val` at `:200` and its use at `:268`), so
   `val placeholder = setlistsPlaceholder`. Rewrite the comment above it:

   ```kotlin
   // The setlists are what this screen is about, and they are listed whenever there are any - an empty library
   // included, where they are simply empty and each offers to be filled. Showing the library's own empty state in
   // their place hid every setlist, and a new one vanished as it was made. Every state goes through the same slot,
   // so that "still loading" turning out to be "you have no setlists" cross fades instead of being swapped in a
   // single frame.
   ```

   The loading indicator and the error are unaffected: `setlistsPlaceholder` is `LOADING`/`ERROR` in the same
   situations `libraryPlaceholder` was, since both come from `emptyPlaceholder` over the same `screenData`.
2. `CampfireViewModel.kt`: delete `libraryPlaceholder` (`:533-536`, KDoc included); `SetlistList` was its only reader
   (check with a grep before deleting).
3. `components/ListItems.kt`, `ListPlaceholder`'s KDoc for `onNewSetlist` (`:634-636`) says "the setlists screen shows
   the empty library's state as well"; replace the reason with: "It is a parameter of its own rather than one "create"
   for whichever list is empty, because each list creates a different thing."
4. `dialogs/Dialogs.kt`, `SongPicker`: an empty library leaves the sheet an empty list under a search field. Pass the
   library's empty title as the list's text in that case:

   ```kotlin
   noResultsText = when {
       // Reached from an empty setlist's "Add songs" in an empty library, which the setlists screen lists as well.
       songs.isEmpty() -> stringResource(Res.string.songs_empty_title)
       matches.isEmpty() && query.isNotBlank() -> stringResource(Res.string.songs_no_search_results)
       else -> null
   },
   ```

   (`songs_empty_title`, "Your library is empty", exists in both `strings.xml` files; import its `Res.string`
   accessor in `Dialogs.kt` if it is not there.)

Nothing changes on the Songs tab, and nothing in the data layer: a setlist in an empty library is an ordinary empty
setlist.

Do **not** make `setlistsPlaceholder` itself return `NO_SONGS` — that is the same hiding moved one layer down.

### If option B is chosen instead
Keep step 2–4 of A, and in `SetlistList` add, before `setlistsWithSongs.forEach`, one full-span item keyed
`"empty_library"` while `screenData` has no songs (read a `Boolean` state the view model derives the way
`libraryPlaceholder` did), drawing `ListPlaceholder(placeholder = NO_SONGS, onNewSong = …, onImport = …, onDemoLibrary
= …)` with the same three callbacks `SongsScreen.kt:296-304` passes (null in performance mode). It animates in and out
with `listItemAnimation` like every other row.

### If option C is chosen instead
Keep the precedence; pass `onNewSong`/`onDemoLibrary` to the setlists screen's `ListPlaceholder` like the Songs tab
does, and decide the "New" menu (`SetlistsScreen.kt:137`) from `setlistsPlaceholder ?: libraryPlaceholder` so it
leaves the bar with the empty state. Correct the comment's reasoning (setlists are empty, not full of missing rows).

## Tests
None (UI is untested).

## Verify
1. Fresh installation (or Settings → add the demo songs to an empty library), delete both demo songs, open Setlists.
   Before: "Your library is empty" with no buttons, the demo setlist gone. After: the demo setlist is listed with its
   header menu and an "Add songs" row; its menu's Delete removes it, and the tab then shows "No setlists yet" with its
   two buttons.
2. In that empty library: + → Create setlist → name it: it appears. "Add songs" on it opens the picker, which says
   "Your library is empty".
3. Desktop: import only a `.setlist.json` into an empty library: the setlist is listed, its rows are "missing" entries
   that can be removed from the setlist.
4. Launch with a large library: the Setlists tab still shows the loading indicator until the read finishes, and the
   error state (make the library unreadable on the desktop) still appears as before.
5. Songs tab with an empty library: unchanged (its own empty state with three buttons).
6. Compile checks: `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, the `ui/screens/` bullet: after "The menu leaves the bar while that empty state, the loading
indicator or the error is up (`allowsNewItemMenu`), since each of those offers its own buttons." add "The setlists
screen lists its setlists whenever there are any, an empty library included: they are empty there rather than
missing, and each offers to be filled, while the library's own empty state belongs to the songs screen." (For B or C,
say instead what was chosen.)

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `presentation/CLAUDE.md`

## Depends on
The decision above. Otherwise nothing: 11 and 35 edit other parts of `SetlistsScreen.kt`, 07 another part of
`ListItems.kt`, 18 the pickers' search fields in `Dialogs.kt` (the `PickerList` call in `SongPicker` is next to the
field 18 changes); schedule edits to the same file one after another.
