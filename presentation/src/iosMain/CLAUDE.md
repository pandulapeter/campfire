<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — iosMain

- UIKit view controllers stay in `:app:ios`; the share extension's `InboxShare`s are each completed as taken once their
  import has settled.
- `onUiModeChanged` hands out the theme **preference**, not the resolved dark flag: iOS reads the system mode from the
  trait collection that the app's override changes, so "System default" must leave the override unset.
- `onAppIconChanged` fires once the color has held for a second while resumed, and at every resume, since iOS alerts on
  each switch and refuses one while the app is not active.
- **First-launch clipboard link**: the App Store has no install referrer, so the landing page copies the link, and
  `detectPatterns` (no prompt) tells whether the pasteboard seems to hold a web address. The read, which prompts Allow
  Paste, happens only on the onboarding card's tap, asking for the URL when the pasteboard has one and for the text
  otherwise, never both.
- Back navigation, the swipe included, is Navigation 3's.
- **Drops** (iPad and iPhone): an item offering `public.utf8-plain-text` is a text, anything else a file, named by
  `suggestedName` plus its type's extension. Every load starts inside the drop, since a provider answers only then; each
  file is read in its completion handler, as long as the provider's copy lives, under one locked `ImportBudget`.
