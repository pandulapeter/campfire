<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Show Connecting, with a working Cancel, while start up finishes a consent page's answer

**Challenged:** amended — the exchange is created lazily and published in `restoreExchange` *before* the state says
`Connecting`, so a Cancel that sees `Connecting` always finds the exchange to cancel (in the plan as written, a Cancel
landing between the two, with `restore` off the main thread, found nothing, set `Disconnected`, and the exchange then
connected anyway); a cancelled exchange that never started still has its pending authorization cleared by
`cancelConnection`. The "Manual check" section is removed.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** web; Android after process death
**Lane:** U
**Files:** `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/SyncConnectionManager.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/CLAUDE.md`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncRepositoryImpl.kt`,
`data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt`,
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/sync/RestoreSyncUseCase.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/sync/RestoreSyncUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/sync/SyncUseCaseImplsTest.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/RepositoryStubs.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/sync/SyncController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/sync/CLAUDE.md`,
`tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Fakes.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/SyncConnectionManagerTest.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/connection/SyncConnectionManagerForgettingTest.kt`

## Problem

On the web, the consent page sends the browser back to the app with `?code=…`, and the page loads afresh on Songs.
`SyncConnectionManager.restoreConnection` finishes the exchange without saying so:

```kotlin
val redirectUri = redirectKeptForRestore?.also { redirectKeptForRestore = null } ?: authenticator.consumePendingRedirect()
if (redirectUri != null && pendingAuthorizationStore.loadPendingAuthorization() != null) {
    return SyncRepository.RestoreResult(
        isConnected = completePendingAuthorization(redirectUri),
        didReturnFromAuthorization = true,
        wasInterrupted = false,
    )
}
```

The state holder starts at `SyncState.Disconnected` (`SyncStateHolder.kt`), and only after the exchange does the UI
learn that this was a return from consent (`presentation/.../ui/sync/SyncController.kt`, `startRestoring`):

```kotlin
if (restoreSync()) onConsentAnswered()
```

