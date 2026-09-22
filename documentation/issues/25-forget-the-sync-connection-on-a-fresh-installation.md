# 25 — Forget the sync connection on a fresh installation

## What the user sees

On iOS. The user has Dropbox connected and a library of their own. They delete Campfire — to free space, to
reinstall it, to start again — and install it once more. The library is gone with the app, so the app plants the
demo library the way it does on any fresh installation. What is *not* gone is the Keychain item holding the refresh
token: Keychain items survive an uninstall. So the new installation starts connected, with no sync index, and its
first launch run uploads what it has into the user's Dropbox folder. Two demo songs and a demo setlist appear in
`Apps/Campfire`, and from there on every other device the user owns downloads them. The user never asked to
connect anything on this installation and never saw a consent page.

The mirror case is worse: if the user's remote folder still holds the four hundred songs they had, the reinstalled
app also downloads all of them, which is arguably what they wanted — but it happens without anyone connecting an
account, which is not something an app may decide on its own. The demo songs still go up either way.

## Cause

Keychain items are not deleted when an app is uninstalled, and `ThisDeviceOnly` does not change that — it only
keeps the item out of the backup and off other devices.

`data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/secret/SecretStore.ios.kt:103-108`,
verified at HEAD `984861e4`:

```kotlin
                val data = retained(NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding))
                val attributes = arrayOf<Pair<CFStringRef?, CFTypeRef?>>(
                    kSecValueData to data,
                    kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                )
```

with the service the item is filed under at `SecretStore.ios.kt:132-134`:

```kotlin
    private companion object {
        const val SERVICE = "com.pandulapeter.campfire.sync"
    }
```

Its own KDoc (`SecretStore.ios.kt:61-69`) states the intent, which holds for a *new device* and not for a reinstall
on the same one:

```kotlin
 * And bound to this device: an item that is not travels in the encrypted device backup and to a new iPhone, where it
 * would arrive without the sync index it belongs with. Android keeps the credentials out of its backup too, so a
 * restored installation starts disconnected on both.
```

The credentials are therefore all that is needed for
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:159`
to find a connection on the very first launch of the new installation:

```kotlin
        val connected = providers.firstOrNull { it.isConnected() }
```

`RestoreSyncUseCaseImpl`
(`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SyncUseCaseImpls.kt:78-89`)
then runs a first sync, because nothing was interrupted:

```kotlin
    override suspend operator fun invoke(): Boolean {
        val result = syncRepository.restore()
        // A run that was cut short is reported rather than repeated: an automatic run would overwrite the message
        // before it could be read, and the button that starts one is right under it.
        if (result.isConnected && !result.wasInterrupted) {
            synchronizeLibrary()
        }
        return result.didReturnFromAuthorization
    }
```

and the run has no index (`sync-index.json` went with the app's data), so every local file is new and is uploaded.

The two coroutines that would have to agree about this are started side by side in
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:733-745`:

```kotlin
    init {
        viewModelScope.launch { loadScreenData(false) }
        viewModelScope.launch { plantDemoLibraryOnFirstRun() }
        // Picks a connected account back up, finishes a consent the app was closed in the middle of, and runs a
        // first sync. Its own coroutine, so that a slow network never holds up the library appearing on screen.
        viewModelScope.launch {
```

and the first-run answer is read live, not held:
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/UserPreferencesRepositoryImpl.kt:33-37`:

```kotlin
    /**
     * Not cached, unlike the preferences themselves: it is asked once as the app starts, and it is the one answer
     * here that a cache would go on repeating after the very first save has made it false.
     */
    override suspend fun hasStoredUserPreferences() = userPreferencesLocalSource.hasStoredUserPreferences()
