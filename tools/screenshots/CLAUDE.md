<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# :tools:screenshots

The store and website screenshots, rendered rather than taken: the desktop build drawn offscreen (`ImageComposeScene`)
as each platform's listing shows it, at its device's own pixels, then framed. A release that asks for it renders and
publishes them on CI (`.github/workflows/screenshots.yml`, `.github/CLAUDE.md`); the `store-screenshots` and
`website-screenshots` skills drive it locally, to look at a change. Its test source set holds the app's UI tests.

- `./gradlew :tools:screenshots:run` writes `renders/<row label>/<NN-id>.png` (gitignored, replaced every run);
  `--args="--out <folder> --only <names>"`; `--apple-chrome true|false` and `--shard <index>/<count>` render a part, as
  the release's jobs do (a device whose chrome is set in Apple's fonts needs a Mac). `--semantics true` writes a
  `<NN-id>.txt` beside each render, its screen's semantics pruned to an indented line a node that says or does
  something (`SemanticsSnapshot.kt`), to check a state without reading the picture.
- **Every framed file is named by `.github/scripts/store_images.py`** (`SETS`, what a store's API takes; `MANUAL`, what
  stays by hand): a new device or image fails the release's check until it is named there, with its store's rules.
- **A run is split across processes** (`ParallelRun.kt`, each about 1.6 GB), since one process holds one app start at
  a time. A render is mostly waiting for the app to settle, so four take little longer than one.
- **The library** (`library/`) is a data folder's `library/`, `covers/`, and `preferences/preferences.json`, committed
  and holding only what the images show of it. A song whose words no image shows keeps its directives and chords (a
  row without chords would read "Lyrics only"), and stays a file, since the song count moves the list's scrollbar. A
  public domain song keeps its words. A song under copyright whose first page an image shows keeps the words of the
  sections that page holds, the rest turned into filler of the same length, chords and line breaks kept, since the
  page count, the Chords and the Scales sections are worked out from the whole song. Its covers are only the songs
  whose rows show. Every render copies it into its own temporary `user.home`, with the shot's theme and
  language written in, the current version marked as introduced, and the song layout and chord names chosen (no
  question dialogs). Nothing reaches the copy or the user's library.
- **One app start per image** (`withCampfireApp`, which the UI tests share): home, `PlatformImpersonation`, and Koin are
  set up before the scene and Koin stopped after, since storage, repositories, and the view model read their start
  once. The scene runs on `Dispatchers.Main` (Swing), which the view model dispatches to, pumped a frame at a time with
  a real `delay` so IO lands.
- **`settle()` waits until the screen has settled** (`Settling.kt`): up to half a second (a second after the start) for
  a first change, then until nothing has changed for a quarter of a second, measured on a grayscale thumbnail of one
  sample per 4 dp square, so a caret or a spinner does not keep it waiting. `settle(milliseconds)` is only for a shot
  that times something (a beat, a press). A shot whose state comes from the view model waits for that state.
