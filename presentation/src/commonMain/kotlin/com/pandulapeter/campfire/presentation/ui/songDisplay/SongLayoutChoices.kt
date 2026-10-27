/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songDisplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_layout_continuous
import com.pandulapeter.campfire.presentation.resources.song_layout_continuous_description
import com.pandulapeter.campfire.presentation.resources.song_layout_pages
import com.pandulapeter.campfire.presentation.resources.song_layout_pages_description
import com.pandulapeter.campfire.presentation.ui.components.ChoiceCard

/**
 * The two ways a song can be laid out, as a card each with what it means: the question the onboarding and the first song
 * opened ask, in the same words. [selected] is null where nothing is to be shown as picked yet.
 */
@Composable
internal fun SongLayoutChoices(
    modifier: Modifier = Modifier,
    selected: SongLayout?,
    onSelected: (SongLayout) -> Unit,
) = Column(
    modifier = modifier.fillMaxWidth().selectableGroup(),
    verticalArrangement = Arrangement.spacedBy(CHOICE_GAP),
) {
    SongLayout.entries.forEach { layout ->
        ChoiceCard(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(if (layout == SongLayout.PAGES) Res.string.song_layout_pages else Res.string.song_layout_continuous),
            description = stringResource(if (layout == SongLayout.PAGES) Res.string.song_layout_pages_description else Res.string.song_layout_continuous_description),
            isSelected = layout == selected,
            role = Role.RadioButton,
            onClick = { onSelected(layout) },
        )
    }
}

private val CHOICE_GAP = 12.dp
