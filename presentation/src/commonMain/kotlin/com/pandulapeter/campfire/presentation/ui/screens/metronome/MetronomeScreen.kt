/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.metronome

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomeAudioIssue
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome_accent_hint
import com.pandulapeter.campfire.presentation.resources.metronome_audio_unavailable
import com.pandulapeter.campfire.presentation.resources.metronome_audio_waiting
import com.pandulapeter.campfire.presentation.resources.metronome_flash
import com.pandulapeter.campfire.presentation.resources.metronome_sound
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision_description
import com.pandulapeter.campfire.presentation.resources.metronome_time_signature
import com.pandulapeter.campfire.presentation.resources.metronome_vibrate
import com.pandulapeter.campfire.presentation.resources.metronome_volume
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomePanel
import com.pandulapeter.campfire.presentation.ui.metronome.TimeSignaturePicker
import com.pandulapeter.campfire.presentation.ui.metronome.sound
import com.pandulapeter.campfire.presentation.ui.metronome.subdivision
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import com.pandulapeter.campfire.presentation.ui.platform.rememberBeatHaptics
import com.pandulapeter.campfire.presentation.ui.screens.settings.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsCard
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsMessage
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsPage
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSection
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsWidthLayout

/**
 * The whole instrument: the song details screen's own metronome panel ([MetronomePanel]) pinned at the top - the bar as
 * it is heard, accented by tapping it, and play and stop at the end of it - so that the click is played the same way
 * on both screens and can be stopped wherever the page has been scrolled to; and under it, scrolling, the rest of what
 * a click is, as rows of a settings page ([SettingsPage]) in two sections that a wide window sets side by side: what
 * is played ([MetronomeBarOptions]) and how it reaches the player ([MetronomeSoundOptions]).
 *
 * The tab always shows and plays its own tempo and time signature: every way onto it clears the back stack, so no song
 * is open behind it, and a click played for a song is stopped with the song long before this screen is shown.
 * Nothing here changes a song or a setlist, so performance mode leaves all of it in place.
 */
@Composable
internal fun MetronomeScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: SettingsWidthLayout,
    scrollPosition: ScrollPosition,
    contentPadding: PaddingValues,
) {
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val isPlaying = playback is MetronomePlayback.Playing
    val layoutDirection = LocalLayoutDirection.current
    Column(
        // A metronome is practised with both hands on the instrument, and a screen that dims and locks takes the beat row
        // with it.
        modifier = modifier.fillMaxSize().then(if (isPlaying) Modifier.keepScreenOn() else Modifier),
    ) {
        MetronomePanel(
            // As wide as the page under it, so that the bar ends where the rows it heads end.
            modifier = Modifier
                .padding(top = PANEL_TOP_PADDING)
                .widthIn(
                    max = layout.pageMaxWidth(sections = 2) + contentPadding.calculateStartPadding(layoutDirection) +
                        contentPadding.calculateEndPadding(layoutDirection),
                ),
            viewModel = viewModel,
            isVisible = true,
            isProminent = true,
            contentPadding = contentPadding.only(start = true, end = true),
        )
        SettingsPage(
            modifier = Modifier.weight(1f),
            sectionColumns = layout.sectionColumns,
            scrollState = rememberRetainedScrollState(scrollPosition),
            contentPadding = contentPadding.only(start = true, end = true, bottom = true),
            section = {
                MetronomeBarOptions(
                    viewModel = viewModel,
                    playback = playback,
                )
            },
            secondSection = { MetronomeSoundOptions(viewModel = viewModel) },
        )
    }
}

/**
 * What the tab plays, in the order it is reached for: why nothing is heard where that is so, the tempo, how the bar is
 * counted and what each beat is divided into. The tempo and the time signature are the tab's own, a song bringing its
 * own of both.
 */
@Composable
private fun MetronomeBarOptions(
    viewModel: CampfireViewModel,
    playback: MetronomePlayback,
) = SettingsSection {
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    AnimatedSettingsRow(value = (playback as? MetronomePlayback.Playing)?.audioIssue) { issue ->
        SettingsMessage(
            text = stringResource(
                when (issue) {
                    MetronomeAudioIssue.UNAVAILABLE -> Res.string.metronome_audio_unavailable
                    MetronomeAudioIssue.WAITING_FOR_GESTURE -> Res.string.metronome_audio_waiting
                }
            ),
        )
    }
    TempoSetting(
        bpm = settings.bpm,
        onBpmChanged = { value -> viewModel.updateMetronomeSettings { copy(bpm = MetronomePattern.coerceBpm(value)) } },
    )
    SettingsSubsection(
        title = stringResource(Res.string.metronome_time_signature),
        // The one place that says the bar pinned above is an editor too, next to what decides how many beats it has.
        description = stringResource(Res.string.metronome_accent_hint),
    ) {
        TimeSignaturePicker(
            timeSignature = settings.timeSignatureOrDefault,
            onChange = { value -> viewModel.updateMetronomeSettings { copy(timeSignature = value.toString()) } },
        )
    }
    SettingsSubsection(
        title = stringResource(Res.string.metronome_subdivision),
        description = stringResource(Res.string.metronome_subdivision_description),
    ) {
        SubdivisionChoice(
            selected = settings.subdivision,
            onSelected = { viewModel.updateMetronomeSettings { copy(subdivisionId = it.id) } },
        )
    }
}

/**
 * How a click reaches the player: what it sounds like, how loud, and whether it is seen and felt. On a card, since
 * unlike the section before it none of this is the tab's own: it is how every click is played, a song's included.
 */
@Composable
private fun MetronomeSoundOptions(
    viewModel: CampfireViewModel,
) = SettingsCard {
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    SettingsSubsection(title = stringResource(Res.string.metronome_sound)) {
        SoundChoice(
            selected = settings.sound,
            onSelected = { sound ->
                viewModel.updateMetronomeSettings { copy(soundId = sound.id) }
                viewModel.previewMetronomeSound(sound)
            },
        )
    }
    SettingsSubsection(title = stringResource(Res.string.metronome_volume)) {
        VolumeSlider(
            volume = settings.volume,
            onVolumeChanged = { value -> viewModel.updateMetronomeSettings { copy(volume = value) } },
        )
    }
    SwitchListItem(
        title = stringResource(Res.string.metronome_flash),
        isChecked = settings.isVisualBeatEnabled,
        onCheckedChange = { value -> viewModel.updateMetronomeSettings { copy(isVisualBeatEnabled = value) } },
    )
    if (rememberBeatHaptics() != null) {
        SwitchListItem(
            title = stringResource(Res.string.metronome_vibrate),
            isChecked = settings.isHapticBeatEnabled,
            onCheckedChange = { value -> viewModel.updateMetronomeSettings { copy(isHapticBeatEnabled = value) } },
        )
    }
}

/** What the bar keeps free above itself, so that it sits where the settings screen's tabs do. */
private val PANEL_TOP_PADDING = 8.dp
