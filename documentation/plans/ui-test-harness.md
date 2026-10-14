<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# UI tests on the desktop JVM — plan

Written 2026-10-14 against `312cbd101`. The first of four test plans (this one, `sync-scenario-tests.md`,
`pedal-coverage-test.md`, `golden-renders.md`); the pedal walk and the goldens build on it.

## Goal

Drive the real app — real view model, use cases, repositories and files — through its UI the way a user does, and
assert what a user would see or what ends up in the library folder. Replace the hand checks that only exist because the
UI has no tests.

Not the goal: testing Composables in isolation, mocking the view model, or asserting internal state. A test that would
still pass after a correct rewrite of the screen is the right test.

## Approach

**Where:** the test source set of `tools/screenshots` (`src/test/kotlin`). That module already boots the whole app
offscreen: an isolated `user.home` with a fixture library, `startCampfireDependencyGraph()` plus the `fakes` Koin
module, `PlatformImpersonation` to pass for any platform, `Device` sizes with fake insets, and friend access to
`:presentation`'s internals. A new module would duplicate all of it. If the tests outgrow the tool, the module can be
renamed later (e.g. `tools/offscreen`); not now.

**How:**
1. Pull the app setup out of `render()` in `Render.kt` into one function both use, e.g.
   `withCampfireApp(device, library, preferences) { content -> … }`: temp home, library copy, platform, Koin start and
   stop, the composition locals. `render()` keeps only the frame capturing. Behaviour-preserving; the screenshots are
   the check.
2. Add `compose.uiTest` (Compose Multiplatform's `ui-test`, `runComposeUiTest`) and `kotlin("test")` to the test
   dependencies.
3. One small entry point, `runAppTest(device = Device.phone, library = …) { … }`, which runs `runComposeUiTest`, calls
   `setContent` with the shared setup and waits for `onAppReady`. Tests use the standard finders (`onNodeWithText`,
   `performClick`, `performKeyInput`) with strings read from the resources, never hard-coded English.
4. Assertions read the screen (semantics) or the library folder on disk. Never the view model's fields.
5. Fixtures: the screenshot tool's `library/` is the user's own songs and gitignored, so tests never read it. Each
   test builds its library from the demo songs (`presentation/.../composeResources/files/demo`) and a few small `.cho`
   files committed under `src/test/resources`, written into the temp home before the app starts.
6. Fakes: reuse `Fakes.kt` as is. Add only what a test in this plan needs, when it needs it — no fake written ahead
   of a test.
7. CI: add `:tools:screenshots:test` to the test step of `.github/workflows/tests.yml` (Linux). Make sure it doesn't
   download the fonts — tests use the default font.

## First tests (the ones that justify the harness)

Each a backtick sentence, each standing in for a manual check:

- **Import review**: importing a zip with one new, one identical, and one colliding song lists them in the
  right groups; Import writes the new one and numbers the collision; Replace, after its confirmation, overwrites the
  file on disk and nothing else.
- **Forms**: a cancelled New song writes nothing; a double-tapped Save writes one file.
- **Short window**: at 480 dp height and below the Tools item replaces Metronome, Tuner, and Chords; above it
  they are separate.
- **Accessibility**: a song card is one merged node with its title, and selection mode exposes the selected
  state.

Then stop and look at what they cost and caught before adding more. Further areas (metronome hand-over, tuner sheet,
chords picker, editor) each get tests only when a change there would have been caught by one.

## Risks

- **Animations and coroutines**: the app's transitions run on the test clock. Use `mainClock.autoAdvance` and
  `waitUntil` sparingly; a test that needs a `Thread.sleep` is a test of the wrong thing.
- **Swing main dispatcher**: the screenshot tool runs on Swing's main dispatcher; check that `runComposeUiTest` agrees
  with it before writing tests (spike first, half a day).
- **Flakiness**: a flaky test is deleted or fixed the same day, never retried in CI.

## Effort

About 2 days for the harness and setup extraction, 1 day for the first tests.

## Docs

One short section in `tools/screenshots/CLAUDE.md` (how to write a test, what may be faked), and one line in the root
`CLAUDE.md`'s Tests section with the command. Nothing else.
