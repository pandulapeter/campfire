# 04 · A song renamed only by case ("Update file name") comes back when deleted, and another device's edit to it becomes a conflict copy

**Severity:** wrong behaviour (all platforms, with Dropbox. Uncommon: it needs a synced song whose file name differs from its normalized name only by case, such as `Hallelujah.cho` whose header gives `hallelujah.cho`, a case-only "Update file name" on it (02642c75 offers those on purpose), and then a deletion, or an edit on another device before this device's next run. When it happens it repeats on every deletion, and the edit case silently puts the older text under the song's name) · **Area:** `:data:repository:implementation` (`sync/SyncEngine.kt`)

## Symptom
Setup: `Hallelujah.cho` with `{title: Hallelujah}` and no artist, synced, so the remote file and the index entry are
both `songs/Hallelujah.cho`.

1. Device A: the song's menu offers **Update file name**. Take it. The file becomes `hallelujah.cho`.
2. Sync on A. Nothing visible happens. Dropbox keeps its spelling `Hallelujah.cho`, since an unchanged file is not
   written again. The index now only knows `hallelujah.cho`.
3. Delete the song on A and sync. The song is downloaded again as `Hallelujah.cho`. The deletion never reaches
   Dropbox or the other devices. Deleting it again does the same.

Variant: between steps 1 and 2, device B edits the song and syncs. A's run then turns B's edit into a
`Hallelujah (2).cho` copy and uploads A's older text over the song. B downloads that older text on its next run.

