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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.print.PrintDocument
import com.pandulapeter.campfire.presentation.ui.print.PrintLabels
import com.pandulapeter.campfire.presentation.ui.print.PrintSource

/**
 * What the screen keeps while it is open, built by [rememberExportState]. Each field is remembered on its own, as
 * saveable or not as it has to be, rather than the holder being remembered whole, which could not be saved field by
 * field: the selection, the page and how it is zoomed outlive a rotation, while the options, the source and the attempt
 * are read again.
 */
internal class ExportState(
    settings: MutableState<PrintSettings>,
    source: MutableState<PrintSource?>,
    failed: MutableState<Boolean>,
    attempt: MutableIntState,
    selected: MutableState<Set<Int>?>,
    page: MutableIntState,
    pageView: MutableState<PageView>,
    layoutFailed: MutableState<Boolean>,
) {
    /** The options the preview is laid out with, ahead of the saved preferences until those catch up. */
    var settings by settings

    /** The snapshot the PDF is made of, null until it has been read. */
    var source by source

    /** Whether reading [source] failed, which the screen offers to try again. */
    var failed by failed

    /** Counts the tries of Retry, which both the read of the source and the layout are keyed by. */
    var attempt by attempt

    /** The songs to export, by their place in [source]; null until the source has been read. */
    var selected by selected

    /** The page of the preview that is open. */
    var page by page

    /** How that page is zoomed and panned, which the preview's pane outlives being laid out again in another place. */
    var pageView by pageView

    /** Whether the last layout failed, which the screen offers to try again the way a failed read is. */
    var layoutFailed by layoutFailed
}

@Composable
internal fun rememberExportState(
    dialog: DialogType.Export,
    initialSettings: () -> PrintSettings,
) = ExportState(
    settings = remember(dialog) { mutableStateOf(initialSettings()) },
    source = remember(dialog) { mutableStateOf<PrintSource?>(null) },
    failed = remember(dialog) { mutableStateOf(false) },
    attempt = remember(dialog) { mutableIntStateOf(0) },
    // Saved, or a rotation would put back every song somebody had unticked; null until the source has been read.
    selected = rememberSaveable(dialog, stateSaver = SELECTION_SAVER) { mutableStateOf<Set<Int>?>(null) },
    page = rememberSaveable(dialog) { mutableIntStateOf(0) },
    pageView = rememberSaveable(dialog, stateSaver = PAGE_VIEW_SAVER) { mutableStateOf(PageView()) },
    layoutFailed = remember { mutableStateOf(false) },
)

/**
 * A laid out document together with what it was laid out from, which is what tells a document that stands in for the
 * next one while that is laid out from one that may be exported. [generation] tells one document from the next without
 * comparing them, which would walk every text on every page.
 */
internal class LaidOutDocument(
    val document: PrintDocument,
    val source: PrintSource,
    val settings: PrintSettings,
    val labels: PrintLabels,
    val generation: Int,
)

/** The selected songs by their place in the source, with null (nothing read yet) saved as nothing at all. */
private val SELECTION_SAVER = Saver<Set<Int>?, List<Int>>(save = { it?.toList() }, restore = { it.toSet() })
