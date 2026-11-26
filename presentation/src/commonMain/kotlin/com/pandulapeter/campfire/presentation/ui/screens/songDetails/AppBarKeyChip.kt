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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DockContent

/**
 * The key chip ([KeyChip]) at the head of the app bar's actions, wherever the chords are on ([isShown]), an import
 * preview's too: for the page the pager is on or heading for ([targetPage], [contents]), crossfading as a page turns as
 * the subtitle does, since a setlist's songs and a song's key changes differ, and resizing as it goes. It opens the
 * Playing sheet (nothing in read only mode) and is left out with neither key nor capo. It comes and goes with the
 * chords switch, fading and growing, so nothing in the bar jumps. [APP_BAR_KEY_CHIP_GAP] parts it from the title; after
 * it the star, the read only mark, or the overflow button keep their glyph's room on either side, so it needs no gap there.
 */
@Composable
internal fun AppBarKeyChip(
    isShown: Boolean,
    contents: Map<Int, DockContent>,
    targetPage: () -> Int,
) = AnimatedVisibility(
    visible = isShown,
    enter = fadeIn() + expandHorizontally(),
    exit = fadeOut() + shrinkHorizontally(),
) {
    AnimatedContent(
        targetState = targetPage(),
        contentAlignment = Alignment.CenterEnd,
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
    ) { page ->
        val content = contents[page]
        val text = remember(content?.playingControls, content?.model) { content?.let { keyChipTextOf(it.playingControls, it.model) } }
        if (content != null && text != null) {
            KeyChip(
                modifier = Modifier.padding(start = APP_BAR_KEY_CHIP_GAP),
                text = text,
                onClick = content.playingControls?.onPlayingClicked?.takeIf { text.opensPlaying },
            )
        }
    }
}

/** What parts the key chip from the title before it, whose touch target reaches up to it. */
internal val APP_BAR_KEY_CHIP_GAP = 4.dp
