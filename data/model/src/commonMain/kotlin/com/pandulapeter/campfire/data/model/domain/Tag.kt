/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * One label the library uses, as the filter controls need it. Tags are not stored anywhere of their own: the set of
 * them is whatever the songs happen to carry, which is what makes them cost nothing until somebody uses them.
 */
data class Tag(
    /** The spelling the library shows, which is the one the first song that carries the tag wrote it in. */
    val name: String,
    val songCount: Int,
)
