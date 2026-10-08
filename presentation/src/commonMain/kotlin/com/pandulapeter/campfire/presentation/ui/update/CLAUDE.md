<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/update

In-app updates.

- `ui/update/AppUpdate.kt` / `ui/update/AppUpdateGate.kt` — what the store the app came from has to say about a newer build of it, which is only ever something on Android: Play is the one store of the four with an API for this (the App Store has none, and asking Apple's public lookup endpoint instead would be the second thing in the app that reaches the network). `AppUpdateGate` wraps the whole app inside `CampfireApp`, so the hint and the blocking screen are in the theme and the language chosen *in the app*. The policy is the Play release's own `updatePriority` and nothing else: 2–3 is a dismissible offer of a flexible update, 4–5 a screen the app cannot be used past. That screen is drawn *over* the app rather than in place of it, so an update that turns out not to install leaves the library where the user was. Dialogs, sheets and menus are windows of their own on Android, which that screen cannot cover, so none of them is composed while it is up (`LocalIsCoveredByRequiredUpdate`, read by `CampfireContent` and `OverflowMenu`); what they were showing stays in their state and comes back if the screen goes. The controller reports and the gate decides when to act: the immediate flow is started by the gate as its screen goes up, and both that and the Restart offer wait for an editor holding unsaved text (Restart also for a sync run). What Play last said is saved with the activity, so a rotation keeps the blocking screen and the dialogs up rather than uncovering the app until Play answers again; a carried-over state that the first check cannot confirm is dropped. Play is first asked once the app is on screen (`CampfireViewModel.isAppOnScreen`), so a cold start does not bind to its service — and maybe start the Play Store's own process — under the splash; a recreated activity, whose app is on screen from its first frame, asks on its resume as before. None of it shows up in a build Play did not install, which answers every check with an error. See the Updates section of the root `CLAUDE.md`.
