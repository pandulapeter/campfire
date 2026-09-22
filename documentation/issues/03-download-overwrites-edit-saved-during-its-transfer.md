# 03 · A song or setlist saved while sync is downloading that same file is overwritten by the download

**Severity:** data loss (all platforms; low likelihood per file, but the window is a whole request — up to a minute and
a half under Dropbox's rate limiting, which a first sync of a library reliably meets) · **Area:** `:data:repository:implementation` (`SyncEngine.download`)

## Symptom
1. A song (or setlist) was edited on another device, so this device's next run plans it as a `Download`.
2. While that download is in flight — the request is being retried after a 429, or the network is slow — the user
   saves an edit to the same file here (the editor, a tag, a transposition in a setlist), or imports / creates a file
   that gets exactly that name.
3. The download lands and writes the incoming bytes over the user's save. The index records the incoming bytes as in
   step, so no later run notices anything: the edit is gone from every device, with no conflict copy.

Review 1 plan 12 set out to close this ("re-check the local file … before writing"); the engine now re-checks before
the *request* rather than before the *write*, so the window it was meant to shrink to one local write is still one
network round trip, plus retries.

## Cause
`SyncEngine.kt:283-309`:

```kotlin
val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)                        // :292
if (local != null && isSameContent(provider, local, remoteFiles[key]?.contentHash)) { … }      // :293
if (local != null && localContentHash(local) != index[key]?.localHash) {                        // :296
    return resolve(provider, SyncOperation.Resolve(key, operation.revision), remoteFiles)
}
val bytes = downloadWithinLimit(provider, key, remoteFiles)                                     // :303 — the request
libraryFileLocalSource.writeLibraryFile(key.kind, key.name, bytes)                              // :304 — no re-read
```

`downloadWithinLimit` is `provider.download`, i.e. `DropboxSyncProvider.request`, which retries up to six times with
waits of up to 32 s each (`DropboxSyncProvider.kt:340-355`, `MAXIMUM_RETRIES`, `MAXIMUM_RETRY_SECONDS`) on top of a
60 s request timeout (`HttpClientConfiguration.kt`). Anything written to the file in that time is written over at
`:304`. The same holds for a file that did not exist at `:292` (`local == null`) and was created meanwhile — the case
the KDoc at `:276-282` says is "never written over".

The existing engine tests do not see it because they change a *different* file from the download hook
(`SyncEngineTest.kt:95`, `:143`, `:166`: song 1's download edits song 2).

## Fix
In `download`, keep the checks where they are (the "same bytes already here" shortcut must stay before the request, so
that identical files are never transferred) and repeat the decision after the request, immediately before the write:

```kotlin
val bytes = downloadWithinLimit(provider, key, remoteFiles)
// The request can take minutes under rate limiting, and the user is free to save this very file meanwhile: decided
// again on what is there now, so that the window in which a save can be lost is one local write, not one request.
val current = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
if (current != null && !current.contentEquals(local)) {
    return resolveWith(provider, key, operation.revision, localBytes = current, remote = bytes)
}
libraryFileLocalSource.writeLibraryFile(key.kind, key.name, bytes)
```

`!current.contentEquals(local)` covers both an edit (`local` was the indexed version) and a creation (`local == null`).
To avoid downloading the same bytes a second time, split `resolve` (`:372-423`) so that its body from the
`if (remote.contentEquals(local))` check after `downloadWithinLimit` onwards (not the `isSameContent` shortcut above
it, which is the part that avoids the request) is a private `resolveWith(provider, key, revision, localBytes, remote)`
that takes the already-downloaded `remote`, with `local` renamed to `localBytes` and `operation.revision` to
`revision` throughout; `resolve` becomes "read local, return if gone, `isSameContent` shortcut, download,
`resolveWith`". Behaviour of `resolve` itself is unchanged.

Update the KDoc of `download` (`:276-282`): "Checked here, the window in which such a save can be lost is one file's
worth of transfer" → "Checked before the request, so that a file already in step is never transferred, and again
after it, just before the write, so that the window in which such a save can be lost is the write itself rather than
a request that may be retried for minutes."

Do **not** move the whole check after the request (every already-in-step file would be downloaded once), and do not
add a lock shared with the repositories (the editor's save would then wait for a network request).

## Tests
`SyncEngineTest`:
- `a song saved while its own download is in flight is kept next to the incoming version`: local `song(1)` =
  "Original", index in step with it, remote `song(1)` = "Edited there"; `provider.onDownload = { key -> if (key ==
  song(1)) local.files[song(1)] = "Edited here".encodeToByteArray() }`. After the run: `local.files[song(1)]` is
  "Edited here", `song_1 (2).cho` is "Edited there", the remote `song(1)` is "Edited here", and
  `provider.downloadCount` (add a counter to `FakeSyncProvider.download`) for `song(1)` is 1 — the incoming bytes were
  not fetched twice. Fails today (the local file ends as "Edited there").
- `a song created under the name while its download is in flight is kept`: no local `song(2)`, no index entry, remote
  `song(2)` = "Theirs"; `onDownload` writes `song(2)` = "Mine". After: "Mine" under `song(2)`, "Theirs" under
  `song_2 (2).cho`.
- The existing three tests keep passing unchanged.

## Verify
1. `./gradlew :data:repository:implementation:desktopTest`.
2. Manual (desktop, Dropbox test account, `local.properties` key): with another device, change a long song; on the
   desktop turn on a network link conditioner / throttle (macOS Network Link Conditioner "Very Bad Network"), start
   **Sync now**, and while the progress row shows, open that song in the editor, change a word and save. After the
   run: the song keeps the saved word and a ` (2)` copy holds the other device's version.

## Docs
`data/repository/implementation/CLAUDE.md`, the `sync/` paragraph: after "A conflict's incoming version is written next
to the local one *before* the local one goes up", add "A download is decided about twice — before its request, so that
a file already in step is not transferred, and again just before the write, so that a save made while the request was
in flight is resolved as a conflict rather than written over."

## Touches
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
Nothing. Complements 02 (which covers the cached setlist that an already-finished download makes stale).
