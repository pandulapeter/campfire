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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_days_ago
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_in_days
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_today
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_tomorrow
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_yesterday
import com.pandulapeter.campfire.presentation.resources.setlists_total_duration
import com.pandulapeter.campfire.presentation.resources.setlists_total_duration_minimum
import com.pandulapeter.campfire.presentation.ui.components.RelativeDay
import com.pandulapeter.campfire.presentation.ui.components.relativeDay
import com.pandulapeter.campfire.presentation.localization.pluralTextResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.datetime.LocalDate

/** Whether [SetlistSummary] has anything to show: a countdown, a running time, or a description. */
internal val SetlistWithSongs.hasSummary
    get() = setlist.description.isNotBlank() || setlist.isCountdownShown || totalDuration != null

/**
 * How long a setlist's songs take to play, where any of them says, with a `+` where some do not
 * ([SetlistTotalDuration.isMinimum]); null where none of them says. Named a running time, so that it is not read as the
 * time the set starts at.
 */
@Composable
internal fun SetlistWithSongs.runningTimeText(): String? {
    val duration = remember(entries) { totalDuration } ?: return null
    val total = ChordProDuration.format(duration.total)
    return stringResource(
        Res.string.setlists_total_duration,
        if (duration.isMinimum) stringResource(Res.string.setlists_total_duration_minimum, total) else total,
    )
}

/**
 * How far the setlist's day is, where the user asked for it, for the summary at the top of the setlist: in performance
 * mode nothing else on the screen shows the day.
 */
@Composable
internal fun Setlist.countdownText(today: LocalDate): String? {
    val date = date.takeIf { isCountdownShown } ?: return null
    return when (val day = relativeDay(date = date, today = today)) {
        RelativeDay.Today -> stringResource(Res.string.setlists_countdown_today)
        RelativeDay.Tomorrow -> stringResource(Res.string.setlists_countdown_tomorrow)
        RelativeDay.Yesterday -> stringResource(Res.string.setlists_countdown_yesterday)
        is RelativeDay.InDays -> pluralTextResource(Res.plurals.setlists_countdown_in_days, day.days, day.days.toString())
        is RelativeDay.DaysAgo -> pluralTextResource(Res.plurals.setlists_countdown_days_ago, day.days, day.days.toString())
    }
}
