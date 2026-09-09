# Step 07: library management UI

**Goal:** users can create and delete songs, setlists work on the file-based model, and the Settings screen has a
"Library" section. Import/export buttons appear here but stay disabled with a "coming in step 08" TODO until step 08.

**Depends on:** 05 (06 recommended first, so the viewer already shows real content).

## 1. Songs screen

- **Empty state** (`songs_no_data`): replace the hint with two actions under the text: "New song" and "Import"
  (the latter disabled until step 08, `// TODO(step 08)`). Strings: `songs_empty_title` "Your library is empty",
  `songs_empty_hint` "Create a song or import .cho files and zip archives.", `songs_new_song` "New song",
  `songs_import` "Import". Hungarian: "A könyvtárad üres", "Hozz létre egy dalt, vagy importálj .cho fájlokat és zip
  archívumokat.", "Új dal", "Importálás".
- **Floating action button** "New song" (extended FAB on wide layouts, icon-only on phones, hidden while the
  search field is focused) at the bottom end of the Songs screen. Tapping it shows `DialogType.NewSong`: a dialog with
  Title (required) and Artist (optional) fields; "Create" calls `CreateSongUseCase` then navigates to the editor
  (`// TODO(step 09)`: until the editor exists, open the song details instead).
- **List item overflow / long press**: the song item gets a context menu (`DropdownMenu` on desktop, long-press
  bottom sheet on touch: a `DialogType.SongActions(fileName)` reusing the existing bottom sheet mechanism) with
  "Edit" (`// TODO(step 09)`), "Add to setlist" (existing flow), "Export" (`// TODO(step 08)`), "Delete".
- **Delete** shows `DialogType.DeleteSong(song)`: "Delete "%1$s"? The file is removed from the library and from every
  setlist." → `DeleteSongUseCase`. Hungarian: "Törlöd a(z) "%1$s" dalt? A fájl törlődik a könyvtárból és minden
  setlistből."
- Search continues to match title and artist (normalised). Sorting/filters unchanged (the "show songs without chords"
  filter now uses `Song.hasChords` computed from the file).
- The **refresh action** is relabelled "Rescan library" (`songs_rescan`; hu "Könyvtár újraolvasása") and calls
  `LoadScreenDataUseCase(isRescan = true)`.

## 2. Song details screen

Add an "Edit" action to the top app bar (`song_details_edit`, hu "Szerkesztés") that navigates to the editor
(`// TODO(step 09)`: hidden until then) and a "Delete" entry in the display-options sheet's overflow, reusing
`DialogType.DeleteSong`. After deletion navigate back.

## 3. Setlists screen

Already compiled against the file-based model in step 05; finish it:

- Create: `SaveSetlistUseCase(Setlist(fileName = unique(setlistFileName(title)), title, priority = max + 1, entries = []))`.
- Rename: new "Rename" action in the setlist header menu → `DialogType.RenameSetlist(setlist)`; only the `title`
  changes, the file name stays (`setlists_rename` "Rename", `setlists_rename_title` "New title"; hu "Átnevezés",
  "Új cím").
