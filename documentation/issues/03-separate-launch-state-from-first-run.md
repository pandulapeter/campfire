<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Give the launch screen a package of its own, and take the sync and metronome shell effects out of `CampfireApp`

**Kind:** structure · **Severity:** low · **Platforms:** all
**Challenged:** amended — the moved `_isAppOnScreen` KDoc's `[showWhatsNewOnVersionChange]` link breaks with the move
and is rewritten; `LaunchScreenGate` needs the Material 3 Expressive opt-in (it reads `MaterialTheme.motionScheme`);
`launchState`'s placement after `firstRunController` reorders two eager collectors, which is stated as harmless.
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/FirstRunController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/launch/LaunchState.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/launch/LaunchScreenGate.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/launch/LaunchScreen.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/launch/CLAUDE.md` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireApp.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/sync/SyncNotifications.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeNotificationEffect.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/metronome/MetronomeHapticsEffect.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/ImportController.kt` (KDoc only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/CLAUDE.md`,
`.github/scripts/size_allowances.json`

## Problem

1. **`FirstRunController` holds two features.** Besides the first run (demo library, onboarding, What's new, the demo
   offer), it holds what the launch screen waits for and the launch address: `_isLaunchNavigationPending` /
   `isLaunchNavigationPending`, `hasNavigatedOnLaunch`, `hasLibraryToShow`, `hasShownApp`, `_isAppOnScreen` /
   `isAppOnScreen` and `navigateOnLaunch(resolve)` — about 100 of its 409 lines, and four of its constructor parameters
   (`isEditorDraftRecoveryPending`, `editorDraftRecovery`, `restoreNavigationState`, and most of the reason for
   `userPreferencesState`) exist only for them. Its class KDoc lists both ("…the place an address asked the app to open
   on, and what the launch screen waits for before it goes").
2. **`CampfireApp.kt` (437 lines; `CampfireApp` itself 167 lines, over the 120-line limit, on an allowance) holds four
   things besides the root:** the launch screen gate (the `hasShownAppBefore` / `isAppReady` / `Animatable` block,
   about 75 lines inside `CampfireApp`), `LaunchScreen` with `LAUNCH_MARK_SIZE` and `LAUNCH_MARK_EXIT_GROWTH`,
   `rememberSyncNotifications` with `SyncState.progress`, and `MetronomeNotificationEffect` / `MetronomeHapticsEffect`.
   The sync and metronome ones belong to `ui/sync/` and `ui/metronome/`; one Composable per file is the house rule.
3. **Broken and stale references.** In `FirstRunController.kt`'s KDoc: `[importQueue]` (a private member of
   `ImportController`), `[arePreferencesLoaded]` (`PreferencesController`'s), `[librarySummary]` (`LibraryState`'s),
   `[isPerformanceModeEnabled]` (`PreferencesController`'s) do not resolve. Elsewhere: `ImportController.startQueue`'s
   KDoc says "see `CampfireViewModel.demoLibraryDecision`" (it is `FirstRunController.demoLibraryDecision`), and
   `firstRun/CLAUDE.md` says "Planted once, by `CampfireViewModel.plantDemoLibraryOnFirstRun`" (it is
   `FirstRunController.plantDemoLibraryOnFirstRun`). `CampfireApp`'s KDoc links `[LaunchScreen]`, which breaks once it
   moves.

## Fix

Behaviour is preserved throughout. Four commits.

### Commit 1 — "Fix the first run's broken and stale references."

Only KDoc and CLAUDE.md: in `FirstRunController.kt` write the four broken links as backticked qualified names
(`` `ImportController`'s queue ``, `` `PreferencesController.arePreferencesLoaded` ``, `` `LibraryState.librarySummary` ``,
`` `PreferencesController.isPerformanceModeEnabled` ``); fix the `ImportController.startQueue` KDoc and the
`firstRun/CLAUDE.md` sentence as above. Grep for any other `CampfireViewModel.<member>` that no longer exists on the view
model (`grep -rn 'CampfireViewModel\.[a-zA-Z]*' presentation/src --include='*.kt' --include='*.md'` and check each
against the view model) and fix those in the same commit.

### Commit 2 — "Move what the launch screen waits for out of the first run."

New `ui/launch/LaunchState.kt`, `internal class LaunchState`, taking over **verbatim, KDoc included**:
`_isLaunchNavigationPending` / `isLaunchNavigationPending`, `hasNavigatedOnLaunch`, `hasLibraryToShow`, `hasShownApp`
(with its setter), `_isAppOnScreen` / `isAppOnScreen`, and `navigateOnLaunch`. One KDoc link has to change with the
move: `isAppOnScreen`'s "see [showWhatsNewOnVersionChange]" names a `FirstRunController` member, so it becomes
`` `FirstRunController.showWhatsNewOnVersionChange` ``. (`hasLibraryToShow`'s `[isDemoLibraryPending]` and
`[isLaunchNavigationPending]` resolve in the new class; its `[arePreferencesLoaded]` and `navigateOnLaunch`'s
`[isPerformanceModeEnabled]` were fixed by commit 1.) Constructor:

```kotlin
internal class LaunchState(
    scope: CoroutineScope,
    private val screenData: StateFlow<DataState<ScreenData>>,
    private val isDemoLibraryPending: StateFlow<Boolean>,         // FirstRunController's
    isEditorDraftRecoveryPending: StateFlow<Boolean>,
    private val editorDraftRecovery: Deferred<Boolean>,
    private val userPreferencesState: StateFlow<DataState<UserPreferences>>,
    private val backStack: List<CampfireDestination>,
    private val restoreNavigationState: (NavigationState) -> Boolean,
)
```

(`scope` is a `private val` if `navigateOnLaunch`'s `scope.launch` needs it, which it does.)

In `FirstRunController`:

- `isDemoLibraryPending` stays its own (the planting sets it) but is exposed read-only:
  `private val _isDemoLibraryPending = MutableStateFlow(true)` + `val isDemoLibraryPending: StateFlow<Boolean> = _isDemoLibraryPending.asStateFlow()`,
  the writers updated.
- `showWhatsNewOnVersionChange` waits with a new constructor lambda `awaitAppOnScreen: suspend () -> Unit` in place of
  `isAppOnScreen.first { it }`.
- Drop the parameters only the moved members used: `isEditorDraftRecoveryPending`, `editorDraftRecovery`,
  `restoreNavigationState`. `userPreferencesState` stays (planting and What's new read it).
- Update the class KDoc to the first run alone and its `@param` list.

In `CampfireViewModel.kt`:

- Declare `launchState` **immediately after** `firstRunController` (so `hasLibraryToShow`'s `asState`, which starts
  collecting as it is built, starts at the same point of construction as before; it now starts after
  `demoLibraryOffer`'s instead of before it, which changes nothing: both are independent `Eagerly` collectors reading
  `StateFlow`s, and neither writes anything the other reads), passing
  `isDemoLibraryPending = firstRunController.isDemoLibraryPending`, `restoreNavigationState = { restoreNavigationState(it) }`
  and the rest as `firstRunController` received them.
- `firstRunController` gets `awaitAppOnScreen = { launchState.isAppOnScreen.first { it } }`. The lambda reads
  `launchState` only when called, from `showWhatsNewOnVersionChange`, launched in `init` after both exist. This is why
  the order is first run, then launch: `LaunchState` needs `isDemoLibraryPending` while it is built, and
  `FirstRunController` only needs the app on screen later.
- The five delegations (`isLaunchNavigationPending`, `hasLibraryToShow`, `hasShownApp`, `isAppOnScreen`,
  `navigateOnLaunch`) point at `launchState`; their KDoc `See [LaunchState.x]`.

Nothing else in `init` moves.

### Commit 3 — "Move the launch screen and its gate out of CampfireApp."

- New `ui/launch/LaunchScreen.kt`: `LaunchScreen` (now `internal`), `LAUNCH_MARK_SIZE`, with its KDoc.
- New `ui/launch/LaunchScreenGate.kt`: one `@Composable internal fun LaunchScreenGate(viewModel: CampfireViewModel,
  arePreferencesLoaded: Boolean, isThemeSettled: Boolean, launchScreenColors: LaunchScreenColors, onAppReady: () -> Unit)`
  holding the code of `CampfireApp` from the comment "The launch screen covers the app rather than standing in for it…"
  through the end of `if (!isAppReady) { … }`, unchanged, plus `LAUNCH_MARK_EXIT_GROWTH`. It carries
  `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`, as `CampfireApp` does, since the fade reads
  `MaterialTheme.motionScheme`; `CampfireApp` keeps its own opt-in only if something left in it still needs one. It collects
  `viewModel.hasLibraryToShow` itself (that `collectAsStateWithLifecycle` line moves out of `CampfireApp`, since nothing
  else there reads it). It must be called **where the block was**: last inside the `Box`, after the `if
  (arePreferencesLoaded) { … }` app block, so it is drawn over the app.
- `CampfireApp` calls `LaunchScreenGate(…)` there; its `onAppReady` KDoc names `` `LaunchScreen` `` in backticks (or the
  qualified link). `CampfireApp` drops to about 95 lines: remove its `CampfireApp.kt::CampfireApp` function allowance
  with `check_sizes.py --update`.

### Commit 4 — "Move the sync notification and the metronome's shell effects to their packages."

- New `ui/sync/SyncNotifications.kt`: `rememberSyncNotifications` (now `internal`) and `private val SyncState.progress`,
  with their KDoc.
- New `ui/metronome/MetronomeNotificationEffect.kt` and `ui/metronome/MetronomeHapticsEffect.kt`, one Composable each,
  `internal`, KDoc kept.
- `CampfireApp` imports them; its call order (`rememberSyncNotifications`, `MetronomeNotificationEffect`,
  `MetronomeHapticsEffect`, `ProvideCoverArtImageLoader`) is unchanged. Drop the imports it no longer needs.

### Docs (in commits 2 and 3)

- New `ui/launch/CLAUDE.md` (with the MPL header, title `# :presentation — ui/launch`): move `ui/CLAUDE.md`'s
  "#### Launch screen" subsection there (about 170 words, well within the 600-word package budget), adding one line:
  `LaunchState` holds what the screen waits for (`hasLibraryToShow`, the launch address's `navigateOnLaunch`) and
  `hasShownApp` / `isAppOnScreen`; the first run's decision is waited for through `FirstRunController.isDemoLibraryPending`.
  Remove the subsection from `ui/CLAUDE.md` (allowance 2,944 words) and run `check_sizes.py --update`.
- `ui/CLAUDE.md`'s chrome section: the `rememberSyncNotifications` bullet now says `ui/sync/SyncNotifications.kt`
  (plan 05 may move the bullet into a `ui/sync/CLAUDE.md`).
- `firstRun/CLAUDE.md`: "keeps the launch screen up over the whole decision" stays true; add nothing.
- `ui/update/CLAUDE.md` names `CampfireViewModel.isAppOnScreen`, which still exists: leave it.

## Tests

None can be added for what moves: `hasLibraryToShow` and `navigateOnLaunch` are flows over the whole library state and
the gate is a Composable, and the plan only moves them. The existing `WhatsNewGateTest` and `DemoLibraryTest` must still
pass. Check the moves mechanically: `git diff --color-moved=dimmed-zebra HEAD~1` per commit shows the moved members as
moved, with only the constructor, the visibility and the import lines new.

Then the root CLAUDE.md test command, the four platform compile tasks named in the brief, `spotlessCheck`, and the size
check.

## Manual check

Cold start on Android (system splash hands straight over to the app, no mark in between), the desktop app (the mark
grows as it fades), and the web build opened on a song's address (the launch screen goes on that song, not on the song
list first). On a fresh installation (clear the app's data on Android) the onboarding is what the launch screen uncovers.
