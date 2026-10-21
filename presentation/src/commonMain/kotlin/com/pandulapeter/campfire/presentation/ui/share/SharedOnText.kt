/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.share_shared_on
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** "Shared on 16 October", the day written the way the app's language writes a date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun sharedOnText(day: LocalDate): String {
    // Counted in UTC midnights, as the formatter does, so that the day is the same one on either side of UTC.
    val languageCode = currentLanguage.value.code
    val locale = remember(languageCode) { calendarLocale(languageCode) }
    val formatter = remember { DatePickerDefaults.dateFormatter() }
    val date = formatter.formatDate(day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(), locale) ?: day.toString()
    return textResource(Res.string.share_shared_on, date)
}
