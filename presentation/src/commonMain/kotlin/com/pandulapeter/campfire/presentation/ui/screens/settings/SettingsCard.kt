/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A [SettingsSection] on a card: the one of a page's two that is set apart from the other, in place of a title over
 * each. The card starts and ends at the keylines the other section's rows do, so its own rows are inset by them once
 * more.
 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = ElevatedCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    // The card's own edge is where the rows start, so its first and last rows get the room a section title would give.
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        content()
    }
}
