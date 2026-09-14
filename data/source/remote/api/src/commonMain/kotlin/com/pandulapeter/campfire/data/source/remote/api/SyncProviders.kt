/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

/**
 * Every provider the build has credentials for, as one dependency. A provider the build cannot use is not in [all]
 * at all, so an empty list is how a build without sync credentials looks from above.
 *
 * This is a type of its own rather than a `List<SyncProvider>` because the Koin compiler plugin resolves a `List<T>`
 * parameter as `getAll<T>()`, every definition bound to `T`, and never as a definition whose type is the list: a
 * `List<SyncProvider>` parameter compiles, passes the plugin's graph check, and is always empty.
 */
class SyncProviders(
    val all: List<SyncProvider>,
)
