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

## Play in-app updates

Play's in-app updates, and only on Android: `:presentation`'s `ui/update/AppUpdate.kt` is the contract and
`ui/update/AppUpdateGate.kt` the UI, with the Play Core implementation in `androidMain` and a no-op actual on the other
three. The gate wraps the whole app inside `CampfireApp`, so it speaks the theme and the language chosen in the app.

- The **Play release's `updatePriority` is the entire policy** and it is chosen per release rather than in the code:
  0–1 is left to Play's own schedule, 2–3 offers a dismissible flexible update that downloads in the background,
  4–5 covers the app with a screen that cannot be dismissed until the update is there. The thresholds live in
  `AppUpdate.android.kt`. The priority also says which kind of flow an update already in progress is, since Play's
  answer does not — which is what lets an Activity recreated mid-download pick the download up instead of offering
  it again. `publish-android.yml` asks for the number as its `update_priority` input — which a release
  sets with a `<!-- play-store update-priority: N -->` comment in its description — defaulting to 0 — the number belongs to the release being published, not to the code being published.
- Back on the blocking screen closes the app. The app it covers is still composed behind it, so the gesture has to
  be taken rather than allowed through, and leaving is the only thing it can honestly mean there.
- The blocking screen is drawn **over** the app rather than in place of it, so a required update that turns out not
  to install leaves the library exactly where the user was. Nothing that is a window of its own — a dialog, a sheet,
  a menu — is shown while it is up.
- Neither the blocking screen (nor the immediate flow started with it) nor the flexible update's Restart is put over
  an editor with unsaved text: the gate waits until the text has been saved or let go of (`hasUnsavedEditorChanges`).
  Restart waits for a sync run too; a required update does not — a run it cuts off is reported as interrupted the
  ordinary way.
- Nothing of this exists outside a Play-installed build: a debug APK, a sideloaded release or a device with no Play
  answers every check with an error, which is why the flow can only be exercised from an internal testing track.
- **iOS has no equivalent.** Apple ships no API that tells an app the store has a newer build; the only way to ask
  is to poll their public lookup endpoint for the published version, which would make it the second thing in the app
  that reaches the network. iOS updates apps on its own, so the iOS actual stays `NotAvailable`. Desktop and the web
  answer to no store at all, and the web build is downloaded again every time it is opened.
