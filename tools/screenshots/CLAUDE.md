<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# :tools:screenshots

The store screenshots, rendered rather than taken: a plain JVM module that draws the desktop build of the app
offscreen (`ImageComposeScene`) as each platform's store listing shows it, at the exact pixels of the Screenshot Bro
device frame it goes into. No app module depends on it and no workflow runs it; the `store-screenshots` skill drives
it, then imports and exports through Screenshot Bro. `./gradlew :tools:screenshots:run` writes
`renders/<row label>/<NN-id>.png` here (gitignored, replaced on every run); `--args="--out <folder> --only <names>"`.
`Website.kt` adds campfire-songbook.com's screenshots, which go to the website's repository rather than through
Screenshot Bro. `Banners.kt` adds the rows that show several shots at once - the README's two banners, the Play Store's feature
graphic and the website's link preview - from the same shots, in their own theme where a row asks for one.

- **The library is a copy, and never committed**: `library/` here (gitignored) holds a Campfire data folder's
  `library/`, `covers/` and `preferences/preferences.json`. Every render copies it into a temporary home of its own
  (`user.home`, which the desktop file storage derives its folder from as Koin creates it) with the shot's theme and
  language written into the preferences and the current version marked as introduced, so nothing a render does
  reaches the copy, let alone the user's own library, and no What's new dialog opens over a shot.
- **One app start per image** (`render`): the home, `PlatformImpersonation` and Koin are set up before the scene, and
  Koin is stopped after it, since the storage, the repositories and the view model all read their start once. The
  scene runs on `Dispatchers.Main` (Swing), which the view model dispatches to, and is pumped a frame at a time with a
  real `delay` between frames so the library's IO lands; `CampfireApp`'s `onAppReady` says the launch screen has gone.
- **What makes it look like the platform** (`Device`, `SystemChrome`, `Fonts`): `PlatformImpersonation` (in
  `:presentation`'s desktop source set) gives the shared UI the platform's answers — the touch scale instead of the
  desktop's 0.85, the store and the app icon the settings name, Android's wallpaper colors, the interface and monospace
  fonts — and the tool provides the rest itself: the `FilePicker` (whether Share is offered), and the insets through
  `LocalPlatformWindowInsets` (an internal Compose API) for the status and navigation bars of the phones and tablets
  and the title bars of the desktops, which `SystemChrome` then draws over the app in the shot's theme, all at 9:41
  with a full battery. Fonts: Roboto and Droid Sans Mono for Android and ChromeOS, Open Sans for Windows (the closest
  open relative of Segoe UI, whose license keeps it on Windows), the Mac's own SF for the Apple platforms; the build
  downloads the three into `build/fonts`, each pinned to a commit of its repository and checked against its SHA-256 in
  `build.gradle.kts` — updating one means a new commit in its address and a new checksum there.
- **Nothing reaches outside** (`Fakes.kt`): sync is a repository that shows a connected account and runs nothing, the
  metronome is silent and lights only the beat a shot asks for, the file picker writes nothing. Covers come from the
  copied cache.
- **The shots** are `shots` in `Shot.kt`, in the listing's order, each with its theme, language and the `drive` block
  that brings the app to its state through `CampfireViewModel`; their rules are kept there, so a retake is the same
  picture. The headlines are part of the Screenshot Bro project, not of the images.
