# 21 · Web: coming back from the Dropbox consent page with Back leaves Settings on "Connecting…", and Cancel does nothing

**Severity:** minor (web; whenever the browser hands the page back as it was left — the back/forward cache, or a navigation stopped before it commits; reasoned from the code, not reproduced in a browser) · **Area:** `:data:repository:api` / `:implementation` (`SyncRepository`, `SyncRepositoryImpl`), `:domain:api` / `:implementation` (new `CancelSyncConnectionUseCase`), `:presentation` (`CampfireViewModel.cancelSyncConnection`)

## Symptom
1. Web build, Settings → Library → **Connect to Dropbox**. The page starts navigating to Dropbox's consent screen.
2. Either press the browser's Stop button (or Escape) before Dropbox's page has loaded, or let it load and press
   **Back**. In the first case the app never went away; in the second Safari and Firefox (and Chrome, where nothing
   on the page rules it out) restore the page from the back/forward cache exactly as it was left, without running
   the app's start up again.
3. Settings shows "Connecting…" with a **Cancel** row under it. Cancel does nothing, however often it is pressed,
   and the Connect button never comes back. Only reloading the page gets out of it.

The comment above that Cancel row (`SyncSettings.kt:169-170`) states the rule this breaks: "this state must never be
one the user has to restart the app to leave".

## Cause
On the web `connect()` finishes while the state is still `Connecting`, on purpose — the app is about to stop
existing. `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt:156-166`:

```kotlin
_syncState.update { SyncState.Connecting(providerId) }
return try {
    …
    when (val outcome = authenticator.authorize(request.authorizationUrl, completionPage)) {
        is SyncAuthenticator.AuthorizationOutcome.Received -> completePendingAuthorization(outcome.redirectUri)
        // The app is on its way to the consent page; whatever it says arrives at the next start up.
        SyncAuthenticator.AuthorizationOutcome.Redirected -> false
```

