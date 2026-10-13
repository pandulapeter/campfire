# Refuse a browser Forward into an onboarding that was finished, skipped, or closed

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** web
**Challenged:** amended — the onboarding is not always on top: a file dropped on the page imports while it is up and `ImportController` puts the import screen (`showImportReport`) or the imported song (`openImportedSong`, "on top of whatever is on screen") over it. Cutting at every onboarding would then turn a Forward from `[Songs, Onboarding]` into that pushed screen into `[Songs]` and close the onboarding the user is still on. `validate` now cuts only an onboarding the app is not already showing (new `openOnboarding` parameter), and the tests cover the kept case.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutes.kt`, `presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserHistory.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/BrowserRoutesTest.kt`, `presentation/src/wasmJsMain/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/CLAUDE.md`

## Problem

On the web the onboarding is a history entry of its own, at the address of the screen under it (`BrowserRoutes.paths`):

```kotlin
is CampfireDestination.Onboarding -> add(lastOrNull() ?: ROOT)
```

When it is on top, `BrowserHistory.synchronize` records the entry's state, `[Songs, Onboarding(isOpenedFromSettings = false)]` on a first run (`entries[current] = entries[current].copy(state = viewModel.navigationState)`). Start playing or Skip calls `Navigator.closeOnboarding()`, which takes it off the back stack; `synchronize` then goes back one entry, and the entry left keeps that recorded state ("The entry being left keeps the state it was recorded with when it was last on top").

The browser's Forward button is now enabled. Pressing it runs `BrowserHistory.goForward`, which validates the recorded state and restores it:

```kotlin
val state = entry?.state
    ?.let {
        BrowserRoutes.validate(
            state = it,
            ...
        )
    }
    ?: ...
