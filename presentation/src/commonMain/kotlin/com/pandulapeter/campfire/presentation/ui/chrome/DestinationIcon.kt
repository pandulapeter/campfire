/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_scales
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.ui.chords.ChordsIcon
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIcon
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import org.jetbrains.compose.resources.painterResource

/**
 * The icon of a top level screen: its navigation item's, and its tab's among the tools ([ToolsTabRow]). The Metronome's
 * swings and pulses with the click, and the Chords' is drawn for the player's instrument.
 */
@Composable
internal fun DestinationIcon(
    destination: CampfireDestination.TopLevel,
    metronomeBeat: MetronomeIconBeat,
    isKeyboard: Boolean,
) = when (destination) {
    CampfireDestination.Songs -> Icon(painter = painterResource(Res.drawable.ic_songs), contentDescription = null)
    CampfireDestination.Setlists -> Icon(painter = painterResource(Res.drawable.ic_setlists), contentDescription = null)
    CampfireDestination.Metronome -> MetronomeIcon(beat = metronomeBeat, contentDescription = null)
    CampfireDestination.Tuner -> Icon(painter = painterResource(Res.drawable.ic_tuner), contentDescription = null)
    CampfireDestination.Chords -> ChordsIcon(isKeyboard = isKeyboard)
    CampfireDestination.Scales -> Icon(painter = painterResource(Res.drawable.ic_scales), contentDescription = null)
    CampfireDestination.Settings -> Icon(painter = painterResource(Res.drawable.ic_settings), contentDescription = null)
}