- **Looking like the platform**: `PlatformImpersonation` (`:presentation`'s desktop source set) supplies the platform's
  answers; insets go through `LocalPlatformWindowInsets` (an internal Compose API). The app's scene is only its window,
  the system chrome a full-screen scene laid over it: a dialog (every bottom sheet) is a layer as large as its scene,
  so it would otherwise cover a taskbar, a shelf, or a menu bar. A shot's touches are in the screen's pixels.
- Fonts: the app is drawn in the fonts it bundles; only the system chrome takes the platform's: Roboto, Open Sans
  (Segoe UI's license keeps it on Windows), the Mac's own SF. The build downloads the first two, each pinned to a
  commit and checked against its SHA-256 in `build.gradle.kts`. The framing's scenes are not the app's, so they run
  `ProvideScriptFaces` themselves (Inter has no Hebrew) and set a right-to-left language's texts from the right, never
  its layout: every element is placed by its offset from the left, and only a chip's own row is mirrored.
- **Nothing reaches outside** (`Fakes.kt`): sync shows a connected account and runs nothing, the metronome is silent,
  the tuner hears only scripted readings, the file picker writes nothing. Covers come from the copied cache.
- **Framing** renders nothing. `--frame store` writes into a new `~/Desktop/Campfire store images <version>/`, each
  listing on its store's canvas with no alpha channel (Apple refuses screenshots that have one), and the stores' other
  images (`Compositions.kt`). The texts are `documentation/store-listings/screenshot-texts/<language>.md`. `--frame
  readme` composes the README's banners straight into `documentation/screenshots` as WebP and the website's link
  preview into the renders folder.
- **The banners** (`Banners.kt`) are two sets, `store-banners` and `readme-banners`, so that each skill renders only
  its own.
- **The shots** are `shots` in `Shot.kt`, each with a `drive` block that brings the app to its state through
  `CampfireViewModel`; keep their rules there so a retake is the same picture. Headlines and chips are drawn by the
  framing, never into the renders.
- **Promo video footage** (`Promo.kt`, `--only promo`): `record` draws a frame every 1/60 s of the scene's clock, so
  animations play at their own speed however slow drawing is. A tap within a clip is press and release on separate
  frames at the label's middle, since `tap` settles between them. A clip that types raises `OnScreenKeyboard`, which
  the app is told of as the platform's keyboard inset.

## Benchmark

`./gradlew :tools:screenshots:benchmark` (Windows: `gradlew.bat`), `--args="--suite library|transitions|all"` (`all`
by default), is run by the owner only: the numbers are the machine's, comparable only with its own earlier runs, and are
printed with `Locale.ROOT`. `-Pcampfire.benchmark.jvmArgs="…"` adds JVM options (another collector) to a run.

The **library suite** (`Benchmark.kt`; `--runs <n> --songs <n>`, by default 3 and 3,000) times a large library: the
start until the songs list shows its first song, a long song until its first line is drawn, a PDF of 60 songs from
opening the export screen until the picker gets it, and a zip of the library imported into an empty one (until the
review, until listed), plus the first start's resident set and the peak heap. The library is made up from a seed
(`BenchmarkLibrary.kt`). Each figure is taken cold (a fresh process) and then as the median of warm runs, each in an app
started anew. Sync is not measured, since its fake provider is `:data:sync:implementation`'s test code.

The **transitions suite** (`TransitionBenchmark.kt`; `--runs` 10, `--hz` 60, `--width 2400 --height 1500 --density 1.5`)
times every frame of the app's transitions, over the committed `library/` (real cards' covers, labels, and tags cost what
the generated one's cannot), as the Windows app in one `ImageComposeScene`. A frame (`PhasedFrame.kt`) is compose
(`performFrame`), layout (`measureAndLayout`), and draw (recorded into a Skia picture, never rasterized), with the main
thread's bytes allocated and GC pause time (pause beans only), then a pause of `1000 / hz` ms whose overrun is "between"
(the app's coroutines on Main). The phases are private fields of `ImageComposeScene`: re-check after every Compose update
(a failed lookup times `render` whole and says so). Scenarios, 50 frames after a trigger sent through the view model:
`push:` / `pop:` `plain`, `long`, `tabs`, `abc`, `setlist` (a setlist's middle song), and `fresh` (a long song not opened
before, each pass), `tab:songs>setlists`, `tab:setlists>songs`, `sheet:open`, and `sheet:close`. One app serves the whole
run: pass 1 is cold, passes 2+ reopen, warm is the median of passes 3+. A frame is over budget when it and its "between"
pass `1000 / hz` ms. `--csv <path>`, one row per frame, is the only thing either suite writes.

## UI tests

`./gradlew :tools:screenshots:test`, run by `tests.yml`. Each test starts the whole app with `runAppTest` — real view
model, use cases, repositories, and files in a temporary home — and drives it like a user, through Compose's
`SkikoComposeUiTest` at a density of one, so a `TestWindow` is in dp. A right-to-left test runs in Hebrew.

- **Assert what a user sees or what lands on disk**: semantics found by text read from the resources (`string`,
  `pluralString`, never typed English) or the library folder, or pixels where semantics cannot tell. Never the view
  model's fields; a test that would fail after a correct rewrite of the screen is the wrong test. The ways to a screen
  or sheet that several tests take are `AppTestNavigation.kt`'s.
- The library is the demo songs plus the `src/test/resources/songs` and `setlists` files a test names; never the tool's
  own `library/`. A preferences document is written, so no onboarding, no planted demo, and no question dialogs; covers
  are off unless asked for. `isFirstRun` starts from an empty data directory instead, in English.
- **What may be faked** is only what reaches outside or picks files (`Fakes.kt`, `TestFakes.kt`, the test's file
  picker, a test's own `extraFakes`). A new fake comes with the test that needs it.
- **The keyboard** is the test's to raise (`keyboardHeight`). It reaches the app twice, through
  `LocalPlatformWindowInsets` and through the scene's own insets, since a dialog (every bottom sheet) is a layer of the
  scene and `imePadding` reads its root's insets, not the composition's.
- **The platform** is Android's phone unless a test passes `platform`: it has no keyboard, so tooltips name no keys
  and the desktop's shortcuts (Ctrl+1 … 6) are not heard. A test of keys or hints passes `ImpersonatedPlatform.WINDOWS`
  in `TestWindow.DESKTOP`, whose keys are Ctrl on any machine, and writes them with `keys(KeyShortcut.X)`. Keys are
  sent to `onRoot()` with `performKeyInput`, hovering with `performMouseInput { enter(center) }`.
- **A resize** (`resizeWindow`) reaches the app through a window info of the test's own (`ResizableWindowInfo`), since
  the scene's keeps the size it was made with; the test steps the clock or waits for what follows.
- **Back** is the test's to send too (`pressBack`, `swipeBack`, or a gesture held through `startBackGesture` for a test
  stepping `mainClock` itself): only the desktop app's window turns Escape into a back event.
- **`viewModel` and `dataDirectory`** reach the paths no screen drives, the exit and the pause (`AppExitTest`): a holder
  is called inside `runOnIdle`, a suspending member with `runBlocking` outside it, and `storedPreferences()` /
  `storedSetlist()` read what landed. Never `ViewModelStore.clear()` mid-test. An import stage nothing produces yet is
  put up through the import's state (`ImportStagesTest`), never by faking a use case.
- **The test's picker receives an archive into the staging folder** as a platform's intake does, so the import's
  tests drive the streaming import the app runs.
- The view model keeps `Dispatchers.Main` (Swing) while the composition runs on the test clock, and library writes
  happen on IO threads: wait with `waitForLibrary` / `waitUntil`, never a sleep. A flaky test is fixed or deleted the
  same day, never retried.
