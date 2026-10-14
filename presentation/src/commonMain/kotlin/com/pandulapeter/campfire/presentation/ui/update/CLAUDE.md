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

## Play in-app updates

Android only: `AppUpdate.kt` is the contract, `AppUpdateGate.kt` the UI, the Play Core implementation in `androidMain`,
a no-op actual elsewhere. The gate wraps the whole app inside `CampfireApp`, so it uses the in-app theme and language.

- **The Play release's `updatePriority` is the entire policy**, chosen per release, not in code: 0–1 Play's own
  schedule, 2–3 a dismissible flexible update, 4–5 a blocking screen until installed. Thresholds in
  `AppUpdate.android.kt`. The priority also tells which flow an in-progress update is (Play's answer does not), so a
  recreated Activity resumes the download rather than offering it again. Set by `publish-android.yml`'s
  `update_priority` input, from a `<!-- play-store update-priority: N -->` comment in the release description, default 0.
- The blocking screen is drawn **over** the app, so a required update that fails to install leaves the user where they
  were. Back on it closes the app (the gesture must be taken, not passed through).
- Dialogs, sheets, and menus are separate windows on Android that the screen cannot cover, so none is composed while
  it is up (`LocalIsCoveredByRequiredUpdate`, read by `CampfireContent`, `OverflowMenu`, and `TunerListeningEffect`);
  their state survives and they return if the screen goes.
- The gate decides when to act: the blocking screen (and the immediate flow started with it) and the flexible update's
  Restart wait for an editor with unsaved text (`hasUnsavedEditorChanges`). Restart also waits for a sync run and a
  playing click; a required update does not (a cut-off run is reported as interrupted).
- What Play last said is saved with the activity, so a rotation keeps the blocking screen up; a carried-over state the
  first check cannot confirm is dropped.
- Play is first asked once the app is on screen (`CampfireViewModel.isAppOnScreen`), so a cold start does not bind its
  service under the splash; a recreated activity asks on resume.
- A build Play did not install (debug, sideloaded, no Play) answers every check with an error, so the flow can only be
  exercised from an internal testing track.
- **No other platform has this.** iOS has no API; polling Apple's lookup endpoint would be a second network request the
  app makes on its own, and iOS updates apps itself, so its actual stays `NotAvailable`. Desktop and the web answer to
  no store (the web reloads the build on every open).
