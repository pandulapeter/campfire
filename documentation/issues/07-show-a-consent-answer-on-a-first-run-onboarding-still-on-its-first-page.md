# Show a sync consent's answer on a first-run onboarding that is still on its first page

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web (Android only where the redirect starts a fresh process on what still counts as a first launch)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncConsentOrigin.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/Navigator.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncConsentOriginTest.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`

## Problem

`CampfireViewModel.showSyncConsentAnswer()` puts the answer to a consent page where the Connect was tapped, but only onto two exact stacks:

```kotlin
private fun showSyncConsentAnswer() {
    // Taken whatever happens next, so that it cannot be mistaken later for where another consent was asked from.
    val origin = takeSyncConsentOrigin()
    val stack = backStack.toList()
    if (stack != listOf(CampfireDestination.Songs) && stack != listOf(CampfireDestination.Songs, CampfireDestination.Settings)) return
    when (origin) {
        SyncConsentOrigin.ONBOARDING -> navigator.openOnboarding(isOpenedFromSettings = false, step = OnboardingStep.SYNC)
        ...
```

598fab3df made a start up that answers a consent keep that answer even where it still counts as a first launch (`SyncConnectionManager.forgetStoredConnection` keeps the redirect for `restoreConnection`: "the start up that answers it may still look like a first one where the launch before it could not write its preferences"). On such a start up two coroutines race, both started from the view model's `init`:

- `FirstRunController.plantDemoLibraryOnFirstRun()` awaits `isFirstLaunch`, waits for the library to be read, plants the demo library into an empty one, and calls `openOnboardingOnFirstRun()`, which puts `Onboarding(isOpenedFromSettings = false)` on Welcome: local work, done behind the launch screen.
- `SyncController.startRestoring(isFirstLaunch, onConsentAnswered = ::showSyncConsentAnswer)` awaits the same `isFirstLaunch`, calls `forgetSyncConnection()` (which now keeps the redirect), then `restoreSync()`, which exchanges the code with Dropbox over the network (the KDoc of `showSyncConsentAnswer` itself says it "can take a minute on a bad network"), and only then calls `onConsentAnswered`.

So the answer almost always arrives after the onboarding is up, on `[Songs, Onboarding(false)]`, and `showSyncConsentAnswer` returns without doing anything. The user tapped Connect on the onboarding's Dropbox page, came back from Dropbox, and finds themselves on Welcome with no word of how it went until they page through to Dropbox again; `ui/screens/onboarding/CLAUDE.md` promises the opposite ("the web, whose answer is a new start up on the songs, puts the onboarding back on it"). Where the exchange fails fast (offline) the answer may arrive first, on `[Songs]`, and works: `openOnboarding` then puts it on SYNC and `openOnboardingOnFirstRun`'s own `openOnboarding` returns early ("if (backStack.any { it is CampfireDestination.Onboarding }) return").

## Fix

1. Extract "where may the answer be shown" into a pure function next to `syncConsentOriginOf` in `ui/platform/SyncConsentOrigin.kt`:

   ```kotlin
   /** What showing a consent's answer does to the app, see `CampfireViewModel.showSyncConsentAnswer`. */
   internal enum class SyncConsentAnswer { NOTHING, ONBOARDING, ONBOARDING_FROM_SETTINGS, SETTINGS, ONBOARDING_ON_SYNC_PAGE }

   /**
    * Where the answer to a consent asked from [origin] goes on [backStack]: only onto a stack nobody has built on since the
    * app started — the songs, the settings screen Android brings back, or a first run's onboarding still on its first
    * page ([onboardingStep]), which a start up that still counts as a first one opens while the code is being exchanged,
    * and which is moved on to its Dropbox page rather than covered by Settings.
    */
   internal fun syncConsentAnswerOf(backStack: List<CampfireDestination>, onboardingStep: OnboardingStep, origin: SyncConsentOrigin?) = when {
       backStack == listOf(CampfireDestination.Songs, CampfireDestination.Onboarding(isOpenedFromSettings = false)) ->
           if (onboardingStep == OnboardingStep.entries.first()) SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE else SyncConsentAnswer.NOTHING
       backStack != listOf(CampfireDestination.Songs) && backStack != listOf(CampfireDestination.Songs, CampfireDestination.Settings) -> SyncConsentAnswer.NOTHING
       origin == SyncConsentOrigin.ONBOARDING -> SyncConsentAnswer.ONBOARDING
       origin == SyncConsentOrigin.ONBOARDING_FROM_SETTINGS -> SyncConsentAnswer.ONBOARDING_FROM_SETTINGS
       else -> SyncConsentAnswer.SETTINGS
   }
   ```

   The onboarding is accepted only on its first page, whatever the origin: once the user has moved on through its pages they have "built on" the stack in the KDoc's sense, and the Dropbox page will say how it went when they reach it. Whatever the origin, it is the Dropbox page rather than Settings, since a consent answered while the onboarding is on top "does not open Settings over it" (`ui/screens/onboarding/CLAUDE.md`). An Android onboarding restored behind the browser is already on the Dropbox page, not the first one, so it is left alone as today.
2. `Navigator` gets a public way to move an open onboarding to a page, e.g. `fun showOnboardingStepIfOpen(step: OnboardingStep) { if (backStack.lastOrNull() is CampfireDestination.Onboarding) showOnboardingStep(step) }` (the private `showOnboardingStep` already persists the step to `SavedStateStore`).
3. `showSyncConsentAnswer` keeps taking the origin first, then switches on `syncConsentAnswerOf(backStack.toList(), navigator.onboardingStep, origin)`: `ONBOARDING` and `ONBOARDING_FROM_SETTINGS` do what they do today, `SETTINGS` is `openSettingsOn(SettingsTab.LIBRARY)`, `ONBOARDING_ON_SYNC_PAGE` is `navigator.showOnboardingStepIfOpen(OnboardingStep.SYNC)`, `NOTHING` returns. Update its KDoc to name the third stack.

The launch screen is up for most of this, and nothing on the back stack is animated while it is (`hasShownApp`); where the answer arrives after it, the pager moves to the Dropbox page the way the dots and Next move it, which is a change the user caused by tapping Connect.

Update `ui/screens/onboarding/CLAUDE.md` (the consent paragraph: "…puts the onboarding back on it, or moves a first run's onboarding the start up opened meanwhile from its first page to it") and the `showSyncConsentAnswer` sentence of `ui/CLAUDE.md` ("only onto a stack the user has not built on since the app started", add: "a first run's onboarding still on its first page included").

## Tests

In `SyncConsentOriginTest.kt`, over `syncConsentAnswerOf`:

- `[Songs]` with each origin gives `ONBOARDING`, `ONBOARDING_FROM_SETTINGS`, and `SETTINGS` (null as well as `SETTINGS`);
- `[Songs, Settings]` the same;
- `[Songs, Onboarding(false)]` on `WELCOME` gives `ONBOARDING_ON_SYNC_PAGE` for every origin, null included, and on any other step `NOTHING`;
- `[Songs, Settings, Onboarding(true)]`, `[Songs, SongDetails(...)]`, and `[Songs, Setlists]` give `NOTHING`.

## Manual check

Hard to reach by hand, since it needs a start up that still counts as a first one: in the web build (with a Dropbox key), on a fresh origin, open the onboarding's Dropbox page and tap Connect; before approving, delete `preferences/preferences.json` from the origin's OPFS (DevTools → Application → Storage, or `navigator.storage.getDirectory()` in the console); approve on Dropbox. The app must come back with the onboarding on its Dropbox page saying the folder is connected, not on Welcome. Without the deletion (the ordinary case) it must still come back on the Dropbox page, as before.
