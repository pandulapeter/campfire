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

The iOS shell, called by `:app:ios`.

- `ui/CampfireIosApp.kt` takes the URL opener and file picker as parameters (UIKit view controllers stay in
  `:app:ios`), and the share extension's shares as `InboxShare`s, each completed as taken once its import has settled.
- `onUiModeChanged` hands out the theme **preference**, not the resolved dark flag: iOS reads the system mode from the
  trait collection that the app's override changes, so "System default" must leave the override unset.
- `onAppIconChanged` fires once the color has held for a second while resumed, and at every resume, since iOS alerts
  on each switch and refuses one while the app is not active (see `app/ios`).
- Back navigation, the swipe included, is Navigation 3's in `CampfireApp`.
- **Drops** (`ui/LibraryDropTarget.kt`, iPad and iPhone): an item offering `public.utf8-plain-text` is a text (a
  selection), anything else a file (one from Files offers its own type), named by `suggestedName` plus its type's
  extension. Every load starts inside the drop, since a provider answers only then; each file is read in its completion
  handler, which is as long as the provider's copy lives, under one locked `ImportBudget`.
