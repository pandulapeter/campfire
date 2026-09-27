<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 24 — Keep only the song texts the screens can reach, and re-read invalidated ones in one update

| | |
|---|---|
| Lane | D |
| Impact | medium (every rescan, sync run and resume rescan re-reads the texts); low for memory on its own |
| Confidence | high |
| Platforms | all |
| Files | presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt, presentation/CLAUDE.md |
| Depends on / conflicts with | 23 (same file; land 23 first). Also 02 and 18 in `CampfireViewModel.kt`, in other regions. |
| Commit message | `Keep only the song texts the back stack names, and read invalidated ones back in one update.` |

## Problem
`_songTexts` (`CampfireViewModel.kt:467`) is only ever added to:
- `loadSongContent` (`:1500-1509`) adds each song as it is opened.
- `SongDetailsScreen` prefetches the pages either side of the current one, and adds those too.
- Only `deleteSong` and a vanished file ever remove an entry.

So paging through a 100-song setlist leaves 100 texts in memory for the rest of the process.

Every invalidation walks all of them. The collector at `:884-898`:
```kotlin
getSongContentInvalidations().collect { fileName ->
    val affected = _songTexts.value.keys.filter { fileName == null || it == fileName }
    affected.forEach { name ->
        val content = getSongContent(name)
        _songTexts.update { if (content == null) it - name else it + (name to content.text) }
        ...
```
- `fileName == null` comes from every rescan (`SongRepositoryImpl.rescan()` → `invalidate()`). That is the end of every sync run, and every resume rescan on desktop and iOS (plan 23).
- Each such rescan reads every held text from disk sequentially.
- Each result copies the whole map (`it + …`), which makes the pass O(k²).
- Each changed text is its own `songTexts` emission, which recomposes SongDetailsScreen's root (`SongDetailsScreen.kt:138`) and SongEditorScreen (`:158`) once per file.

## Fix
1. **Prune in `updateBackStack`** (`:959-965`), next to the existing `songDetailsCurrentSongs.keys.retainAll(...)`. Keep a text if any of these holds:
   - its file is named by a `SongDetails` destination on the stack (all of its `songFileNames`, so a setlist's pages keep their prefetched texts);
   - it is the file of a `SongEditor` destination;
   - it is `_editorDraft.value?.fileName`.
   ```kotlin
   val reachable = buildSet {
       backStack.forEach { d ->
           when (d) { is SongDetails -> addAll(d.songFileNames); is SongEditor -> add(d.fileName); else -> Unit }
       }
       _editorDraft.value?.fileName?.let(::add)
   }
   _songTexts.update { texts -> if (texts.keys.all { it in reachable }) texts else texts.filterKeys { it in reachable } }
   ```
   Things that must hold:
   - **`hasUnsavedEditorChanges` compares against `songTexts[draft.fileName]`.** The editor's file and the draft's file must never be pruned, or an open editor would suddenly read as unsaved. The set above covers both.
   - **`recoverEditorDraft` puts the text in (`:1401`) before it pushes the editor (`:1405`).** The draft is already set by then (`:1404`), so the prune inside that `updateBackStack` keeps it.
   - **`updateSongFileName` rewrites the stack in place and calls `persistBackStack()` directly, not through `updateBackStack`.** So it neither prunes nor needs to.
   - **`editSongText` falls back to `getSongContent` when the text is not held (`:1304`).** Tag and language edits from a song-list row therefore still work for pruned songs.
2. **Re-read invalidated texts in one update.** In the collector:
   - Read all affected texts first. Sequentially is fine, since there are few after step 1. Up to 4 at once is also fine.
   - Then apply them in one `_songTexts.update`, and only to keys that are still present, so a text pruned meanwhile is not brought back.
   - Send `Message.EditedSongFileGone` after the update, as today.
   ```kotlin
   val results = affected.map { name -> name to getSongContent(name)?.text }
   _songTexts.update { texts ->
       results.fold(texts) { acc, (name, text) ->
           when { name !in acc -> acc; text == null -> acc - name; else -> acc + (name to text) }
       }
   }
   results.filter { (name, text) -> text == null && _editorDraft.value?.fileName == name }.forEach { sendMessage(Message.EditedSongFileGone) }
   ```
   Keep today's behaviour of not holding `songWriteMutex` here. A write that lands between the read and the update is already overwritten today, and this does not make that window wider. If you want to close it, take `songWriteMutex` around the read-and-apply.
3. Leave the texts' semantics alone. A song that is on screen still changes in place after a sync run, and an editor whose file changed underneath it still reads as unsaved.

## Verification
- `./gradlew :presentation:desktopTest` and `./gradlew :app:desktop:run`.
- Open a setlist of 30 songs and page through all of them, then go back to the list. With a temporary log in the collector, trigger a rescan (the desktop resume, or the end of a sync run): zero texts are re-read.
- Open one song, then run a sync that changes that song on the other side: the details page updates in place.
- Open the editor, type without saving, then trigger a rescan: the Save button stays enabled, the unsaved-changes question still appears on Back, and no "file gone" message appears.
- Update the `songTexts` sentence in `presentation/CLAUDE.md` (the `ui/CampfireViewModel.kt` paragraph: "Both are built on `songTexts`, the texts the view model holds, which follow the files…") to say the texts are kept only for the screens on the back stack. Also update the KDoc of `_songTexts` (`:463-466`).
