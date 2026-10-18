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

import com.pandulapeter.campfire.presentation.ui.dialogs.rememberSingleConfirmation
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.BottomSheetConfirmButton
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.TextFieldBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.rememberFirstFieldFocusRequester
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_calendar
import com.pandulapeter.campfire.presentation.resources.setlists_countdown
import com.pandulapeter.campfire.presentation.resources.setlists_date
import com.pandulapeter.campfire.presentation.resources.setlists_date_value
import com.pandulapeter.campfire.presentation.resources.setlists_description
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist_title
import com.pandulapeter.campfire.presentation.resources.setlists_pick_date
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Everything the user gets to say about a setlist: its title, the description that goes under its header on the
 * setlists screen, and the day it is for, with whether its header counts down to that day. Creating one, editing one and
 * naming a copy of one are the same dialog with different labels, since all three are answering the same questions.
 *
 * Only the title is required. The description is what somebody writes for their own sake ("acoustic, two sets, no
 * encore"), and most setlists never get one - but the setlists screen's search reads it, so a setlist that is hard
 * to name can still be found by what it is for. The date starts as today unless the setlist already has one - a
 * copy is made for another evening, so it starts as today too - and a setlist written before there were dates gets
 * today's the first time it is edited, since there is no creation date left to fall back on.
 *
 * @param subtitle The setlist being edited or copied, named under the title. A new setlist has none to name.
 * @param isTitleFocused Whether the dialog opens with the caret in the title. A new setlist and a copy are opened to be
 *   named, while an edit is as often opened to look the setlist's details over or to change its date, which a keyboard
 *   coming up over the dialog would only be in the way of.
 * @param requiresChanges Whether the confirm button waits for something to be changed. Only an edit has nothing to
 *   save as it opens; a copy and a setlist named after a search are opened on a title that is meant to be accepted as
 *   it is.
 */
@Composable
internal fun SetlistDetailsDialog(
    title: String,
    subtitle: String = "",
    isTitleFocused: Boolean = true,
    requiresChanges: Boolean = false,
    initialTitle: String = "",
    initialDescription: String = "",
    initialDate: LocalDate? = null,
    initialIsCountdownShown: Boolean = false,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, date: LocalDate, isCountdownShown: Boolean) -> Unit,
) {
    // A TextFieldValue rather than a String, for the selection: a dialog that opens on a title the user is meant to
    // replace ("Summer set (copy)", a search that found nothing) has all of it selected, so the first key typed writes the
    // new name instead of appending to the old one. Nothing is lost by it either, since a tap or an arrow key puts the
    // caret where it was aimed. The description is opened on for editing rather than for replacing, so its caret goes
    // to the end of what is already written instead.
    var setlistTitle by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text = initialTitle, selection = TextRange(initialTitle.length)))
    }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    // Saved as its ISO text, since a LocalDate is nothing the saved instance state of every platform can hold.
    val startingDateText = rememberSaveable { (initialDate ?: today()).toString() }
    var dateText by rememberSaveable { mutableStateOf(startingDateText) }
    val date = LocalDate.parse(dateText)
    var isCountdownShown by rememberSaveable { mutableStateOf(initialIsCountdownShown) }
    val isValid = setlistTitle.text.isNotBlank()
    val hasChanges = !requiresChanges || setlistTitle.text.trim() != initialTitle.trim() ||
        description.trim() != initialDescription.trim() || dateText != startingDateText ||
        isCountdownShown != initialIsCountdownShown
    var hasTitleBeenFocused by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val focusRequester = rememberFirstFieldFocusRequester(isFocused = isTitleFocused)
    val confirmOnce = rememberSingleConfirmation()
    TextFieldBottomSheet(
        onDismissRequest = onDismiss,
        title = title,
        subtitle = subtitle,
        text = { contentPadding ->
            Column(modifier = Modifier.fadingTopEdge(scrollState, sheetContainerColor()).bounceVerticalScroll(scrollState).padding(contentPadding)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
                        if (it.isFocused && !hasTitleBeenFocused) {
                            hasTitleBeenFocused = true
                            setlistTitle = setlistTitle.copy(selection = TextRange(0, setlistTitle.text.length))
                        }
                    },
                    value = setlistTitle,
                    onValueChange = { newValue ->
                        val text = newValue.text.replace("\n", "").take(MAX_TITLE_LENGTH)
                        // Rebuilt only where the text had to be cut, or every keystroke would throw away the
                        // selection the field is reporting - which is the caret itself, and the run of text a drag
                        // is picking out.
                        setlistTitle = if (text == newValue.text) {
                            newValue
                        } else {
                            TextFieldValue(text = text, selection = TextRange(newValue.selection.end.coerceAtMost(text.length)))
                        }
                    },
                    label = { Text(stringResource(Res.string.setlists_new_setlist_title)) },
                    trailingIcon = rememberClearTextButton(isVisible = setlistTitle.text.isNotEmpty(), onClear = { setlistTitle = TextFieldValue() }),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Several lines rather than one, and no Done action on the keyboard: this is a sentence somebody
                // writes about a setlist, and the return key belongs to it rather than to the dialog.
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it.take(MAX_DESCRIPTION_LENGTH) },
                    label = { Text(stringResource(Res.string.setlists_description)) },
                    trailingIcon = rememberClearTextButton(isVisible = description.isNotEmpty(), onClear = { description = "" }),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = DESCRIPTION_LINES,
                    maxLines = DESCRIPTION_LINES,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SetlistDateRow(
                    date = date,
                    onDateChange = { dateText = it.toString() },
                    isCountdownShown = isCountdownShown,
                    onCountdownShownChange = { isCountdownShown = it },
                )
            }
        },
        confirmButton = { close ->
            BottomSheetConfirmButton(
                enabled = isValid && hasChanges,
                onClick = {
                    confirmOnce {
                        onConfirm(setlistTitle.text, description, date, isCountdownShown)
                        close()
                    }
                },
            ) { Text(confirmLabel) }
        },
    )
}

