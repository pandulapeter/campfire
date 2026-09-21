# 20 · Files deleted on one device come back after the Dropbox account's name is first read, or its e-mail address changes

**Severity:** minor (all platforms; needs a connection made on a flaky network, or a changed Dropbox e-mail) · **Area:** `:data:model` (`SyncAccount`), `:data:source:remote:implementation` (`DropboxSyncProvider`), `:data:repository:implementation` (`SyncRepositoryImpl`, `sync/SyncIndexDocument`)

## Symptom
1. Connect Dropbox on a bad connection: the authorization succeeds, but the follow-up request for the account's
   name fails. Settings shows the account as `dbid:AAH…` and the first sync runs.
2. Delete a song on another device and sync there.
3. Start Campfire again on the first device, now with a working network: the name is read, Settings shows the real
   account — and the run that follows **uploads the deleted song again** instead of deleting it here. Every deletion
   made on either side since the last run is undone once.

The same happens once after the user changes the e-mail address of their Dropbox account.

## Cause
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:435-439`

```kotlin
/**
 * Who the account is, as far as the index is concerned. The display name is what every provider can offer, and
 * comparing it only has to answer "is this the same account as last time".
 */
private fun accountIdOf(account: SyncAccount) = "${account.providerId.id}:${account.email ?: account.displayName}"
```

The index is written under that key, and `SyncEngine.synchronize` (`sync/SyncEngine.kt:76-78`) ignores an index whose
key differs: `document.takeIf { it.accountId == accountId }?.toIndex().orEmpty()`. With no index, a file that is here
and not there is "new on this device" and goes up. The key is made of things that change: an account whose name was
never read is `SyncAccount(displayName = token.accountId, email = null)` (`DropboxSyncProvider.kt:121`, and the
stored fallback at `:177-178`), so the key is `dropbox:dbid:…` until the first successful read and
`dropbox:me@example.com` after it.

Dropbox's `account_id` never changes, is returned by the token exchange, and is already stored in
`SyncCredentialsDocument.accountId`; it only never leaves the provider.

## Fix
Written against the code as it is after plan 19 (`SyncProvider.storedAccount()` and `storedAccountOf` exist).

1. **`:data:model`, `SyncAccount`** (`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/SyncState.kt:21-26`)
   — add the id, as the second property:

   ```kotlin
   /**
    * Who the connected provider says the user is, shown so that it is obvious which account the library goes to.
    *
    * @param id What the service itself calls the account: opaque, never shown, and the one thing about an account
    *   that does not change when its owner renames it or moves it to another e-mail address - which is why the sync
    *   index is filed under it. Empty where it was never learnt.
    */
   data class SyncAccount(
       val providerId: SyncProviderId,
       val id: String,
       val displayName: String,
       val email: String?,
   )
   ```

2. **`DropboxSyncProvider`** — fill it at the three places a `SyncAccount` is built (they are the only three in the
   project):
   - `completeAuthorization`: `SyncAccount(providerId = id, id = token.accountId, displayName = token.accountId, email = null)`
   - `loadAccount()`, the success path: `id = account.accountId,`
   - `storedAccountOf(credentials)` (plan 19): `id = credentials.accountId,`

3. **New file `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncAccountKey.kt`**
   (MPL header from a sibling) — the key and its predecessor, as pure functions so they can be tested:

   ```kotlin
   package com.pandulapeter.campfire.data.repository.implementation.sync

   import com.pandulapeter.campfire.data.model.domain.SyncAccount

   /**
    * What `sync-index.json` is filed under: the provider and the id the service itself gives the account. Nothing the
    * user can change is part of it, because an index that stops matching is an index that is ignored, and a run
    * without one brings back every file that was deleted since the last.
    *
    * An account whose id was never learnt falls back on what the key was made of before there was one.
    */
   internal fun SyncAccount.indexKey() = "${providerId.id}:${id.ifEmpty { email ?: displayName }}"

   /**
    * The key an index of this account was filed under before [indexKey] used the service's id: its e-mail address.
    *
    * Only the address, although the old key fell back on the display name where there was none. A display name is
    * not unique, so an index found under one proves nothing about whose it is - and the one time that fallback was
    * ever taken for a Dropbox account, the name stood in for the very id the key is made of now, so that index
    * matches as it is.
    */
   internal fun SyncAccount.legacyIndexKey() = email?.takeIf(String::isNotEmpty)?.let { "${providerId.id}:$it" }
   ```

4. **`sync/SyncIndexDocument.kt`** — one new member of the data class, after `toIndex()`, and one more paragraph in
   the class KDoc ("An index filed under the key an earlier version used is taken over rather than ignored, see
   [adoptedBy].").

   ```kotlin
   /**
    * This document as [account]'s, if it is: an index written when the key was still the account's e-mail address
    * is filed under the new key instead of being ignored, which would cost the user every deletion made since the
    * run that wrote it. What says it is the same account is the address itself - the services keep those unique,
    * the index is removed on disconnect, and so the only index that can be found under this account's address is
    * the one the previous version wrote for it, and would have accepted on exactly the same evidence.
    *
    * The new key is on disk with the next write, after which this has nothing left to do. An index that belongs to
    * another account comes back unchanged, for the engine to disregard as before.
    */
   fun adoptedBy(account: SyncAccount) = if (accountId.isNotEmpty() && accountId == account.legacyIndexKey()) {
       copy(accountId = account.indexKey())
   } else {
       this
   }
   ```

   (`accountId == account.indexKey()` needs no branch of its own: `copy` would change nothing.) Add
   `import com.pandulapeter.campfire.data.model.domain.SyncAccount`.

5. **`SyncRepositoryImpl`** — three small hunks; `SyncEngine` is **not** touched, it keeps comparing the key it is
   given with the document it is given.
   - Delete the private `accountIdOf(account)` and its KDoc; import
     `com.pandulapeter.campfire.data.repository.implementation.sync.indexKey`.
   - `runSynchronization`: `val document = loadIndex()` becomes

     ```kotlin
     // Taken over before the marker is written, so that an index filed under the key an earlier version used is
     // under the current one from the first write of this run, however the run ends.
     val document = loadIndex().adoptedBy(connected.account)
     ```

     and the engine call passes `accountId = connected.account.indexKey(),`.
   - `completePendingAuthorization`: the same account reconnecting (after a revoked grant, where the index is kept on
     purpose) must keep a legacy index too:

     ```kotlin
     val document = loadIndex().adoptedBy(account)
     if (document.accountId != account.indexKey()) {
         saveIndex(SyncIndexDocument())
     }
     ```

   `restore()` needs nothing: it only reads `lastSyncedAt` and the marker, which belong to the device, not the account.

6. **Do not** add a version field or a one-off migration pass at start up: the adoption is a pure function of the
   document and the account, runs where the index is read for a run anyway, and stops applying by itself once the
   key has been rewritten. Do not widen the legacy rule to the display name (see the KDoc in step 3).

   One case is knowingly left as it is: a user who changes their Dropbox e-mail address *between* the last run of the
   old version and the first run of the new one has an index under the old address and an account under the new one.
   It is ignored once, exactly as the old version would have done on that same launch.

## Tests
New `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncAccountKeyTest.kt`
(MPL header), with `account(id, displayName, email)` building a `SyncAccount` for `SyncProviderId.DROPBOX` and
`ENTRIES = mapOf("songs/a.cho" to SyncIndexDocument.Entry(localHash = "h", remoteRevision = "r"))`:

- `the key is the id of the account` — `account("dbid:1", "Jane", "jane@example.com").indexKey()` is `"dropbox:dbid:1"`.
- `the key does not change when the name is read for the first time` — `account("dbid:1", "dbid:1", null)` and
  `account("dbid:1", "Jane", "jane@example.com")` have the same `indexKey()` (the scenario above).
- `the key does not change with the e-mail address` — same id, two addresses, same key.
- `an account without an id is filed the way it used to be` — `account("", "Jane", "jane@example.com").indexKey()` is
  `"dropbox:jane@example.com"`; with `email = null` it is `"dropbox:Jane"`.
- `an index filed under the e-mail address is adopted` —
  `SyncIndexDocument(providerId = "dropbox", accountId = "dropbox:jane@example.com", lastSyncedAt = 42, entries = ENTRIES)
  .adoptedBy(account("dbid:1", "Jane", "jane@example.com"))` has `accountId == "dropbox:dbid:1"`, and `entries`,
  `lastSyncedAt` unchanged.
- `an index filed under the id before the name was read matches as it is` — document `accountId = "dropbox:dbid:1"`,
  same account: returned document `==` the input.
- `an index of another account is not adopted` — document `accountId = "dropbox:john@example.com"`: returned
  unchanged (so the engine disregards it).
- `an index filed under a display name is not adopted` — document `accountId = "dropbox:Jane"`: unchanged.
- `an empty index is not adopted` — `SyncIndexDocument()` with `account("dbid:1", "", null)`: unchanged.

Add one case to `SyncEngineTest` that ties it to what the user sees, reusing its existing fakes:
`a deletion made elsewhere reaches a device whose index was filed under the e-mail address` — the class's
`ACCOUNT_ID` is already of the old shape (`"dropbox:someone@example.com"`), so: local holds `song(1)` with `original`,
the remote is empty, `val account = SyncAccount(SyncProviderId.DROPBOX, id = "dbid:1", displayName = "Someone", email =
"someone@example.com")`; run `SyncEngine(local).synchronize(document = indexOf(song(1) to original).adoptedBy(account),
accountId = account.indexKey(), deletionPolicy = SyncDeletionPolicy.DELETE_LOCALLY, …)` and assert that
`local.files` is empty and `provider.files` is still empty. (Without `adoptedBy` the same input uploads the song.)

Update the `SyncAccount(...)` expectations plan 19 added to `DropboxRequestTest` with the `id` (`""` for the "Jane"
case unless its stored document gets an `accountId`; `"dbid:1"` for the never-read case).

## Verify
- `./gradlew :data:repository:implementation:desktopTest :data:source:remote:implementation:desktopTest`
- Migration, desktop: with a build from *before* this change, connect and sync, then check that
  `preferences/sync-index.json` has `"accountId": "dropbox:<your e-mail>"`. Delete a song in the Dropbox folder from
  the website. Start the new build: the run must delete the song locally (or ask, if it trips the deletion guard),
  must not upload it, and the file must now say `"accountId": "dropbox:dbid:…"` with its entries intact.
- Never-read name: connect with the new build while blocking `api.dropboxapi.com/2/users/get_current_account` (cut the
  network right after approving). Settings shows `dbid:…`; sync once the network is back; restart. The index key is
  the same before and after the name appears, and a song deleted remotely in between is deleted locally.
- `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
- `data/repository/implementation/CLAUDE.md`, the `sync/` bullet: after the sentence introducing `SyncIndexDocument`
  add "It is filed under the account's id as the service gives it (`SyncAccount.indexKey`), never under a name or an
  address the user can change; an index an earlier version filed under the e-mail address is adopted on the next
  run (`adoptedBy`) rather than ignored, since a run without an index brings back every file deleted since."
- `data/model/CLAUDE.md`: nothing (it lists `SyncAccount` without describing its fields).
- Root `CLAUDE.md`, Sync section: nothing becomes untrue.

## Touches
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/SyncState.kt`
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncAccountKey.kt` (new)
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncIndexDocument.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
  (`runSynchronization`: two lines; `completePendingAuthorization`: two lines; `accountIdOf` removed)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncAccountKeyTest.kt` (new)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/repository/implementation/CLAUDE.md`

## Depends on
19 (`storedAccount()` / `storedAccountOf` are one of the three places the id is filled in). In the same lane after
the sync writer's plans that edit `runSynchronization` (01, 06, 08, 16, 17, 23, 24): the hunk there is the
`val document = loadIndex()` line and the `accountId =` argument, wherever they have moved to.