```

`plantDemoLibraryOnFirstRun` writes the preferences at its end (`CampfireViewModel.kt:1430`:
`saveUserPreferences(userPreferences.filterNotNull().first())`), so anything that asks the question again later gets
a different answer. That is why the fix cannot simply ask `IsFirstRunUseCase` a second time from the sync
coroutine: the two would race, and a slow library scan would decide it.

### The other three platforms do not have this

Verified, and worth stating in the plan because it is why the fix lives in `commonMain` but only ever fires on iOS:

- **Android** — `AndroidSecretStore` writes `preferences/sync-credentials.bin` into the app's own files directory
  and encrypts it with a Keystore key (`SecretStore.android.kt:35-56`). Uninstalling deletes the files directory
  *and* the app's Keystore entries, and `app/android/src/main/res/xml/data_extraction_rules.xml` is an allow-list
  that names only `library/` and `preferences/preferences.json`, so neither the credentials nor the index come
  back from a backup either.
- **Desktop** — nothing is deleted by an uninstall at all: `~/Library/Application Support/Campfire` (or its
  Windows/Linux equivalent) keeps the library, the preferences, the credentials *and* `sync-index.json` together.
  The installation that comes back is not a first run by any measure, and its index matches its library, so there
  is nothing to fix. A user who deletes the data directory by hand deletes all four together.
- **Web** — the credentials are `preferences/sync-credentials.json` in OPFS. Clearing the site's data, or the
  browser evicting the origin, removes the preferences and the credentials in one go.

So the wipe is a no-op everywhere but iOS. It goes in `commonMain` all the same: the rule is about what a fresh
installation may inherit, not about the Keychain, and a platform that one day keeps something across an uninstall
should be covered by it without anybody remembering to.

## The change

On a launch that finds no preferences document — the same question the demo library is planted on — forget any
stored sync credentials and any unfinished authorization **before** `restore()` is allowed to read them. Nothing is
revoked and no request is made: this is a local wipe, and the app has not been given permission to touch the network
on a first run.

### 1. The first-run answer becomes one value, asked once

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, declared
immediately above the `init` block (so that it is started before the coroutines that read it):

```kotlin
    /**
     * Whether this is the first launch of this installation, asked once and shared. It stops being true the moment
     * the preferences are written, which is the last thing the demo library planting does, so a second caller
     * asking the question again would be answered by whichever coroutine got there first. Two of them need it: the
     * demo library, and the sync connection a reinstalled app must not inherit (see [forgetSyncConnection]).
     *
     * An answer that could not be read is false: neither caller may act on a guess.
     */
    private val isFirstLaunch = viewModelScope.async {
        try {
            isFirstRun()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not tell whether this is a first run: ${exception.message}")
            false
        }
    }
