/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import com.pandulapeter.campfire.data.model.domain.Setlist
import kotlinx.datetime.LocalDate

/** What the setlist details sheet says about a setlist: the four things a user writes about it. */
internal data class SetlistDetails(
    val title: String,
    val description: String,
    val date: LocalDate,
    val isCountdownShown: Boolean,
)

internal val Setlist.details
    get() = SetlistDetails(title = title, description = description, date = date, isCountdownShown = isCountdownShown)

/**
 * [chosen] where the sheet changed a value from what it [offered], and [current] - the setlist as the library has it
 * now - everywhere else, so that a value another device changed while the sheet was open and the user left alone is
 * kept. Text is compared trimmed, as it is written trimmed.
 */
internal fun mergedSetlistDetails(offered: SetlistDetails, chosen: SetlistDetails, current: SetlistDetails) = SetlistDetails(
    title = if (chosen.title.trim() != offered.title.trim()) chosen.title else current.title,
    description = if (chosen.description.trim() != offered.description.trim()) chosen.description else current.description,
    date = if (chosen.date != offered.date) chosen.date else current.date,
    isCountdownShown = if (chosen.isCountdownShown != offered.isCountdownShown) chosen.isCountdownShown else current.isCountdownShown,
)
