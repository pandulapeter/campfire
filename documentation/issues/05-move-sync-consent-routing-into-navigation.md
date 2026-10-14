<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Move the sync consent's routing out of the view model and gather the sync holders in `ui/sync`

**Kind:** structure · **Severity:** low · **Platforms:** all (the routing matters on the web and Android)
**Challenged:** amended — `SyncConsentAnswer`'s KDoc pointer to `CampfireViewModel.showSyncConsentAnswer` is re-pointed
in commit 2 (it lives in a `.kt` file, which the docs grep missed), and the moved `showSyncConsentAnswer` KDoc's links to
`takeSyncConsentOrigin` need an import of `ui.platform.takeSyncConsentOrigin` in `SyncConsent.kt` (only the KDoc uses it).
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncConsentOrigin.kt`,
`presentation/src/{androidMain,desktopMain,iosMain,wasmJsMain}/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncConsentOrigin.*.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/sync/SyncConsent.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SyncController.kt` → `ui/sync/SyncController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/AppExitController.kt` (import only),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SyncConsentOriginTest.kt` → `ui/sync/SyncConsentTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/navigation/NavigatorTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/sync/CLAUDE.md` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`,
`.github/scripts/size_allowances.json`

Lands after plans 01 and 03 (it edits `Navigator`-adjacent code and the `rememberSyncNotifications` bullet plan 03
re-points). It includes moving `SyncController` to `ui/sync/` — a pure move — so plan 13 must leave `SyncController` out
of its `state` split.

## Problem

- **Routing logic in the facade.** `CampfireViewModel.showSyncConsentAnswer()` and `openSettingsOn(tab)` (about 35
  lines with KDoc) decide where the answer to a sync consent page is shown and navigate there:

  ```kotlin
  private fun showSyncConsentAnswer() {
      // Taken whatever happens next, so that it cannot be mistaken later for where another consent was asked from.
      val origin = takeSyncConsentOrigin()
      when (syncConsentAnswerOf(backStack = backStack.toList(), onboardingStep = navigator.onboardingStep, origin = origin)) {
          SyncConsentAnswer.NOTHING -> Unit
          SyncConsentAnswer.ONBOARDING -> navigator.openOnboarding(isOpenedFromSettings = false, step = OnboardingStep.SYNC)
          SyncConsentAnswer.ONBOARDING_FROM_SETTINGS -> {
              openSettingsOn(SettingsTab.ABOUT)
              navigator.openOnboarding(isOpenedFromSettings = true, step = OnboardingStep.SYNC)
          }
          SyncConsentAnswer.SETTINGS -> openSettingsOn(SettingsTab.LIBRARY)
          SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE -> navigator.showOnboardingStepIfOpen(OnboardingStep.SYNC)
      }
  }
  ```

  and `connectSyncProvider` is not a one-line delegation either (`keepSyncConsentOrigin(syncConsentOriginOf(backStack.lastOrNull()))`
  before `syncController.connectSyncProvider(…)`). Every step of it is navigation; none of it is testable in the view
  model.
- **Pure decisions in the platform package.** `ui/platform/SyncConsentOrigin.kt` holds, besides the two
  `expect fun`s that are the platform shell (`keepSyncConsentOrigin`, `takeSyncConsentOrigin`), the enums
  `SyncConsentOrigin` and `SyncConsentAnswer` and the pure functions `syncConsentOriginOf` and `syncConsentAnswerOf`,
  which are app logic, tested by `ui/platform/SyncConsentOriginTest.kt`. `SyncConsentAnswer`'s KDoc points at
  `CampfireViewModel.showSyncConsentAnswer`.
- **The sync holders are scattered**: `state/SyncController.kt` beside `ui/sync/SyncConnection.kt` (the stage,
  the completion page, the failure messages).

## Fix

Three commits, each compiling, behaviour preserved. Check moves with `git diff --color-moved=dimmed-zebra`.

### Commit 1 — "Move the sync consent's decisions out of the platform package."

- New `ui/sync/SyncConsent.kt` (package `…presentation.ui.sync`): `SyncConsentOrigin`, `syncConsentOriginOf`,
  `SyncConsentAnswer`, `syncConsentAnswerOf`, verbatim with KDoc.
- `ui/platform/SyncConsentOrigin.kt` keeps only the two `expect fun`s and their KDoc, importing
  `ui.sync.SyncConsentOrigin`. The four `actual` files (`androidMain`, `desktopMain`, `iosMain`, `wasmJsMain`) add the
  same import (they used the enum unqualified from their own package).
- Move `commonTest/…/ui/platform/SyncConsentOriginTest.kt` to `commonTest/…/ui/sync/SyncConsentTest.kt` (package and
  class renamed to match the file it tests; the six test bodies unchanged).
- `CampfireViewModel.kt`: imports updated.

### Commit 2 — "Let the navigator route a sync consent's answer."

Add to `ui/sync/SyncConsent.kt` two extensions on `Navigator` (extensions rather than members because they use only its
public API, and `Navigator.kt` is at about 475 lines after plan 01 — members would take it to the 500-line limit):

```kotlin
/** Remembers where a Connect was tapped, see [keepSyncConsentOrigin], from the screen on top. */
internal fun Navigator.rememberSyncConsentOrigin() = keepSyncConsentOrigin(syncConsentOriginOf(backStack.lastOrNull()))

