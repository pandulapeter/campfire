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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_setlist_empty
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer
import kotlinx.coroutines.flow.SharedFlow

/**
 * The preview of an export: what the library's own files are written as, the PDF's pages, with the pill turning them
 * drawn inside the preview where it is stacked above the options, or the link ([linkPreview]).
 */
@Composable
internal fun ExportPreviewPane(
    modifier: Modifier,
    state: ExportState,
    format: PrintSettings.Format,
    isSideBySide: Boolean,
    bottomInset: Dp,
    laidOut: LaidOutDocument?,
    isCurrent: Boolean,
    renderer: PrintRenderer,
    pageFit: PageFit,
    pagerState: PagerState,
    magnifications: SharedFlow<Float>,
    onPageViewChanged: (PageView) -> Unit,
    hasPages: Boolean,
    isFirstShownAtOnce: Boolean,
    onTurn: (Int) -> Unit,
    linkPreview: @Composable () -> Unit,
) = AnimatedContent(format, modifier, transitionSpec = { fadeIn() togetherWith fadeOut() }) { shownFormat ->
    if (shownFormat == PrintSettings.Format.LINK) {
        linkPreview()
    } else if (shownFormat == PrintSettings.Format.FILES) {
        FilesPreview(
            modifier = if (isSideBySide) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            source = state.source!!,
            isInList = !isSideBySide,
            // Beside the options the save button floats over the end of this pane.
            bottomPadding = if (isSideBySide) bottomInset + FLOATING_CONTROLS_CLEARANCE else 0.dp,
        )
    } else {
        val emptyMessage = when {
            state.source!!.songs.isEmpty() -> stringResource(Res.string.print_setlist_empty)
            else -> null
        }
        // Stacked, the preview is an item of the options' list, under the format, so the pill turning its pages
        // is drawn inside it and scrolls away with it rather than staying over the options. There only the pages take
        // a height of their own, the files and the link being as tall as what they say, and the change between them is
        // the size this pane's crossfade animates.
        Box(if (isSideBySide) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(STACKED_PREVIEW_HEIGHT)) {
            PrintPreview(
                modifier = Modifier.fillMaxSize(),
                laidOut = laidOut,
                isCurrent = isCurrent,
                renderer = renderer,
                emptyMessage = emptyMessage,
                fit = pageFit,
                areOptionsBelow = !isSideBySide,
                pagerState = pagerState,
                magnifications = magnifications,
                pageView = { state.pageView },
                onPageViewChanged = onPageViewChanged,
            )
            if (!isSideBySide) {
                PageButtons(
                    modifier = Modifier.align(Alignment.TopEnd).padding(PAGE_MARGIN),
                    isVisible = hasPages,
                    isFirstShownAtOnce = isFirstShownAtOnce,
                    page = pagerState.currentPage,
                    pageCount = pagerState.pageCount,
                    onTurn = onTurn,
                )
            }
        }
    }
}
