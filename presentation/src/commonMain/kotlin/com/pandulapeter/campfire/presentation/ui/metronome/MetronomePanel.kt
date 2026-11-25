/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_start
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.ui.components.CampfireTooltip
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_END_INSET
import com.pandulapeter.campfire.presentation.ui.components.menu.NEW_ITEM_BUTTON_SIZE
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.NEW_BUTTON_Z_INDEX
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.TabChromeContent
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.TabChromeElement
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.drawnContainerColor
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.rememberSharedTabChrome
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.sharedTabChrome
import kotlinx.coroutines.flow.Flow

/**
 * The least of a metronome that is still one - the bar as it is heard, with its accents tapped on it, and play and stop
 * at the end of the row, where the thumb holding the phone reaches it - as the Metronome tab's header, pinned above
 * everything else the tab sets, so that the click can be stopped wherever the page has been scrolled to. Song details'
 * dock draws the same bar and the same button in its own way (`DockTempoChip`, `DockMetronomePanel`).
 *
 * The accents and the button are as tall as each other, the accents starting and ending where the rows of the page
 * under them do. The button is the list screens' New button in size, mark, and place at the window's end, the one other
 * filled circle a top level screen leads with, so that the two read as the same kind of control.
 *
 * @param beatLevels The bar the click counts as it is accented, see [metronomeTimeSignatureOf].
 * @param beats One item per click as it is heard, which the flash follows.
 * @param onBeatLevelsChanged Called with the bar's accents once one of its beats is tapped.
 * @param onPlayStop Starts or stops the click, as M does.
 * @param pageMaxWidth How wide the page the panel heads grows at most, its insets left out: the accents are as wide as
 *   its rows and centered over them, while the button keeps to the end of the window, as the New button does.
 */
@Composable
internal fun MetronomePanel(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    beatLevels: List<BeatLevel>,
    beats: Flow<MetronomeBeat>,
    isFlashEnabled: Boolean,
    onBeatLevelsChanged: (List<BeatLevel>) -> Unit,
    onPlayStop: () -> Unit,
    pageMaxWidth: Dp,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    MetronomePanelRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + PANEL_HORIZONTAL_PADDING,
                end = contentPadding.calculateEndPadding(layoutDirection) + NEW_ITEM_BUTTON_END_INSET,
            )
            .height(PANEL_HEIGHT),
        // As wide as the rows of the page under the panel, inside the page's margins on both sides.
        beatsMaxWidth = pageMaxWidth - PANEL_HORIZONTAL_PADDING * 2,
        gap = PANEL_GAP,
        startPadding = PANEL_HORIZONTAL_PADDING,
        endPadding = NEW_ITEM_BUTTON_END_INSET,
        beats = {
            BeatRow(
                beatLevels = beatLevels,
                beats = beats,
                isPlaying = isPlaying,
                isFlashEnabled = isFlashEnabled,
                blockHeight = PANEL_HEIGHT,
                blockGap = PANEL_BEAT_GAP,
                onBeatLevelsChanged = onBeatLevelsChanged,
            )
        },
    ) {
        val label = stringResource(if (isPlaying) Res.string.metronome_stop else Res.string.metronome_start)
        CampfireTooltip(label = label, shortcut = KeyShortcut.METRONOME) {
            // The button stands on the New button's spot, so a tab swap carries it over as that button: one container,
            // only the icon crossing (see SharedTabChrome), which draws the container in its stead.
            val colors = IconButtonDefaults.filledIconButtonColors()
            val chrome = rememberSharedTabChrome(
                element = TabChromeElement.NEW_BUTTON,
                containerColor = colors.containerColor,
                content = if (isPlaying) TabChromeContent.STOP else TabChromeContent.PLAY,
            )
            FilledIconButton(
                modifier = Modifier
                    .sharedTabChrome(chrome = chrome, zIndex = NEW_BUTTON_Z_INDEX, isContainerDrawnOutsideSwaps = true)
                    .size(PANEL_HEIGHT)
                    .semantics { contentDescription = label },
                onClick = onPlayStop,
                colors = colors.copy(containerColor = chrome.drawnContainerColor(colors.containerColor)),
            ) {
                PlayStopMark(
                    isPlaying = isPlaying,
                    size = PLAY_MARK_SIZE,
                )
            }
        }
    }
}

private val PANEL_HEIGHT = NEW_ITEM_BUTTON_SIZE
private val PANEL_HORIZONTAL_PADDING = 16.dp
private val PANEL_GAP = 12.dp
private val PANEL_BEAT_GAP = 6.dp
private val PLAY_MARK_SIZE = 24.dp