## Cause
Only the remote listing is matched onto the local spelling
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt:44-52`):

```kotlin
internal fun foldRemoteNamesOntoLocal(local: List<LocalFileState>, remote: List<RemoteFileState>): List<RemoteFileState> {
    val localKeys = local.mapTo(mutableSetOf()) { it.key }
    val localKeysByFolded = local.associate { it.key.folded() to it.key }
    return remote.map { file ->
        if (file.key in localKeys) file else localKeysByFolded[file.key.folded()]?.let { file.copy(key = it) } ?: file
    }
}
```

The index is still looked up by exact name in `SyncPlanner.plan` (`SyncPlanner.kt:107-116`). So:

- Step 2: local `hallelujah.cho`, remote folded to `hallelujah.cho`, no entry under that spelling, so
  `Resolve` (`SyncPlanner.kt:136`). The content is the same, so the engine records it. The entry
  `Hallelujah.cho` has no file on either side any more, so it is a `Forget` (`:153`). Dropbox is
  case-preserving and nothing is written, so the remote name stays `Hallelujah.cho` (the provider lists `entry.name`,
  `DropboxSyncProvider.kt:296`).
- Step 3: no local file, so there is nothing to fold onto. The remote `Hallelujah.cho` has no entry, so `Download`
  (`:147`). The entry `hallelujah.cho` has no file under that exact spelling on either side, so `Forget`.
- Variant: local `hallelujah.cho` (unchanged since the last run), remote folded with a new revision, and no entry
  under the local spelling. That is `Resolve` instead of `Download`, and the engine keeps the local text under the name
  and writes the remote one as a copy.

## Fix
Match the index onto the listings too, right after the two listings have been matched with each other.

1. `SyncEngine.kt`, below `foldRemoteNamesOntoLocal` and above `private fun SyncKey.folded()`, add:

   ```kotlin
   /**
    * Files an index entry under the spelling the listings now have for its file, where the two differ only by case.
    *
    * [foldRemoteNamesOntoLocal] matches the two listings with each other, but the planner looks the index up by exact
    * name as well. A song moved to another spelling of the same name ("Update file name" on `Hallelujah.cho`) keeps the
    * old spelling on a service that ignores case, and its entry under whichever spelling the last run saw. Left like that,
    * the planner reads one file as two: an entry whose file is gone from both sides, which it forgets, and a file
    * nobody has seen, which it downloads. A deletion made here then brings the song back, and an edit made elsewhere is
    * taken for a conflict. An entry only moves when its own name is in neither listing and exactly one listed name
    * folds to it and has no entry of its own, so an index that already matches is returned as it is.
    */
   internal fun foldIndexNamesOntoListings(
       index: Map<SyncKey, SyncIndexEntry>,
       listed: Set<SyncKey>,
   ): Map<SyncKey, SyncIndexEntry> {
       val unclaimedByFolded = listed.filter { it !in index }.groupBy { it.folded() }
       val moves = index.keys
           .filter { it !in listed }
           .groupBy { it.folded() }
           .mapNotNull { (folded, orphans) ->
               val candidates = unclaimedByFolded[folded]
               if (orphans.size == 1 && candidates?.size == 1) orphans.single() to candidates.single() else null
           }
       if (moves.isEmpty()) return index
       return index.toMutableMap().apply {
           moves.forEach { (from, to) -> remove(from)?.let { put(to, it) } }
       }
   }
   ```

2. In `synchronize`, directly after the `val remote = foldRemoteNamesOntoLocal(...).filterNot { it.key in tooLarge }`
   expression (ends at `:121`) and before the comment that leads into `index = index - tooLarge`, add:

   ```kotlin
               // After the listings have been matched with each other, so that an entry follows the spelling the plan
               // uses for its file.
               index = foldIndexNamesOntoListings(
                   index = index,
                   listed = (local.map { it.key } + tooLarge + remote.map { it.key }).toSet(),
               )
   ```

   The too-large keys are included so that an entry of such a file is matched and then dropped by the
   `index - tooLarge` below, as it is today. The folded index is what the rest of the pass, the `KEEP_AND_UPLOAD`
   filter, the wipe guard, `apply` and `Result.Completed` see. A pass with an empty plan still returns it, so the
   re-keyed entry is written at the end of the run even when nothing moved.

What it does in each case:
- Step 2: entry `Hallelujah.cho` moves to the local `hallelujah.cho`. Local and remote match it, so nothing is
  planned, and from then on the entry is under the local spelling.
- Step 3: entry `hallelujah.cho` moves to the only listed spelling, the remote `Hallelujah.cho`. That is
  `DeleteRemote(Hallelujah.cho, revision)`, which deletes the file on Dropbox.
- Variant: entry moves to `hallelujah.cho`. The local file matches it and the remote one has a new revision, so
  `Download`. B's edit arrives.

Do **not** re-key the remote listing onto the index spelling instead. Step 3 would then send the delete under a
spelling the remote does not list, which only works on a service that ignores case.

## Tests
`data/repository/implementation/src/commonTest/.../sync/`:

1. `FakeSyncProvider.kt`: on a service that ignores case, requests must land on the stored spelling, as they do on
   Dropbox. Add below `contentHashOf`:

   ```kotlin
   /** Where a request lands: on a service that ignores case, the stored spelling of the name, as Dropbox keeps it. */
   private fun stored(key: SyncKey) = if (ignoresCase) {
       files.keys.firstOrNull { it.kind == key.kind && it.name.equals(key.name, ignoreCase = true) } ?: key
   } else {
       key
   }
   ```

   Use it in `download` (`return files.getValue(stored(key)).first`, keep `downloadCounts` keyed by the requested
   key), in `upload` (`val target = stored(key)`, then compare `files[target]?.second` and write
   `files[target] = bytes to revision`; the `isHeldInAnotherCase` check stays as it is) and in `delete`
   (`files -= stored(SyncKey(kind = kind, name = name))`). The existing test
   `a local name another one shadows on a service that ignores case is reported instead of retried` still gets its
   `Conflict` (now from the revision check), so it must keep passing unchanged.

2. `SyncEngineTest.kt`, next to the other case tests:
   - `a song renamed by case keeps its index entry under the new spelling`: local `song("hallelujah")` to `ORIGINAL`,
     `FakeSyncProvider(files = mapOf(song("Hallelujah") to ORIGINAL), ignoresCase = true)`, index
     `SyncIndexDocument.of(providerId = SyncProviderId.DROPBOX.id, accountId = ACCOUNT_ID, lastSyncedAt = 1, index =
     mapOf(song("Hallelujah") to SyncIndexEntry(localHash = localContentHash(ORIGINAL), remoteRevision = "r1")))`.
     Expect `Completed` with `index.entries.keys == setOf(song("hallelujah").path)`, `summary.hasChanges == false`, and
     the provider still holding `song("Hallelujah")`.
   - `deleting a song renamed by case deletes it remotely`: local empty, same provider, index entry under
     `song("hallelujah")` at `"r1"`. Expect `provider.files` empty and `local.files` empty.
   - `an edit made elsewhere to a song renamed by case is downloaded rather than taken for a conflict`: local
     `song("hallelujah")` to `ORIGINAL`, provider `song("Hallelujah")` to `THERE` (fake revision `"r1"`), index entry
     under `song("Hallelujah")` at `"r0"` with the hash of `ORIGINAL`. Expect `local.files.keys == setOf(song("hallelujah"))`,
     its content equal to `THERE`, and `summary.conflicts` empty.
   - Pure: `foldIndexNamesOntoListings` leaves an index alone when two listed names fold to the orphan's name (two
     local spellings), and when two orphans fold to one listed name.

Run `./gradlew :data:repository:implementation:desktopTest`.

## Verify
1. Needs a Dropbox-configured build (`campfire.dropbox.appKey`). On desktop, put `Hallelujah.cho` containing
   `{title: Hallelujah}` into the library, sync, then take **Update file name**, sync, delete the song, sync. The song
   stays deleted, and the Dropbox web UI no longer lists `songs/Hallelujah.cho`.
2. Variant: rename by case on desktop, edit the song on a second device (or in the Dropbox web UI) before syncing
   desktop, then sync desktop. The edit arrives in the song and no `(2)` copy appears.
3. Compile `:data:repository:implementation` for desktop and wasmJs (`compileKotlinWasmJs`).

## Docs
`data/repository/implementation/CLAUDE.md`, the `sync/` bullet, after "…because a file listed on one side only reads
as a deletion.": add "Names are matched by case where a service ignores it: a remote name that differs from a local
one only by case takes the local spelling (`foldRemoteNamesOntoLocal`), and an index entry whose name neither listing
has moves to the one listed spelling that folds to it (`foldIndexNamesOntoListings`). Without the second, a song moved
to another spelling of its own name was downloaded again after it was deleted."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
Nothing. 46 edits the same `CLAUDE.md` bullet in another sentence.
