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
 * Progress within one phase, rather than an estimate for a whole import whose size is unknown until archives and
 * collections have been opened. A null total means that the phase has no measurable file count.
 */
data class ImportProgress(
    val phase: Phase,
    val completed: Int = 0,
    val total: Int? = null,
    val fileName: String? = null,
) {
    enum class Phase {
        UNPACKING,
        READING,
        COMPARING,
        IMPORTING,
        FINISHING,
    }
}
