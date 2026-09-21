# 11 · Android: connecting Dropbox silently fails when the app was killed while the user was in the browser

**Severity:** wrong behaviour (android; low-memory devices and 2FA detours, where it can repeat on every attempt) · **Area:** `:data:source:remote:implementation` (`AndroidSyncAuthenticator`), `:data:repository:implementation` (`SyncRepositoryImpl.restore`), `:app:android` (`CampfireActivity`)

## Symptom
1. On a phone short of memory (or with *Developer options → Background process limit → No background processes*),
   open Settings → **Connect to Dropbox**.
2. The browser opens Dropbox's login. Switch to an authenticator or SMS app for the 2FA code, come back, approve.
   Campfire's process has been reclaimed in the meantime.
3. Dropbox redirects to `campfire://oauth?code=…`, which starts Campfire again. The app comes up **Disconnected**:
   no account, no error, right after Dropbox said yes. The single-use code is thrown away; pressing Connect starts
   the whole thing again, and on such a device it can end the same way every time.

## Cause
The Android authenticator is modelled as "the redirect always reaches the process that asked for it":
`data/source/remote/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncAuthenticator.android.kt:82-83`

```kotlin
/** Android delivers the redirect to the running app, so there is never one waiting at start up. */
override suspend fun consumePendingRedirect(): String? = null
```

After process death `CampfireActivity.handle()` (`app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt:92-99`)
still forwards the redirect to `AndroidSyncAuthenticator.onRedirectReceived`, which `trySend`s it into the conflated
`redirects` channel of the companion — but the `authorize()` coroutine that was receiving from it died with the old
process. `SyncRepositoryImpl.restore()` (`SyncRepositoryImpl.kt:103-112`) asks `consumePendingRedirect()`, gets
`null`, finds no credentials and reports `Disconnected`.

Everything needed to finish is there: `connect()` writes the pending authorization (PKCE verifier and `state`) through
the Keystore-backed store *before* the browser opens (`SyncRepositoryImpl.kt:160-162`), a killed process runs no
`CancellationException` handler that would clear it, and `restore()` already completes a pending authorization from
`consumePendingRedirect()` — it is the web's ordinary path.

### How the redirect reaches a new process (this decides the ordering in the fix)
- **The task was removed** (swiped away from the recents while in the browser): the redirect intent creates the
  activity afresh — `onCreate(savedInstanceState = null)` with `getIntent()` = the redirect.
- **The task survived and only the process died** (the common case): the system relaunches the `singleTask` activity
  from its record — `onCreate(savedInstanceState != null)` with `getIntent()` = the *original* launch intent — and
  hands the redirect over as a pending new intent, which `ActivityThread.performResumeActivity` delivers to
  `onNewIntent` *before* `onResume`.

In both cases the redirect is in the channel before `restore()` asks for it: `setContent` only creates the
composition once the `ComposeView` is attached to the window, which happens in the first traversal after `onResume`;
the first composition creates `CampfireViewModel`, whose `init` launches `restoreSync()`. Today `handle(intent)` sits
*after* `setContent`, which is correct only because of that Compose detail; the fix makes the first case independent
of it.

### What a re-delivered redirect intent does (the reviewer's "stays harmless" is not quite true)
`onNewIntent` calls `setIntent(intent)`, so after a redirect the activity's intent *is* the redirect, and Android
hands it to `onCreate` again on every recreation: rotation (the view model survives, nobody asks — harmless), but
also after Back has finished the activity and the task is reopened from the recents
(`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`), or with *Don't keep activities*. Those create a **new** view model, so
`restore()` runs again in the same process, and with `consumePendingRedirect()` returning the replayed URI it would
take this path:

```kotlin
authenticator.consumePendingRedirect()?.let { redirectUri ->
    return SyncRepository.RestoreResult(
        isConnected = completePendingAuthorization(redirectUri),   // pending == null -> Disconnected, false
        didReturnFromAuthorization = true,
```

`completePendingAuthorization` finds no pending authorization (it was cleared when the code was spent), sets
`SyncState.Disconnected` and returns — and `restore()` returns with it, *without ever looking at the stored
credentials*. A connected user would see "Disconnected" and be thrown to Settings until the process restarts. The
same is true today on the web for any URL that carries a stale `?code=` (a bookmark, a shared link). So the
repository has to treat a redirect nothing is waiting for as no redirect at all (step 2), whatever plan 04 does about
replayed intents.

