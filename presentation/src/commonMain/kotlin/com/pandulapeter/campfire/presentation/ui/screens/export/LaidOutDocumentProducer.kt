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

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.ui.print.PrintLabels
import com.pandulapeter.campfire.presentation.ui.print.PrintRenderer
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.print.layoutPrintDocument
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The document [source] lays out to with [settings], for the PDF only. The last document stays on screen while the next
 * one is laid out, so that a step of an option fades from one page to the next instead of blanking the preview to a
 * spinner each time. Retry is keyed too ([ExportState.attempt]): the source it reads again equals the one that failed
 * to lay out, so without it nothing would. [renderer] is a key alone, the layout measuring with one of [newRenderer]'s
 * of its own off the main thread.
 */
@Composable
internal fun produceLaidOutDocument(
    state: ExportState,
    source: PrintSource?,
    settings: PrintSettings,
    labels: PrintLabels,
    renderer: PrintRenderer,
    newRenderer: () -> PrintRenderer,
) = produceState<LaidOutDocument?>(null, source, settings, labels, renderer, state.attempt) {
    state.layoutFailed = false
    val input = source
    val inputSettings = settings
    if (input == null || input.songs.isEmpty()) {
        value = null
        return@produceState
    }
    // An export of the library's own files has no pages, and the ones laid out for the PDF are kept for the format being switched back,
    // which then lays out nothing again.
    if (inputSettings.format != PrintSettings.Format.PDF) return@produceState
    if (value.let { it != null && it.source === input && it.settings == inputSettings && it.labels == labels }) return@produceState
    // A burst of steps is one layout, but the first one, with nothing on screen yet, starts at once.
    if (value != null) delay(LAYOUT_DEBOUNCE)
    try {
        val document = withContext(Dispatchers.Default) {
            val measurements = newRenderer()
            layoutPrintDocument(
                source = input,
                settings = inputSettings,
                labels = labels,
                measureText = measurements::width,
            )
        }
        value = LaidOutDocument(
            document = document,
            source = input,
            settings = inputSettings,
            labels = labels,
            generation = (value?.generation ?: 0) + 1,
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        state.layoutFailed = true
    }
}

/** How long the options have to hold still before the pages are laid out again, so that a burst of steps is one layout. */
private val LAYOUT_DEBOUNCE = 120.milliseconds