/**
 * The date field with the switch for its countdown next to it, which moves under the field where the two do not fit
 * side by side - on a phone the dialog is narrower than a date and a label, and a date cut short is no date.
 */
@Composable
private fun SetlistDateRow(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    isCountdownShown: Boolean,
    onCountdownShownChange: (Boolean) -> Unit,
) {
    // Above the two layouts rather than in the field, so that the calendar stays open, on the day picked in it, when
    // the sheet crosses the width between them - a rotation, a window resized.
    var isPickerVisible by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints {
        if (maxWidth >= MIN_DATE_ROW_WIDTH) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetlistDateField(
                    modifier = Modifier.weight(1f),
                    date = date,
                    onPickerRequested = { isPickerVisible = true },
                )
                Spacer(modifier = Modifier.width(8.dp))
                // An outlined field keeps room above its border for the label to sit in, and it is the border the box
                // is meant to be centered against, not the field with that room.
                SetlistCountdownCheckbox(
                    modifier = Modifier.padding(top = OUTLINED_FIELD_LABEL_ROOM),
                    isChecked = isCountdownShown,
                    onCheckedChange = onCountdownShownChange,
                )
            }
        } else {
            Column {
                SetlistDateField(
                    modifier = Modifier.fillMaxWidth(),
                    date = date,
                    onPickerRequested = { isPickerVisible = true },
                )
                Spacer(modifier = Modifier.height(4.dp))
                SetlistCountdownCheckbox(
                    isChecked = isCountdownShown,
                    onCheckedChange = onCountdownShownChange,
                )
            }
        }
    }
    if (isPickerVisible) {
        SetlistDatePickerSheet(
            date = date,
            onDateChange = onDateChange,
            onDismiss = { isPickerVisible = false },
        )
    }
}

/** The label toggles the box too, so the whole row is the one control a screen reader lands on. */
@Composable
private fun SetlistCountdownCheckbox(
    modifier: Modifier = Modifier,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = Row(
    modifier = modifier
        .clip(MaterialTheme.shapes.small)
        .toggleable(value = isChecked, role = Role.Checkbox, onValueChange = onCheckedChange)
        .padding(end = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Checkbox(checked = isChecked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
    Text(text = stringResource(Res.string.setlists_countdown), style = MaterialTheme.typography.bodyLarge)
}

/**
 * The day a setlist is for, as a field that is never typed into: the calendar it opens is the one way to change it,
 * so there is no text that could fail to be a date. The whole field opens it on a touch, and its icon is the button
 * the keyboard reaches, since a read-only field does nothing with Enter.
 */
@Composable
private fun SetlistDateField(
    modifier: Modifier = Modifier,
    date: LocalDate,
    onPickerRequested: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnPickerRequested by rememberUpdatedState(onPickerRequested)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnPickerRequested() }
    }
    OutlinedTextField(
        modifier = modifier,
        value = stringResource(
            Res.string.setlists_date_value,
            date.year.toString(),
            date.month.number.toString().padStart(length = 2, padChar = '0'),
            date.day.toString().padStart(length = 2, padChar = '0'),
        ),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(Res.string.setlists_date)) },
        trailingIcon = {
            IconButton(onClick = onPickerRequested) {
                Icon(painter = painterResource(Res.drawable.ic_calendar), contentDescription = stringResource(Res.string.setlists_pick_date))
            }
        },
        interactionSource = interactionSource,
    )
}

private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

private const val MAX_DESCRIPTION_LENGTH = 300

private const val DESCRIPTION_LINES = 3

/** What a whole date and the countdown's label take side by side, in either language. */
private val MIN_DATE_ROW_WIDTH = 360.dp

/** What Material's `OutlinedTextField` leaves above its border for the label that sits on it. */
private val OUTLINED_FIELD_LABEL_ROOM = 8.dp