- Delete: existing confirmation → `DeleteSetlistUseCase`.
- Add song / remove song / reorder: modify `entries` and save the whole setlist.
- A setlist entry whose file no longer exists (deleted outside the app, rescan found it missing) is shown greyed out
  with the file name as title and a "missing" subtitle (`setlists_missing_song` "File not found"; hu "A fájl nem
  található"); opening it is disabled; removing it works.
- Per-setlist transposition is stored on the entry (`Setlist.Entry.transposition`) and survives export/import.

## 4. Settings screen: "Library" section

Replace the deleted "Active databases" section with **Library** (`settings_library`, hu "Könyvtár") containing:

- A summary row: "%1$d songs · %2$d setlists" (`settings_library_summary`; hu "%1$d dal · %2$d setlist").
- "Import songs or zip archives…" (`settings_import`; hu "Dalok vagy zip archívumok importálása…") — disabled, `TODO(step 08)`.
- "Export library as zip" (`settings_export_all`; hu "Könyvtár exportálása zipként") — disabled, `TODO(step 08)`.
- "Rescan library" (`songs_rescan`).
- Platform hint row (`settings_library_location`, plain text, platform-specific via an `expect val libraryLocationHint: String?` in
  `presentation/.../platform/Platform.kt`): desktop shows the absolute path; iOS "Visible in the Files app under
  Campfire" (after step 10; until then `null`); Android and web `null` (row hidden). Hungarian for the iOS text:
  "A Fájlok alkalmazásban a Campfire mappa alatt található".

Keep the existing "Song display", "Theme", "Language" and "About" sections.

## 5. Error state

`GetScreenDataUseCase` reports `DataState.Failure` when the library cannot be read (OPFS unavailable, permission
problem). The Songs screen shows `error_no_data` / `error_no_data_hint` with a Retry that rescans; no snackbar.

## 6. Strings

Every string above goes into both `values/strings.xml` and `values-hu/strings.xml`. Remove `refresh` if nothing uses it.

## Verify

- Full build for all four platforms.
- Desktop + Android + web: create a song from the empty state (file appears in the library folder / OPFS), delete it
  (file gone, removed from setlists), create/rename/delete a setlist, add and reorder songs, per-setlist
  transposition persists across restart, "Rescan library" picks up a file copied in from outside, the Library
  summary counts are right.

## Execution notes

- **Two bugs the step's own features exposed, both fixed here.**
  - `UserPreferences` had *two* defaults that had to agree: `UserPreferencesDocument`'s field defaults (used when the
    document exists but omits a field) and a `defaultUserPreferences` constant in `UserPreferencesRepositoryImpl`
    (used when there is no document at all). They disagreed the moment one of them had to change, and the symptom
    was severe: a song created from the new dialog is chords-free, so with `shouldShowSongsWithoutChords = false` it
    was filtered out of `ScreenData.songs` the instant it was made - the list did not show it and the song details
    screen it navigated to was blank. `UserPreferencesLocalSource.loadUserPreferences()` now returns a non-null
    value (a missing or unreadable document both mean "the defaults"), the repository constant is gone, and the one
    remaining default lives next to the document. `shouldShowSongsWithoutChords` also flips to `true`: hiding songs
    without chords hides every song the user has just created.
  - A setlist entry pointing at a song the *filters* hide is not an entry whose file is missing, but both looked the
    same to the setlists screen (which resolved entries against the filtered `ScreenData.songs`), so the new
    "File not found" row would have appeared for perfectly present songs. `ScreenData` gained `songFileNames`, the
    unfiltered set, and `setlistsWithSongs` uses it to tell the two apart: hidden entries are left out as before,
    only genuinely missing files become `Entry.Missing`.
- **`Placeholder.ALL_SONGS_HIDDEN` was added** (strings `songs_all_hidden` / `_hint`) for the same reason: with
  "Your library is empty" and a "New song" button as the empty state, a library whose songs are all filtered out
  must not claim to be empty and offer to create a first song. `songsPlaceholder` and `libraryPlaceholder` decide
  emptiness from `songFileNames` rather than from the filtered list.
- **`songs_unknown_artist` was added.** The new song dialog makes the artist optional, which the old world never
  did, and an artist-less song produced an empty section header pill and an empty second line in its row. The
  header now falls back to "Unknown artist" and the row drops its supporting line entirely.
- **`settings_library_summary` reads "Songs: %1$d · Setlists: %2$d"** rather than the step's "%1$d songs · %2$d
  setlists": the string tables have no plurals, and "1 setlists" was on screen.
- **The song context menu has no "Edit" entry at all** rather than a disabled one. Section 2 says the song details
  "Edit" action stays hidden until step 09, so the list's menu does the same; "Export" *is* shown disabled, as the
  goal statement asks. Both carry `// TODO(step 09)` / `// TODO(step 08)` comments.
