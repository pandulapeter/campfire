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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_share
import com.pandulapeter.campfire.presentation.resources.songs_share_song
import com.pandulapeter.campfire.presentation.resources.songs_share_songs
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.print.PrintSource

/** What the screen's area shows, which it fades between; a new document is not one of these, see [PrintPreview]. */
internal enum class PrintScreenContent { FAILED, LOADING, LOADED }

/** What the screen shows: a failure (a layout failing only for the PDF), the source still being read, or the source. */
internal fun exportContentOf(isFailed: Boolean, isLayoutFailed: Boolean, isPdf: Boolean, hasSource: Boolean) = when {
    isFailed || isLayoutFailed && isPdf -> PrintScreenContent.FAILED
    !hasSource -> PrintScreenContent.LOADING
    else -> PrintScreenContent.LOADED
}

/**
 * Whether the library's own files can be exported: a song whose file could not be read has nothing to write, while a
 * zip always has something, a setlist with no songs its manifest.
 */
internal fun canExportFiles(source: PrintSource?) = when {
    source == null -> false
    source.hasSeveralSongs -> true
    else -> source.songs.firstOrNull()?.text != null
}

/** How wide the options are beside the preview, null where the two are stacked. */
internal fun optionsWidthFor(bodyWidth: Dp, bodyHeight: Dp) = when {
    bodyWidth >= 760.dp && bodyHeight >= 480.dp -> 330.dp
    // A phone on its side: too short to stack the two, wide enough to put them side by side.
    bodyWidth >= 520.dp -> 260.dp
    else -> null
}

/**
 * The room the page leaves for the controls floating over its pane: in a short window the column [floatingControlsWidth]
 * wide they stack in at its end, elsewhere a band at each end a control floats over.
 */
internal fun pageFitFor(isSideBySide: Boolean, isShortWindow: Boolean, floatingControlsWidth: Dp, bottomInset: Dp) = when {
    // Under the options only the pill floats over the preview, in the list the save button floats over.
    !isSideBySide -> PageFit(top = FLOATING_CONTROLS_CLEARANCE)
    isShortWindow -> PageFit(end = floatingControlsWidth + PAGE_MARGIN * 2, bottom = bottomInset + PAGE_MARGIN)
    else -> PageFit(top = FLOATING_CONTROLS_CLEARANCE, bottom = bottomInset + FLOATING_CONTROLS_CLEARANCE)
}

/**
 * Whether the save button is filled with the primary color rather than tinted: where the options stand beside a preview
 * pane that is not short, the button floats in the far corner of a large window, away from the options the eye is on,
 * where the tinted one went unnoticed beside the page.
 */
internal fun isActionButtonFilledFor(isSideBySide: Boolean, isShortWindow: Boolean) = isSideBySide && !isShortWindow

/**
 * The room the Link preview leaves for the button floating over its pane. Beside the options it floats over the pane's
 * end, which a short window takes as a column for the shared link alone, the one state whose button is a bare icon there
 * (Copy link), so that its QR code has the pane's height; every other state's button keeps its label, which only a band
 * at the bottom has the width for. Stacked, the button floats over the options' list instead.
 */
internal fun linkPreviewPaddingFor(isSideBySide: Boolean, isShortWindow: Boolean, isShared: Boolean, bottomInset: Dp) = when {
    !isSideBySide -> PaddingValues()
    isShortWindow && isShared -> PaddingValues(end = FLOATING_CONTROLS_CLEARANCE, bottom = bottomInset + PAGE_MARGIN)
    else -> PaddingValues(bottom = bottomInset + FLOATING_CONTROLS_CLEARANCE)
}

/**
 * How large the shared link's QR code is in a pane as large as [paneWidth] by [paneHeight]: as large as it reads best,
 * smaller where the pane cannot show it whole otherwise, but never below what a camera still reads, where it scrolls
 * with the link under it instead, and never wider than the pane.
 */
internal fun qrCodeSizeFor(paneWidth: Dp, paneHeight: Dp) = minOf(QR_CODE_WIDTH, paneHeight - SHARED_LINK_VERTICAL_PADDING * 2)
    .coerceAtLeast(QR_CODE_MIN_WIDTH)
    .coerceAtMost(paneWidth - SHARED_LINK_HORIZONTAL_PADDING * 2)
    .coerceAtLeast(0.dp)

/** The title of the screen, by what is exported. */
internal fun exportTitleOf(dialog: DialogType.Export) = when {
    dialog.setlist != null -> Res.string.setlists_share
    dialog.songs.isNotEmpty() -> Res.string.songs_share_songs
    else -> Res.string.songs_share_song
}
