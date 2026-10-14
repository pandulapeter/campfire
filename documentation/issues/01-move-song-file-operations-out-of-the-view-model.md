<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Move the song file operations out of the view model, each holder following a rename or a deletion in its own state

**Kind:** structure · **Severity:** medium · **Platforms:** all
**Challenged:** amended — the "page forgotten" test re-pushes the same entry (a new `SongDetails` gets a new random
`id`, so the original test passed without any pruning); the rename test seeds the old text first; commit 1 names the
`closeScreensShowing` import; cross-plan notes for plans 04 and 13 added.
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/Navigator.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/SongFileChanges.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongFilesController.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongTextStore.kt` (KDoc only),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/NavigatorTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongFilesControllerTest.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`,
`.github/scripts/size_allowances.json`

## Problem

`CampfireViewModel` is meant to be a facade whose every public member is a one-line delegation (`ui/CLAUDE.md`), but
four song file intents carry real logic in it, and to do so it reaches into the private state of two holders.

- `createSong`, `showDuplicateSongDialog` / `duplicateSongDialogOf`, `duplicateSong`, `updateSongFileName` and
  `deleteSong` are written out in the view model (about 80 lines including KDoc), with their five use cases as
  `private val` constructor properties.
- To follow a rename, `updateSongFileName` writes four maps that belong to other holders, which is why those maps are
  public:

  ```kotlin
  if ((metronomeContext as? MetronomeContext.Song)?.songFileName == song.fileName) metronomeRenames[song.fileName] = fileName
  songTextStore.updateSongTexts { texts -> texts[song.fileName]?.let { texts - song.fileName + (fileName to it) } ?: texts }
  backStack.forEachIndexed { index, destination ->
      val renamed = destination.followingSongRename(from = song.fileName, to = fileName)
      if (renamed !== destination) backStack[index] = renamed
  }
  songDetailsCurrentSongs.entries.filter { it.value == song.fileName }.forEach { songDetailsCurrentSongs[it.key] = fileName }
  songDetailsTargetSongs.entries.filter { it.value == song.fileName }.forEach { songDetailsTargetSongs[it.key] = fileName }
  followReportedFileNames { if (it == song.fileName) fileName else it }
  persistBackStack()
  ```

  (`Navigator.songDetailsCurrentSongs`, `MetronomeController.songDetailsTargetSongs`, `songDetailsTargetTimings` and
  `metronomeRenames` are all public `val`s for this, and are exposed again as private getters in the view model.)
- A back stack listener in the view model prunes those same three maps:

  ```kotlin
  addOnBackStackChanged { _, stack ->
      stack.mapNotNullTo(mutableSetOf()) { (it as? CampfireDestination.SongDetails)?.id }.let { ids ->
          songDetailsCurrentSongs.keys.retainAll(ids)
          songDetailsTargetSongs.keys.retainAll(ids)
          songDetailsTargetTimings.keys.retainAll(ids)
      }
  }
  ```
- `deleteSong` pops the screens showing the file with a loop over `backStack` (`while (backStack.lastOrNull().isShowingSong(fileName)) popBackStack()`).

None of this is testable where it is (the view model has no tests and cannot be built in one), although the rename's
ordering around `songsBeingRenamed` is load-bearing and has broken before (see the `songsBeingRenamed` KDoc in
`SongTextStore.kt`).

## Fix

Four commits, each compiling and each behaviour-preserving. Run `python3 .github/scripts/check_sizes.py --update` in
every commit that shrinks `CampfireViewModel.kt` (its allowance is 1707 lines and an allowance larger than the file
fails the check).

### Commit 1 — "Let the navigator follow a renamed or deleted song itself."

In `Navigator.kt`:

- Add, beside `onSongDetailsPageSettled`:

  ```kotlin
  /**
   * Rewrites every screen open on the song file [from] to name [to] instead, in place: an entry is rewritten rather
   * than popped (see `followingSongRename`), and its pager stays on the song, so this goes past [updateBackStack] -
   * nothing has been navigated, and no listener may take it for a navigation. Saved at once, like any other change.
   */
  fun followSongRename(from: String, to: String) {
      backStack.forEachIndexed { index, destination ->
          val renamed = destination.followingSongRename(from = from, to = to)
          if (renamed !== destination) backStack[index] = renamed
      }
      songDetailsCurrentSongs.entries.filter { it.value == from }.forEach { songDetailsCurrentSongs[it.key] = to }
      persistBackStack()
  }
  ```

  Keep the direct `backStack[index] = renamed` writes: going through `updateBackStack` would run every listener (the
  metronome stop among them) and bump `navigationGeneration`, which the current code does not do.
