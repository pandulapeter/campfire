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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A form in the app's usual sheet, with its actions in the header and its original fields kept in one composition.
 * The header's close button cancels the draft. Content keeps clear of the keyboard and system bars, while the sheet
 * handles short windows the same way as the assignment sheets.
 */
@Composable
internal fun TextFieldBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String = "",
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    startButton: (@Composable () -> Unit)? = null,
) = CampfireBottomSheet(
    title = title,
    subtitle = subtitle,
    actions = {
        startButton?.invoke()
        confirmButton()
    },
    onDismiss = onDismissRequest,
) { contentPadding ->
    Box(
        modifier = Modifier
            .weight(1f, fill = false)
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = contentPadding.calculateBottomPadding()),
    ) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
    }
}
