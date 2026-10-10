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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer
import kotlinx.coroutines.flow.Flow

/** What the preview shows, which it fades between, keyed by its kind so that a new document does not count as a change. */
private sealed interface PreviewContent {
    data class Empty(val message: String) : PreviewContent
    data object Loading : PreviewContent
    data class Pages(val laidOut: LaidOutDocument) : PreviewContent
}

/**
 * The pages, or what stands in for them, over the whole of [modifier]'s pane: nothing around them takes a band of it, so
 * a zoomed page reaches out to the pane's edges and what controls it floats over it.
 *
 * @param emptyMessage What to say instead of the pages where there are none to show: a setlist with no songs, or none
 *   of them chosen.
 * @param fit What the floating controls leave of the pane for a page at a zoom of 1.
 * @param areOptionsBelow Whether the options are under the pane rather than beside it, at its start.
 * @param pagerState The pager shared with the page selector floating over the screen.
 * @param magnifications The touchpad pinches the window hears, see [CampfireViewModel.magnifyByTouchpad].
 * @param pageView How that page is zoomed and panned, kept by the screen for the same reason, and read where it is used,
 *   since the gestures that change it read it again before the next composition.
 */
@Composable
internal fun PrintPreview(
    modifier: Modifier,
    laidOut: LaidOutDocument?,
    isCurrent: Boolean,
    renderer: PrintRenderer,
    emptyMessage: String?,
    fit: PageFit,
    areOptionsBelow: Boolean,
    pagerState: PagerState,
    magnifications: Flow<Float>,
    pageView: () -> PageView,
    onPageViewChanged: (PageView) -> Unit,
) {
    val content = when {
        emptyMessage != null -> PreviewContent.Empty(emptyMessage)
        laidOut == null || laidOut.document.pages.isEmpty() -> PreviewContent.Loading
        else -> PreviewContent.Pages(laidOut)
    }
    AnimatedContent(
        targetState = content,
        modifier = modifier,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
    ) { shown ->
        when (shown) {
            is PreviewContent.Empty -> Box(Modifier.fillMaxSize().padding(PAGE_MARGIN), contentAlignment = Alignment.Center) {
                Text(shown.message)
            }
            PreviewContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
            is PreviewContent.Pages -> PrintPages(
                laidOut = shown.laidOut,
                isCurrent = isCurrent,
                renderer = renderer,
                fit = fit,
                areOptionsBelow = areOptionsBelow,
                pagerState = pagerState,
                magnifications = magnifications,
                pageView = pageView,
                onPageViewChanged = onPageViewChanged,
            )
        }
    }
}