On a bad network, the token exchange (with lane T's retries) takes a minute or more. Meanwhile:

- Settings offers "Connect to Dropbox", with no progress and no Cancel. Tapping it starts a second authorization and
  navigates away mid-exchange.
- The screen the user came from (Settings, or the onboarding's Dropbox page) is put back only after the exchange, and
  only if the back stack is still the start stack (`syncConsentAnswerOf`). A user who opened anything meanwhile is left
  wherever they are.
- Android after the process was reclaimed behind the browser takes the same path.

Even once the state did say Connecting, Cancel wouldn't stop this exchange. `cancelConnection()` only clears the
pending authorization and sets the state, while the exchange carries on in `startRestoring`'s coroutine and then sets
`Connected` unconditionally, connecting an account the user just cancelled.

## Fix

1. **Connecting before the exchange.** In `restoreConnection`'s redirect branch, once `loadPendingAuthorization()`
   answers a pending authorization, set `stateHolder.update { SyncState.Connecting(pending.providerId) }` before calling
   `completePendingAuthorization`. Every exit of `completePendingAuthorization` already leaves `Connecting`: `Connected`
   on success, `stateHolder.fail(…)` on a refusal or a bad state, and `Disconnected` for "no authorization waiting"
   (unreachable here because of the check). Settings and the onboarding's `SyncStep` already draw `Connecting` with
   Cancel. No UI change is needed.
2. **Tell the UI at once.** Replace `RestoreResult.didReturnFromAuthorization` with a callback:
   `suspend fun restore(onReturnedFromAuthorization: () -> Unit): RestoreResult`, called right after step 1's state
   change (on the caller's coroutine, so on the main thread). Pass it through `SyncRepositoryImpl`,
   `RestoreSyncUseCase.invoke(onReturnedFromAuthorization: () -> Unit)` (which then returns `Unit`; update its KDoc),
   and `SyncController.startRestoring`: `restoreSync(onReturnedFromAuthorization = onConsentAnswered)`.
   `onConsentAnswered` (`navigator.showSyncConsentAnswer(takeSyncConsentOrigin())`) needs nothing from the outcome. It
   picks the screen, and that screen draws whatever the state is, Connecting now and the result later. Calling it before
   the exchange is what makes the start stack check in `syncConsentAnswerOf` hold, since the user has had no time to
   build on the stack. Drop the field from `RestoreResult` and from its KDoc, which moves to the parameter, and update
   the fakes (`RepositoryStubs.kt`, `tools/screenshots/.../Fakes.kt`) and the tests that build `RestoreResult`.
   (Alternative: keep the field and add the callback. That gives two answers to one question, so it is not
   recommended.)
3. **Cancel really cancels a restore exchange.** Run the exchange as a child the manager can cancel:

   ```kotlin
   val isConnected = coroutineScope {
       // Published before Connecting is shown: a Cancel that sees Connecting must find something to cancel.
       val exchange = async(start = CoroutineStart.LAZY) { completePendingAuthorization(redirectUri) }
       restoreExchange = exchange
       stateHolder.update { SyncState.Connecting(pending.providerId) }
       onReturnedFromAuthorization()
       try {
           exchange.await()
       } catch (exception: CancellationException) {
           currentCoroutineContext().ensureActive()  // the caller's own cancellation goes on
           false
       } finally {
           restoreExchange = null
       }
   }
   ```

   This is where step 1's state change and step 2's callback go, in that order, after the assignment. `await()` starts
   the lazy exchange. `cancelConnection()` becomes: `restoreExchange?.cancelAndJoin()`, then what it does today. An
   exchange cancelled before it started never cleared the pending authorization, which `cancelConnection`'s own
   `discardPendingAuthorization()` then does. The cancelled exchange
   already forgets whatever tokens the provider stored (`completePendingAuthorization`'s `CancellationException` branch:
   `forgetCredentialsOf(provider)`), and the pending authorization was cleared as the exchange began. The state then
   goes to `stateAfterBackingOut()`, which is `Disconnected` here, because `stateBeforeConnecting` is null on a fresh
   start. That matches the credentials just forgotten. `restore` answers `isConnected = false`, so no launch run starts.
   `restoreExchange` is a `@Volatile var` (or an `AtomicReference`), because Cancel comes from the main thread while
   `restore` may be on another. `restoreMutex` is not taken by `cancelConnection`, so the two can't deadlock.
4. Check `connect()`'s own exchange is unaffected. It runs inside `syncConnectionJob`, which
   `SyncController.cancelSyncConnection` already cancels, and `restoreExchange` is null then.

`connection/CLAUDE.md`, first bullet, add: "A start up carrying a consent page's answer shows `Connecting` (and tells the
UI, which puts the user back where they tapped Connect) before the token exchange; Cancel cancels that exchange and
forgets what it stored." `ui/sync/CLAUDE.md`: the answer's screen is shown when the redirect is found, not when the
exchange ends.

## Tests

`SyncConnectionManagerTest`, through `syncRepository(…)` as the redirect tests there do (`PENDING`, `TEST_REDIRECT`):

- `a start up finishing a consent page's answer shows connecting meanwhile`: `onCompleteAuthorization = { gate.await();
  TEST_ACCOUNT }`. Launch `restore(onReturnedFromAuthorization = { returned = true })` and `runCurrent()`. The state is
  `Connecting(DROPBOX)` and `returned` is true. Then `gate.complete(Unit)` and `runCurrent()`: `Connected`, with
  `isConnected = true`.
- `cancelling while start up finishes a consent page's answer ends disconnected and keeps nothing`: the same gate,
  never completed, with the fake storing credentials before it waits (`connected = true`). `cancelConnection()` →
  `Disconnected`, `provider.hasForgottenCredentials`, `store.pending == null`, and the `restore` call returns
  `isConnected = false` without throwing.
- `a start up without a consent page's answer never shows connecting and never calls back`: the existing
  restore cases, plus `returned` stays false.
- Update `a start up that returns from the consent page completes the connection`, `a consent page the user declined …`,
  and the forgetting tests for the new signature.

`SyncUseCaseImplsTest`: the callback is passed through, and a run starts only when connected and not interrupted, as
now.
