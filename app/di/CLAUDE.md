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

The Koin application, and nothing else: one file, `CampfireDependencyGraph.kt`. A `campfire-library` module like the
rest of the shared code, because all four platform entry points call into it, and the one shared module that is
allowed to depend on the `implementation` modules — it exists so that the list of them is written once rather than in
each `:app:*` module.

- `CampfireDependencyGraph` is the `@KoinApplication`, naming the five module objects (`DataLocalSourceModule`,
  `DataRemoteSourceModule`, `DataRepositoryModule`, `DomainModule`, `PresentationModule`). Adding a Koin module to
  the app is adding it to that list. The compiler plugin validates the whole graph at this declaration, so a
  definition that asks for something no module declares fails this module's build; the plugin also recompiles this
  module on every build for that reason, which is what the `strictSafety` line in the Gradle output is about.
- `startCampfireDependencyGraph(configuration)` is `startKoin<CampfireDependencyGraph>` with the platform's own
  additions: Android passes `androidContext`, which is what its file storage and authenticator take as `@Provided`;
  the other three pass nothing. It starts Koin globally rather than through the `KoinApplication` composable, since
  the graph belongs to the process and `koinViewModel()` reaches the global instance on its own.
