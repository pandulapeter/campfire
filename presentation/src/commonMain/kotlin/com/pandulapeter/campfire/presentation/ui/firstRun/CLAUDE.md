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

## The demo library

- Two public domain songs and one setlist in `composeResources/files/demo`, planted through the ordinary import, so
  nothing here writes a file and collisions behave like any import. Between them the songs use the directives the
  details screen draws; each carries a `Demo` tag so it can be filtered out.
- Each file is named exactly as the library would name it, so `isPresentIn` answers from the library's own file names,
  not from anything remembered: Settings offers to add them back while any is missing (adding only what is missing), and
  the empty library state offers it as its last button.
- **Planted once**, only when `IsFirstRunUseCase` (no preferences document) and an empty *whole* library
  (`ScreenData.isWholeLibrary`, not an unreadable folder) agree; it announces nothing. Preferences are written
  afterwards whether or not anything was planted, so a library emptied later is not taken for fresh, unless the
  preferences read failed, which saves nothing. The launch screen is released before that save. That write is an
  `updateUserPreferences`, not a save of the view model's copy, which could lag by a dispatch and write it back out.
- Imported directly, not through the import queue; the queue takes no batch until the decision is over
  (`demoLibraryDecision`), so a file opened with the app on first start lands in a library already holding the demo.
- `isDemoLibraryPending` starts true (not set when planting begins) and keeps the launch screen up over the whole
  decision: the scan can finish first, and an empty library would flash before the songs arrived.
- Reading the bundle times out after ten seconds: on the web those are requests, and one never answering would hold the
  launch screen for good.
- Both ways of planting record what they wrote (`RememberDemoLibraryFilesUseCase`, local-only), so sync takes another
  device's untouched demo from the cloud instead of keeping a copy of each.
- Android's first launch also reads Play's install referrer (`src/androidMain`); a shared link it names joins the import
  queue, landing beside the demo, after the onboarding.
- iOS's first launch asks instead whether the clipboard seems to hold a web address (`offerClipboardLink`), without
  reading it. If so, the onboarding's last page offers to open it (`isClipboardLinkOffered`) until taken or the
  onboarding leaves; skipped before its last page, a snackbar offers it once (`Message.ClipboardLinkOffered`). Only the
  tap reads it: a Campfire link joins the queue, anything else is `Message.ClipboardNotCampfireLink`, and a refused read
  is left at that.

## The onboarding

- Put on the back stack behind the launch screen and held back only by a dialog (`canShowOnboarding`). A file the app
  was opened with is read behind it; its import screen (or the one song it opens) follows as the onboarding leaves.
- The first run's preferences write sets `UserPreferences.isOnboardingPending`; only the onboarding leaving the back
  stack (finished, skipped, or backed out of from page one) clears it, before any import screen is pushed. A start that
  finds it still owed opens it again onto a stack of only its home screen, and What's new is recorded rather than shown.

## What's new

- Introduces each version once, after the app is on screen and startup imports and their reviews have finished.
- `UserPreferences.seenWhatsNewVersions` remembers every introduced version and the first installed one, which is
  skipped in favor of the onboarding.
- An empty `whats_new_message` suppresses the dialog and still records the version; the prepare-release skill replaces
  or clears it for every release.
