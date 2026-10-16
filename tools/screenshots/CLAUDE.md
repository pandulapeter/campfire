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
  platform's answers (touch scale, store, icon, Android wallpaper colors); the tool provides the `FilePicker`
  (whether Share is offered) and insets through `LocalPlatformWindowInsets` (an internal Compose API), which
  `SystemChrome` draws over the app (9:41, full battery).
- Fonts: the app is drawn in the fonts it bundles, as on every platform. Only the system chrome around it takes the
  platform's: Roboto (Android, ChromeOS), Open Sans (Windows; Segoe UI's license keeps it on Windows), the Mac's own SF
  (Apple). The build downloads the two into `build/fonts`, each pinned to a commit and checked against its SHA-256 in
  `build.gradle.kts` — updating one means a new commit address and a new checksum.
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
  metronome runs at the video's 160 BPM. The video is composed in `tools/promo-video`.

## UI tests

`./gradlew :tools:screenshots:test` (`src/test`), run by `tests.yml`. Each test starts the whole app with `runAppTest`
(`AppTest.kt`) — real view model, use cases, repositories, and files in a temporary home — and drives it like a user,
through Compose's `SkikoComposeUiTest` at a density of one, so a `TestWindow` is in dp. `TestAppearance` sets the
language, theme, and system text size.

- **Assert what a user sees or what lands on disk**: semantics found by text read from the resources (`string`,
  `pluralString`, never typed English) or the library folder (`songsFolder`), or pixels where semantics cannot tell
  (`PedalCoverageTest` reads the song's faded top edge from them). Never the view model's fields; a test that
  would fail after a correct rewrite of the screen is the wrong test. Tests sit in the package of the screen they cover;
  the ways to a screen or sheet that several of them take are `AppTestNavigation.kt`'s.
- The library is the demo songs plus the `src/test/resources/songs` and `setlists` files a test names; never the tool's
  own `library/`. A preferences document is written, so no onboarding and no planted demo; covers are off unless asked
  for, and the song text size is `runAppTest`'s `fontScale`. `isFirstRun` starts from an empty data directory instead,
  in English (the system's language, pinned for the test).
- **What may be faked** is only what reaches outside or picks files: `Fakes.kt`, `TestFakes.kt` (the cover search finds
  nothing, no cover downloads), and the test's file picker (`pickedFiles`). A new fake comes with the test that needs it.
- **The keyboard** is the test's to raise (`keyboardHeight`): the desktop has none. It reaches the app twice, through
  `LocalPlatformWindowInsets` and through the scene's own insets, since a dialog (every bottom sheet) is a layer of the
  scene and `imePadding` reads its root's insets, not the composition's.
- The view model keeps `Dispatchers.Main` (Swing) while the composition runs on the test clock, and library writes
  happen on IO threads: wait for them with `waitForLibrary` / `waitUntil`, never a sleep. A flaky test is fixed or
  deleted the same day, never retried.

### Golden renders

`GoldenRendersTest` renders a short list of cases (smallest phone, Hungarian at text sizes 1.3 and 2, sheets with the
keyboard up, the rail on a phone on its side, one dark case per screen type) and compares each with
`src/test/resources/goldens/<name>.png`, allowing a few pixels of anti-aliasing (`Goldens.kt`). Each case names the bug
it would have caught; keep the set small, so an intended change's rewritten goldens are quick to review. A run leaves
what it drew in `build/goldens/actual` and, for a changed golden, a diff in `build/goldens/diff` (CI uploads both when
it fails). Goldens are recorded and compared on Linux only, since each system rasterizes text its own way; elsewhere
the cases are drawn and not compared. To update them after an intended change, run
`./gradlew :tools:screenshots:test -Pcampfire.updateGoldens=true` on Linux, or copy a failed CI run's
`goldens/actual` over the folder, then review the PNG diff in the commit.
