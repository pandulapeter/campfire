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

import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * Sound and listening end with what they were started on: the click with its screen, the tuner with its tab or the
 * song its sheet is over, a scale with its sheet. The view model registers [onBackStackChanged] with the navigator and
 * [onBeforeDialogChange] with the dialog host.
 */
internal class SoundLifetime(
    private val metronome: Metronome,
    private val stopTuner: () -> Unit,
    private val stopScale: () -> Unit,
    private val dismissTunerSheet: () -> Unit,
) {

    fun onBackStackChanged(previousTop: CampfireDestination?, stack: List<CampfireDestination>) {
        // The two screens that hold a metronome are the two it can be stopped from, so a click never outlives the one
        // it was started on: the editor opened over a song, a song closed, a tab selected, all stop it - and so does a
        // different screen arriving on top, a song opened over the Metronome tab or over another song, which holds a
        // metronome but not the one that was started. The panel the click was played from is a preference and stays
        // where the user put it, so the next song is read to a click without asking for the instrument again.
        if (isMetronomeScreenLeft(previousTop = previousTop, top = stack.lastOrNull())) metronome.stop()
        // Nothing of the tuner outlives the screen it is used on: the tab, or the song its sheet is opened over. The
        // screen starts it again once it is composed, and only that screen does - which a sheet left over another screen
        // never is, so the sheet goes with the song it was opened over.
        if (previousTop?.contentKey != stack.lastOrNull()?.contentKey) {
            stopTuner()
            dismissTunerSheet()
        }
    }

    fun onBeforeDialogChange(previousDialog: DialogType?, dialogType: DialogType?) {
        // The export screen covers the song the click is played from as a screen of its own would, and leaves no way
        // to stop it, so it stops a click the way pushing a destination does (see navigator.updateBackStack). The tuner sheet
        // listens to the room through the audio session the click plays in, and the two never hold the audio together.
        if (dialogType is DialogType.Export || dialogType is DialogType.Tuner) metronome.stop()
        // The tuner sheet listens for as long as it is up and not a moment longer, as the Tuner tab does.
        if (previousDialog is DialogType.Tuner && dialogType !is DialogType.Tuner) stopTuner()
        // A scale plays only under the sheet that shows it: closed, or covered by a chord's sheet, it stops.
        if (previousDialog is DialogType.Scale && dialogType != previousDialog) stopScale()
    }
}
