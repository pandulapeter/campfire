/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.optional_field_label
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import com.pandulapeter.campfire.presentation.ui.platform.numericPlatformImeOptions

@Composable
internal fun SongMetadataField(
    modifier: Modifier = Modifier,
    field: Field,
    value: String,
    onValueChange: (String) -> Unit,
    isOptional: Boolean = false,
    maxLength: Int = Int.MAX_VALUE,
    onDone: (() -> Unit)? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        modifier = modifier,
        value = value,
        // A brace would end the directive early or open another one, and a line break would leave the rest of the value
        // in the song as lyrics.
        onValueChange = { newValue ->
            onValueChange(
                when (field) {
                    Field.YEAR -> newValue.filter { it in '0'..'9' }.take(YEAR_LENGTH)
                    Field.DURATION -> durationDigitsTyped(newValue)
                    else -> newValue.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }.take(maxLength)
                },
            )
        },
        label = {
            val label = stringResource(field.label)
            Text(
                text = if (isOptional) stringResource(Res.string.optional_field_label, label) else label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = rememberClearTextButton(isVisible = value.isNotEmpty(), onClear = { onValueChange("") }),
        singleLine = true,
        visualTransformation = if (field == Field.DURATION) DurationDigitsTransformation else VisualTransformation.None,
        // Next walks the form; the final Done confirms creation or puts the editing form's keyboard away.
        keyboardActions = KeyboardActions(onDone = { if (onDone != null) onDone() else keyboardController?.hide() }),
        keyboardOptions = KeyboardOptions(
            capitalization = if (field.isNumeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (field.isNumeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (field == SONG_METADATA_FIELDS.last()) ImeAction.Done else ImeAction.Next,
            platformImeOptions = numericPlatformImeOptions.takeIf { field.isNumeric },
        ),
    )
}

/** The fields typed as digits alone: a year, and a duration through [DurationDigitsTransformation]. */
private val Field.isNumeric get() = this == Field.YEAR || this == Field.DURATION

private const val YEAR_LENGTH = 4
