# 17 — A rename that only changes case never reaches other devices, but the setlists that follow it do

**Severity:** setlist entries shown as missing on every other device (sync; any platform pair) ·
**Area:** `:data:source:local:implementation` (`FileNames.kt` — recommended fix); alternatives would touch
`sync/SyncEngine.kt`, `SyncProvider`, `DropboxSyncProvider` and `:presentation`'s `CampfireViewModel.kt`

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced with two
devices. An existing engine test already documents the half of it the engine owns (see Cause). The "Verification"
section below is how to confirm the rest.

> **Work in progress at review time.** `SyncEngine.kt`, `SyncEngineTest.kt`, `FakeSyncProvider.kt`, `SyncProvider.kt`
> and `DropboxSyncProvider.kt` had uncommitted modifications by another agent when this plan was written. Quotes from
> those files are `git show HEAD:<path>`. The **recommended** change below touches none of them; the alternatives
> would, and would have to be written against whatever is committed by then.

Reviewer finding 3-sync#1, with the lookup aspect of 4-domain#2 (the import side of 4-domain#2 is lane C's plan 22).

## What the user sees

A song file named by an older version, `Hallelujah.cho`, with `{title: Hallelujah}` inside. Its menu offers
**Update file name**; on device A the user takes it. On A the file is now `hallelujah.cho` and every setlist naming
it was rewritten. A syncs.

- The rewritten setlists go up and reach device B.
- The file itself does not move anywhere: the cloud folder keeps `Hallelujah.cho` (Dropbox ignores case, and nothing
  asks it to change the spelling), and so does B.
- On B, every setlist that holds the song now shows it as **missing** — the entry says `hallelujah.cho`, the library
  has `Hallelujah.cho`. A new device connecting later downloads `Hallelujah.cho` too, with the same result.

## Cause

1. **The rename is offered.** `isNamed` (`FileNames.kt:126-132`) composes the file's name to NFC before comparing,
   precisely so that a name differing from the desired one only by Unicode form is *not* offered a rename — but it
   compares case exactly:

   ```kotlin
   internal fun String.isNamed(desired: String): Boolean {
       val extension = knownExtension()
       if (!extension.equals(desired.knownExtension(), ignoreCase = true)) return false
       val base = removeSuffix(extension).normalizedToNfc()
       val desiredBase = desired.removeSuffix(desired.knownExtension())
       return base == desiredBase || LibraryFiles.withoutCollisionSuffix(base) == desiredBase
   }
   ```

   `Song.canUpdateFileName` (`SongMappers.kt:39-40`) and `renameSong` / `renameSetlist` (`SongLocalSourceImpl.kt:105`,
   `SetlistLocalSourceImpl.kt:103`) all go by it.
2. **The engine folds the move away.** HEAD `SyncEngine.kt:47-53` gives the remote file the local spelling
   (`foldRemoteNamesOntoLocal`), and `:66-82` moves the index entry onto it (`foldIndexNamesOntoListings`), so the
   planner sees one unchanged file and plans nothing. Its own KDoc says so (HEAD `:58-60`): "A song moved to another
   spelling of the same name ("Update file name" on `Hallelujah.cho`) keeps the old spelling on a service that ignores
   case". The test `` `a song renamed by case keeps its index entry under the new spelling` `` (HEAD
   `SyncEngineTest.kt:573-590`) asserts exactly that the provider still holds `song("Hallelujah")`.