## Fix
1. **`SyncAuthenticator.android.kt`, `AndroidSyncAuthenticator.consumePendingRedirect`** — hand out what is waiting
   in the channel:

   ```kotlin
   /**
    * The redirect that arrived with nobody waiting for it. The browser the consent page opens in pushes Campfire into
    * the background, and on a device short of memory the process is gone by the time the service answers: the
    * redirect then starts a new one, in which [authorize] is not running. The activity has put it into [redirects]
    * by the time start up asks, and the pending authorization was written down before the browser opened, which is
    * all that finishing it takes.
    */
   override suspend fun consumePendingRedirect(): String? = redirects.tryReceive().getOrNull()
   ```

   `discardStaleRedirects()` stays: it still protects `authorize()` from a redirect that arrived after an attempt was
   given up on. Nothing else in the class changes.

2. **`SyncRepositoryImpl.restore()`** — only the first statement of the function (plan 17 adds its early return
   *after* this check; keep that order). Replace the `authenticator.consumePendingRedirect()?.let { … }` block with:

   ```kotlin
   // A consent page the app was sent away to, answered while it was not running - the web's ordinary case, and
   // Android's once the process was reclaimed behind the browser. Checked first because it decides what the
   // stored credentials are about to become. A redirect that no authorization is waiting for answers nothing -
   // Android hands a spent one over again when a finished task is reopened from the recents - and must not keep
   // the account that is connected from being restored below.
   val redirectUri = authenticator.consumePendingRedirect()
   if (redirectUri != null && pendingAuthorizationStore.loadPendingAuthorization() != null) {
       return SyncRepository.RestoreResult(
           isConnected = completePendingAuthorization(redirectUri),
           didReturnFromAuthorization = true,
           wasInterrupted = false,
       )
   }
   ```

   `completePendingAuthorization` keeps its own `pending == null` branch (it is shared with `connect()`), and still
   rejects a redirect whose `state` does not match, so a foreign `campfire://oauth` intent stays harmless. The second
   `loadPendingAuthorization()` inside it is a read of `SyncCredentialsStore`'s cache.

3. **`CampfireActivity.onCreate`** — move the `handle(intent)` statement from after `setContent { … }` to just before
   it (after `keepStartupScreenUntilAppIsReady()`), with the reason:

   ```kotlin
   keepStartupScreenUntilAppIsReady()
   // Ahead of the content on purpose: a redirect that started this process answers an authorization the previous
   // one was killed in the middle of, and it has to be waiting by the time the first composition creates the view
   // model, whose start up asks for it exactly once.
   handle(intent)
   setContent {
   ```

   This is safe for the other thing `handle` does: `importFrom` launches in `lifecycleScope` and sends into the
   buffered `filesToImport` channel, neither of which needs the content to exist.
   `onNewIntent` is unchanged — it is how the redirect arrives both in the running app and in the relaunched
   activity of the "task survived" case.

   **Compatibility with plan 04** (`04-android-launch-intent-handled-again-on-recreation`), which guards this same
   call so that a recreation does not handle the launch intent again:
   - If 04 has landed, move its *whole guarded statement* (`if (savedInstanceState == null …) handle(intent)`, or
     whatever consumed-intent check it settled on) above `setContent`; do not remove or weaken the guard. The guard
     does not get in this plan's way: a cold start in a removed task has `savedInstanceState == null` and is handled;
     a relaunch of a surviving task has a non-null state and the *original* intent, which is rightly skipped, and the
     redirect comes through `onNewIntent`, which 04 leaves unguarded.
   - If this plan lands first, 04 wraps the call where it now stands and must keep it above `setContent`.
   - If 04 also stops a consumed redirect from being replayed (clearing the intent after handling it), step 2 is still
     required: it is what covers a redirect that arrived too late for an attempt the user had given up on and is
     still sitting in the channel when the next view model starts.

