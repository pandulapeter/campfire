<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/launch

- `LaunchScreen` (the mark, neutral color, no text) **covers** the app as an opaque `Surface` while it composes behind
  it, and fades once preferences are in, the theme settled, `hasLibraryToShow` true (both lists built too, not only
  read, or the songs bar's New button lands late and the setlists screen shows its loading placeholder), and any `navigateOnLaunch` place found. It is Compose rather than a hidden desktop window because Compose Desktop has no frame clock with no window
  shown, so it would deadlock.
- Nothing on the back stack animates while it is up (`hasShownApp`).
- Desktop app only: the mark grows as it fades (`isLaunchScreenWholeStartup`). Android's splash and the web loading page
  are held until `onAppReady` (fires two frames after the launch screen goes, since `withFrameNanos` resumes before its
  frame is drawn) and there it does not fade (`isStartupScreenHeldUntilAppReady`). Web: `window.campfireReady`;
  Android: a pre-draw listener.
- It belongs to the process, not the composition: after `hasShownApp`, a recreated activity composes none and releases
  `onAppReady` at once. On the web it also waits up to five seconds for `areDrawablesLoaded`, since resources never
  report a failed drawable.
- `LaunchState` holds what the screen waits for (`hasLibraryToShow`, the launch address's `navigateOnLaunch`) and
  `hasShownApp` / `isAppOnScreen`; the first run's decision is waited for through `FirstRunController.isDemoLibraryPending`.
