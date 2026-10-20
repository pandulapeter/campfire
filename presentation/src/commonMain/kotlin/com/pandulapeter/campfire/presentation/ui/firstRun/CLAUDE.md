<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/firstRun

## The demo library and the first run

### The app is shipped with two songs and one setlist

- Public domain songs in `composeResources/files/demo`, read back as `ImportedFile`s (`DemoLibrary.kt`) and planted
  through the ordinary import (`ImportController`), so nothing here writes a file and collisions behave like any import.
  The songs use between them the directives the details screen draws, and each carries a `Demo` tag so it can be
  filtered out.
- Each file is named exactly as the library would name it, so `isPresentIn` answers from the library's own file names,
  not from anything remembered: Settings offers to add them back while any is missing (adding only what is missing),
  and the empty library state offers it as its last button with no condition of its own.
- **Planted once**, by `FirstRunController.plantDemoLibraryOnFirstRun`, only when `IsFirstRunUseCase` (no preferences
  document) and an empty *whole* library (`ScreenData.isWholeLibrary`, not an unreadable folder) agree. It announces
  nothing. Preferences are written afterwards whether or not anything was planted, so a library emptied later is not
  taken for fresh — unless the preferences read failed, which saves nothing. The launch screen is released before that
  save.
- That preferences write is an `updateUserPreferences`, not a save of the view model's copy, which could lag the record
  by a dispatch and write it back out.
- Imported directly, not through the import queue; the queue takes no batch until the decision is over
  (`demoLibraryDecision`), so a file opened with the app on first start lands in a library that already has the demo.
- `isDemoLibraryPending` starts true (not set when planting begins) and keeps the launch screen up over the whole
  decision: the scan can finish first, and an empty library would flash before the songs arrived.
- Reading the bundle times out after ten seconds (`readDemoLibrary`): on the web those are requests, and one that never
  answers would hold the launch screen for good.
- Both ways of planting record what they wrote under the demo's names (`RememberDemoLibraryFilesUseCase`, local-only;
  from `applyImportPlan` for an `isDemoLibrary` request), so sync takes another device's untouched demo from the cloud
  instead of keeping a copy of each.

### The onboarding on a first run

- The first run opens the onboarding (`ui/screens/onboarding/CLAUDE.md`), put on the back stack behind the launch
  screen and held back only by a dialog (`canShowOnboarding`). A file the app was opened with is read behind it; its
  import screen (or the one song it opens) follows as the onboarding leaves.
- The first run's preferences write sets `UserPreferences.isOnboardingPending`; only the onboarding leaving the back
  stack (finished, skipped, or backed out of from page one) clears it (`onBackStackChanged`), before any import screen
  is pushed. A start that finds it still owed opens it again onto a stack of only its home screen, and What's new is
  recorded rather than shown over it.

### What's new

- Introduces each version once, after the app is on screen and startup imports and their reviews have finished.
- `UserPreferences.seenWhatsNewVersions` remembers every introduced version and the first installed one, which is
  skipped in favor of the onboarding.
- An empty `whats_new_message` suppresses the dialog and still records the version; the prepare-release skill replaces
  or clears it in both languages for every release.
