# Split SyncProvider into SyncConnection (account) and SyncFolder (files), with SyncProvider extending both and the engine taking only SyncFolder

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/source/remote/api/src/commonMain/.../remote/api/SyncProvider.kt` (`SyncProvider`); `data/source/remote/implementation/.../dropbox/DropboxSyncProvider.kt` (declaration only); `data/repository/implementation/.../sync/SyncEngine.kt` (`synchronize`'s and every private helper's `provider: SyncProvider` parameter, `SyncIndexDocument.of(providerId = provider.id.id, …)`), `.../sync/SyncedPreferencesSync.kt` (`synchronize(provider, …)`); tests `sync/FakeSyncProvider.kt` (may stay a full `SyncProvider`), `sync/SyncEngineTest.kt`, `sync/SyncedPreferencesTest.kt` (only if they name the type); `data/source/remote/api/CLAUDE.md`, `data/source/remote/implementation/CLAUDE.md`
**Depends on:** none; easier after 24 (the engine's parameters are then gathered in `SyncRun`)

## Problem

`SyncProvider` has 15 members in two unrelated groups, already separated by comments in the file:

- account (`// Authorization`): `isConnected`, `buildAuthorizationRequest`, `completeAuthorization`, `disconnect`,
  `forgetStoredCredentials`, `loadAccount`, `storedAccount`;
- folder (`// Files`, `// Documents`): `list`, `download`, `contentHashOf`, `upload`, `delete`, `downloadDocument`,
  `uploadDocument`;
- and `id`, which both need.

The engine and `SyncedPreferencesSync` only ever touch the folder half, but their signature accepts the whole provider,
so a reader of `SyncEngine.synchronize(provider: SyncProvider, …)` cannot tell from the type that a run never disconnects
or re-authorizes anything, and a test double of the engine's world (`FakeSyncProvider`, 181 lines) must implement the
account half too. A second provider (the KDoc names Drive and WebDAV) is the case the interface was designed for;
naming the two halves is where its "rules that keep a second provider cheap" belong.

## Fix

1. In `SyncProvider.kt`:

   ```kotlin
   /** Who is connected, and the credentials that say so. */
   interface SyncConnection {
       val id: SyncProviderId
       suspend fun isConnected(): Boolean
       fun buildAuthorizationRequest(redirectUri: String?): RemoteAuthorizationRequest
       suspend fun completeAuthorization(response: RemoteAuthorizationResponse, verifier: String, redirectUri: String?): SyncAccount
       suspend fun disconnect()
       suspend fun forgetStoredCredentials()
       suspend fun loadAccount(): SyncAccount?
       suspend fun storedAccount(): SyncAccount?
   }

   /** One cloud folder, flat, addressed by (kind, name) - what a run reads and writes. */
   interface SyncFolder {
       val id: SyncProviderId
       suspend fun list(): RemoteListing
       suspend fun download(kind: LibraryFileKind, name: String): RemoteDocument
       fun contentHashOf(bytes: ByteArray): String?
       suspend fun upload(kind: LibraryFileKind, name: String, bytes: ByteArray, expectedRevision: String?): RemoteWriteResult
       suspend fun delete(deletions: List<RemoteDeletion>): Map<RemoteDeletion, String>
       suspend fun downloadDocument(name: String): RemoteDocument?
       suspend fun uploadDocument(name: String, bytes: ByteArray, expectedRevision: String?): RemoteWriteResult
   }

   interface SyncProvider : SyncConnection, SyncFolder
   ```

   Every member keeps its KDoc; the interface-level KDoc's bullet list is split between the two (the revision / name /
   `delete` rules go to `SyncFolder`, "nothing throws for an expired token" to both). `id` is declared in both with the
   same type, which Kotlin merges in `SyncProvider`.
2. `SyncEngine` (and every private helper taking `provider`) and `SyncedPreferencesSync.synchronize` take
   `SyncFolder`. `SyncRepositoryImpl` (or `SyncRunner` after plan 22) still picks a `SyncProvider` from `SyncProviders`
   and passes it — no cast needed. `SyncProviders` stays a list of `SyncProvider`.
3. Nothing in `DropboxSyncProvider` changes but its declaration line (it still implements `SyncProvider`). Koin binds it
   through the `syncProviders` `@Single` function, not by type, so nothing in the graph changes.

One commit.

## Tests

None new. `FakeSyncProvider` can keep implementing `SyncProvider`; optionally the engine tests that never connect use a
`FakeSyncFolder` extracted from it. Guards: `SyncEngineTest`, `SyncedPreferencesTest`, `SyncRepositoryImplTest`,
`DropboxRequestTest`, `DropboxAuthorizationTest`.

## Manual check

none — covered by tests