`WebSyncAuthenticator.authorize` (`data/source/remote/implementation/src/wasmJsMain/.../auth/SyncAuthenticator.wasmJs.kt:36-42`)
is `window.location.assign(url)` followed by `return Redirected`, so the coroutine is over long before the page is.
The only way out of `Connecting` that the UI has is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1447-1451`:

```kotlin
/** Gives up on an authorization that is waiting, which is the way out of a browser the user closed. */
fun cancelSyncConnection() {
    syncConnectionJob?.cancel()
    syncConnectionJob = null
}
```

which relies on `connect()`'s `catch (exception: CancellationException)` branch to clear the pending authorization
and set `Disconnected`. A job that has already completed cannot be cancelled, so that branch never runs. Whenever
the page survives the navigation, the state is one nothing can change.

The same dead end is reachable on every platform by any `connect()` that ends without resetting the state (plan 22
closes the one known way of doing that), which is why the fix is in the repository rather than in the web shell.

## Fix
The cancel button asks the repository to leave `Connecting`, instead of relying on a job still being there to cancel.

1. **`:data:repository:api`, `SyncRepository`** — a new member between `connect` and `disconnect`:

   ```kotlin
   /**
    * Gives up on an authorization that is waiting: forgets what was written down for it and leaves
    * [SyncState.Connecting]. Does nothing in any other state.
    *
    * Cancelling the caller's own [connect] does the same on its way out, and is still how the platform's half of
    * the wait is ended - the sheet on iOS, the desktop's socket. This is for the [connect] that is no longer there
    * to be cancelled: on the web it returns as soon as the page starts to navigate away, and a page the browser
    * then hands back as it was left (Back out of the consent page, a navigation that was stopped) is still
    * connecting with nothing going on behind it. A caller that does have a [connect] running cancels it and waits
    * for it first.
    */
   suspend fun cancelConnection()
   ```

2. **`SyncRepositoryImpl`** — implement it directly under `connect()`:

   ```kotlin
   override suspend fun cancelConnection() {
       if (_syncState.value !is SyncState.Connecting) return
       try {
           pendingAuthorizationStore.clearPendingAuthorization()
       } catch (exception: CancellationException) {
           throw exception
       } catch (exception: Exception) {
           // What stays behind is a verifier nothing will ask for again, and the next authorization writes over
           // it. Not a reason to keep the user on a screen whose only button would then do nothing.
           println("Could not clear the pending authorization: ${exception::class.simpleName}")
       }
       _syncState.update { if (it is SyncState.Connecting) SyncState.Disconnected else it }
   }
   ```

   - Only the class name is logged, like everywhere else the credentials document is involved: a storage failure's
     message may quote what it failed on.
   - The state is checked again inside `update` because the clear suspends, and a redirect that was completed in
     the meantime must not be turned back into `Disconnected`.
   - Do **not** touch `connect()` here. `Redirected -> false` with the state left at `Connecting` is right: while the
     page is on its way to Dropbox, "Connecting…" is the truth. Plan 22 (executed after this one) replaces the
     inline `try` above with the helper it introduces for `connect()`'s own clean-up.
   - Do **not** solve this in `wasmJsMain` with a `pageshow` listener that resets the state: it would cover the
     back/forward cache and not the stopped navigation, and it would need a way from the authenticator into the
     repository's state that nothing else has.

3. **`:domain:api`, `useCases/ConnectSyncProviderUseCase.kt`** — a new interface after `DisconnectSyncProviderUseCase`:

   ```kotlin
   interface CancelSyncConnectionUseCase {

       /**
        * Gives up on an authorization that is waiting, whether or not the [ConnectSyncProviderUseCase] call that
        * started it is still running - on the web it never is. The caller cancels its own call first.
        */
       suspend operator fun invoke()
   }
   ```

4. **`:domain:implementation`, `useCases/SyncUseCaseImpls.kt`** — after `DisconnectSyncProviderUseCaseImpl`, plus the
   import of the interface in the file's alphabetical import block. `DomainModule`'s component scan finds it; nothing
   is added to a `Module.kt`.

   ```kotlin
   @Factory
   class CancelSyncConnectionUseCaseImpl internal constructor(
       private val syncRepository: SyncRepository,
   ) : CancelSyncConnectionUseCase {

       override suspend operator fun invoke() = syncRepository.cancelConnection()
   }
   ```

5. **`CampfireViewModel`** — add `private val cancelSyncConnection: CancelSyncConnectionUseCase,` to the constructor
   right after `disconnectSyncProvider` (`:147`; the property shares its name with the function below, the way
   `connectSyncProvider` and `disconnectSyncProvider` already do, which is why they are called with `.invoke`), add
   the import, and replace `cancelSyncConnection()` (`:1447-1451`) with:

   ```kotlin
   /**
    * Gives up on an authorization that is waiting, which is the way out of a browser the user closed. Cancelling
    * the job is what ends the platform's half of the wait; the repository is asked as well because on the web the
    * job is over as soon as the page starts to navigate away, and a page the browser hands back as it was left is
    * still connecting with nothing to cancel.
    */
   fun cancelSyncConnection() {
       val connection = syncConnectionJob
       syncConnectionJob = viewModelScope.launch {
           // Joined first, so that the clean up of an attempt that is being given up on cannot land on the next
           // one: until it is over this job is the active one, and connectSyncProvider() refuses to start another.
           connection?.cancelAndJoin()
           cancelSyncConnection.invoke()
       }
   }
   ```

   Add `import kotlinx.coroutines.cancelAndJoin` (after `NonCancellable`; the file does not have it). Also update the KDoc of
   `syncConnectionJob` (`:1432-1435`), whose job it now describes only half of:

   ```kotlin
   /**
    * Kept so that it can be cancelled: an authorization waits on a browser that may never come back, and the
    * cancellation is what closes the sheet on iOS and releases the desktop's socket. While an attempt is being
    * given up on, this is the job doing that, so that the next attempt waits for it.
    */
   ```

   `connectSyncProvider()` needs no change: its `syncConnectionJob?.isActive == true` guard now also covers the few
   milliseconds of clean-up, during which the Connect button is not on screen anyway.
   `cancelConnection()` cannot throw anything but a cancellation (step 2), so the bare `launch` is safe.

6. `SyncSettings.kt` needs no change — the Cancel row already calls `viewModel::cancelSyncConnection`.

What the user sees afterwards: Cancel puts the Connect button back on every platform, job or no job. On the web, a
user who cancels and then presses the browser's **Forward** and approves after all comes back to a page that has no
pending authorization; `completePendingAuthorization` already answers that with `Disconnected` (and with plan 11,
`restore()` ignores the redirect outright), so they see the Connect button and press it again. That is the correct
reading of "Cancel".

## Tests
`SyncRepositoryImplTest` (the harness plan 06 adds in `:data:repository:implementation`). Three small additions to
its fakes first:
- `FakeSyncProvider.buildAuthorizationRequest` returns
  `RemoteAuthorizationRequest(authorizationUrl = "https://example.com/authorize", redirectUri = redirectUri, state = "state", verifier = "verifier")`
  instead of throwing (nothing relies on it throwing).
- `FakeSyncAuthenticator` takes `private val outcome: SyncAuthenticator.AuthorizationOutcome? = null`; `authorize`
  returns it, and keeps throwing `UnsupportedOperationException` when it is null.
- `FakePendingAuthorizationStore` remembers what it is given: `var pending: PendingAuthorization? = null`, set by
  `savePendingAuthorization` (from the provider id and the request), returned by `loadPendingAuthorization`, nulled
  by `clearPendingAuthorization`. Let the test class's `repository(...)` helper take the authenticator and the
  store as defaulted parameters.

Cases:
- `` `a connection that was redirected away can still be cancelled` `` — authenticator with
  `AuthorizationOutcome.Redirected`; `connect(SyncProviderId.DROPBOX, AuthorizationCompletionPage(title = "", message = ""))` returns
  false and `syncState.first()` is `Connecting(DROPBOX)` with `store.pending != null`; then `cancelConnection()`;
  expect `SyncState.Disconnected` and `store.pending == null`. Fails before the fix only in the sense that the
  member does not exist; it pins the behaviour the view model now relies on.
- `` `cancelling a connection leaves every other state alone` `` — `restore()` with a provider whose account is set
  (plan 06's `FakeSyncProvider(account = …)`), so the state is `Connected`; put a pending authorization into the
  store by hand; `cancelConnection()`; expect the state still `Connected` and `store.pending` untouched.

The view model and the use case are a line each and untested like the rest of their modules.

## Verify
- `./gradlew :data:repository:implementation:desktopTest`
- Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`, built with a `campfire.dropbox.appKey`), in Safari and
  Firefox, and in Chrome with DevTools → Application → Back/forward cache open to see whether the page was eligible:
  1. Settings → Library → Connect to Dropbox; on Dropbox's page press Back. If the app comes back without its
     loading screen (restored from the cache) it shows "Connecting…": press **Cancel** — the Connect button must
     return. Press Connect again and finish the consent: it must connect normally.
  2. Throttle the network (DevTools → Network → Slow 3G), press Connect and press Escape / Stop before Dropbox's
     page appears: same check.
  3. If the page reloads on Back instead (not cached), it must simply start up disconnected, as today.
