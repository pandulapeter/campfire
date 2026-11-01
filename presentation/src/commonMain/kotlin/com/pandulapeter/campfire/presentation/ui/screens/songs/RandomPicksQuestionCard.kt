/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.random_picks_question_keep
import com.pandulapeter.campfire.presentation.resources.random_picks_question_text
import com.pandulapeter.campfire.presentation.resources.random_picks_question_title
import com.pandulapeter.campfire.presentation.resources.random_picks_question_turn_off
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_OUTER_PADDING
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_VERTICAL_PADDING

/**
 * Under the first random picks shown, whether to keep them ([SongGroup.isRandomPicksQuestionAfter]): an existing
 * library, or one just imported, is met with a section at its top that nobody asked for, so it is asked once rather
 * than left to be found in Settings. Either answer takes the card away for good; turning them off takes the picks with
 * it. Drawn as [AddSongsHintCard] is, in the song cards' color.
 */
@Composable
internal fun RandomPicksQuestionCard(
    modifier: Modifier = Modifier,
    onKeep: () -> Unit,
    onTurnOff: () -> Unit,
) = Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = SONG_CARD_OUTER_PADDING, vertical = SONG_CARD_VERTICAL_PADDING + 8.dp),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(Res.string.random_picks_question_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            text = stringResource(Res.string.random_picks_question_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onTurnOff) { Text(stringResource(Res.string.random_picks_question_turn_off)) }
            Button(onClick = onKeep) { Text(stringResource(Res.string.random_picks_question_keep)) }
        }
    }
}
