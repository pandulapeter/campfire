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

_(filled in by the executing agent)_