- **Delete lives in a song-actions overflow in the song details app bar**, not in the display options sheet's
  overflow as section 2 describes. That sheet only exists on windows too narrow for the inline steppers, so half of
  the window sizes would have had no way to delete the song they are showing. The same `SongActions` list backs the
  list's dropdown menu, the list's long-press sheet and this app bar button; it is rendered through an `item` lambda
  so each host can close itself before the action's dialog opens. "Add to setlist" is left out of the song details
  copy, since the bar next to it already offers it.
- **The setlist header pill holds two icon actions now** (rename, delete) instead of a menu. Step 08 adds "Export
  setlist" there, which is the point at which a menu starts to pay for itself.
- `SetlistWithSongs.songs` became a derived property over `entries`, and `openSongInSetlist` takes the `Song`
  instead of a row index: the rows now include entries the pager cannot open, so the index of a row and the index of
  a page are no longer the same number.
- **`SetlistRepository.rescan()` was added** and `LoadScreenDataUseCase(isRescan = true)` calls it. The action is
  called "Rescan library" now, and setlists are part of the library; step 08's import needs the same thing.
- `libraryLocationHint` is the `expect val` the step asks for, so the desktop actual re-derives the data directory
  the same way `FileStorage.desktop.kt` does (`:presentation` cannot depend on `:data:source:local:implementation`).
  Both sides carry a comment pointing at the other; it is the one piece of knowingly duplicated logic in this step.
- The songs list's fast scroller and the new floating action button share the bottom end corner. The button is
  drawn over the scroller's touch strip for its last ~70dp, which is the standard Material arrangement and was left
  as is.

### Verified

- All four platforms build; `:chordpro:desktopTest` (41) and `:data:source:local:implementation:desktopTest` (28)
  pass.
- **Desktop** (driven by a temporary `LaunchedEffect` in `app/desktop`'s `main`, removed afterwards; the file
  matches `HEAD`), starting from the three sample files:
  - "New song" dialog opens with the caret in the Title field and "Create" disabled until it has text; creating
    "Driver Test Song" writes `Driver Artist - Driver Test Song.cho`, opens it on the "This file has no content
    yet." state, and the song is in the list (with the "Lyrics only" marker) on the way back.
  - A setlist is created, renamed from the header pill's pencil, has two songs added and reordered, and one song
    transposed by +2; the `.setlist.json` on disk holds `"transposition": 2` for that entry only.
  - Deleting `legacy.cho` from outside the app and copying `outside.cho` in, then "Rescan library": the setlist row
    for the deleted file turns into a greyed "legacy.cho / File not found" and the copied file appears in the list
    under its own `{title}` / `{artist}`.
  - The long-press sheet lists "Add to setlist", a disabled "Export" and "Delete"; "Delete" opens the confirmation,
    which removes the file and takes it out of the setlist that held it.
  - Settings shows "Songs: 4 · Setlists: 1", the real library path under "Location", disabled Import/Export and a
    working "Rescan library".
  - Hiding songs without chords when every song is chord-free shows "Every song is hidden", not the empty library.
  - A song created without an artist gets an "Unknown artist" header and a single-line row.
  - An empty library shows "Your library is empty" with an enabled "New song" and a disabled "Import", and the
    floating action button is hidden behind it.
  - Restarting the app and opening the transposed song from its setlist: the key reads `Gb` (from `E`, +2) and the
    chords are transposed - the per-setlist transposition survived.
- **Android** (emulator, phone size): the button is the icon-only variant, the rows have no overflow button, a long
  press opens the actions sheet, "Delete" hands off from the sheet to the confirmation dialog, and focusing the
  search field hides the button while the keyboard is up.
- **Web** (wasmJs dev server, Chrome): the empty state and its two buttons render, creating a song writes
  `/library/songs/Web Song.cho` into the OPFS (verified through `navigator.storage.getDirectory()`) and deleting it
  removes it again; Settings shows the Library section without the "Location" row, and the whole section plus the
  delete confirmation (with its formatted title) read correctly in Hungarian.
