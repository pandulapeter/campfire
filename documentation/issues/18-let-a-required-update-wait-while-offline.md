<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Let the user past a required update while the device has no connection, until one returns

**Challenged:** amended — the gate's saved `isRequiredUpdateInTheWay` never resets, so a returning screen would skip the
unsaved-editor wait; the flag is saved unconditionally (Play's offline answer cannot be checked off a Play track);
internal logic keeps the real state; the callback runs on the main looper from a `DisposableEffect`; the reconnect check
and the clearing rule are spelled out; `EmptyState` gains per-action visibility animation; the useless fake test is
dropped; the permission is confirmed absent from the merged manifest.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** Android
**Lane:** A
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/update/AppUpdate.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/update/AppUpdateGate.kt`,
`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/update/AppUpdate.android.kt`, any new
pure-decision file beside `AppUpdateGate.kt` and its test in `presentation/src/commonTest/.../ui/update/`,
`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/update/DefaultNetworkWatcher.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/EmptyState.kt`,
`app/android/src/main/AndroidManifest.xml`, `presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/update/CLAUDE.md`

**Decided by the user (2026-10-19):** a required update may be waited out while offline.

## Problem

A Play release with `updatePriority` 4–5 puts up `AppUpdateRequiredScreen` (`AppUpdateGate.kt`), which covers the
whole app. `AppUpdate.android.kt` keeps it up through a failed flow:

```kotlin
isInProgress && isRequired -> AppUpdateState.Required
```

```kotlin
AppUpdateState.Required -> {
    hasStartedImmediateFlow = true
    // The blocking screen stays whatever happens here, and its button is the way to try again.
    if (!launchUpdateFlow(appUpdateInfo, launcher, AppUpdateType.IMMEDIATE)) checkForUpdate()
}
```

A player who starts the immediate update and then loses the connection (at a campfire, on stage) backs out of Play's
screen and is locked out of their own library until a network returns: Update reopens a download that cannot go
anywhere. The hint now says updating needs a connection (`app_update_required_hint`), but offers no way on.

## Fix

Show a second action, **Continue offline**, on the blocking screen only while the device has no validated network.
Tapping it lifts the gate until a connection returns and Play answers again (or the app is closed). Online, the screen
stays as it is: priority 4–5 exists for builds that damage the library, so being online and backing out of Play is not
a way past it.

What the code does today, which the steps below rely on:

- Within one activity, a failed check never drops `Required`: the failure listener only acts while
  `availableUpdate == null`, i.e. before Play has answered this controller once. After a recreation it does drop a
  carried-over `Required` if the first check fails. Whether Play's `appUpdateInfo` *fails* offline or answers from its
  cache (an in-progress immediate update, `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`, is local install state and very
  likely answers) cannot be found out off a Play track, so the plan does not depend on it.
- `AppUpdateGate`'s `isRequiredUpdateInTheWay` is `rememberSaveable` and is only ever set to true. Today that is
  harmless because the screen never goes away while the app runs; once it can, a `Required` that comes back would be
  shown at once, over unsaved editor text, skipping the wait the gate exists for.

1. **Contract** (`AppUpdate.kt`): add to `AppUpdateController` a `val canContinueOffline: Boolean` (true only while
   the update is required, not being waited out, and the device has no validated network) and
   `fun continueOffline()`; `NoAppUpdates` (the only other implementer: iOS, desktop, and web return it; no test fake
   or `tools/screenshots` code implements the interface) answers `false` / no-op. KDoc the rule above in the
   interface's voice.
2. **Network** (new `androidMain/.../ui/update/DefaultNetworkWatcher.kt`, keeping `AppUpdate.android.kt` (298 lines)
   about Play): a small class holding `isOnline` as Compose state.
   - The initial value is read synchronously when it is created (`getNetworkCapabilities(activeNetwork)` has
     `NET_CAPABILITY_VALIDATED`), so the blocking screen's first frame already has the right buttons.
   - `start()` calls `registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))` (API 26; minSdk is
     28), so every callback, and every Compose state write, is on the main thread. `onCapabilitiesChanged` sets
     `isOnline` from `VALIDATED`, `onLost` sets it false. `stop()` unregisters (guarded, idempotent). An `onBecameOnline`
     lambda fires only on a false → true transition (`onCapabilitiesChanged` repeats for the same network).
   - Started and stopped from the composition, not from `attachLauncher` (a `SideEffect`, run on every recomposition)
     nor from the constructor (a `remember` whose composition is abandoned never reaches `onDispose`, leaking the
     callback): `DisposableEffect(controller) { controller.startWatchingNetwork(); onDispose(controller::release) }`,
     with `release()` stopping the watcher.
   - Add `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` to
     `app/android/src/main/AndroidManifest.xml`, with a one-line comment in the file's existing voice (what it is for).
     Checked: no dependency merges it in today (`app/android/build/.../processDebugMainManifest/AndroidManifest.xml`
     has INTERNET and no ACCESS_NETWORK_STATE). It is a normal permission: granted at install, no prompt, no Data
     safety entry (nothing is collected or sent), not a network request (root `CLAUDE.md`'s privacy list is unchanged).
   - `VALIDATED` is what to trust: a captive portal or a network whose validation probe is blocked reads offline, so
     the button shows while Play could perhaps work (acceptable: the user chose offline use); a validated network on
     which Play still fails keeps the screen, as today.
3. **Android controller** (`AppUpdate.android.kt`):
   - A third saved flag beside the other two, `isWaitingForConnection = rememberSaveable { mutableStateOf(false) }`,
     saved unconditionally: an offline recreation may well get a cached `Required` from Play, and it would put the
     screen back up while still offline.
   - The saved `lastKnownState` stays the real state and every internal decision (`startUpdate`'s `when`,
     `startRequiredUpdateOnce`, `onUpdateFlowResult`, the `Downloading` guard in `onAppUpdateInfoReceived`) keeps
     reading it. Only the overridden `state` the gate sees reads
     `if (isWaitingForConnection && realState == Required) NotAvailable else realState`, so an optional update or a
     finished download found while waiting is still offered.
   - `continueOffline()` sets the flag only while `canContinueOffline`.
   - The watcher's `onBecameOnline` calls `checkForUpdate()` while the flag is set. `onAppUpdateInfoReceived` clears
     the flag only when `isOnline` is true at that moment, before setting the new state, so an answer Play gives
     offline (a resume check) does not put the screen back; an online answer decides again.
   - `hasStartedImmediateFlow` is left as it is, so the flow does not start on its own when the screen returns; the
     Update button does.
4. **Gate** (`AppUpdateGate.kt`):
   - Reset the in-the-way flag when the update stops being required:
     `if (state != AppUpdateState.Required) isRequiredUpdateInTheWay = false` in the same `LaunchedEffect`, so a
     screen coming back waits for unsaved editor text again. Rewrite the "Once the screen is up it stays up" comment and
     the gate's KDoc accordingly. A rotation is unaffected (the state and both flags are saved, so the first frame of the
     recreated activity still has the screen up).
   - `LocalIsCoveredByRequiredUpdate` follows `isRequiredScreenVisible`, so `CampfireScreens`' dialogs,
     `OverflowMenu`, and `TunerListeningEffect` come back by themselves when the screen goes; nothing to change there.
   - `AppUpdateRequiredScreen` takes `onContinueOffline: (() -> Unit)?` and shows a second action for it. Extract
     the action list into a pure `internal` function beside the gate (state of the screen in, `EmptyStateAction`s with
     a visibility flag out) so it is unit tested.
5. **`EmptyState`**: it has no animation today (actions are laid out once, in a `Column` portrait or a `FlowRow`
   landscape). The button appearing is not user-caused, but it is a change while the screen is up, not a first frame
   (that one is right at the source, step 2), so it is animated: give `EmptyStateAction` an `isVisible: Boolean = true`
   (a plain data class, so the code-style rule against defaults on Koin definitions does not apply) and wrap each button in
   `AnimatedVisibility` (fade plus expand/shrink). Put the gap into the animated item's own padding rather than
   `spacedBy`, so the row does not jump by the gap as the button arrives or leaves. The button stays outlined (second).
6. **Strings**: `app_update_continue_offline` = "Continue offline" / Hungarian "Folytatás offline", next to
   `app_update_update` in both files.
7. **Docs**: `ui/update/CLAUDE.md`'s 4–5 bullet gains: "Offline (no validated network), the blocking screen also offers
   Continue offline, which lifts it until a connection returns and Play answers again; online there is no way past
   it. A screen that comes back waits for unsaved editor text again." The "What Play last said is saved" bullet gains
   that the Continue offline choice is saved too. Keep the file under 600 words (453 now).

Rejected: offering a way past after any unsuccessful immediate flow (no permission needed), because an online user
could then always back out of Play and continue, which ends the policy of priority 4–5.

## Tests

- `presentation/src/commonTest/.../ui/update/`: the pure action-list function — `Required` and offline shows both
  actions; `Required` and online shows Update with Continue offline hidden.
- If the controller's two rules are pulled out as pure functions beside it in `commonMain` (the state the gate sees
  from the real state and the flag; whether an answer clears the flag from `isOnline`), test those: waiting hides only
  `Required`; an offline answer keeps the flag, an online one clears it. No fake controller: it would only test itself.
- The Play side and the network callback cannot be unit tested (`AppUpdateManager` answers only on an internal testing
  track).
