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

import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Previous and Next as steps from the page the last of them asked for, rather than from the page on screen:
 * `PagerState.currentPage` only moves once an animation is past halfway, and even `PagerState.targetPage` only once the
 * launched animation has started, so presses that come quicker than that would each ask for the same page. A request
 * is forgotten when its animation ends, however it ends (a swipe cancels it), and whatever the pager then settles on
 * is where the next press starts from.
 *
 * Only touched from the main thread - key and click handlers, and the coroutine on the composition's dispatcher - so a
 * plain field is enough. The request is an object rather than the page number, so that two requests for the same page
 * are still told apart.
 */
internal class PageStepper(
    private val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    private var request: Request? = null

    private class Request(val page: Int)

    /** Whether the pager is being moved by a step rather than by a swipe. */
    val isStepping get() = request != null

    fun step(delta: Int) {
        val from = request?.page ?: pagerState.targetPage
        val page = (from + delta).coerceIn(0, pagerState.pageCount - 1)
        if (page == from) return
        val request = Request(page).also { request = it }
        coroutineScope.launch {
            try {
                pagerState.animateScrollToPage(page)
            } finally {
                // Only the latest request is cleared: an earlier one ends when the next press cancels it.
                if (this@PageStepper.request === request) this@PageStepper.request = null
            }
        }
    }
}
