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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_animate
import com.pandulapeter.campfire.presentation.resources.metronome_change_timing
import com.pandulapeter.campfire.presentation.resources.metronome_change_timing_description
import com.pandulapeter.campfire.presentation.resources.metronome_group_playback
import com.pandulapeter.campfire.presentation.resources.metronome_sound
import com.pandulapeter.campfire.presentation.resources.metronome_vibrate
import com.pandulapeter.campfire.presentation.resources.metronome_volume
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.platform.rememberBeatHaptics
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection

/**
 * How a click reaches the player: what it sounds like, how loud, where a song's click takes up a new tempo, and whether
 * it is seen and felt. None of this is the Metronome tab's own: it is how every click is played, a song's included (the
 * tempo change only there, since the tab plays one tempo), which is why it is both the tab's second section and the
 * Preferences' Metronome page, drawn the same in both.
 */
@Composable
internal fun MetronomePlaybackSection(
    metronomeController: MetronomeController,
) = SettingsSection(title = stringResource(Res.string.metronome_group_playback)) {
    val settings by metronomeController.metronomeSettings.collectAsStateWithLifecycle()
    SettingsSubsection(title = stringResource(Res.string.metronome_sound)) {
        SoundChoice(
            selected = settings.sound,
            onSelected = { sound ->
                metronomeController.updateMetronomeSettings { copy(soundId = sound.id) }
                metronomeController.previewMetronomeSound(sound)
            },
        )
    }
    SettingsSubsection(title = stringResource(Res.string.metronome_volume)) {
        VolumeSlider(
            volume = settings.volume,
            onVolumeChanged = { value -> metronomeController.updateMetronomeSettings { copy(volume = value) } },
        )
    }
    SettingsSubsection(
        title = stringResource(Res.string.metronome_change_timing),
        description = stringResource(Res.string.metronome_change_timing_description),
    ) {
        ChangeTimingChoice(
            isChangeOnNextBar = settings.isChangeOnNextBar,
            onSelected = { value -> metronomeController.updateMetronomeSettings { copy(isChangeOnNextBar = value) } },
        )
    }
    SwitchListItem(
        title = stringResource(Res.string.metronome_animate),
        isChecked = settings.isVisualBeatEnabled,
        onCheckedChange = { value -> metronomeController.updateMetronomeSettings { copy(isVisualBeatEnabled = value) } },
    )
    if (rememberBeatHaptics() != null) {
        SwitchListItem(
            title = stringResource(Res.string.metronome_vibrate),
            isChecked = settings.isHapticBeatEnabled,
            onCheckedChange = { value -> metronomeController.updateMetronomeSettings { copy(isHapticBeatEnabled = value) } },
        )
    }
}
