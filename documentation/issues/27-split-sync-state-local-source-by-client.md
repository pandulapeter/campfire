# Split SyncStateLocalSource into SyncCredentialsLocalSource (for the remote source) and SyncIndexLocalSource (for the repository)

**Challenged:** amended — the `write` helper belongs to the index side only (not both), and `app/ios/CLAUDE.md` is one more file that names the class.

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/source/local/api/src/commonMain/.../local/api/SyncStateLocalSource.kt` (replace by two files); `data/source/local/implementation/src/commonMain/.../source/SyncStateLocalSourceImpl.kt` (split in two); `data/source/local/implementation/src/desktopTest/.../source/SyncStateLocalSourceTest.kt`; `data/source/remote/implementation/src/commonMain/.../auth/SyncCredentialsStore.kt` (`syncStateLocalSource`); `data/source/remote/api/src/commonMain/.../model/RemoteAuthorization.kt` (KDoc mention); `data/repository/implementation/src/commonMain/.../SyncRepositoryImpl.kt` (`syncStateLocalSource`); tests `data/source/remote/implementation/src/commonTest/.../auth/SyncCredentialsStoreTest.kt` (`FakeStorage`), `.../dropbox/DropboxRequestTest.kt`, `.../dropbox/DropboxAuthorizationTest.kt`, `data/repository/implementation/src/commonTest/.../sync/FakeSyncCollaborators.kt` (`FakeSyncStateLocalSource`), `.../SyncRepositoryImplTest.kt`; `data/source/local/api/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`, `data/repository/implementation/CLAUDE.md` (names `SyncStateLocalSource.setForgettingCredentialsOwed`), `app/ios/CLAUDE.md` (names `SyncStateLocalSourceImpl`)
**Depends on:** none (if 22 lands first, the repository-side field lives in `SyncIndexStore` and `SyncConnectionManager`)

## Problem

One interface serves two clients in two modules that use disjoint halves of it:

```kotlin
interface SyncStateLocalSource {
    suspend fun loadSyncCredentials(): String?          // SyncCredentialsStore (:data:source:remote:implementation)
    suspend fun saveSyncCredentials(document: String?)  // SyncCredentialsStore
    suspend fun loadSyncIndex(): String?                // SyncRepositoryImpl (:data:repository:implementation)
    suspend fun saveSyncIndex(document: String?)        // SyncRepositoryImpl
    suspend fun isForgettingCredentialsOwed(): Boolean  // SyncRepositoryImpl
    suspend fun setForgettingCredentialsOwed(isOwed: Boolean) // SyncRepositoryImpl
}
```

So the remote source module can write the sync index and the repository can overwrite the tokens, and each fake has to
implement the other client's half (`FakeSyncStateLocalSource` in the repository tests implements credentials it never
asserts on; `SyncCredentialsStoreTest.FakeStorage` implements the index and the note). The implementation already
treats them as two concerns (the credentials go through `SecretStore` with a plain-file migration; the index and the
note are plain files kept out of the device backup).

## Fix

1. In `:data:source:local:api`, replace `SyncStateLocalSource` with:

   ```kotlin
   /** The connected account's tokens - see the moved KDoc on where each platform keeps them. */
   interface SyncCredentialsLocalSource {
       suspend fun loadSyncCredentials(): String?
       suspend fun saveSyncCredentials(document: String?)
   }

   /** What sync remembers between runs on this device besides the tokens: the index, and the forgetting note. */
   interface SyncIndexLocalSource {
       suspend fun loadSyncIndex(): String?
       suspend fun saveSyncIndex(document: String?)
       suspend fun isForgettingCredentialsOwed(): Boolean
       suspend fun setForgettingCredentialsOwed(isOwed: Boolean)
   }
   ```

   The forgetting note goes with the index although it is *about* credentials: its only client is the repository, which
   decides when a previous installation's credentials are forgotten, and it is a plain file next to the index, kept out
   of the backup the same way. Each member keeps its KDoc; the interface KDoc ("opaque strings… the storage layer has no
   business knowing either shape") is split between them.
2. Split `SyncStateLocalSourceImpl` into `@Single internal class SyncCredentialsLocalSourceImpl(fileStorage, secretStore)`
   (`loadSyncCredentials`, `saveSyncCredentials`, `migratePlainFileIfPresent`, `read`, `CREDENTIALS_FILE_NAME`) and
   `@Single internal class SyncIndexLocalSourceImpl(fileStorage)` (the rest, `INDEX_FILE_NAME`,
   `FORGETTING_OWED_FILE_NAME`); the private `write` helper goes with the index class alone (the credentials side writes
   through `secretStore.save` and deletes the migrated plain file with `fileStorage.delete` directly), and `read` with
   the credentials class alone. Two classes rather than one implementing both interfaces, so the Koin
   compiler plugin's binding of a class to several supertypes never has to be relied on.
3. `SyncCredentialsStore` injects `SyncCredentialsLocalSource`; `SyncRepositoryImpl` injects `SyncIndexLocalSource`.
4. Split the fakes: `FakeSyncStateLocalSource` → `FakeSyncIndexLocalSource` (drops `credentials`); the remote tests'
   `FakeStorage` implements only `SyncCredentialsLocalSource`. Split `SyncStateLocalSourceTest` into two test classes
   along the same line (the "credentials the secret store refuses for now…" and "fail to read in any other way…" tests
   go with credentials, "the forgetting note is written and crossed off" with the index).
5. Update the CLAUDE.md mentions.

One commit (an interface split with every implementer and client).

## Tests

None new beyond the split of `SyncStateLocalSourceTest`. Guards: that test, `SyncCredentialsStoreTest`,
`DropboxRequestTest`, `DropboxAuthorizationTest`, `SyncRepositoryImplTest`, and `:app:di`'s compile-time Koin graph check.

## Manual check

An installation that was connected before the change: launch the new build and confirm Dropbox is still connected (the
credentials file name and the secret-store key are unchanged) and a run completes without re-downloading everything
(the index is read from the same `sync-index.json`).