/** <today's showSyncConsentAnswer KDoc, moved verbatim> */
internal fun Navigator.showSyncConsentAnswer(origin: SyncConsentOrigin?) {
    when (syncConsentAnswerOf(backStack = backStack.toList(), onboardingStep = onboardingStep, origin = origin)) {
        SyncConsentAnswer.NOTHING -> Unit
        SyncConsentAnswer.ONBOARDING -> openOnboarding(isOpenedFromSettings = false, step = OnboardingStep.SYNC)
        SyncConsentAnswer.ONBOARDING_FROM_SETTINGS -> {
            openSettingsOn(SettingsTab.ABOUT)
            openOnboarding(isOpenedFromSettings = true, step = OnboardingStep.SYNC)
        }
        SyncConsentAnswer.SETTINGS -> openSettingsOn(SettingsTab.LIBRARY)
        SyncConsentAnswer.ONBOARDING_ON_SYNC_PAGE -> showOnboardingStepIfOpen(OnboardingStep.SYNC)
    }
}

private fun Navigator.openSettingsOn(tab: SettingsTab) {
    // Not through the re-selection of an open settings screen, which would send it back to General a moment after.
    if (backStack.lastOrNull() != CampfireDestination.Settings) selectTopLevelDestination(CampfireDestination.Settings)
    settingsTab = tab
}
```

The origin is a parameter so the routing can be tested; the caller takes it. The moved KDoc links
`[takeSyncConsentOrigin]` and `[syncConsentAnswerOf]`: the second is in the same file, the first needs
`import com.pandulapeter.campfire.presentation.ui.platform.takeSyncConsentOrigin` (ktlint counts a KDoc link as a use).
In the same commit, `SyncConsentAnswer`'s KDoc ("see `CampfireViewModel.showSyncConsentAnswer`") becomes "see
[showSyncConsentAnswer]". `openSettingsOn` calls
`Navigator.selectTopLevelDestination` directly instead of the view model's wrapper: the wrapper only adds
`tunerController.markOpenedByTap()` when the tab opened is the Tuner, which Settings never is, so the two are the same
here. It is named `rememberSyncConsentOrigin` so it does not read as the platform's `keepSyncConsentOrigin` it calls.

In `CampfireViewModel.kt`:

- `syncController.startRestoring(isFirstLaunch = isFirstLaunch, onConsentAnswered = { navigator.showSyncConsentAnswer(takeSyncConsentOrigin()) })`
  — `takeSyncConsentOrigin()` stays the first thing that happens once a consent is answered ("taken whatever happens
  next"), as today.
- `connectSyncProvider` becomes `navigator.rememberSyncConsentOrigin()` then `syncController.connectSyncProvider(…)`, same
  order (the origin is kept even when a connection already running refuses the tap, as today). Optionally make it a
  one-liner by giving `SyncController` a `keepConsentOrigin: () -> Unit` constructor lambda called first in its
  `connectSyncProvider`; recommended only if the reviewer of the execution prefers strict one-line delegations — it is
  not needed for behaviour.
- Delete `showSyncConsentAnswer`, `openSettingsOn`, and the imports only they used (`SyncConsentAnswer`,
  `syncConsentAnswerOf`, `syncConsentOriginOf`, `keepSyncConsentOrigin`, `OnboardingStep` if unused).
- `check_sizes.py --update`.

### Commit 3 — "Move the sync controller into the sync package."

`git mv state/SyncController.kt sync/SyncController.kt`, package `…presentation.ui.sync`; it uses `asState` from
`ui.state` (add the import) and `MessageSink`. Update the imports in `CampfireViewModel.kt` and
`state/AppExitController.kt`. Its KDoc's `CampfireViewModel.isFirstLaunch` reference stays valid.

### Docs (with commits 1 and 3)

New `ui/sync/CLAUDE.md` (MPL header, `# :presentation — ui/sync`, well under 600 words), only what is not obvious:

