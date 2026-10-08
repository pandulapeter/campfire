/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_album
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.rememberSettledCoverArtUrl
import org.jetbrains.compose.resources.painterResource

/**
 * The card of what the song is in the editor's preview, as wide as its column whatever it holds, so that its edit buttons
 * stay where they are as the groups fill up and empty. Its links are not followed, since the preview is there to show
 * what is being typed. Its cover follows the text the way the editor's title does, once the typing has paused, and only
 * where the cover art setting gives the card its cover button, which is what tells the card that setting. That button is
 * only in the header while there is no cover to tap, since the cover itself opens the same search.
 */
@Composable
internal fun SongInfoCard(
    metadata: ChordProMetadata,
    editing: SongInfoEditing?,
    titleStyle: TextStyle,
    fontScale: Float,
) = Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val coverArtUrl = rememberSettledCoverArtUrl(metadata.coverArt.takeIf { editing?.onEditCoverArt != null })
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(FOLD_CHEVRON_SIZE * fontScale),
                painter = painterResource(Res.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                text = stringResource(Res.string.song_details_song_info),
                style = titleStyle,
                color = MaterialTheme.colorScheme.primary,
            )
            if (editing != null) {
                // Follows the settled cover the body draws rather than the text, so that the button and the cover trade places together.
                AnimatedVisibility(
                    modifier = Modifier.overlappingAction(start = 0.dp, end = ACTION_BUTTON_OVERLAP),
                    visible = editing.onEditCoverArt != null && coverArtUrl == null,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    IconButton(onClick = { editing.onEditCoverArt?.invoke() }) {
                        Icon(
                            modifier = Modifier.size(EDIT_ICON_SIZE * fontScale),
                            painter = painterResource(Res.drawable.ic_album),
                            contentDescription = stringResource(Res.string.song_details_set_cover_art),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = editing.onEditMetadata) {
                    Icon(
                        modifier = Modifier.size(EDIT_ICON_SIZE * fontScale),
                        painter = painterResource(Res.drawable.ic_edit),
                        contentDescription = stringResource(Res.string.song_details_metadata_edit),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        SongInfoBody(
            modifier = Modifier.padding(bottom = 12.dp),
            metadata = metadata,
            coverArtUrl = coverArtUrl,
            fontScale = fontScale,
            horizontalPadding = 12.dp,
            editing = editing,
            onOpenLink = null,
        )
    }
}
