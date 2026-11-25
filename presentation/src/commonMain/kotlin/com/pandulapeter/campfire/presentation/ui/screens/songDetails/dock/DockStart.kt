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
 * ([DockInstrumentTools]), then the key chip ([DockKeyChip]) where there is a [dockKey], [height] tall, the diagram row's
 * tall one where [isTall].
 */
@Composable
internal fun DockStart(content: DockContent, dockKey: DockKey?, height: Dp, isTall: Boolean = false) {
    DockInstrumentTools(content = content, keyOpensPlaying = dockKey?.opensPlaying == true)
    if (dockKey != null) {
        DockKeyChip(
            dockKey = dockKey,
            height = height,
            isTall = isTall,
            onClick = content.playingControls?.onPlayingClicked?.takeIf { dockKey.opensPlaying },
        )
    }
}
