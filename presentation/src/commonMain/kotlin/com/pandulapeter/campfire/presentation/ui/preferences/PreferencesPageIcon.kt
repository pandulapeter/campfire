/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.ui.chords.ChordsIcon
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIcon
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeIconBeat
import org.jetbrains.compose.resources.painterResource

/** The icon of the screen a page is about, the one its navigation item has; General has Settings' own. */
@Composable
internal fun PreferencesPageIcon(
    page: PreferencesPage,
    isKeyboard: Boolean,
) = when (page) {
    PreferencesPage.GENERAL -> Icon(painter = painterResource(Res.drawable.ic_settings), contentDescription = null)
    PreferencesPage.SONG -> Icon(painter = painterResource(Res.drawable.ic_songs), contentDescription = null)
    PreferencesPage.CHORDS -> ChordsIcon(isKeyboard = isKeyboard)
    // At rest: a page button is no click to follow.
    PreferencesPage.METRONOME -> MetronomeIcon(
        beat = remember { MetronomeIconBeat(swingState = Animatable(0f), pulseState = Animatable(0f)) },
        contentDescription = null,
    )
}
