# Forget the last synced preferences of the songs "Keep them and upload" keeps, so the run uploads their overrides with them

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`,
`data/repository/implementation/CLAUDE.md`

Lane B, fourth: apply after 10–12 (it relies on the merge only adding for a song the base does not name, which 11
keeps true) and before 14 and 15.

## Problem

Device A deletes its library from Settings (`DeleteLibraryUseCase`): every song file goes from the cloud folder, and
the preferences step drops every song from `preferences.json`, uploading `{"version":1,"songs":{}}`. Device B's next
run is stopped by the deletion guard; the user answers **Keep them and upload**, which runs with
`SyncDeletionPolicy.KEEP_AND_UPLOAD`. `SyncEngine.synchronize` forgets the index entries of the files it keeps:

```kotlin
if (deletionPolicy == SyncDeletionPolicy.KEEP_AND_UPLOAD) {
    // Forgetting that the last run saw these files is what makes them new on this device: ...
    val localKeys = local.mapTo(mutableSetOf()) { it.key }
    val remoteKeys = remote.mapTo(mutableSetOf()) { it.key }
    index = index.filterKeys { it !in localKeys || it in remoteKeys }
}
```

but hands the preferences base through unchanged:

```kotlin
val syncedPreferences = document.syncedPreferences.takeIf { document.accountId == accountId }
...
return Result.Completed(summary = summary, index = SyncIndexDocument.of(..., syncedPreferences = syncedPreferences))
```

`SyncRepositoryImpl.synchronizePreferences` then merges with that base: for every song B kept, local == base (B
changed nothing) and the remote removed it, so `mergeValue`'s `local == base -> remote` removes B's transposition,
tempo and capo for every song the user just chose to keep. Confirmed with a probe at ed4a1a5ce: base
`{a.cho:{capo:2}}`, preferences `capo a.cho=2`, library `a.cho`, remote `{"version":1,"songs":{}}` → capos `{}`.
The files come back to the folder; their overrides are gone on B too.

## Fix

Options:

1. **(Recommended)** Mirror the index: drop from the preferences base the songs whose index entries the
   `KEEP_AND_UPLOAD` block forgets. Their overrides are then "new on this device" exactly as their files are; the
   merge (base null for that song, remote null) keeps local's entry and uploads it. Every other song is merged as
   usual, so a removal another device made for a song that was not in question is still honoured.
2. Pass `base = null` for the whole preferences step of a `KEEP_AND_UPLOAD` run. Simpler, but it resurrects every
   override any device removed since the last run, not only those of the kept songs, and loses the pass-through of
   unknown fields `localDocument` takes from the base.

For option 1, in `SyncEngine.synchronize`: make `syncedPreferences` a `var`; in the `KEEP_AND_UPLOAD` block, before
filtering, collect the forgotten song names (`index.keys.filter { it.kind == LibraryFileKind.SONG && it in localKeys
&& it !in remoteKeys }`, folded with `normalizedToNfc().lowercase()`) into a set that accumulates across passes, then

```kotlin
syncedPreferences = syncedPreferences?.let { base ->
    SyncedPreferencesDocument.withSongsWhere(base) { it.normalizedToNfc().lowercase() !in forgottenSongs }
}
```

The changed base flows into `apply` (index snapshots) and `Result.Completed`, from which `SyncRepositoryImpl` takes
`result.index.syncedPreferences` as the step's base — no change needed there. `KEEP_AND_DOWNLOAD` needs nothing: the
overrides live outside the library folder, so a device that lost its folder still has them, and the cloud document
still has its own.

Add a sentence to `data/repository/implementation/CLAUDE.md` where the keep answers are described (and/or the
`preferences.json` paragraph): keeping the files to upload also forgets the last synced preferences of those songs,
so their overrides travel back with them.

## Tests

In `SyncEngineTest`, next to `keeping the files a run asked about uploads them again`: an index document built with
`indexOf(...)` for ten songs, copied with `syncedPreferences = JsonObject(mapOf("version" to JsonPrimitive(1), "songs"
to <an object with one entry per library song, plus "elsewhere.cho" which is in neither listing>))`; run with
`KEEP_AND_UPLOAD` against an empty `FakeSyncProvider` → `(result as Completed).index.syncedPreferences` names none of
the ten kept songs and still names `elsewhere.cho`. (Check `SyncIndexDocument.of`'s parameters for the exact shape.)

Optionally, in `SyncedPreferencesTest`, the end-to-end shape: base without `a.cho`, preferences `capo a.cho=2`, remote
`{"version":1,"songs":{}}` → capos still `{a.cho=2}` and the uploaded document names it.

## Manual check

Two devices on one account, a library song with a capo set from the library on both. On A, Settings → Library →
delete the library (type DELETE). On B, Sync now; answer **Keep them and upload**. The song is back in the cloud
folder with its capo on B, `preferences.json` names it, and after A syncs (and downloads it) A shows the capo too.
