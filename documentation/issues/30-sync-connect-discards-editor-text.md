# 30 — A sync connection finishing at start up can throw away unsaved editor text

**Severity:** data loss, low likelihood (web; Android after process death) · **Area:** `:presentation`
(`CampfireViewModel.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

## What the user sees

The web build connects Dropbox by leaving for the consent page and being loaded again on the redirect; Android does
the same when its process was reclaimed behind the Custom Tab. That second start up finishes the authorization — a
code exchange over the network, which on a bad connection can take a minute or more — while the app is already on
screen. If in the meantime the user opened a song, opened the editor and typed, the moment the exchange answers the
app jumps to Settings → Library and the editor, with everything typed in it, is gone. Nothing asks.

## Cause

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:790-800`:

```kotlin
            try {
                ...
                if (isFirstLaunch.await()) forgetSyncConnection()
                if (restoreSync()) {
                    settingsTab = SettingsTab.LIBRARY
                    selectTopLevelDestination(CampfireDestination.Settings)
                }
```

`restoreSync()` answers `didReturnFromAuthorization` (`SyncUseCaseImpls.kt:92-100`), which is true for any start up
that consumed a pending redirect, whatever the user has done since — and it returns only after
`completePendingAuthorization` has exchanged the code (`SyncRepositoryImpl.kt:143-149`, `:591-625`).
`selectTopLevelDestination` (`:970-979`) rebuilds the stack from nothing:

```kotlin
    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) {
        if (backStack.lastOrNull() == destination) return
        updateBackStack {
            clear()
            add(CampfireDestination.Songs)
            if (destination != CampfireDestination.Songs) {
                add(destination)
            }
        }
    }
```

With the editor gone from the stack, `ReportDraft`'s dispose calls `onEditorClosed` (`:1192-1196`), which drops the
draft because no editor names the file any more. Every other way the editor's text leaves the screen goes through
`navigateBack` or `requestExit` and asks first, and `restoreNavigationState` refuses outright while there is unsaved
text (`:926-927`); `navigateOnLaunch` only acts on an untouched stack (`:961`). This path does neither.

`selectTopLevelDestination` is otherwise only called from the navigation bar and rail (`CampfireApp.kt:366`), which
are hidden while the editor is showing, so this is the one caller that can reach an editor.

## The change

Invoke the **`code-style`** skill before the first edit.

Two parts: the jump only lands on a stack nobody has built on since the app started (the reviewer's first
suggestion, the same rule `navigateOnLaunch` uses), and `selectTopLevelDestination` refuses to take unsaved editor
text off the screen, stating in code the invariant `restoreNavigationState` already states.

### The jump after a consent page

Replace `:797-800` with a call to a new private function, and add it next to `navigateOnLaunch`:

```kotlin
                if (restoreSync()) openSyncSettingsAfterConsent()
```

```kotlin
    /**
     * Shows the answer to a consent page the app was sent away to, which is where the user was when they left: Settings,
     * on the tab holding the sync section. Only onto a stack nobody has built on since the app started - the songs the
     * app opens on, or the settings screen Android brings back after reclaiming the process behind the browser. The
     * code exchange this follows can take a minute on a bad network, and a user who has gone somewhere else meanwhile
     * has moved on: the settings screen says the same thing whenever they get there, and an editor they opened in the
     * meantime holds text that nothing but its own ways out may take away.
     */
    private fun openSyncSettingsAfterConsent() {
        val stack = backStack.toList()
        if (stack != listOf(CampfireDestination.Songs) && stack != listOf(CampfireDestination.Songs, CampfireDestination.Settings)) return
        settingsTab = SettingsTab.LIBRARY
        selectTopLevelDestination(CampfireDestination.Settings)
    }
```

On the `[Songs, Settings]` stack `selectTopLevelDestination` returns at once (`backStack.lastOrNull() == destination`),
as it does today, and the tab is only read by the settings screen as it is composed — the behaviour of that case does
not change.

### The invariant in `selectTopLevelDestination`

```kotlin
    /**
     * Rebuilds the stack around a top level screen. Refused while an editor on the stack holds unsaved text: the
     * navigation chrome that calls this is hidden over the editor, and nothing else may take that text off the screen
     * without asking, see [navigateBack].
     */
    fun selectTopLevelDestination(destination: CampfireDestination.TopLevel) {
        if (backStack.lastOrNull() == destination) return
        if (hasUnsavedEditorText() && backStack.any { it is CampfireDestination.SongEditor }) return
        updateBackStack {
            ...unchanged
        }
    }
```

`hasUnsavedEditorText()` (`:1059`) reads the draft as of this moment, which is what a decision that is not drawn
should read.

## Tests

- **No unit test is possible** (view model, untested by policy).
- Compile check: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`

## Verification

Needs a build with `campfire.dropbox.appKey` in `local.properties` and the throwaway Dropbox account.

1. **Web**, confirm first: `./gradlew :app:web:wasmJsBrowserDevelopmentRun`. In Chrome DevTools → Network, create a
   custom throttling profile with 20 000 ms of latency. Settings → Library → Connect Dropbox; on the consent page
   press Allow, and as the app is loaded again switch the throttling to that profile once the page itself is up (the
   code exchange is the request that follows it).
   - While the exchange hangs, go to Songs, open a song, ⋮ → Edit, type a line.
   - **Before:** when the exchange answers, the app jumps to Settings; Back does not ask; the typed line is gone.
   - **After:** the editor stays; Settings → Library (via Close → Save/Discard) shows the account connected.
2. Web, the ordinary case: connect without touching anything while it loads → the app opens on Settings → Library,
   as before.
3. **Android** process death: build the debug APK with a sync key, Settings → Library → Connect, and while the Custom
   Tab is up run `adb shell am kill com.pandulapeter.campfire.debug`. Approve. The app comes back on Settings (the
   restored stack is `[Songs, Settings]`), connected — unchanged behaviour.
4. Any platform: with the editor open and unsaved, nothing new is reachable that calls `selectTopLevelDestination`;
   a regression check is that tapping the rail/bar still switches tabs everywhere else.

## Docs

- `domain/api/.../useCases/ConnectSyncProviderUseCase.kt`'s `RestoreSyncUseCase` KDoc says the answer "only happens on
  the web"; `SyncRepositoryImpl.restoreConnection`'s comment names Android after process death as well. Change
  "which only happens on the web" to "which happens on the web, and on Android when the process was reclaimed behind
  the browser". (Lane C owns `:domain:api`; this is a one-clause KDoc fix and can ride with this plan.)
- `presentation/CLAUDE.md`, the `ui/CampfireApp.kt` bullet, mentions "Settings after a consent page" being put on the
  stack behind the launch screen. Add after it: "— only onto a stack the user has not built on since the app started
  (`openSyncSettingsAfterConsent`), since the code exchange it follows can outlast the launch screen by a minute".

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/ConnectSyncProviderUseCase.kt` (KDoc)
- `presentation/CLAUDE.md`

## Depends on

Nothing. Plan 28 (lane C) is about the `forgetSyncConnection()` call on the line above but, as written, leaves
`CampfireViewModel.kt` alone; if it ends up changing that `try` block after all, land one and rebase the other. Plan 44 relies on this guard (the editor it reopens on launch must not
be replaced by the consent jump), so it lands after this. Lane D order: 33, 31, **30**, 29, 34, …