3. **Setlists look songs up by exact name.** `CampfireViewModel.kt:637`:

   ```kotlin
                       when (val song = songsByFileName[entry.songFileName]) {
                           null -> SetlistWithSongs.Entry.Missing(index = index, songFileName = entry.songFileName)
   ```

   and the same exact-name use runs through every setlist operation (`removeSongFromSetlist` `:1777`,
   `reorderSetlist` `:1787-1790`, `setSetlistSongs` `:1727`, the search `:2053`, per-setlist transpositions, and the
   domain's rename/delete reference walks).

## Options

The review brief named two; both have a gap. A third closes the bug at its source and is recommended.

- **(a) Fallback lookup in the view model** — when `songsByFileName[entry.songFileName]` is null, look up by
  `normalizedToNfc().lowercase()`. *Not recommended.* It fixes the one list the user looks at, and nothing else:
  removing, reordering, transposing or assigning that entry all match by exact name and would silently do nothing or
  add a second entry; the domain's rename and delete walks would miss it too. Doing it properly means a folded lookup
  at a dozen sites across `:presentation` and `:domain`, in lane D's busiest file.
- **(b) Move the file on the service** — `SyncProvider.move(kind, from, to)` (Dropbox `files/move_v2`), called when the
  local spelling differs from the listed one only by fold and the index entry was under the listed spelling. *Not
  sufficient on its own.* It fixes the cloud folder and new devices, but on device B the engine then folds the cloud's
  new spelling onto B's local `Hallelujah.cho` exactly as it folds today, so B's setlists still miss it. It also needs
  the mirror image (a **local** case-only move on B, under `LibraryFileLock`, with the rename's reference follow-up),
  a new provider method, a Dropbox behaviour that should be verified by hand first (case-only moves via `move_v2`),
  and it would have to be written against the batch-deletion work in progress in the same files.
- **(c) Recommended: do not offer a rename that only changes case or Unicode form.** A name that differs from the
  desired one only by case is the same file on every file system the library lives on by default (APFS on macOS and
  iOS, NTFS on Windows) and on the sync service; the app already treats Unicode form this way (`isNamed` composes
  before it compares). Extending that to case means the app never produces a move that nothing else can see, so
  nothing diverges. What it costs: a file named `Hallelujah.cho` by an older version keeps its capital until something
  else about its name changes — the same promise the root `CLAUDE.md` already makes about decomposed names.

Plan 14's scenario (a capitalised decomposed name) is also a fold-only difference, so under (c) it is no longer
reachable from the UI; plan 14 remains as hardening of `uniqueName`/`moveFile`.

**Existing divergence** (a library where a case-only rename already happened on a released build — 4.2.x shipped
**Update file name**): (c) does not repair it. On the device that shows entries as missing, the user can fix it by
editing the setlist, or by renaming the file on the other device back. If the user wants an automatic repair, option
(a) restricted to the *display* of `Missing` entries is the smallest thing that would help, and belongs to lane D;
this plan does not include it.

## The change (option c)

Invoke the **`code-style`** skill before the first edit.

`FileNames.kt:117-132`:

```kotlin
/**
 * Whether this file is already named [desired], the number a collision may have added included: a file that had to
 * make way for another one is named as well as it can be, and an offer to rename it again would be one that never
 * goes away however often it is taken.
 *
 * Neither case nor Unicode form counts. A name that differs from [desired] only in those is the same file to the file
 * systems the library lives on by default - APFS on a Mac and an iPhone ignores both, NTFS ignores case - and to the
 * sync service, so moving it would be a move nothing else can see: another device keeps the old spelling while the
 * setlists that follow the move reach it, and shows the song as missing from them. A file named on a Mac arrives
 * decomposed, and one written by an older version may be capitalised; either keeps its spelling until something that
 * is part of the name changes. Composing it does not rename anything.
 */
internal fun String.isNamed(desired: String): Boolean {
    val extension = knownExtension()
    if (!extension.equals(desired.knownExtension(), ignoreCase = true)) return false
    val base = removeSuffix(extension).normalizedToNfc()
    val desiredBase = desired.removeSuffix(desired.knownExtension())
    return base.equals(desiredBase, ignoreCase = true) ||
        LibraryFiles.withoutCollisionSuffix(base)?.equals(desiredBase, ignoreCase = true) == true
}
```

(`withoutCollisionSuffix` returns `String?`; today's `== desiredBase` handles the null implicitly, the `ignoreCase`
form needs the `?.… == true`.) Nothing else changes: `canUpdateFileName`, `renameSong` and `renameSetlist` all follow.

The engine's fold functions and their KDoc stay: a case-only rename can still happen *outside* the app (a user
renaming in Finder), and the fold is what keeps that from uploading a duplicate. Update the KDoc sentence at HEAD
`SyncEngine.kt:59-60` in passing only if the file is being edited anyway (e.g. by plan 09/16): "("Update file name" on
`Hallelujah.cho`)" → "(a file renamed by hand from `Hallelujah.cho`)" — the app itself no longer does it.

## Tests

`data/source/local/implementation/src/commonTest/.../FileNamesTest.kt`:

1. `aNameThatDiffersOnlyInCaseIsAlreadyNamed`: `"Hallelujah.cho".isNamed("hallelujah.cho")`,
   `"Hallelujah_2.cho".isNamed("hallelujah.cho")`, `"Summer.setlist.json".isNamed(setlistFileName("Summer"))` are true;
   `"Hallelujah2.cho".isNamed("hallelujah.cho")` is false.
2. `aNameThatDiffersInCaseAndFormIsAlreadyNamed`: `"Ένα.cho".isNamed("ένα.cho")`.

`desktopTest/.../source/RenameTest.kt` — the two existing case-only tests assert the opposite of the new rule and are
rewritten:

3. `` `a song renamed only in case keeps its name without a number` `` → `` `a song whose name differs from its header only in case is not renamed` ``:
   `songLocalSource.renameSong(song)` is `null`, the listing is still `listOf("Foo.cho")`, and
   `songLocalSource.loadSong("Foo.cho")!!.canUpdateFileName` is false.
4. `` `a setlist renamed only in case keeps its name without a number` `` → `` `a setlist retitled only in case keeps its file` ``:
   `renameSetlist(setlist, "Summer").fileName == "Summer.setlist.json"`, one file listed, title `Summer`.

Keep `JvmFileStorageTest`'s `` `lists the directory for a rename that only changes case` `` — it tests `uniqueName`
directly, which still supports such a caller.

## Verification

1. Tests above; root unit test command.
2. Desktop: quit, put `Hallelujah.cho` with `{title: Hallelujah}` in the library folder, start. **Before:** the song's
   menu has **Update file name**. **After:** it does not. Put `Hallelujah - Leonard Cohen.cho` with
   `{title: Hallelujah}` and `{artist: Leonard Cohen}`: the entry is still offered (the name differs by more than
   case), and taking it gives `leonard_cohen-hallelujah.cho`.
3. Two devices (optional, confirms the original bug is gone rather than moved): with the menu entry gone there is no
   in-app way to produce the divergence; SYNC tests for renames in `documentation/testing/07-sync-multi-device.md`
   should be re-run as written.

## Docs

- Root `CLAUDE.md` (HEAD `:193-197`, end of the naming bullet): "Nothing is migrated: a file whose name is decomposed
  keeps it until **Update file name** (or, for a setlist, a new title) moves it." → "Nothing is migrated, and a name
  that differs from the normalized one only by case or by Unicode form is taken as that name — it is the same file to
  APFS, NTFS and the sync service, and a move nothing else can see is one other devices never follow — so a
  capitalised or decomposed file keeps its spelling until **Update file name** (or, for a setlist, a new title) moves
  it for a reason that is part of the name."
- Root `CLAUDE.md` (HEAD `:212-216`, the **Update file name** bullet): after "Where a song's name and its metadata have
  drifted apart" add "(by more than case or Unicode form)".
- `data/source/local/implementation/CLAUDE.md:90-92` — "`isNamed` reads `LibraryFiles.withoutCollisionSuffix` to answer
  whether a file already carries the name it would be given, that suffix included" → add ", ignoring case and Unicode
  form".
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Song.kt:43-50`, `canUpdateFileName`
  KDoc: add "False as well for a name that differs from the derived one only in case or Unicode form, which is the same
  file to the file systems and to sync."
- `data/repository/implementation/CLAUDE.md` — the sentence "Without the second, a song moved to another spelling of
  its own name was downloaded again after it was deleted" stays true (hand renames).

## Files touched

- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
- `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/RenameTest.kt`
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Song.kt` (KDoc only)
- `CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`

## Depends on

- Land **before 14** (same file, and 14's framing depends on this decision).
- Related, not dependent: lane C's **plan 22** (an import on a case-insensitive file system records the desired
  spelling instead of the listed one, which is the other way a setlist can end up naming a spelling the library does
  not list). Both are needed; neither fixes the other.
- **If the user chooses (b) instead:** it touches `SyncEngine.kt` (after 09 and 16), `SyncProvider.kt`,
  `DropboxSyncProvider.kt`, `FakeSyncProvider.kt` — all with uncommitted work at review time — and still needs a local
  move on the receiving device; plan it afresh against the committed code. **If (a):** it is lane D's
  `CampfireViewModel.kt` plus `:domain:implementation`'s reference walks.