4. **KDoc that becomes untrue** (same change):
   - `data/source/remote/api/.../SyncAuthenticator.kt`, `consumePendingRedirect`: replace "Only the web ever returns
     anything; every other platform answers null." with "The web's ordinary case, and Android's when the process was
     killed behind the browser and the redirect started a new one; the other two platforms answer null. May hand out
     a redirect that nothing is waiting for any more, which the caller has to tell from one that is."
   - `data/repository/api/.../SyncRepository.kt`, `RestoreResult.didReturnFromAuthorization`: replace "Only ever true
     on the web, where the app stops existing while the user is on that page" with "True on the web, where the app
     stops existing while the user is on that page, and on Android when the process was killed behind the browser".
   - `AndroidManifest.xml`, the comment above the `campfire://oauth` filter: append "- or, when the process was
     killed while the browser was in front, in the onCreate or onNewIntent of a new one, which is what
     AndroidSyncAuthenticator.consumePendingRedirect is for."

5. **What the user sees afterwards:** `RestoreSyncUseCase` gets `didReturnFromAuthorization = true`, so
   `CampfireViewModel.init` opens Settings on the Library tab exactly as it does on the web — connected, or with the
   failure message if the exchange was refused — and the first sync starts. No UI change is needed.

6. Not covered, on purpose: the activity being *destroyed without the process dying* while the browser is in front
   (*Don't keep activities*). That cancels `viewModelScope`, `connect()`'s cancellation branch clears the pending
   authorization as "the user gave up", and the redirect then finds nothing waiting. Android does not destroy single
   activities to reclaim memory outside that developer option, and telling the two cancellations apart is not worth
   a second code path.

## Tests
None: the change in `:data:source:remote:implementation` is Android-only platform code (the module's tests run on
the desktop target), and `SyncRepositoryImpl` has no test harness. The pure parts it relies on
(`redirectParameters`, the `state` comparison) are already covered.

## Verify
`./gradlew :app:android:assembleDebug`, install on an emulator with a `campfire.dropbox.appKey` in
`local.properties`.
- **Task survives, process dies:** Settings → Connect; when the browser is showing Dropbox's page run
  `adb shell am kill com.pandulapeter.campfire.debug` (the app must be in the background for `am kill` to act), then
  approve in the browser. Campfire must come up on Settings → Library, connected, with the first sync running.
- **Task removed:** same, but swipe Campfire away from the recents before approving.
- **Replay:** after a successful connection made through a redirect, press Back until the app closes, reopen it from
  the recents: it must come up connected (not "Disconnected", not on Settings), and no second exchange is logged.
- **Ordinary flow unchanged:** Connect and approve without killing anything; cancel by closing the browser — the
  750 ms grace and the "Cancelled" outcome behave as before.
- Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): connect once, then load the page again with a stale
  `?code=abc&state=def` appended: the account must still come up connected and the query string must be removed.
- `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution` for the api KDoc edits.

## Docs
- `data/source/remote/implementation/CLAUDE.md`, the **Android** bullet: add "A process killed while the browser was
  in front gets the redirect as the intent that starts the next one; it waits in the same channel and
  `consumePendingRedirect` hands it to `restore`, which finishes the authorization the way the web does."
- `app/android/CLAUDE.md`, the paragraph on the `campfire://oauth` scheme: add that `handle(intent)` runs before
  `setContent` so that a redirect which started the process is waiting before the view model asks for it.
- `data/source/remote/api/CLAUDE.md`, `SyncAuthenticator` bullet: "`consumePendingRedirect` picks the answer up at
  the next start" — add "(the web always, Android after process death)".
- `data/repository/implementation/CLAUDE.md`: add to the `restore` sentence at the end that a redirect no
  authorization is waiting for is ignored and the stored account restored as usual.

## Touches
- `data/source/remote/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/auth/SyncAuthenticator.android.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/SyncRepositoryImpl.kt`
  (`restore`, first statement only)
- `app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt` (`onCreate`)
- `app/android/src/main/AndroidManifest.xml` (comment only)
- `data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/SyncAuthenticator.kt` (KDoc)
- `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/SyncRepository.kt` (KDoc)
- `data/source/remote/implementation/CLAUDE.md`
- `data/source/remote/api/CLAUDE.md`
- `data/repository/implementation/CLAUDE.md`
- `app/android/CLAUDE.md`

## Depends on
Nothing strictly. Shares `CampfireActivity.onCreate` with plan 04 (either order works, see step 3) and
`SyncRepositoryImpl.restore` with plans 17 and 19 (this plan's hunk is the first statement of the function and must
stay ahead of theirs).
