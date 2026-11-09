/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.page_turn_column
import com.pandulapeter.campfire.presentation.resources.page_turn_page
import com.pandulapeter.campfire.presentation.resources.settings_page_turn
import com.pandulapeter.campfire.presentation.resources.settings_page_turn_description
import com.pandulapeter.campfire.presentation.resources.song_layout_continuous
import com.pandulapeter.campfire.presentation.resources.song_layout_continuous_description
import com.pandulapeter.campfire.presentation.resources.song_layout_pages
import com.pandulapeter.campfire.presentation.resources.song_layout_pages_description
import com.pandulapeter.campfire.presentation.ui.components.ChoiceCard
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice

/**
 * The two ways a song can be laid out, as a card each with what it means: the question the first song opened asks.
 * [selected] is null where nothing is to be shown as picked yet. While pages are picked, how they are turned follows
 * under the cards, as it does under the layout on the Preferences' Song page, so that the one question settles both.
 */
@Composable
internal fun SongLayoutChoices(
    modifier: Modifier = Modifier,
    selected: SongLayout?,
    pageTurn: PageTurn?,
    onSelected: (SongLayout) -> Unit,
    onPageTurnSelected: (PageTurn) -> Unit,
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
    PageTurnFollowUp(
        isVisible = selected != SongLayout.CONTINUOUS,
        selected = pageTurn,
        onSelected = onPageTurnSelected,
    )
}

@Composable
private fun PageTurnFollowUp(
    isVisible: Boolean,
    selected: PageTurn?,
    onSelected: (PageTurn) -> Unit,
) = AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
) {
    // Further from the cards than they are from each other, so that it reads as a follow-up rather than a third answer.
    Column(modifier = Modifier.padding(top = CHOICE_GAP)) {
        Text(
            text = stringResource(Res.string.settings_page_turn),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.settings_page_turn_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedChoice(
            modifier = Modifier.padding(top = CHOICE_GAP),
            options = listOf(
                PageTurn.PAGE to stringResource(Res.string.page_turn_page),
                PageTurn.COLUMN to stringResource(Res.string.page_turn_column),
            ),
            selected = selected,
            shouldApplyPadding = false,
            onSelected = onSelected,
        )
    }
}

private val CHOICE_GAP = 12.dp