- Desktop (`./gradlew :app:desktop:run`): Connect, close the browser tab, press Cancel — the Connect button returns
  at once and the loopback socket is released (a second Connect opens the browser again). Press Cancel and Connect
  in quick succession: exactly one browser tab opens per Connect, and the state ends as `Connecting`.
- iOS simulator: Connect, then dismiss the consent sheet with its own Cancel (the app's row is behind the sheet and
  cannot be reached): unchanged behaviour, the Connect button returns.
- `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`
  (the Koin compiler plugin checks there that the new use case resolves).

## Docs
- `data/repository/api/CLAUDE.md`, the `SyncRepository` bullet: the member list becomes `restore` / `connect` /
  `cancelConnection` / `disconnect` / `synchronize` / `cancelSynchronization`.
- `data/repository/implementation/CLAUDE.md`, the sync paragraph, after the sentence about disconnecting: add
  "`cancelConnection` is the way out of `Connecting` that does not need the `connect()` that got there to be
  running still — on the web it never is, and a page restored from the back/forward cache is otherwise connecting
  for good."
- `domain/api/CLAUDE.md`, "Sync adds …": add `CancelSyncConnectionUseCase` to the list.
- `domain/implementation/CLAUDE.md`, the `SyncUseCaseImpls.kt` bullet: "all seven sync use cases" becomes "all eight".
- `presentation/CLAUDE.md`, the `SyncSettings.kt` bullet: after "pressing it again moves on to `Connecting`, which
  clears the message" add "and `Connecting` is left through its Cancel row whether or not the attempt that got there
  is still running — on the web it never is".

## Touches
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
  (new `cancelConnection` under `connect`; no existing function is edited)
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImplTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncCollaborators.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/FakeSyncProvider.kt`
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/ConnectSyncProviderUseCase.kt`
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SyncUseCaseImpls.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
  (constructor, `syncConnectionJob` KDoc, `cancelSyncConnection`)
- `data/repository/api/CLAUDE.md`
- `data/repository/implementation/CLAUDE.md`
- `domain/api/CLAUDE.md`
- `domain/implementation/CLAUDE.md`
- `presentation/CLAUDE.md`

## Depends on
06 (the `SyncRepositoryImplTest` harness and `FakeSyncCollaborators.kt` the tests extend). In the same lane after
the sync writer's plans on `SyncRepositoryImpl.kt` (01, 06, 08, 16, 17, 23, 24) and after 07, 11, 19 and 20; none of
them touches `connect()` or adds a member next to it, so the new function does not conflict. Plan 22 comes after
this one and folds the `try` of step 2 into its helper. Plan 49's `pageshow` handler (the web lock is taken again
after a back/forward cache restore) is what keeps such a restored page usable at all; the two are independent.