if (state != null) viewModel.restoreNavigationState(state)
```

`BrowserRoutes.validate` keeps every destination it is not told to cut:

```kotlin
CampfireDestination.ImportReport -> destination.takeIf { hasImportReport }
else -> destination
```

so the onboarding comes back, on the page `Navigator.onboardingStep` was left on (its last page, or Welcome after a Skip), asking questions the user has already finished with. The same happens to one opened from Settings and closed with Done or Close (`[Songs, Settings, Onboarding(isOpenedFromSettings = true)]`).

Nothing promises this: `ui/screens/onboarding/CLAUDE.md` says the screen is "one history entry at the address of the screen under it (`BrowserRoutes`), so a reload opens the app rather than a question", and `BrowserRoutes`' KDoc says an address names nothing for it, so a Forward opened from the entry's address (`resolve`) already lands on the screen under it and is refused for being one entry short (`takeIf { BrowserRoutes.entryCount(it) == to + 1 }`). Only the recorded state brings it back.

## Fix

`validate` cuts the stack at an onboarding the app is not showing now, as it cuts it at an import screen with no report:

1. `BrowserRoutes.validate` gets a parameter `openOnboarding: CampfireDestination.Onboarding?` — the onboarding on the app's back stack as the Forward arrives, or null — and a case:

   ```kotlin
   // The onboarding is only ever opened by a first run or from Settings: an entry left by finishing, skipping or
   // closing it is not one to go forward into again, as an address names nothing for it either. One that is still
   // open stays, since a dropped file can put the import screen or a song over it, and a Forward back into those
   // must not close it.
   is CampfireDestination.Onboarding -> destination.takeIf { it == openOnboarding }
   ```

2. `BrowserHistory.goForward` passes `openOnboarding = viewModel.backStack.lastOrNull { it is CampfireDestination.Onboarding } as? CampfireDestination.Onboarding` (read beside `songs` and `setlists`, before anything is restored).

Why not cut every onboarding: the onboarding is *not* always on top. On the web, files dropped on the page are imported whatever is on screen (`CampfireWebApp`'s `droppedFiles()`), and `ImportController` then puts `CampfireDestination.ImportReport` (`showImportReport`, once no dialog is up) or the imported song (`Navigator.openImportedSong`, "on top of whatever is on screen") over it. Going Back from that screen to the onboarding leaves an entry above whose recorded state is `[Songs, Onboarding(false), ImportReport]`; a Forward into it, cut at the onboarding, would restore `[Songs]` and take away the onboarding the user is still on. Keeping an onboarding equal to the open one (same `isOpenedFromSettings`) restores that stack as it was; an onboarding that is no longer open is cut with everything above it.

A refused Forward: the `?: break` leaves the stack under the onboarding, which is the state the app is already in, so `restoreNavigationState` changes nothing and `synchronize` takes the browser back to the entry the app stayed on, which is how every other refused Forward behaves (`goForward`: "Refused or not, the browser is on that entry now: if the app stays where it was, synchronizing takes the browser back to it").

Does this break the onboarding's own history handling? No: the entry being put back while there is a page left is done by `synchronize`'s `canGoForward` traversal (`traverse`, whose `popstate` `awaitTraversal` consumes), never by `goForward`, and `validate` is called from `goForward` alone (`grep -rn "BrowserRoutes.validate" presentation/src`).

A special case in `BrowserHistory.goForward` instead was considered and rejected: it would do the same in a platform file with no tests.

Update the KDoc of `validate` ("and an onboarding the app is no longer showing, which only a first run or Settings opens"), the Forward paragraph of `presentation/src/wasmJsMain/CLAUDE.md` (Forward restores the recorded state "cut short at whatever the library no longer holds" — add "and at an onboarding that has been finished, skipped, or closed, which a Forward never opens again"), and the web sentence of `ui/screens/onboarding/CLAUDE.md` ("…so a reload opens the app rather than a question, and neither does a Forward once it is finished, skipped, or closed").

## Tests

In `BrowserRoutesTest.kt`, beside `the editor is cut in performance mode and the import screen without a report` (pass `openOnboarding = null` in the six existing `validate` calls):

```kotlin
@Test
fun `a finished onboarding is not gone forward into again`() {
    listOf(
        listOf(CampfireDestination.Songs, CampfireDestination.Onboarding(isOpenedFromSettings = false)),
        listOf(CampfireDestination.Songs, CampfireDestination.Settings, CampfireDestination.Onboarding(isOpenedFromSettings = true)),
    ).forEach { backStack ->
        assertEquals(
            backStack.dropLast(1),
            BrowserRoutes.validate(home.copy(backStack = backStack), songs, setlists, isPerformanceModeEnabled = false, hasImportReport = false, openOnboarding = null).backStack,
        )
    }
}

@Test
fun `an open onboarding stays under the screen a Forward returns to`() {
    val onboarding = CampfireDestination.Onboarding(isOpenedFromSettings = false)
    val backStack = listOf(CampfireDestination.Songs, onboarding, CampfireDestination.ImportReport)
    assertEquals(
        backStack,
        BrowserRoutes.validate(home.copy(backStack = backStack), songs, setlists, isPerformanceModeEnabled = false, hasImportReport = true, openOnboarding = onboarding).backStack,
    )
    // Not the one that is open: cut there, with what is over it.
    assertEquals(
        listOf(CampfireDestination.Songs),
        BrowserRoutes.validate(
            home.copy(backStack = backStack), songs, setlists, isPerformanceModeEnabled = false, hasImportReport = true,
            openOnboarding = CampfireDestination.Onboarding(isOpenedFromSettings = true),
        ).backStack,
    )
}
```

## Manual check

In the web build, with a fresh origin (or Settings → the onboarding): go through the onboarding and press Start playing (or Skip on its first page, or Done when opened from Settings). The browser's Forward button may be enabled; press it. The app must stay where it is, with no onboarding page sliding in, and a Back afterwards must behave as before (leave the page from the songs, or go back to the songs from Settings).

Also: with the onboarding up (first run), drop a ChordPro file on the page, open the song (or the import screen), press the browser's Back to return to the onboarding and then Forward: the song must come back with the onboarding still under it, and Back must return to the onboarding on the page it was on.
