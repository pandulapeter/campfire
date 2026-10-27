/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_layout_message
import com.pandulapeter.campfire.presentation.resources.song_layout_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.songDisplay.SongLayoutChoices

/**
 * The question the first song opened asks once, over that song, of a reader the onboarding never asked: pages, or one
 * column that scrolls by itself. A pick is written at once and closes the sheet, the song behind it changing as it goes;
 * closing it without one keeps the pages, which is what is shown as picked.
 */
@Composable
internal fun SongLayoutSheet(
    viewModel: CampfireViewModel,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    CampfireBottomSheet(
        title = stringResource(Res.string.song_layout_title),
        onDismiss = { viewModel.dialogHost.dismissSheet(DialogType.SongLayout) },
    ) { contentPadding ->
        val sheet = this
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                modifier = Modifier.padding(bottom = 16.dp),
                text = stringResource(Res.string.song_layout_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SongLayoutChoices(
                selected = userPreferences?.songLayout,
                onSelected = { layout ->
                    viewModel.preferencesController.setSongLayout(layout)
                    sheet.close()
                },
            )
        }
    }
}
