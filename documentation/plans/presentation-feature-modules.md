<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: `:presentation` split into feature modules

**Status:** not started. The goal is architecture and readability: `:presentation` (126k lines of shared code, 37k of
tests, one Gradle module) becomes a few shared modules named by what they hold and one module per feature, so that a
feature's code, tests, and `CLAUDE.md` sit together and the module graph, not a script, says what may use what. Build
speed is not the goal: since `kotlin.internal.incremental.enableUnsafeOptimizationsForMultiplatform` (5caed83ce) an edit
compiles in about 4 s, and a split mostly helps clean builds and the pre-commit hook's test run. `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.

## Starting point (measured 2026-11-27)

An import graph of every main source set, packages grouped as below:

- **The screens already stand apart.** Grouped into features, no feature imports another (`check_layers.py`'s rule
  that a screen imports no other screen kept it so). Their only upward references are to the `ui` package (each
  destination's entry file takes `CampfireViewModel`) and, in the editor, export, onboarding, and settings, to `chrome`
  (1–5 imports each).
- **The shared code is one tangle.** Packages named after features are used everywhere and belong to no feature:
  `playing` (overrides; 17 imports from export alone), `songInfo`, `pictures` (21 from `state`), `rendering`,
  `preferences`, `chords`, `instrument`. Grouped as core, music, and song view below, the three form a cycle over about
  30 imports: core → music 20 (`preferences` → `metronome`/`chords`/`instrument`, `state` → `instrument`/`scales`,
  `navigation` → `chords`/`scales`), core → song view 7 (`share` → `print`, `state` → `songView`), music → song view 5
  (`instrument` → `songView`).
- **`CampfireViewModel` is a container** (822 lines) for about 25 controllers. Screens read the shared ones
  (`navigator` 44 times, `dialogHost` 42, `preferencesController` 30) but also feature-only ones (`tunerController`,
  `metronomeController`, `exportController`, `importReportState`, `editorSession`).
- **2,799 `internal` declarations.** Every one used across a new module boundary becomes public.
- **One `Res`** (1,322 strings in 13 languages, used by 304 files), `publicResClass = false`, plus the hyperether
  localization plugin's tables.
- `:presentation` is used by `:app:di`, the four platform apps, and `:tools:screenshots`.

## Decisions

- **By feature, never by type.** No `:presentation:viewmodels` or `:presentation:dialogs`. A shared module exists only
  for code that several features use, and is named by what it is.
- **Packages keep their names.** A file moves between modules, never between packages, so imports, `CLAUDE.md` links by
  package, and `git log --follow` stay as they are; a package may span modules only while a step is in flight.
- **One resources module.** Strings, fonts, and drawables stay in one module with `publicResClass = true`, so
  translations, the `add-language` skill, and the string checks do not change. Per-feature strings are out of scope.
- **Features get their state injected, not reached through the view model.** A feature's controllers move into its
  module and are Koin definitions there; `CampfireViewModel` keeps only what several features share (navigation,
  dialogs, preferences, the library). A destination's entry file takes the shared holders it needs as parameters.
- **The shell stays `:presentation`.** `CampfireApp`, `CampfireScreens` (the Navigation 3 `entryProvider` registers
  each feature's entries), the trimmed `CampfireViewModel`, and `chrome` stay there, so the `:app:*` modules and
  `:tools:screenshots` keep depending on one module.
- **Features never depend on each other.** A feature opens another through a `CampfireDestination` on the shared back
  stack, as today. A convention plugin, `campfire-feature`, applies the Compose library setup and Koin, adds the shared
  modules, and fails the build when a feature depends on another feature or on the shell (as `campfire-library` does
  for `:libs:*`).
- **No `explicitApi()`** for these modules: they are app code, and a declaration is public because another module of
  the app uses it.
- **Each module gets the platform source sets it needs** (`androidMain`, `iosMain`, `desktopMain`, `wasmJsMain`,
  `jvmSharedMain`), its own `commonTest`, and its own `CLAUDE.md`, moved from the package `CLAUDE.md` files it takes.

## Target modules

Names are a proposal; step 1 settles the placements the cycles decide.

| Module | Packages (under `ui.` unless rooted) |
|---|---|
| `:presentation:resources` | `resources`, `localization` (the plugin's tables), the strings, fonts, drawables, and files |
| `:presentation:foundation` | root package (`ApplicationScope`, generated version), `mapper`, `theme`, `platform`, `search`, `fontScale`, `progress`, `launch` |
| `:presentation:designsystem` | `components`, `settingsPage`, `dialogs.sheet` |
| `:presentation:state` | `state`, `navigation`, `messages`, the `dialogs` package itself, `dialogs.forms`, `dialogs.pickers`, `dialogs.labels`, `playing`, `songInfo`, `pictures`, `rendering`, `preferences`, `sync`, `share`, `firstRun`, the `screens` package itself |
| `:presentation:music` | `chords`, `instrument`, `dialogs.instrument`, `scales`, `dialogs.scales`, `tuner`, `metronome`, `tabs` |
| `:presentation:songview` | `songView`, `songLayout`, `notation`, `print` |
| `:presentation:feature:songs` | `screens.songs` |
| `:presentation:feature:setlists` | `screens.setlists` |
| `:presentation:feature:songdetails` | `screens.songDetails`, `dialogs.playing`, `dialogs.songDisplay` |
| `:presentation:feature:editor` | `screens.songEditor` |
| `:presentation:feature:export` | `screens.export` |
| `:presentation:feature:importreport` | `screens.importReport`, `dialogs.pageLink` |
| `:presentation:feature:lookup` | `dialogs.lookup` |
| `:presentation:feature:settings` | `screens.settings`, `dialogs.about`, `dialogs.shortcuts`, `update` |
| `:presentation:feature:onboarding` | `screens.onboarding` |
| `:presentation:feature:metronome`, `:tuner`, `:chords`, `:scales` | `screens.metronome`, `screens.tuner`, `screens.chords`, `screens.scales` |
| `:presentation:feature:widget` | `widget` |
| `:presentation:feature:shortcuts` | `shortcuts` |
| `:presentation:feature:spreadtheword` | `spreadTheWord`, `dialogs.spreadTheWord` |
| `:presentation` (shell) | the `ui` package itself, `chrome` |

Shared modules depend downward only: resources ← foundation ← designsystem ← state ← music ← songview ← features ←
shell. About 41k lines end in feature modules, 58k in `state`, `designsystem`, and `foundation`; `state` is the one to
watch, and step 7 looks at it again.

## Steps

Each step lands on `main` on its own, with every platform compiling and every test passing; nothing waits for a later
step. The import graph script (step 1) is rerun after each to show what is left.

1. **Untangle the shared code (about 1 day).** Rerun the import graph (a script in the scratchpad, never committed) and
   break the core → music → song view cycle by moving declarations down or passing them in: what `preferences` and
   `state` take from `metronome`, `chords`, `instrument`, and `scales` (mostly value types and defaults), what `share`
   takes from `print`, what `instrument` takes from `songView`. Move the four screens' `chrome` imports into
   `designsystem`'s packages or pass them in. Extend `check_layers.py`'s table to the target modules' order so the
   untangled graph stays so until the modules enforce it.
2. **Trim `CampfireViewModel` (2–3 days).** Move each feature-only controller (`tunerController`,
   `metronomeController`, `exportController`, `importReportState`, `importController`, `editorSession`,
   `pastedSongController`, `shareLinkController`, `songMetadataEditing`, …) into a Koin definition its destination's
   entry file asks for, keeping their lifetimes (app-wide ones stay `@Single`; a run that belongs to the app, like
   sync, stays out of any screen's scope). The view model keeps the shared holders. The UI tests cover the behaviour;
   no new fakes.
3. **Shared modules (2 days).** Create `campfire-feature` and the six shared modules, move their packages (`git mv`,
   packages unchanged), make public what crosses a boundary, and move each `@Single`/`@Factory` into its module's
   `@Module` object, named in `:app:di`. Set `publicResClass = true`. Move `UseCaseStubs` and the other shared test
   helpers into a `:presentation:testing` module that tests depend on.
4. **First feature: settings (1 day).** Sets the pattern: the module, its `CLAUDE.md`, its tests, the entry registered
   in `CampfireScreens`, its Koin module in `:app:di`, `tools:screenshots` still passing. Write the pattern into
   `presentation/CLAUDE.md`.
5. **The other features (3–4 days, one commit series each).** Export, import report, lookup, onboarding, setlists,
   songs, editor, the four tool screens, widget, shortcuts, and spread the word; song details last, as the largest.
6. **Retire what the modules now enforce (half a day).** Drop `check_layers.py`'s presentation layers (keep any rule
   the module graph cannot say, such as which file may take `CampfireViewModel`, if it still exists), and update the
   root `CLAUDE.md`'s architecture tree, its `ui/` convention, and every product rule's path, the `code-style` skill,
   and `check_sizes.py` and the other scripts' path patterns.
7. **Look at `state` again.** With the features out, decide whether it reads as one module or splits further (library,
   navigation, dialogs), by the same rule: only where it makes the code easier to read.

About 10–12 working days in total, mostly in sequence: moved files conflict with any other work in `:presentation`,
so each step lands quickly, and other sessions rebase over it.

## Risks and open questions

- **Compose resources across modules**: `publicResClass` with `packageOfResClass` from a module of its own, the
  hyperether plugin's generated tables, and fonts loaded by other modules need checking on all four platforms (step 3,
  before anything else moves).
- **The pre-commit hook** chooses UI tests by package and runs `:presentation:desktopTest`; it must learn the new
  modules (`.githooks/lib.sh`), or a commit's tests silently shrink.
- **iOS framework**: the shell's dependencies reach the iOS framework through `:app:ios`; check that exports and binary
  size do not change.
- **Baseline Profile**: regenerated at release as always; not a blocker.
- **Incremental compilation**: the flag from 5caed83ce stays; check that cross-module incremental builds stay around
  the current 4 s for a feature edit.

## Out of scope

- Per-feature strings, a design-system library outside `:presentation`, and any change to `:domain` or `:data`.
- Behaviour changes: every step is a move or a refactor; the UI tests pass unchanged.
