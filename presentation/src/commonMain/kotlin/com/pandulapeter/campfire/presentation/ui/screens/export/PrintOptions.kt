/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_missing
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.print.PrintSource

/**
 * The options, in one list that scrolls on its own: the format and what it is good for, then the preview where it is
 * stacked with them ([header]), then for a PDF its options ([PdfOptions]). Every song goes out, a setlist whole. The library's own files are the songs as the library
 * holds them, so the PDF's options fold away under the format while it is chosen; a link offers [linkOptions] instead.
 * [onSettings] takes every change, normalized and saved by the screen.
 */
@Composable
internal fun PrintOptions(
    modifier: Modifier,
    source: PrintSource,
    settings: PrintSettings,
    formats: List<PrintSettings.Format>,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    areScaleSuggestionsEnabled: Boolean,
    bottomPadding: Dp,
    header: (@Composable () -> Unit)? = null,
    onSettings: (PrintSettings) -> Unit,
    linkOptions: @Composable () -> Unit,
) {
    val state = rememberLazyListState()
    // Every row here is moved only by the options above it folding out or away, which it follows as they fold, so its
    // animation is the fade alone.
    val isPdf = settings.format == PrintSettings.Format.PDF
    val isLink = settings.format == PrintSettings.Format.LINK
    LazyColumn(modifier.bounceScrollableContent(state).fadingTopEdge(state, MaterialTheme.colorScheme.background), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        // The format comes before the preview it decides, so that what the preview shows follows from what is chosen
        // above it, and the choice is the first thing on the screen.
        item(key = "format") {
            FormatChoice(
                modifier = listItemAnimation(state, placementSpec = null),
                format = settings.format,
                formats = formats,
                kind = source.kind,
                onSelected = { onSettings(settings.copy(format = it)) },
            )
        }
        if (header != null) {
            item(key = "preview") { header() }
        }
        if (isLink) {
            item(key = "link") { Column(listItemAnimation(state, placementSpec = null)) { linkOptions() } }
        }
        item(key = "pdf") {
            FoldingOption(modifier = listItemAnimation(state, placementSpec = null), isVisible = isPdf) {
                PdfOptions(
                    source = source,
                    settings = settings,
                    areChordsEnabled = areChordsEnabled,
                    isMetronomeEnabled = isMetronomeEnabled,
                    areScaleSuggestionsEnabled = areScaleSuggestionsEnabled,
                    onSettings = onSettings,
                )
            }
        }
        if (!source.hasSeveralSongs && source.songs.any { it.song == null }) {
            item(key = "missing") {
                Text(
                    text = stringResource(Res.string.print_missing),
                    modifier = listItemAnimation(state, placementSpec = null).padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Options that only some other choice makes sense with, folding out under it while it is made and away again. */
@Composable
internal fun FoldingOption(
    modifier: Modifier,
    isVisible: Boolean,
    content: @Composable () -> Unit,
) = AnimatedVisibility(isVisible, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
    Column { content() }
}
