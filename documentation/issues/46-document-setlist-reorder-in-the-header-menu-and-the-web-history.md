# Document the setlist header menu's Reorder entry and the reorder mode's history entry, and the consent return's two entries

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all (menu), web (history)
**Files:** presentation/CLAUDE.md, CLAUDE.md, app/web/CLAUDE.md, presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt

**Challenged:** amended — also fix `BrowserRoutes.paths` KDoc and two more stale sentences in `presentation/CLAUDE.md:71` (what can come out of a setlist header, and the list of always-in-menu entries); added a merge note for the line plan 35 may touch.

## Problem

Three descriptions fell behind the code at 800ebde0b. No behaviour is wrong; an executor or reviewer reading the
docs is told something the code does not do.

1. **Setlist header menu.** `presentation/CLAUDE.md:71` describes `SetlistActions` as "the per-setlist actions at
   the end of its `SectionHeader` pill: edit, song assignments, duplicate, archive or unarchive, "Export setlist",
   delete - on a phone more than a pill has room for as buttons, so mostly behind its overflow button", which reads
   as if any of them can come out as a header button where there is room, and lists no Reorder. At HEAD
   `ui/components/SetlistActions.kt` (lines ~66-115) gives every entry `isAlwaysInMenu = true` except
   **Song assignments**, Edit details included (since b8d48dcec), and adds **Reorder songs** / **Done reordering**
   after Song assignments, also always in the menu:

   ```kotlin
   onReorder?.let {
       ActionsMenuItem(
           title = stringResource(if (isReordering) Res.string.setlists_done_reordering else Res.string.setlists_reorder),
           ...
           isAlwaysInMenu = true,
   ```

   The file's own KDoc (lines 40-42) is already right.

2. **Reorder mode's history entry.** `BrowserRoutes.paths` (`BrowserRoutes.kt:56-61`) adds one more `setlists` entry
   while a setlist is being reordered and the setlists search is closed:

   ```kotlin
   // A transient step with the same address gives browser Back a mode to dismiss before the tab.
   if (viewModel.isSetlistReordering && !viewModel.setlistsSearch.isOpen.value) add(SETLISTS)
   ```

   It is missing from the object's KDoc list of addresses (`BrowserRoutes.kt:22-40`, whose last bullet names only "a
   dialog, a sheet or a menu"), from `entryCount`'s KDoc (`BrowserRoutes.kt:85-90`, which says what is *not* part of
   `NavigationState` only for a dialog — the reorder mode is not part of it either, so a Forward to its entry is
   refused the same way), from the root `CLAUDE.md` Web bullet ("one history entry per step a back gesture would take —
   a dialog, a sheet or a menu open over a screen is one too", ~line 736) and from `presentation/CLAUDE.md`'s
   `wasmJsMain/ui/navigation/` bullet (~lines 27-31, "a screen of the back stack, an open search, or a dialog, a sheet
   or a menu open over them").

3. **Consent return.** `app/web/CLAUDE.md:209-212` says the Dropbox consent return opens "the Library tab of Settings,
   which is the screen the user pressed the button on and which gets its own history entry on top". Since 394f09861 a
   tab other than General is an entry on top of `settings/general` (`BrowserRoutes.kt:63-66`), so the return adds
   two entries on top of the songs: `settings/general` and `settings/library`.

## Fix

Docs and KDoc only; no code changes.

- `presentation/CLAUDE.md:71`: make the list "edit, song assignments, reorder songs (Done reordering while the mode is
  on, offered for an unarchived setlist of two songs or more), duplicate, archive or unarchive, "Export setlist",
  delete", and replace "on a phone more than a pill has room for as buttons, so mostly behind its overflow button" with
  wording that says Song assignments is the only one that becomes a header button where the header has room; every
  other entry always stays behind the overflow button.
- `presentation/CLAUDE.md:71`, two more sentences of the same line say the same stale thing in the `SongActions`
  part: "Archive, Duplicate, Export, Delete, Update file name and the editor's Revert are always menu entries" —
  add Edit details (a setlist's) and Reorder songs to that list; and "so what can come out is a setlist's Edit and
  Song assignments in its header" → "so what can come out is a setlist's Song assignments in its header". Line 71 is
  one very long line that lane C's plan 35 may also touch (its last paragraph asks to add the archived case to the
  song details menu description); merge the two at word level as EXECUTION.md says, keeping both edits.
- `BrowserRoutes.paths` KDoc ("one per step a back gesture would take, which is a screen of the back stack, an open
  search, or a dialog, a sheet or a menu open over them all"): add "the setlist reorder mode," after "an open
  search,".
- `BrowserRoutes.kt` object KDoc: add to the list, before the last bullet, "`setlists` again while a setlist's songs
  are being reordered with the search closed, so that Back ends the mode before it leaves the screen;". `entryCount`
  KDoc: change "A dialog is never part of [state]" to "A dialog and the setlist reorder mode are never part of
  [state]" (keep the rest of the sentence).
- Root `CLAUDE.md` Web bullet: "— a dialog, a sheet or a menu open over a screen is one too, and so is the setlist
  reorder mode, at the screen's address". While there, add the missing comma after `` `song/{song}/edit` `` in the
  same sentence.
- `presentation/CLAUDE.md` `wasmJsMain/ui/navigation/` bullet: add "the setlist reorder mode," to the list of steps
  ("a screen of the back stack, an open search, the setlist reorder mode, or a dialog, a sheet or a menu open over
  them").
- `app/web/CLAUDE.md:211`: "which gets its own history entry on top" → "which gets two history entries on top of the
  songs, `settings/general` and `settings/library`, since a Back from a tab other than General goes to General
  first".

Follow the code-style skill for the KDoc edit (no trailing-comma or header changes are involved).

## Tests

None: documentation only.

## Manual check

None needed beyond reading the edited sentences against `SetlistActions.kt` and `BrowserRoutes.paths`. Optionally,
on the web build: start reordering a setlist, press the browser's Back, and see the mode end with the Setlists screen
still showing; open the Library tab through a Dropbox consent return and press Back twice to reach General and then
the songs.
