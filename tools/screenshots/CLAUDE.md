<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# :tools:screenshots

The store screenshots, rendered rather than taken: a plain JVM module that draws the desktop build offscreen
(`ImageComposeScene`) as each platform's listing shows it, at the exact pixels of its Screenshot Bro device frame.
Nothing depends on it; the `store-screenshots` skill drives it, and its test source set holds the app's UI tests.

- `./gradlew :tools:screenshots:run` writes `renders/<row label>/<NN-id>.png` (gitignored, replaced every run);
  `--args="--out <folder> --only <names>"`. `Website.kt` adds the website's shots, `Banners.kt` the multi-shot rows
  (README banners, Play feature graphic, website link preview).
- **The library is a copy, never committed**: `library/` (gitignored) holds a data folder's `library/`, `covers/`, and
  `preferences/preferences.json`. Every render copies it into its own temporary `user.home` (the desktop file storage
  derives its folder from it when Koin creates it), with the shot's theme and language written in and the current
  version marked as introduced (no What's new dialog). Nothing reaches the copy or the user's library.
- **One app start per image** (`render`, over `withCampfireApp`, which the UI tests share): home,
  `PlatformImpersonation`, and Koin are set up before the scene and Koin stopped after, since storage, repositories,
  and the view model read their start once. The scene runs on `Dispatchers.Main` (Swing), which the view model
  dispatches to, pumped a frame at a time with a real `delay` so IO lands; `onAppReady` says the launch screen is gone.
- **Looking like the platform**: `PlatformImpersonation` (`:presentation`'s desktop source set) supplies the
  platform's answers (touch scale, store, icon, Android wallpaper colors, fonts); the tool provides the `FilePicker`
  (whether Share is offered) and insets through `LocalPlatformWindowInsets` (an internal Compose API), which
  `SystemChrome` draws over the app (9:41, full battery).
- Fonts: Roboto and Droid Sans Mono (Android, ChromeOS), Open Sans (Windows; Segoe UI's license keeps it on Windows),
  the Mac's own SF (Apple). The build downloads the three into `build/fonts`, each pinned to a commit and checked
  against its SHA-256 in `build.gradle.kts` — updating one means a new commit address and a new checksum.
- **Nothing reaches outside** (`Fakes.kt`): sync shows a connected account and runs nothing, the metronome is silent and
  lights only the beat a shot asks for, the tuner (`ScriptedTuner`) hears only scripted readings, the file picker
  writes nothing. Covers come from the copied cache.
- **The shots** are `shots` in `Shot.kt`, in listing order, each with theme, language, and a `drive` block that brings
  the app to its state through `CampfireViewModel`; keep their rules there so a retake is the same picture. Headlines
  belong to the Screenshot Bro project, not the images.
- **Promo video footage** is `Promo.kt`, rendered only with `--only promo` into `Promo/`: stills and clips. `record`
  draws a frame every 1/60 s of the scene's clock as JPEGs, so animations play at their own speed however slow drawing
  is. A drag moves a finger a frame at a time (`touch`); a tap within a clip is press and release on separate frames at
  the label's middle (`centerOf`; `centersOf` where several share a label), since `tap` settles between them. The
  metronome runs at the video's 160 BPM. The video is composed outside the repository.

## UI tests

`./gradlew :tools:screenshots:test` (`src/test`), run by `tests.yml`. Each test starts the whole app with `runAppTest`
(`AppTest.kt`) — real view model, use cases, repositories, and files in a temporary home — and drives it like a user,
through Compose's `runSkikoComposeUiTest` at a density of one, so a `TestWindow` is in dp.

- **Assert what a user sees or what lands on disk**: semantics found by text read from the resources (`string`,
  `pluralString`, never typed English) or the library folder (`songsFolder`). Never the view model's fields; a test that
  would fail after a correct rewrite of the screen is the wrong test. Tests sit in the package of the screen they cover.
- The library is the demo songs plus `src/test/resources/songs` files a test names; never the tool's own `library/`.
  A preferences document is written, so no onboarding and no planted demo; covers are off (they would download).
- **What may be faked** is only what reaches outside or picks files: `Fakes.kt` and the test's file picker
  (`pickedFiles`). A new fake comes with the test that needs it.
- The view model keeps `Dispatchers.Main` (Swing) while the composition runs on the test clock, and library writes
  happen on IO threads: wait for them with `waitForLibrary` / `waitUntil`, never a sleep. A flaky test is fixed or
  deleted the same day, never retried.
- Tests use Compose's default font; the font download is the `run` task's only.
