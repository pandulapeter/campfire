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

import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import org.jetbrains.compose.resources.StringResource

/** The name of a top level screen: its item in the navigation chrome, and its button among the tools ([ToolsButtonGroup]). */
internal val CampfireDestination.TopLevel.label: StringResource
    get() = when (this) {
        CampfireDestination.Songs -> Res.string.songs
        CampfireDestination.Setlists -> Res.string.setlists
        CampfireDestination.Metronome -> Res.string.metronome
        CampfireDestination.Tuner -> Res.string.tuner
        CampfireDestination.Chords -> Res.string.chords
        CampfireDestination.Scales -> Res.string.scales
        CampfireDestination.Settings -> Res.string.settings
    }
