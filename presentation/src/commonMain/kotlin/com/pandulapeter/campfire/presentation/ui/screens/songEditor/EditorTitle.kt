/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.pandulapeter.campfire.presentation.ui.components.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.rememberSettledCoverArtUrl
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.APP_BAR_COVER_GAP
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.APP_BAR_COVER_SIZE

/**
 * The song as the text being typed names it, with its cover in front of the title the way the song details screen
 * draws it, so that an address written or changed by hand is seen before it is saved.
 *
 * The cover follows the text only once the typing has paused ([rememberSettledCoverArtUrl]). It takes no press: the
 * cover search is in the editor's menu and on the preview's card, with the rest of what the song says about itself.
 */
@Composable
internal fun EditorTitle(
    title: String,
    artist: String,
    coverArtUrl: String?,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
) {
    val shownCoverArtUrl = rememberSettledCoverArtUrl(coverArtUrl)
    // Keyed on whether there is a cover rather than on its address, so that one address changing to another is the
    // image crossfading in its place rather than the room for it closing and opening again.
    AnimatedContent(
        targetState = shownCoverArtUrl,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it != null },
    ) { url ->
        if (url != null) {
            CoverArtImage(
                modifier = Modifier.padding(end = APP_BAR_COVER_GAP).size(APP_BAR_COVER_SIZE),
                url = url,
            )
        }
    }
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
