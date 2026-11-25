/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * What starts the dock for one song's [content]: the instrument and the tuner where the key chip does not hold them
 * ([DockInstrumentTools]), then the key chip ([DockKeyChip]) where there is a [dockKey], [height] tall. In the diagram
 * row ([tallHeight], its height), a key chip that opens the Playing sheet stands over the tuner's pill
 * ([DockKeyAndTuner]) where the tuner is on.
 */
@Composable
internal fun DockStart(content: DockContent, dockKey: DockKey?, height: Dp, tallHeight: Dp? = null) {
    DockInstrumentTools(content = content, keyOpensPlaying = dockKey?.opensPlaying == true)
    if (dockKey == null) return
    val onPlayingClicked = content.playingControls?.onPlayingClicked?.takeIf { dockKey.opensPlaying }
    val onTunerClicked = content.playingTools?.onTunerClicked
    val chordDiagrams = content.chordDiagrams
    if (tallHeight != null && onPlayingClicked != null && onTunerClicked != null && chordDiagrams != null) {
        DockKeyAndTuner(
            dockKey = dockKey,
            chordDiagrams = chordDiagrams,
            height = tallHeight,
            onPlayingClicked = onPlayingClicked,
            onTunerClicked = onTunerClicked,
        )
    } else {
        DockKeyChip(dockKey = dockKey, height = height, onClick = onPlayingClicked)
    }
}