- Make `songDetailsCurrentSongs` `private`, and prune it inside `updateBackStack` right after `backStack.update()`
  (before `lastTool` and the listeners):

  ```kotlin
  backStack.songDetailsIds().let { ids -> songDetailsCurrentSongs.keys.retainAll(ids) }
  ```

  with a private `List<CampfireDestination>.songDetailsIds()` helper. Pruning before the listeners rather than after
  the fifth of them is unobservable: no listener before the view model's pruning one reads the map (they read only
  `previousTop`/`stack`, the reorder and selection modes, the tuner's `contentKey` and the editor's presence), and
  `currentSongFileName` is only ever asked about destinations on the stack. `followSongRename` needs no pruning: a
  rename keeps every entry's `id`.

In `navigation/SongFileChanges.kt`, next to `isShowingSong`, add the deletion half as an extension, since it only needs
the navigator's public members (and keeps `Navigator.kt`, at 457 lines, well under 500):

```kotlin
/**
 * Closes the screens showing the song file [fileName] that has just gone, top first: the editor goes before the details
 * screen underneath it, so both are checked rather than only the top one.
 */
internal fun Navigator.closeScreensShowing(fileName: String) {
    while (backStack.lastOrNull().isShowingSong(fileName)) popBackStack()
}
```

In `CampfireViewModel.kt`, `updateSongFileName` calls `navigator.followSongRename(from = song.fileName, to = fileName)`
in place of the `backStack.forEachIndexed` block and the `songDetailsCurrentSongs` line, and drops its trailing
`persistBackStack()` (now inside `followSongRename`; see "Ordering" below for why its moving ahead of
`followReportedFileNames` is unobservable). `deleteSong` calls `navigator.closeScreensShowing(fileName)` in place of its
`while` loop (import `navigation.closeScreensShowing`, an extension). Delete the view model's `songDetailsCurrentSongs`
getter, `persistBackStack()` and the now unused imports (`followingSongRename`, `isShowingSong`). The pruning listener keeps its two metronome lines for now.

### Commit 2 — "Let the metronome follow a renamed song and the closed song screens itself."

In `MetronomeController.kt`:

- Make `songDetailsTargetSongs`, `songDetailsTargetTimings` and `metronomeRenames` `private`.
- Add:

  ```kotlin
  /**
   * Follows the song file [from] being renamed to [to]: where the click plays for that song, the move to the new name is
   * remembered as the same song ([metronomeRenames]) so the bar goes on, and the pages headed for it name the new file.
   * Called before the back stack is rewritten, since [metronomeContext] is read from it and must still name [from].
   */
  fun followSongRename(from: String, to: String) {
      if ((metronomeContext as? MetronomeContext.Song)?.songFileName == from) metronomeRenames[from] = to
      songDetailsTargetSongs.entries.filter { it.value == from }.forEach { songDetailsTargetSongs[it.key] = to }
  }

  /** Lets go of the pages of the song details screens that are no longer on the back stack. */
  fun onBackStackChanged(stack: List<CampfireDestination>) {
      val ids = stack.mapNotNullTo(mutableSetOf()) { (it as? CampfireDestination.SongDetails)?.id }
      songDetailsTargetSongs.keys.retainAll(ids)
      songDetailsTargetTimings.keys.retainAll(ids)
  }
  ```
- Fix the `metronomeRenames` KDoc's `[updateSongFileName]` link (it points at the view model, and after commit 3 at
  `SongFilesController.updateSongFileName`; write it as plain backticks).

In `CampfireViewModel.kt`, the pruning listener becomes `addOnBackStackChanged { _, stack -> metronomeController.onBackStackChanged(stack) }`
**at the same position** in the `navigator.apply { … }` block (after `editorSession.onBackStackChanged()`, before
`importController.onBackStackChanged()`); `updateSongFileName` calls `metronomeController.followSongRename(from = song.fileName, to = fileName)`
where the `metronomeRenames` line was, and drops its `songDetailsTargetSongs` line. Delete the view model's
`songDetailsTargetSongs`, `songDetailsTargetTimings` and `metronomeRenames` getters and the `MetronomeContext` import if
unused.

### Commit 3 — "Move creating, duplicating, renaming, and deleting a song file into a holder of its own."

New `state/SongFilesController.kt`, `internal class SongFilesController`, built in the view model like the other holders
(not a Koin definition), with narrow lambdas for the holders it calls so it can be tested without them:

```kotlin
internal class SongFilesController(
    private val messageSink: MessageSink,
    private val songTextStore: SongTextStore,
    private val showDialog: (DialogType) -> Unit,
    private val duplicateSongDialog: (song: Song, text: String) -> DialogType,   // songMetadataEditing::duplicateSongDialog
    private val openEditor: (fileName: String, shouldStartInsideFirstSection: Boolean) -> Unit,
    private val clearEditorDraft: () -> Unit,                                     // editorSession::clearEditorDraft
    private val followMetronomeRename: (from: String, to: String) -> Unit,        // metronomeController::followSongRename
    private val followScreensRename: (from: String, to: String) -> Unit,          // navigator::followSongRename
    private val closeScreensShowing: (fileName: String) -> Unit,                  // navigator.closeScreensShowing(it)
    private val followReportedFileNames: ((String) -> String?) -> Unit,           // importController::followReportedFileNames
    private val getSongContent: GetSongContentUseCase,
    private val createSongFile: CreateSongUseCase,
    private val duplicateSongFile: DuplicateSongUseCase,
    private val renameSongFile: RenameSongFileUseCase,
    private val deleteSongFile: DeleteSongUseCase,
)
```

It takes over, with their KDoc, `createSong`, `showDuplicateSongDialog`, `duplicateSongDialogOf` (now `internal suspend
fun`, since `restoreVisibleDialog` / plan 02's restorer calls it), `duplicateSong`, `updateSongFileName` and
`deleteSong`, each launched through `messageSink.launchLibraryChange` as now. Do not give the constructor defaulted
parameters (it is not a Koin class, but keep the house habit).

**Ordering — keep exactly this sequence in `updateSongFileName`**, and copy the comments that explain it:

1. `songTextStore.updateSongsBeingRenamed { it + (song.fileName to song) }` — before the file moves, so that the moment
   the library drops the old name is already covered (`DialogHost.startClosingWithSong`, the details screen and the
   metronome all resolve the old name through it).
2. `try { val rename = renameSongFile(song) ?: return@launchLibraryChange` — the one suspension; the file, the setlists
   and the transposition move here.
3. `followMetronomeRename(from, to)` — **before step 5**: it asks `metronomeContext`, which is read from the back stack
   and the target pages, whether the click plays for the old name; asked after the rewrite it would name the new file
   and the rename would be taken for a different song (the bar restarts).
4. `songTextStore.updateSongTexts { rekey from → to }`.
5. `followScreensRename(from, to)` — back stack entries rewritten in place, current pages, saved state.
6. `followReportedFileNames { if (it == from) to else it }`.
7. `if (!rename.haveReferencesFollowed) messageSink.sendMessage(Message.SongFileRenamedPartly)` — said after the screens
   have followed.
8. `} finally { songTextStore.updateSongsBeingRenamed { it - song.fileName } }` — only after step 5, and on every path
   (nothing to do, an exception, cancellation).

Two statements change place relative to today, both unobservable: the target page rewrite now happens in step 3
instead of after the back stack rewrite, and `persistBackStack()` in step 5 instead of after step 6. Steps 3–7 run on
the main thread without a suspension point; snapshot state written there reaches `snapshotFlow` collectors (the
metronome's pattern follower) and recomposition only at the next apply notification, after the block; none of the
flows the metronome follower combines (`tempos`, settings, beat levels, `songsByFileName`, `songsBeingRenamed`) is
written in steps 3–7; and the saved state handle is read by nobody in between.

`deleteSong` keeps its order too: `deleteSongFile(fileName)` (suspends) → `clearEditorDraft()` (nobody is asked to save
a file that has just been deleted, so the draft goes before the screens) → `closeScreensShowing(fileName)` →
`songTextStore.updateSongTexts { it - fileName }` → `followReportedFileNames { it.takeUnless { it == fileName } }` →
`SongDeletedPartly` if the references were not all removed.

In `CampfireViewModel.kt`: build `songFilesController` after `importController` (it needs `editorSession`,
`metronomeController`, `navigator`, `songMetadataEditing` and `importController`, which are all declared by then; it
starts nothing, so its position changes no behaviour). The five public members become one-line delegations with the
same signatures (`createSong`, `showDuplicateSongDialog`, `duplicateSong`, `updateSongFileName`, `deleteSong`);
`reopenSavedDialog` calls `songFilesController.duplicateSongDialogOf(…)`. The constructor parameters `createSong`,
`duplicateSong`, `renameSongFile` and `deleteSong` lose `private val` (they are only passed on); `getSongContent` stays a
`private val`, since `editorSession`, `songTextStore` and `exportController` get it too.

Plan 04, if it lands after this one, re-points the `followReportedFileNames` lambda at `importReportState`; plan 13
moves `SongFilesController.kt` **and `SongFilesControllerTest.kt`** to `state/songs`, which then imports `FakeSongFiles`
from `state/songText` (it is `internal`, so an import is all it takes).

Update the KDoc links that named the view model: `SongTextStore.songsBeingRenamed`'s `[updateSongFileName]` →
`` `SongFilesController.updateSongFileName` `` (it is in another class, so backticks, or a fully qualified link).

### Commit 4 — docs

In `ui/CLAUDE.md`'s **Renames** bullet, say the rename lives in `state/SongFilesController.kt`, that the metronome and
the navigator follow it themselves (`MetronomeController.followSongRename`, `Navigator.followSongRename`), and keep the
existing sentence about the order. Grep the repository (`CLAUDE.md` files, `.claude/skills`, `documentation/`) for
`updateSongFileName`, `songDetailsCurrentSongs`, `songDetailsTargetSongs`, `metronomeRenames` and
`CampfireViewModel.deleteSong` and update what names the old place. `ui/CLAUDE.md` is over its budget (allowance 2,944
words): this edit must not add words; run `check_sizes.py --update` if it shrinks.

## Tests

- `NavigatorTest`:
  - `a renamed song's screens are rewritten in place on the page they were on`: push a `SongDetails` over
    `["a.cho", "b.cho"]`, settle it on `"b.cho"` (`onSongDetailsPageSettled`), register a counting listener, call
    `followSongRename("b.cho", "c.cho")`; the entry keeps its `id`, names `["a.cho", "c.cho"]`,
    `currentSongFileName(entry)` is `"c.cho"`, the listener was not called and `navigationGeneration` did not change.
  - `a renamed song open in the editor is rewritten too`: a `SongEditor("b.cho")` becomes `SongEditor("c.cho")`.
  - `deleting a song closes the editor and the details screen under it, and nothing else`: stack
    `[Songs, SongDetails(["x.cho"]), SongEditor("x.cho")]`, `closeScreensShowing("x.cho")` leaves `[Songs]`; a details
    screen over another song is left alone.
  - `a page of a screen that left the stack is forgotten`: settle a pager on its second song, pop it, push **the same
    destination instance** back (`updateBackStack { add(entry) }`, the web's Forward does the same with a restored
    entry); `currentSongFileName(entry)` falls back to its `initialIndex`. (A new `SongDetails` would not test anything:
    its `id` is a fresh `Uuid.random()`, so it never finds the old page, pruned or not.)
- New `state/SongFilesControllerTest.kt` (over `FakeSongFiles.songTextStore(...)`, a `MessageSink(backgroundScope)` and
  fake use cases as anonymous objects, like `ImportControllerQueueTest`):
  - `a song is held as being renamed from before the file moves until the screens have followed`: seed the old name's
    text first (`songTextStore.updateSongTexts { mapOf("old.cho" to "{title: Old}") }`), so the rekeying has something
    to move; the fake
    `RenameSongFileUseCase` records `songTextStore.songsBeingRenamed.value` when invoked (must hold the song); the
    lambdas append to one `calls` list; afterwards `calls == listOf("metronome", "screens", "reported")`, the text is
    under the new name by the time `"screens"` is appended (record `songTexts.value.keys` in that lambda), and
    `songsBeingRenamed` is empty.
  - `a rename that moves nothing or fails lets go of the song and follows nothing`: the fake returns `null`, then
    throws; `songsBeingRenamed` is empty, `calls` is empty, and the failure is `Message.OperationFailed`.
  - `a rename whose references did not all follow says so after the screens followed`: `haveReferencesFollowed = false`
    → the last message is `Message.SongFileRenamedPartly`.
  - `a deleted song lets go of the draft before the screens close`: `calls == listOf("draft", "close", "reported")` and
    the text is gone from `songTexts`.
- `MetronomeController.followSongRename` gets no test of its own: building the controller needs a `Metronome` engine
  fake and the rest of its wiring; the ordering test above pins when it is called, and its body is two lines.

Then run the root CLAUDE.md test command and `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`,
`:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, `spotlessCheck`, and the size check.

## Manual check

On the desktop app: open a song from a setlist, page to a song whose file name differs from its header, start the
click, and use **Update file name** from its menu: the page stays on the song, the click keeps its bar (no restart),
Back returns to the setlist, and the setlist still opens the song. Then delete the song from the details screen's menu:
the screen closes onto the setlist, which no longer lists the song.
