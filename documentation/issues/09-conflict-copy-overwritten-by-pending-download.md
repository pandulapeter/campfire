# 09 · A second conflict on the same song overwrites the conflict copy, and the version that lost is gone from every device

**Severity:** data loss (all platforms; needs the same song to conflict twice between two devices, or a song
created during a long first sync under a name that is on its way down) · **Area:** `:data:repository:implementation`
(`SyncEngine.download`)

## Symptom
Two devices, A and B.

1. Both edit `x.cho`. A syncs, then B syncs: B resolves the conflict — B's text stays `x.cho`, A's lands as
   `x (2).cho` on B and in Dropbox.
2. Before A syncs again, A edits `x.cho` once more. Now A syncs.
3. A ends up with its own `x.cho` and an `x (2).cho` holding **A's first edit** — the old copy from Dropbox. B's
   version of `x.cho`, the one that just lost the second conflict, is nowhere: not on A, not in Dropbox (A uploaded
   over it), and B's own copy is replaced by A's on B's next run. Settings on A even reports the conflict and names
   `x (2).cho` as where to look.

The same happens without a second device's conflict: during a long first sync, create or import a song whose name
is one still waiting to come down. The download overwrites it.

## Cause
A's plan holds `Resolve(x.cho)` (changed on both sides) and `Download(x (2).cho)` (remote only, no index entry).
Resolves run first (`SyncOperation.order`, `SyncEngine.kt:365-373`). `resolve` uploads A's text over B's, then writes
B's text to the first name that is free **locally** — `x (2).cho`, since the download has not run yet — and uploads
it with `expectedRevision = null`, which Dropbox refuses (the name is taken remotely); that result is ignored
(`:350-353`). Then the download runs
(`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt:271-277`):

```kotlin
val indexEntry = index[key]
if (local != null && indexEntry != null && localContentHash(local) != indexEntry.localHash) {
    // Edited since the listing: this is now a file changed on both sides, and it is resolved as one.
    return resolve(provider, SyncOperation.Resolve(key, operation.revision), contentHashes)
}
val bytes = provider.download(key.kind, key.name)
libraryFileLocalSource.writeLibraryFile(key.kind, key.name, bytes)
```

The local file exists and differs from the remote one, but `indexEntry` is null, so the "edited since the listing"
guard is skipped and the file is overwritten. That is the one case the guard (from commit `ab5c54ae`) lets through,
and it is exactly the case it exists for: `SyncPlanner` only plans a `Download` for a key with no index entry when
the file was **not there** at the listing (`SyncPlanner.kt:137-138`), so a local file found under that name now can
only have appeared since — a conflict copy written earlier in this pass, or a song the user made in the meantime.
Either way it is a file changed on both sides.

Verified against `29820b93`; no other path plans a `Download` for a key that is present locally without an index
entry (`foldRemoteNamesOntoLocal` gives a remote key the local spelling before the plan is made, and
`KEEP_AND_UPLOAD` only drops entries of keys that are absent remotely).

## Fix
`SyncEngine.kt`, function `download`, written against the file as plan 01 leaves it (`remoteFiles` instead of
`contentHashes`, `downloadWithinLimit` instead of `provider.download`).

1. Replace the guard with one that treats a missing index entry as "not the file the last run saw" too:

   ```kotlin
   if (local != null && localContentHash(local) != index[key]?.localHash) {
       // Not the file the last run saw. With an index entry it was edited since the listing; with none it was not
       // there at all when the run listed the library, so it appeared since - a conflict copy written earlier in
       // this pass, or a song the user made while the run was going. Either way it has changed on both sides, and
       // it is resolved as that rather than written over.
       return resolve(provider, SyncOperation.Resolve(key, operation.revision), remoteFiles)
   }
   ```

   (The `val indexEntry` local goes, being used once.) The check above it — identical content on both sides only
   records the pair — stays first, so a file that arrived by hand with the same bytes is still not a conflict.

2. Update the KDoc of `download`: after "…a song that is still waiting to come down." add "The same goes for a file
   that was not there at all when the run listed the library and is now: nothing planned for this name knew about
   it, so it is never written over."

What the scenario does after the fix: `Download(x (2).cho)` becomes a resolve of `x (2).cho` — A's copy (B's text)
keeps the name and goes up over the old remote copy, and the old remote copy (A's first edit) lands next to it as
`x (2) (2).cho` on both sides, numbered the way any copy of `x (2).cho` is. All three texts survive everywhere, and
B converges on its next run by two ordinary downloads.

Considered and left out: handing `resolve` the remote names so that a copy gets a name that is free on both sides
(B's text would then land as `x (3).cho` straight away and `x (2).cho` would come down untouched). It only changes
which text ends up under which name, and it costs a new parameter on
`LibraryFileLocalSource.writeLibraryFileToFreeName` and on `FileStorage.uniqueName`, which plan 26 is reworking. The
guard is needed either way, because a song the user creates during a run takes the same path.

Do not "fix" this by reordering the groups so that downloads run before resolves: a copy would then be numbered
around the downloaded file, but the user-made-song case stays open, and `apply`'s KDoc explains why the order is
what it is.

## Tests
`SyncEngineTest`, new case `` `a conflict copy is not written over by a download waiting under its name` ``:

- local: `song(1)` = `"A's second edit"`;
- remote: `song(1)` = `"B's edit"`, `SyncKey(SONG, "song_1 (2).cho")` = `"A's first edit"`;
- index: `indexOf(song(1) to "Original".encodeToByteArray())` (so `song_1.cho` has changed on both sides and the copy
  name has no entry).

After one `synchronize`: `local.files[song(1)]` is A's second edit; the set of contents held locally and the set held
by the provider (`files.values.map { it.first.decodeToString() }.toSet()`) are both exactly
`{"A's second edit", "B's edit", "A's first edit"}`; `"song_1 (2).cho"` holds B's edit on both sides; the summary's
`conflicts` has two names.

Second case `` `a song created under a name that is waiting to come down is kept` ``: remote `song(1)` and `song(2)`,
library and index empty; `provider.onDownload = { key -> if (key == song(1)) local.files[song(2)] = mine }`. Expect
`local.files[song(2)]` to be `mine`, and `"song_2 (2).cho"` to hold the remote text. (With
`CONCURRENT_TRANSFERS` above one both downloads are in flight together, but under `runTest` the first runs up to its
write before the second starts, which is what the existing "edited while it waits to come down" test relies on
too.)

## Verify
- The unit test command.
- By hand it takes two installations, since the device that loses the text must never have held the ` (2)` copy:
  the desktop build as A and the web build (or an emulator) as B, on one Dropbox account. Sync a song to both. Edit
  it on both; sync A, then B (B gets `x (2).cho`). Without syncing A in between, edit the song on A again, then
  sync A. Afterwards A's library holds three files for that song — A's second edit under the name, and B's edit and
  A's first edit as copies — and so does Dropbox; before the fix B's edit is in neither.

## Docs
None beyond the KDoc in step 2: `documentation/sync.md` and the module `CLAUDE.md` already say that nothing is
merged and nothing is thrown away, which this makes true again.

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`

## Depends on
01 (same lane; renames the parameter this code passes along). Plan 10 rewrites `resolve`, which this plan calls
but does not change.
