# Record the revision a sync download actually fetched, not the one the listing named

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncProvider.kt`,
`data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/model/RemoteDocument.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`,
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`,
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`,
`data/source/remote/api/CLAUDE.md`, `data/source/remote/implementation/CLAUDE.md`, `data/repository/implementation/CLAUDE.md` (wherever they describe `download`)

## Problem

`SyncEngine.download` records the revision from the **listing** with the bytes it **fetched**
(`SyncEngine.kt:504` and `:529-532` at 8ee010b36):

```kotlin
val bytes = downloadWithinLimit(provider, key, remoteFiles)
...
return OperationOutcome(
    entries = mapOf(key to SyncIndexEntry(localContentHash(bytes), operation.revision)),
    summary = SyncSummary(downloaded = 1),
)
```

`SyncProvider.download` returns bytes only (`SyncProvider.kt:94`), so the engine cannot know that the file moved on
between the listing and the request. If another device uploads the song again in that window (two saves ten seconds
apart on device B; device A's download queued behind the run's six permits and rate limiting), A writes the newer
content C2 and records `(hash(C2), R1)`. When the user then edits the song on A (to C3), the next run sees local ≠
index and remote R2 ≠ R1, plans `Resolve`, fetches C2, finds C2 ≠ C3 and writes a conflict copy `s (2).cho` holding
C2 — the user's own previous version — on both devices.

Proven with a throwaway probe against the real `SyncEngine` and `FakeSyncProvider` (whose `onDownload` hook replaced
the file with C2 at `r2` during the first run's download): after the first run the index held `remoteRevision=r1` for
content C2; after editing to C3 the second run reported `conflicts=[s (2).cho]` and left `s.cho = C3`,
`s (2).cho = C2`.

## Fix

1. **Contract.** Make `SyncProvider.download` return the revision with the bytes, like `downloadDocument` does:
   `suspend fun download(kind: LibraryFileKind, name: String): RemoteDocument`. Generalize `RemoteDocument`'s KDoc
   from "a file of the remote folder's own" to "the content of a remote file and the revision it is at", keeping the
   `revision` parameter doc (or, if the executor prefers a separate type, add `RemoteFileContent(bytes, revision)` in
   the same package — the recommended default is reusing `RemoteDocument`).
2. **Dropbox.** In `DropboxSyncProvider.download`, read the revision from the `Dropbox-API-Result` header exactly as
   `downloadDocument` does; factor the header parsing into one private function used by both
   (`private fun revisionOf(response: HttpResponse): String`, throwing `DropboxApiException(status, "the download
   named no revision")` when the header is missing or does not decode).
3. **Engine.** `downloadWithinLimit` returns the `RemoteDocument`. In `download`, record `downloaded.revision` in the
   index entry and pass it as `revision` into `resolveWith` on the save-under-the-write path. In `resolve`, pass the
   fetched revision into `resolveWith` instead of `operation.revision`, so the local version is uploaded with
   `expectedRevision` set to the remote version it was actually compared with (a version that moved on again since is
   then a `Conflict` and another pass, as for any other contested write). The `isOwnWrite` check in `resolveWith`
   uses `.bytes`. The `isSameContent` short-circuits keep `operation.revision`: the content hash they compared came
   from the listing at that revision.
4. **Fakes and tests.** `FakeSyncProvider.download` returns `RemoteDocument(bytes, revision)` from its `files` map;
   `DropboxRequestTest`'s two `download` calls read `.bytes`; add the header to whatever mock response those tests
   serve for a download.
5. Update the module CLAUDE.md files where they describe `download` returning bytes.

## Tests

In `SyncEngineTest`, add `a download records the revision it fetched`: provider with `song(1)` at `r1`, empty local
library and index; `provider.onDownload = { provider.files[song(1)] = THERE to "r2"; provider.onDownload = {} }`;
after the run the index entry of `song(1)` has `remoteRevision == "r2"`. Add `an edit after a download that raced a
remote save is not a conflict`: the same, then `local.files[song(1)] = EDITED` and a second run → no conflicts in the
summary, the remote holds `EDITED`, no `song_1 (2).cho` locally. In `DropboxRequestTest`, a download answers the
revision from the header, and one without the header fails.

Run `./gradlew :data:repository:implementation:desktopTest :data:source:remote:implementation:desktopTest :data:source:remote:api:desktopTest`.

## Manual check

Two devices on one Dropbox account. On device B save a song, and ten seconds later save it again with another change.
While B's second run is going, start a sync on device A (a large library makes A's download of that song land later).
Then edit the song on A and let it sync: no "(2)" copy of the song appears on either device.
