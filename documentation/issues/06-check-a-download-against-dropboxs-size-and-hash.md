<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Refuse a downloaded file whose bytes do not match the size and content hash Dropbox sent with it

**Challenged:** amended — a mismatch now fails only that file (`DropboxApiException`, like the existing "the download named no revision"), not the run: nothing is written either way and the next run downloads it again, but as `SyncNetworkException` a mismatch that recurs (a hash disagreement, a middlebox that always cuts that file) would end every run, keep the rest of the library and `preferences.json` from finishing, read as "could not reach Dropbox", and with plan 13 re-run every 30 minutes forever.

**Kind:** bug (data loss)  ·  **Severity:** high  ·  **Platforms:** all
**Lane:** T
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxModels.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxFilesTest.kt`,
`data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/sync/SyncFolder.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`DropboxSyncProvider.download` and `downloadDocument` (`dropbox/DropboxSyncProvider.kt:228` and `:235`):

```kotlin
return RemoteDocument(bytes = transport { response.readRawBytes() }, revision = revisionOf(response))
```

and `revisionOf` (`:242-246`) reads only `.rev` from the `Dropbox-API-Result` header:

```kotlin
private fun revisionOf(response: HttpResponse) = try {
    response.headers[DOWNLOAD_RESULT_HEADER]?.let { json.decodeFromString<DropboxFileMetadata>(it) }
} catch (exception: SerializationException) {
    null
}?.rev ?: throw DropboxApiException(response.status.value, "the download named no revision")
```

That header is the file's full metadata, `size` and `content_hash` included. A body that arrives short without the
transport noticing (no declared length: an HTTP/1.0 proxy, a connection closed at a chunk boundary by a middlebox) is
accepted. Probe: metadata `size` 4000, body 12 bytes → accepted. The engine writes the 12 bytes as the song and records
`SyncIndexEntry(localContentHash(bytes), revision)` as in step; the user sees a song cut off, edits it, and the next run
uploads the truncated text over the full one with the revision Dropbox named (an `update` that succeeds). The full song
then survives only in Dropbox's version history. For `preferences.json` a cut document is unreadable and merged as the
base (safe), but the same check costs nothing there.

`DropboxFileMetadata` (`DropboxModels.kt:31-38`) has `size: Long = 0` and `contentHash: String? = null`, so an absent
`size` cannot be told from an empty file.

## Fix

- In `DropboxModels.kt`, make `DropboxFileMetadata.size` a `Long? = null` (it is read nowhere today; the upload receipt
  decodes the same class and only reads `rev`). `contentHash` is already nullable.
- Replace `revisionOf` with one decode of the header (e.g. `private fun downloadMetadataOf(response): DropboxFileMetadata`,
  throwing the same `DropboxApiException` when the header is missing, undecodable, or has no `rev`).
- Read the body, then check it against the metadata, in one private helper used by both `download` and
  `downloadDocument`:

  ```kotlin
  val metadata = downloadMetadataOf(response)
  val bytes = transport { response.readRawBytes() }
  if (metadata.size != null && bytes.size.toLong() != metadata.size ||
      metadata.contentHash != null && dropboxContentHash(bytes) != metadata.contentHash
  ) {
      throw DropboxApiException(response.status.value, "the download arrived incomplete (${bytes.size} of ${metadata.size} bytes)")
  }
  return RemoteDocument(bytes = bytes, revision = metadata.rev)
  ```

  A mismatch is **one file's failure**, not the run's (`DropboxApiException`, as the missing revision already is):
  nothing is written, the index is left alone, the file is named among the run's failures, and the next run downloads
  it again (`run/CLAUDE.md`: "one file failing does not end a run"). Ending the run instead (`SyncNetworkException`)
  was considered and rejected: a mismatch that recurs for one file would end every run at that file, so the rest of the
  library and `preferences.json` would never finish, Settings would say "could not reach Dropbox" on a working
  network, and plan 13 would retry it every 30 minutes for as long as the app is open. A connection that is really
  failing fails the other transfers on its own. For `downloadDocument` the same exception is the one document's
  problem (`havePreferencesFailed`); in `ConflictResolver.resolveWith`'s own-write check it propagates as any
  download failure there does today. The size check is the cheap one; the hash also catches a body of the right length
  with wrong bytes. Both compare the decoded
  bytes against the file's own size and hash, so a transfer the engine decompressed (OkHttp, NSURLSession, `fetch`) is
  compared correctly; this is not the transfer's `Content-Length`. Hashing is `Sha256` over at most 8 MB
  (`SyncEngine.MAXIMUM_FILE_SIZE`), negligible beside the download.
- Comment the check with the reason (a body cut short without a declared length is otherwise written as the song and
  indexed as in step, and the next edit uploads it over the full one).
- `SyncFolder.download`'s KDoc (`data/source/remote/api/.../sync/SyncFolder.kt:49-52`) promises nothing about integrity
  today. Add one sentence to the contract: the bytes are the whole file as the service holds it, and a transfer that
  cannot be shown to be whole is a failure of that download (thrown, never a shorter file). A second provider then has to honour it.
- `data/source/remote/implementation/CLAUDE.md`, Dropbox section, "A download's revision comes from the
  `Dropbox-API-Result` header (its absence is a failure)": add "and its `size` and `content_hash`, which the bytes must
  match, or that file fails (not the run)".

## Tests

In `DropboxFilesTest`:

- `a download shorter than the file Dropbox describes fails`: body `"song"`, header
  `{"rev":"r7","size":4000}` → `assertFailsWith<DropboxApiException> { provider.download(LibraryFileKind.SONG, "song.cho") }`.
- `a download whose bytes do not hash to Dropbox's hash fails`: body `"song"`, header with `"size":4` and a
  `content_hash` of other bytes (`dropboxContentHash("sing".encodeToByteArray())`) → `DropboxApiException`.
- `a download that matches its size and hash is accepted`: header with `"size":4` and
  `"content_hash":"${dropboxContentHash("song".encodeToByteArray())}"` → bytes and revision returned.
- The same short-body case through `downloadDocument("preferences.json")`.
- The existing `a download answers the revision it fetched` (header `{"rev":"r7"}` only) keeps passing: absent fields are
  not checked.
