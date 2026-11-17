/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.progress

import com.pandulapeter.campfire.data.model.importing.ImportProgress

/** Whether the phase moves an archive's bytes, which is when an [ImportProgress] may count them. */
internal val ImportProgress.Phase.countsBytes
    get() = this == ImportProgress.Phase.RECEIVING || this == ImportProgress.Phase.DOWNLOADING || this == ImportProgress.Phase.UNPACKING

/**
 * The [ImportProgress.completedBytes] of a phase that [countsBytes], or null where it reports none: the import screen then
 * counts files as it always has.
 */
internal val ImportProgress.reportedBytes get() = completedBytes?.takeIf { phase.countsBytes }

/** How far into its bytes a phase that [countsBytes] is, or null where it reports no total to measure them against. */
internal val ImportProgress.byteFraction
    get() = reportedBytes?.let { completed ->
        totalBytes?.takeIf { it > 0 }?.let { total -> (completed.toDouble() / total).toFloat().coerceIn(0f, 1f) }
    }
