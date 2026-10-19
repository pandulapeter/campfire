<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :app:di

The Koin application, in `CampfireDependencyGraph.kt`: a `campfire-library` module called by the four entry points and
`:tools:screenshots`, and the one shared module allowed to depend on `implementation` modules, so their list is written
once.

- `CampfireDependencyGraph` (`@KoinApplication`) names every module object; adding a Koin module means adding it there.
  The compiler plugin validates the whole graph here (so this module recompiles every build).
- `startCampfireDependencyGraph(configuration)` starts Koin globally (not the `KoinApplication` composable: the graph
  belongs to the process, and `koinViewModel()` finds the global instance). Android passes `androidContext` (the one
  `@Provided` dependency); the others pass nothing.
- It also starts the first `LoadScreenDataUseCase` read on `Dispatchers.Default`, overlapping the platform's window
  setup; the view model's read joins it (repositories hold their lock for the whole read) and re-reads only what
  failed.