```

`plantDemoLibraryOnFirstRun` then reads it instead of calling the use case:

```kotlin
            if (isFirstLaunch.await()) {
```

`kotlinx.coroutines.async` and `kotlinx.coroutines.Deferred` are new imports; `isFirstRun` stays a constructor
parameter and is now used only here.

### 2. The sync coroutine wipes before it restores

Same file, inside the existing `try` of the sync coroutine in `init`, before `if (restoreSync())`:

```kotlin
                // A reinstall is the one case where credentials outlive the library: iOS leaves the Keychain item
                // behind while everything else goes, so a fresh installation would find itself connected to an
                // account nobody connected here, and its first run would upload the demo library into the user's
                // folder. In this coroutine rather than beside it, because it is the one thing that must happen
                // before restore() reads those credentials.
                if (isFirstLaunch.await()) forgetSyncConnection()
                if (restoreSync()) {
```

The existing `catch` around it already keeps any failure from holding up the library.

### 3. The use case

`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/ConnectSyncProviderUseCase.kt` —
the file the sync use cases already share:

```kotlin
interface ForgetSyncConnectionUseCase {

    /**
     * Drops whatever this device has stored about a sync account, without telling the service anything: for an
     * installation that has just been made, credentials left behind by the previous one belong to nobody.
     * Different from [DisconnectSyncProviderUseCase], which is the user disconnecting and does revoke the token.
     */
    suspend operator fun invoke()
}
```

`domain/implementation/.../SyncUseCaseImpls.kt`:

```kotlin
@Factory
class ForgetSyncConnectionUseCaseImpl internal constructor(
    private val syncRepository: SyncRepository,
) : ForgetSyncConnectionUseCase {

    override suspend operator fun invoke() = syncRepository.forgetStoredConnection()
}
```

### 4. The repository

`data/repository/api/.../SyncRepository.kt`:

```kotlin
    /**
     * Forgets the credentials, the unfinished authorization and the index this device has stored, and makes no
     * request while doing it. For a fresh installation that finds a previous one's credentials still in the
     * platform's store - the iOS Keychain outlives an uninstall - where connecting is not something the user of
     * this installation has done. [disconnect] is the other one: that is the user disconnecting, and it tells the
     * service so.
     */
    suspend fun forgetStoredConnection()
```

`data/repository/implementation/.../SyncRepositoryImpl.kt`:

```kotlin
    override suspend fun forgetStoredConnection() = restoreMutex.withLock {
        withContext(NonCancellable) {
            providers.forEach { provider ->
                try {
                    provider.forgetStoredCredentials()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    println("Could not forget the ${provider.id} credentials: ${exception.message}")
                }
            }
            // A build with no provider at all still has to lose an authorization written down by one that had one.
            discardPendingAuthorization()
            // There cannot be an index on a fresh installation, and one that is somehow there describes a folder
            // this installation has never looked at.
            try {
                syncStateLocalSource.saveSyncIndex(null)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                println("Could not clear the sync index: ${exception.message}")
            }
            _syncState.update { SyncState.Disconnected }
        }
    }
```

`restoreMutex` is what makes this unable to race `restore()` even if a second `CampfireViewModel` (Android creates
one per Activity) asks for a restore at the same moment: whichever gets the lock first, the wipe is over before any
restore reads anything, because the only caller runs it in the same coroutine that then restores. `NonCancellable`
for the same reason `disconnect` uses it: half a wipe is worse than none.

### 5. The provider's local-only forget

The credentials and the unfinished authorization live in one document behind `SyncCredentialsStore`, which is
`internal` to `:data:source:remote:implementation`, so the repository cannot clear it directly and the wipe has to
go through the provider.

`data/source/remote/api/.../SyncProvider.kt`, next to `disconnect`:

```kotlin
    /**
     * Forgets the stored credentials and tells the service nothing. [disconnect] is the user's disconnect, which
     * also revokes the token; this is an installation dropping what a previous one left in a store that outlived
     * it, where there is nobody to revoke on behalf of and no permission to make a request.
     */
    suspend fun forgetStoredCredentials()
```

`data/source/remote/implementation/.../dropbox/DropboxSyncProvider.kt` — one line, next to `disconnect`:

```kotlin
    override suspend fun forgetStoredCredentials() = credentialsStore.save(null)
```

`credentialsStore.save(null)` replaces the whole document, so the `pending` half goes with it and the store's own
cache is updated rather than left stale — which is the reason for going through the provider instead of writing
`syncStateLocalSource.saveSyncCredentials(null)` from the repository.

### Rejected alternatives

- **Asking `IsFirstRunUseCase` from `RestoreSyncUseCaseImpl`.** Simpler, and wrong: the demo planting writes the
  preferences at its end, so what the sync coroutine would read depends on how long the library scan took. Races
  are answered with state — one answer, shared — not with who gets there first.
- **Calling `disconnect()`.** It revokes the token over the network. A first run must make no request, and the
  account is not the user's to disconnect from this installation.
- **Deleting the Keychain item from the iOS shell at start up.** It would work and it would be iOS-only knowledge
  in the app module, with no way for the repository to know its cached state had been emptied underneath it.

## Tests

`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`,
with `FakeSyncProvider` (`sync/FakeSyncProvider.kt`) gaining `forgetStoredCredentials()`, which sets
`connected = false` and records that it was called — and must *not* run `onDisconnect`, which is how a test tells
the two apart:

1. `forgetting the connection leaves nothing to restore` — a provider with `connected = true` and an account, an
   index document in the fake local source. Call `forgetStoredConnection()`, then `restore()`, and assert the
   result is `RestoreResult(isConnected = false, …)`, the state is `SyncState.Disconnected` and the fake local
   source's index is null.
2. `forgetting the connection tells the service nothing` — set `onDisconnect` to fail the test, call
   `forgetStoredConnection()`, assert it is not called.
3. `forgetting the connection drops an unfinished authorization` — store a pending authorization in the fake
   `PendingAuthorizationStore`, wipe, assert `loadPendingAuthorization()` is null.
4. `an ordinary launch does not forget anything` — `restore()` on its own still finds the account, which is the
   regression this could cause.

The view model half (the shared `isFirstLaunch`, the order inside the sync coroutine) is untested by policy: the UI
is untested and `CampfireViewModel` is part of it. The ordering it relies on is structural — the wipe and the
restore are two statements in one coroutine — rather than timed, which is what makes it reviewable without a test.

## Verification

```
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:android:assembleDebug
./gradlew :app:desktop:run
./gradlew :app:web:wasmJsBrowserDistribution
```

Manual, **needs an iOS device or simulator and a Dropbox account** — this is the whole point of the plan:

1. Install the app, connect Dropbox in Settings, let a first sync finish. Confirm the folder in Dropbox holds the
   library.
2. Delete the app from the home screen. Install it again from Xcode (deleting the app is what leaves the Keychain
   item; a "clean build" does not).
3. On the first launch, Settings must offer to **connect** an account rather than showing one, no run may start,
   and the Dropbox folder must be **unchanged** — in particular it must not have gained `demo` songs.
4. Connect again in Settings: the account comes back normally, and the run that follows downloads the library.
5. Repeat step 2 with a library the user has filled (not a fresh one): the reinstalled app plants the demo library
   as usual and stays disconnected.

Manual, **needs Android** (regression): install a debug build, connect Dropbox, uninstall, install again. It must
behave exactly as before — disconnected, demo library planted, nothing uploaded.

Manual, **desktop** (regression): connect Dropbox, quit, start again. The account must still be connected: the
desktop data directory survives, so this is not a first run and nothing may be wiped.

## Docs

`CLAUDE.md` (root), the library layout section — true for a new device, not for a reinstall, and the sentence is
what this plan makes true in both cases:

> On Android and iOS `library/` and `preferences/preferences.json` are in the system backup and the transfer to a new
> device; the sync credentials and `sync-index.json` are not, so a restored installation starts disconnected and its
> first sync run compares by content.

Extend it: a reinstall starts disconnected too, because a launch that finds no preferences document forgets any
credentials it finds — the iOS Keychain outlives an uninstall, and nothing else does.

`CLAUDE.md` (root), the Sync section — add the rule in one sentence, next to the deletion guard, since it is the
same kind of rule: what a run is not allowed to do on the strength of something the user did not do here.

`data/source/local/implementation/CLAUDE.md`, the `SecretStore` bullet:

> `IosSecretStore` is a Keychain generic password, readable after the first unlock because a background sync may
> need it on a locked device, and bound to the device (`AfterFirstUnlockThisDeviceOnly`) so that it stays out of the
> backup, as Android's does

Add: the item survives an uninstall, which is why a first launch forgets it.

`SecretStore.ios.kt`'s own KDoc, quoted above, says "a restored installation starts disconnected on both" — it has
to name what makes that true on iOS now.

`data/source/remote/api/CLAUDE.md` — the `SyncProvider` bullet gains `forgetStoredCredentials` next to `disconnect`,
with the difference between them.

`data/repository/api/CLAUDE.md` and `domain/api/CLAUDE.md` — the sync lists there gain the new method and the new
use case.

`documentation/sync.md` — one user-facing sentence: reinstalling the app disconnects it, and connecting again is a
tap in Settings.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/ConnectSyncProviderUseCase.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SyncUseCaseImpls.kt`
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncProvider.kt`
- `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`, `data/source/remote/api/CLAUDE.md`,
  `data/repository/api/CLAUDE.md`, `domain/api/CLAUDE.md`, `documentation/sync.md`
- `data/source/local/implementation/src/iosMain/.../storage/secret/SecretStore.ios.kt` (KDoc only)

## Depends on

Nothing. It adds a method to `SyncProvider` and to `SyncRepository`, so it touches the same two interfaces as any
plan in the sync lane — land one and rebase the other. No new strings, so no `strings.xml` conflict.

## Rules

- Load the `code-style` skill before the first edit and keep it loaded: MPL header on any new file, KDoc on
  declarations and `//` inside statements, "why, not what", trailing commas.
- `commonMain` stays JVM-free — everything here is pure Kotlin and `kotlinx.coroutines`.
- No new UI strings. If one is ever added for this, it goes into **both** `values/strings.xml` and
  `values-hu/strings.xml`.
- The race is answered with one shared answer (`isFirstLaunch`) and with the order of two statements in one
  coroutine, never with a delay or a debounce window.
- Unit tests are run with the command in the root `CLAUDE.md`, quoted under Verification.
- The per-module `CLAUDE.md` files are part of the change, not a follow-up.
