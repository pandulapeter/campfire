/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A single line field for a web address, which selects all of it whenever it gains the focus, however it gains it: an
 * address is pasted over far more often than it is edited by hand, so the first key typed or the first paste replaces
 * it. A drag or a double tap after that still picks out a part of it.
 *
 * A tap focuses the field and then places the caret where it landed, in the same pass, so the caret it places is let
 * through only after the frame the focus arrived in; a field focused from the keyboard has no such caret coming, and an
 * arrow key pressed in it later moves the caret as usual.
 */
@Composable
internal fun AddressTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    trailingIcon: (@Composable () -> Unit)?,
    imeAction: ImeAction,
    keyboardActions: KeyboardActions,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
    // The address is the caller's, the selection the field's own: an address changed from outside, cleared or
    // looked up, puts the caret at its end.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shownValue = if (fieldValue.text == value) fieldValue else TextFieldValue(value, TextRange(value.length))
    var isFocusArriving by remember { mutableStateOf(false) }
    LaunchedEffect(isFocusArriving) {
        if (isFocusArriving) {
            withFrameNanos { }
            isFocusArriving = false
        }
    }
    OutlinedTextField(
        modifier = modifier.onFocusChanged {
            if (it.isFocused) {
                fieldValue = TextFieldValue(value, TextRange(0, value.length))
                isFocusArriving = true
            }
        },
        value = shownValue,
        onValueChange = { newValue ->
            if (!isFocusArriving || newValue.text != value) {
                fieldValue = newValue
                if (newValue.text != value) onValueChange(newValue.text)
            }
        },
        label = label,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText,
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = imeAction),
        keyboardActions = keyboardActions,
    )
}