- The consent answer is shown only onto a stack nobody has built on since start (Songs, Settings, or an owed onboarding
  still on page one, which is moved to its Dropbox page), where the Connect was tapped (`SyncConsentOrigin`); only the
  web keeps the origin across the page load (session storage, `ui/platform`), since only there does every consent end in
  a fresh start — Android restores its back stack. (Moved from `platform/CLAUDE.md`'s `SyncConsentOrigin.kt` section,
  which keeps one line: the `expect`s keep the origin, see `ui/sync/CLAUDE.md`.)
- `rememberSyncNotifications` (plan 03 put it in `ui/sync/SyncNotifications.kt`) only reports "nothing to show" after
  it has shown something — moved from `ui/CLAUDE.md`'s chrome section.
- `SyncController.startRestoring` forgets the credentials on a first launch before restoring, because the iOS Keychain
  outlives an uninstall (one line; the root CLAUDE.md says why).

`screens/onboarding/CLAUDE.md`'s "Start up and sync consent" names `SyncConsentOrigin` and `syncConsentAnswerOf`; leave
the names, point to `ui/sync/CLAUDE.md` if it says where they live. Grep every `CLAUDE.md`, `.claude/skills` and
`documentation/` for `showSyncConsentAnswer`, `SyncConsentOriginTest`, `ui/platform/SyncConsent` and
`state/SyncController` and update them. `platform/CLAUDE.md` and `ui/CLAUDE.md` shrink: `check_sizes.py --update`.

## Tests

- `SyncConsentTest` (moved): unchanged bodies.
- `NavigatorTest`, which already builds a `Navigator` with fakes:
  - `a consent answered on the songs opens the settings on the library tab`: stack `[Songs]`,
    `showSyncConsentAnswer(SyncConsentOrigin.SETTINGS)` → `[Songs, Settings]`, `settingsTab == LIBRARY`.
  - `a consent answered on the settings screen stays there and switches to the library tab`: stack `[Songs, Settings]`
    on `GENERAL` → stack unchanged (no re-selection, so no scroll-to-top request), `settingsTab == LIBRARY`.
  - `a consent asked from an onboarding opened in the settings reopens it on its sync page over the about tab`:
    `ONBOARDING_FROM_SETTINGS` from `[Songs]` → `[Songs, Settings, Onboarding(isOpenedFromSettings = true)]`,
    `settingsTab == ABOUT`, `onboardingStep == SYNC`.
  - `an answer is not shown over a stack the user has built on`: `[Songs, SongDetails(…)]` → unchanged.
  (`OnboardingStep.SYNC` must be among the test's `onboardingSteps`; the test class keeps them in a `var`.)

Then the root CLAUDE.md test command, the four platform compile tasks named in the brief (the `actual`s change in every
source set, so all four matter), `spotlessCheck`, and the size check.

## Manual check

On the web build with a Dropbox app key configured (`local.properties`): Connect from Settings → Library and from the
onboarding's Dropbox page (opened from Settings → About); after the consent page each comes back where it was tapped,
showing the connection.
