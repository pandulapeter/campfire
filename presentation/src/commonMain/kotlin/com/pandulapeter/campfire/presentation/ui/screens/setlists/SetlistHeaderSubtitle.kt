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
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_days_ago
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_in_days
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_today
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_tomorrow
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_yesterday
import com.pandulapeter.campfire.presentation.resources.setlists_header_subtitle
import com.pandulapeter.campfire.presentation.resources.setlists_total_duration
import com.pandulapeter.campfire.presentation.resources.setlists_total_duration_minimum
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.RelativeDay
import com.pandulapeter.campfire.presentation.ui.components.relativeDay
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.datetime.LocalDate

/**
 * The subtitle of a setlist's header: its countdown where the user asked for one, then its running time, how long its songs
 * take to play, where any of them says, with a `+` where some do not ([SetlistTotalDuration.isMinimum]).
 */
@Composable
internal fun CampfireViewModel.SetlistWithSongs.headerSubtitle(today: LocalDate): String? {
    val countdown = setlist.countdownText(today)
    val duration = remember(entries) { totalDuration }?.let { duration ->
        val total = ChordProDuration.format(duration.total)
        stringResource(
            Res.string.setlists_total_duration,
            if (duration.isMinimum) stringResource(Res.string.setlists_total_duration_minimum, total) else total,
        )
    }
    return when {
        countdown != null && duration != null -> stringResource(Res.string.setlists_header_subtitle, countdown, duration)
        else -> countdown ?: duration
    }
}

/**
 * How far the setlist's day is, for the subtitle of its header, where the user asked for it: the header is pinned while
 * its songs are read, so the day stays in sight in performance mode too, where nothing else on the screen shows it.
 */
@Composable
internal fun Setlist.countdownText(today: LocalDate): String? {
    val date = date.takeIf { isCountdownShown } ?: return null
    return when (val day = relativeDay(date = date, today = today)) {
        RelativeDay.Today -> stringResource(Res.string.setlists_countdown_today)
        RelativeDay.Tomorrow -> stringResource(Res.string.setlists_countdown_tomorrow)
        RelativeDay.Yesterday -> stringResource(Res.string.setlists_countdown_yesterday)
        is RelativeDay.InDays -> pluralStringResource(Res.plurals.setlists_countdown_in_days, day.days, day.days)
        is RelativeDay.DaysAgo -> pluralStringResource(Res.plurals.setlists_countdown_days_ago, day.days, day.days)
    }
}
