/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.forms

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.setlists_pick_date
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.BottomSheetConfirmButton
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.platform.calendarLocale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The calendar the date field opens, in a sheet of its own like every other modal with a value to choose, whose Save
 * hands the picked day to [onDateChange]. The setlists screen opens it on its own from a setlist's countdown
 * ([DialogType.SetlistDate]), since that is where the day is read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetlistDatePickerSheet(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker counts in milliseconds of UTC midnights, whatever the device's time zone, so the day goes in and
    // comes out through UTC rather than through the local zone, which would move it by a day on one side of it.
    var selectedMillis by rememberSaveable { mutableStateOf(date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()) }
    // The day the calendar was opened on: the form's date changes as Save is tapped, and the button sliding away with
    // the sheet must not turn grey in its last frames.
    val openedMillis = rememberSaveable { date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() }
    // The calendar is given the app's language rather than the system's, which is what rememberDatePickerState
    // would take. The state built here is not saveable, so the day picked but not yet confirmed is carried
    // through a rotation by selectedMillis instead.
    val languageCode = currentLanguage.value.code
    val locale = remember(languageCode) { calendarLocale(languageCode) }
    val state = remember(locale) { DatePickerState(locale = locale, initialSelectedDateMillis = selectedMillis) }
    LaunchedEffect(state) { snapshotFlow { state.selectedDateMillis }.collect { it?.let { millis -> selectedMillis = millis } } }
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    CampfireBottomSheet(
        title = stringResource(Res.string.setlists_pick_date),
        onDismiss = onDismiss,
        actions = { close ->
            BottomSheetConfirmButton(
                enabled = state.selectedDateMillis != null && state.selectedDateMillis != openedMillis,
                onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                    close()
                },
            ) { Text(stringResource(Res.string.save)) }
        },
    ) { contentPadding ->
        val calendarScrollState = rememberScrollState()
        DatePicker(
            modifier = Modifier
                .weight(1f, fill = false)
                .fadingTopEdge(calendarScrollState, sheetContainerColor())
                .bounceVerticalScroll(calendarScrollState)
                .padding(contentPadding.only(bottom = true)),
            state = state,
            dateFormatter = dateFormatter,
            colors = DatePickerDefaults.colors(containerColor = sheetContainerColor()),
            title = null,
            // Typed entry is left out: its field's label, pattern and errors are Material's own strings, read in the
            // system's language rather than the app's, and there is no parameter for any of them.
            showModeToggle = false,
            // Material's own headline formats the day in the system's locale whatever the state's is, so it is
            // drawn here in the calendar's, with the paddings and the color Material gives it.
            headline = {
                val pickDate = stringResource(Res.string.setlists_pick_date)
                val description = dateFormatter.formatDate(state.selectedDateMillis, locale, forContentDescription = true) ?: pickDate
                Text(
                    modifier = Modifier
                        .padding(PaddingValues(start = 24.dp, end = 12.dp, bottom = 12.dp))
                        .semantics { contentDescription = description },
                    text = dateFormatter.formatDate(state.selectedDateMillis, locale, forContentDescription = false) ?: pickDate,
                    color = DatePickerDefaults.colors().headlineContentColor,
                    maxLines = 1,
                )
            },
        )
    }
}
