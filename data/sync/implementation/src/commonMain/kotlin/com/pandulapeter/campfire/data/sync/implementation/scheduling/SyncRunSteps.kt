/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.scheduling

/** The app's [SyncRunStep]s, in the order a run takes them. A wrapper rather than a list, which Koin cannot inject. */
internal class SyncRunSteps(
    val all: List<SyncRunStep>,
) {

    /** Every document a step owns, none of which starts a run when another device changes it. */
    val documentNames: Set<String> = all.flatMapTo(mutableSetOf()) { it.documentNames }
}
