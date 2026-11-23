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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The editor's [panes]: [editor] and [preview] side by side for Split, else the one shown, crossfading between the two. */
@Composable
internal fun EditorPaneLayout(
    modifier: Modifier = Modifier,
    panes: EditorPanes,
    editor: @Composable (Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
) = if (panes == EditorPanes.SPLIT) {
    Row(modifier = modifier) {
        editor(Modifier.weight(1f).fillMaxHeight())
        VerticalDivider()
        preview(Modifier.weight(1f).fillMaxHeight())
    }
} else {
    AnimatedContent(
        modifier = modifier,
        targetState = panes == EditorPanes.PREVIEW,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { showPreview ->
        if (showPreview) preview(Modifier.fillMaxSize()) else editor(Modifier.fillMaxSize())
    }
}
