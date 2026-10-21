# Cap every Dropbox download at the library's file limit before its body is read, so a huge `preferences.json` or `shared-links.json` put into the folder by hand reads as unreadable and is written over, rather than retried out of memory forever

**Kind:** bug (OOM, endless retry) · **Severity:** high · **Platforms:** all
**Challenged:** amended — the mechanism was wrong three ways: in Ktor 3.6 `skipSavingBody()` is deprecated and a no-op (it only logs a warning; the body is still saved inside `post`), so nothing would change; had it worked, the error paths read the body twice (`retryAfterMillis` reads a 409's summary, then `downloadDocument` reads it again and gets `""` from a `DoubleReceiveException`, so a missing document stops being null); and an exception thrown inside `transport { }` that is not a `DropboxApiException` is wrapped as `SyncNetworkException`, the run-ending network failure the plan meant to avoid. The body is now streamed with `preparePost(...).execute { }` inside `requests.request`'s block (so a reset mid-body is still retried), error answers are saved there, and the refusal is a `DropboxApiException` that `request` does not retry.
**Decided:** an oversized document reads as unreadable and is overwritten (user, 2026-10-21).
**Files:**
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/sync/SyncDocumentTooLargeException.kt` (new)
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/sync/SyncFolder.kt` (KDoc of `download` / `downloadDocument`)
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxFilesTest.kt`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/FolderDocuments.kt` (new)
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/SyncedPreferencesSync.kt`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRemoteWatcher.kt`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRegistry.kt`
- `data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/FakeSyncProvider.kt`
- `data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunnerPreferencesRevisionTest.kt`
- `data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRemoteWatcherTest.kt`
- `data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksRepositoryImplTest.kt`
- `data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedCopySyncTest.kt`
- `data/source/remote/api/CLAUDE.md`, `data/source/remote/implementation/CLAUDE.md`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/CLAUDE.md` and `.../preferences/CLAUDE.md` (one clause each)

## Problem

The folder's own documents are downloaded with no size limit at all. `DropboxSyncProvider.kt:268` at 5514fcf76:

```kotlin
override suspend fun downloadDocument(name: String): RemoteDocument? {
    val response = downloadResponse(documentPath(name))
    ...
    return downloadedDocument(response)
}
```

`downloadResponse` is a plain `client.post(DOWNLOAD_URL)`. In Ktor 3.6 a non-streaming call saves the whole body into
memory before it returns (`SaveBody`), and `downloadedDocument` then reads it again with `readRawBytes()`. Only the
library files have a pre-check: `SyncEngine.downloadWithinLimit` compares the listing's size with
`MAXIMUM_FILE_SIZE`. The listing carries no size for the documents, because `toDocumentRevisions` keeps only the
revision.

The hand edit that triggers it: the user, or a tool, drops a large file named `preferences.json` or
`shared-links.json` at the top of `Apps/Campfire Sync`. Examples are a backup, a log, or a video renamed by mistake,
a few hundred MB or 2 GB.

- `preferences.json` is downloaded by every run that is not paired with the index's revision
  (`SyncedPreferencesSync.kt:121`). That is every run, since no run ever settles it. The watcher also downloads it on
  every feed change (`SyncRemoteWatcher.kt:66`).
- `shared-links.json` is downloaded by `SharedLinksRegistry.read` (its only download), which every run's shared-copy
  step (`SharedCopySync`), the journal applier, Settings → Sharing (`SharedLinksRepositoryImpl.refresh`), and every
  sharing action (`SharedLinksRegistry.edit` reads before it writes) go through.

Buffering the body exhausts the heap: about 192 MB on Android, and the web's Wasm heap. `DropboxTransport.request`
catches every `Throwable` thrown inside the call as a transport failure and resends the call (lines 114–131). After
the last attempt, `transport` wraps it as `SyncNetworkException` (line 269). The run therefore ends as
`Failure(NETWORK)`, which is the one reason `SyncRunScheduler` retries by itself, after 30 s, 1 min, 2 min, and then
every 5 min while the app is alive. Each retry downloads up to four heap-fulls of the file again. On Android an
`OutOfMemoryError` landing on another thread meanwhile kills the process. On mobile data the waste alone is serious.

Refusing the download is not enough on its own. A refused `preferences.json` would leave every run with
`havePreferencesFailed` and the document in place, and a refused registry would make Settings → Sharing and every
sharing action fail until the user found and removed the file by hand, since a sharing action reads the registry
before writing over it. The user decided that an oversized document is the user's own doing and reads exactly like a
malformed one (plan 09): the preferences step writes this device's values over it, and the registry counts as empty
until a sharing action writes over it. Sync recovers by itself.

A smaller race does the same to a library file: `downloadWithinLimit` checks the size the listing gave, but
`download` reads whatever the file holds when the request arrives. Library files keep today's rule (refused, named
once among the run's failures).

## Fix

Read every download as a stream, inside the one call `requests.request` retries, and stop before the body once the
size is known to be over the limit. `CoverArtRemoteSourceImpl` and `ImportDownloadRemoteSourceImpl` already stream
this way. A library file over the limit is then a `DropboxApiException`; a folder document over it is a typed
refusal carrying its revision, which the sync module maps onto the existing unreadable path.

### The provider

1. `downloadResponse` becomes a function that answers the response and, for a success, its metadata and bytes, null
   bytes meaning "over the limit":

   ```kotlin
   private class Download(val response: HttpResponse, val metadata: DropboxFileMetadata?, val bytes: ByteArray?)

   private suspend fun download(path: String): Download {
       var metadata: DropboxFileMetadata? = null
       var bytes: ByteArray? = null
       val response = requests.request { accessToken ->
           metadata = null
           bytes = null
           httpClientHolder.client().preparePost(DOWNLOAD_URL) { … same attributes and headers … }.execute { response ->
               if (response.status.isSuccess()) {
                   metadata = downloadMetadataOrNull(response)
                   bytes = boundedBody(response, declaredSize = metadata?.size)
                   response
               } else {
                   // The retry loop, ensureSuccessful and downloadDocument's not_found check each read an error's
                   // body, which a streamed answer allows once: keep this small answer in memory like any other call.
                   response.call.save().response
               }
           }
       }
       return Download(response, metadata, bytes)
   }
   ```

   Status and headers of the streamed success stay readable after `execute`, which is all `request`'s loop looks at.
   A transfer that breaks mid-body fails inside the block, so it is retried as today (today the whole body is read
   inside `post`, also inside the block).
2. `boundedBody(response, declaredSize)`: answers null without touching the body when `declaredSize >
   MAXIMUM_DOWNLOAD_SIZE`; otherwise reads `response.bodyAsChannel().readBuffer(MAXIMUM_DOWNLOAD_SIZE + 1L)
   .readByteArray()` and answers null if more than the limit arrived. Leaving `execute` cancels the rest of the body,
   so a refused download never holds its connection open. **Nothing is thrown inside the block**: every refusal is
   thrown after `request` returns, so `request` never retries one and `transport` never wraps one, and `request`
   needs no new `catch` (the previous version's rethrow step is dropped). For the same reason `downloadMetadataOf` is
   split: `downloadMetadataOrNull` (the lenient parse, inside the block) and the "the download named no revision"
   throw, which stays outside it as today.
3. Outside the block:
   - `download(kind, name)`: `ensureSuccessful()`, then the revision check, then, for null bytes,
     `throw DropboxApiException(status, "the download is larger than $MAXIMUM_DOWNLOAD_SIZE bytes")`, which the
     engine already counts as one file's failure, named. Otherwise `downloadedDocument(metadata, bytes)`.
   - `downloadDocument(name)`: the `path/not_found` check (reads the saved error body, as now), `ensureSuccessful()`,
     the revision check, then, for null bytes, `throw SyncDocumentTooLargeException(revision = metadata.rev)`.
     Otherwise `downloadedDocument(metadata, bytes)`. The revision is in the `Dropbox-API-Result` header, read before
     the body, so the refusal costs one request and no body.
4. `downloadedDocument(metadata, bytes)` keeps its size and hash comparison over the bytes it is handed. Drop the
   `readRawBytes` import.
5. `MAXIMUM_DOWNLOAD_SIZE = ImportLimits.MAX_TEXT_FILE_SIZE` (8 MiB, the same as `SyncEngine.MAXIMUM_FILE_SIZE`). A
   `preferences.json` for a library of 6,000 songs, with chords and tags, is well under 1 MB, and a registry of
   thousands of shares is under a few MB. The cap therefore never reaches a document plan 09's token budget accepts
   for a real library, so it cannot turn a real `preferences.json` into one that is overwritten.

On the web the Js engine streams `fetch`'s body the same way, and `BrowserCallTimeout` still bounds the whole call,
body included, since the call ends only when `execute` returns. On the native engines the socket timeout keeps
applying between packets of the body.

### The API

6. `SyncDocumentTooLargeException(val revision: String)` in `:data:source:remote:api`'s `sync` package, its own file
   beside `SyncScopeMissingException.kt`: a plain `Exception`, deliberately not a `SyncRunEndingException`. KDoc: a
   folder document too large to download, refused before its body was read, with the revision it is at so that a
   write over it is not a conflict. `SyncFolder.downloadDocument`'s KDoc says it throws this for a document over the
   provider's download limit; `download`'s says a library file over it is that file's failure. A typed refusal rather
   than empty bytes keeps the API truthful; what "unreadable" means is the sync module's decision, made in one place.

### The sync module

7. One helper, `FolderDocuments.kt` in `data/sync/implementation`'s root package:

   ```kotlin
   /**
    * [SyncFolder.downloadDocument], except that a document too large to download is answered as an empty one at its
    * revision. Every reader of a folder document takes no bytes as unreadable, so the preferences step writes this
    * device's values over it and the registry counts as empty until a sharing action writes over it, both against
    * that revision, which is what lets the write through. The user put it there; recovering beats keeping it.
    */
   internal suspend fun SyncFolder.downloadFolderDocument(name: String): RemoteDocument? = try {
       downloadDocument(name)
   } catch (exception: SyncDocumentTooLargeException) {
       RemoteDocument(bytes = ByteArray(0), revision = exception.revision)
   }
   ```

   Empty bytes are unreadable to both readers today: `SyncedPreferencesDocument.decode` catches the
   `IllegalArgumentException` that `parseToJsonElement("")` throws and answers null, and `SharedLinksFormat.decode`
   catches its `SerializationException` and answers `Decoded.Unreadable`. Plan 09's bound check in front of them
   passes `""` on to the same parse, so the order of the two plans does not matter.
8. The three `downloadDocument` calls become `downloadFolderDocument`; nothing else in them changes:
   - `SyncedPreferencesSync.synchronize` (line 121). Traced: `remoteDocument` is null, so `effectiveRemote` is
     `previous` (this device's last base, or null on a first settle); the merge keeps this device's values;
     `merged == remoteDocument` is false, so it uploads with `expectedRevision = remote.revision`, the oversized
     file's own, and the upload is `Written`. The run records that revision as `syncedPreferencesRevision`, so the
     next run's listing pairs with it and downloads nothing. A conflict (another device wrote meanwhile) retries with
     a fresh download, as today.
   - `SyncRemoteWatcher` (line 66): `isOtherPreferences(empty)` decodes to null, unlike the settled document, so it
     schedules a run, which overwrites the document; the overwrite then matches the settled document, so it does
     not loop. Without the helper the refusal would reach the watcher's "any other failure" branch and stop watching
     until the next run.
   - `SharedLinksRegistry.read` (line 67), which is every registry download: the run's shared-copy step, the journal
     applier, Settings → Sharing's `refresh`, and every sharing action. It logs "cannot be read; counts as empty" as
     for any unreadable registry, saves the empty list at that revision into the mirror, and answers it. An `edit`
     reads it this way (one refused request, never the body), applies its change to the empty list, and writes with
     that revision, so the first sharing action replaces the file. Until then Settings shows no shares instead of a
     failure, and the run's shared-copy step reports no failure.

What stays as it is:
- A library file over the limit, by the listing's size (`downloadWithinLimit`) or by the race above, is refused and
  named once among the run's failures; `SyncEngine` catches the `DropboxApiException` as one file's failure.
- `SyncDocumentTooLargeException` never leaves `:data:sync:implementation`'s helper, so no catch elsewhere changes.

Docs:
- `data/source/remote/implementation/CLAUDE.md`, Dropbox's download bullet: "A download is streamed and refused
  before its body is read once its declared size, or the body itself, passes 8 MiB: a library file then fails as one
  file, a folder document is `SyncDocumentTooLargeException` with its revision; neither is retried or a network
  failure."
- `data/source/remote/api/CLAUDE.md`, the `downloadDocument` / `uploadDocument` bullet: "(only `preferences.json`)"
  becomes "(`preferences.json` and `shared-links.json`)", and it gains "one too large to download throws
  `SyncDocumentTooLargeException` with its revision".
- `preferences/CLAUDE.md`: "A folder document that is missing or unreadable…" gains "(too large to download counts as
  unreadable, and is written over with this device's values)".
- `shared/CLAUDE.md` Registry bullet: "One that does not decode, or is too large to download, counts as empty, and
  only a sharing action writes over it."

Name only classes and functions that exist once this lands (`check_doc_names.py`).

## Tests

All run with `./gradlew desktopTest`. Each fails at ea4b86a1e: the provider's tests by behaviour, the sync module's
because the refusal reaches the callers unmapped (with the fake from the first bullet below; before the new exception
exists they do not compile, which is also a failure).

`FakeSyncProvider`: add `val oversizedDocuments = mutableSetOf<String>()`. `downloadDocument` of a name in it, while
the document exists, throws `SyncDocumentTooLargeException(revision)` with the stored revision (after counting the
download and running `onDownloadDocument`, as now); `uploadDocument` that writes a name removes it from the set.

In `DropboxFilesTest` (MockEngine through `dropboxProvider`):

- ``a document Dropbox describes as larger than the limit is refused with its revision without reading its body``:
  answer with a `Dropbox-API-Result` header of `rev` "r1" and `size` 8 MiB + 1, and a body channel that throws if it
  is read past its first byte (or a counting channel, asserting it was not drained). Assert that
  `downloadDocument` throws `SyncDocumentTooLargeException` whose `revision` is "r1", after exactly one request.
  Today `SaveBody` reads the body inside `post`, the read throws, the call is retried, and it ends as
  `SyncNetworkException`.
- ``a document whose body runs past the limit without a declared size is refused with its revision``: no `size`,
  8 MiB + 1 bytes streamed. Same assertions. Today it returns the bytes.
- ``a library file larger than the limit is one file's failure, asked once``: `download(SONG, …)` under the same two
  answers throws `DropboxApiException` (not `SyncNetworkException`), after exactly one request each.
- ``a document that is not there is no document rather than a failure`` (existing) must still pass: it is what breaks
  if an error answer is streamed rather than saved, since `retryAfterMillis` reads a 409's summary first. The existing
  `a download that matches its size and hash is accepted` and the shorter-than-declared cases must still pass.

In `:data:sync:implementation`:

- `SyncRunnerPreferencesRevisionTest`, ``an oversized preferences document is written over with this device's values,
  and the next run leaves it alone``: a connected device with a set transposition, the folder's `preferences.json`
  marked oversized. The run completes without `havePreferencesFailed`; the folder's document now decodes to this
  device's values at a new revision; a second run asks for no document download (`documentDownloadCount` unchanged)
  and reports no failure. At ea4b86a1e every run reports `havePreferencesFailed` and the document is never written.
- `SyncRemoteWatcherTest`, ``a feed change to an oversized preferences document schedules a run and keeps
  watching``: the change schedules a run and the logger records no "Stopped watching". At ea4b86a1e the refusal
  reaches the "any other failure" branch, which logs that line.
- `SharedLinksRepositoryImplTest`, ``a list too large to download counts as empty and is written over by a share``
  (twin of ``a list that cannot be read counts as empty and is written over by a share``): the registry marked
  oversized; `refresh()` answers null and `shares` is empty; `share(…)` answers `Shared`, and the folder's registry
  then lists exactly that share. At ea4b86a1e `refresh()` answers a failure and the share fails.
- `SharedCopySyncTest`, ``an oversized list fails no run``: a run with the registry marked oversized does not report
  `haveSharedCopiesFailed`. At ea4b86a1e it does.
