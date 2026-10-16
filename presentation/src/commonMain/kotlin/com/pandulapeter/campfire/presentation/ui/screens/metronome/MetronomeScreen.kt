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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
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
import com.pandulapeter.campfire.presentation.resources.metronome_group_playback
import com.pandulapeter.campfire.presentation.resources.metronome_group_rhythm
import com.pandulapeter.campfire.presentation.resources.metronome_animate
import com.pandulapeter.campfire.presentation.resources.metronome_sound
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision
import com.pandulapeter.campfire.presentation.resources.metronome_subdivision_description
import com.pandulapeter.campfire.presentation.resources.metronome_time_signature
import com.pandulapeter.campfire.presentation.resources.metronome_vibrate
import com.pandulapeter.campfire.presentation.resources.metronome_volume
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopOnRequest
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_TOP_INSET
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomePanel
import com.pandulapeter.campfire.presentation.ui.metronome.TimeSignaturePicker
import com.pandulapeter.campfire.presentation.ui.metronome.sound
import com.pandulapeter.campfire.presentation.ui.metronome.subdivision
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.rememberBeatHaptics
import com.pandulapeter.campfire.presentation.ui.metronome.rememberMetronomePanelState
import com.pandulapeter.campfire.presentation.ui.settingsPage.AnimatedSettingsRow
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsMessage
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsPage
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.settingsPage.PageWidthLayout

/**
 * The whole instrument: the song details screen's own metronome panel ([MetronomePanel]) pinned at the top - the bar as
 * it is heard, accented by tapping it, and play and stop at the end of it - so that the click is played the same way
 * on both screens and can be stopped wherever the page has been scrolled to; and under it, scrolling, the rest of what
 * a click is, as rows of a settings page ([SettingsPage]) in two titled sections that a wide window sets side by side,
 * as every settings page does: what is played ([MetronomeBarOptions]) and how it reaches the player
 * ([MetronomeSoundOptions]).
 *
 * The tab always shows and plays its own tempo and time signature: every way onto it clears the back stack, so no song
 * is open behind it, and a click played for a song is stopped with the song long before this screen is shown.
 * Nothing here changes a song or a setlist, so performance mode leaves all of it in place.
 */
@Composable
internal fun MetronomeScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: PageWidthLayout,
    scrollPosition: ScrollPosition,
    isUnderTabs: Boolean,
    contentPadding: PaddingValues,
) {
    val playback by viewModel.metronomeController.metronomePlayback.collectAsStateWithLifecycle()
    val isPlaying = playback is MetronomePlayback.Playing
    val scrollState = rememberRetainedScrollState(scrollPosition)
    ScrollToTopOnRequest(
        requests = viewModel.navigator.scrollToTopRequests,
        destination = CampfireDestination.Metronome,
        scrollState = scrollState,
        onSelectDestination = viewModel::selectTopLevelDestination,
    )
    Column(
        // A metronome is practised with both hands on the instrument, and a screen that dims and locks takes the beat row
        // with it.
        modifier = modifier.fillMaxSize().then(if (isPlaying) Modifier.keepScreenOn() else Modifier),
    ) {
        val panel = rememberMetronomePanelState(viewModel)
        // Under the tools' tabs, the gap the other tool screens leave above their first control; at the top of the window,
        // where the list screens' bar would stand, so that the play button is on the New button's spot. On the spec the tab
        // row's room grows and shrinks by, so the bar moves with it.
        val panelTopPadding by animateDpAsState(
            targetValue = if (isUnderTabs) PANEL_TOP_PADDING else NEW_ITEM_BUTTON_TOP_INSET,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        )
        MetronomePanel(
            modifier = Modifier.padding(top = panelTopPadding),
            isPlaying = panel.isPlaying,
            beatLevels = panel.beatLevels,
            beats = viewModel.metronomeController.metronomeBeats,
            isFlashEnabled = panel.isFlashEnabled,
            onBeatLevelsChanged = { levels -> viewModel.metronomeController.setBeatLevels(panel.timeSignature, levels) },
            onPlayStop = viewModel.metronomeController::toggleMetronome,
            isVisible = true,
            isProminent = true,
            // As wide as the page under it, so that the bar ends where the rows it heads end.
            pageMaxWidth = layout.pageMaxWidth(sections = 2),
            contentPadding = contentPadding.only(start = true, end = true),
        )
        SettingsPage(
            modifier = Modifier.weight(1f),
            sectionColumns = layout.sectionColumns,
            scrollState = scrollState,
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
) = SettingsSection(title = stringResource(Res.string.metronome_group_rhythm)) {
    val settings by viewModel.metronomeController.metronomeSettings.collectAsStateWithLifecycle()
    AnimatedSettingsRow(value = (playback as? MetronomePlayback.Playing)?.audioIssue) { issue ->
        SettingsMessage(
            text = stringResource(
                when (issue) {
                    MetronomeAudioIssue.UNAVAILABLE -> Res.string.metronome_audio_unavailable
                    MetronomeAudioIssue.WAITING_FOR_GESTURE -> Res.string.metronome_audio_waiting
                }
            ),
            isAnnounced = true,
        )
    }
    TempoSetting(
        bpm = settings.bpm,
        onBpmChanged = { value -> viewModel.metronomeController.updateMetronomeSettings { copy(bpm = MetronomePattern.coerceBpm(value)) } },
    )
    SettingsSubsection(
        title = stringResource(Res.string.metronome_time_signature),
        // The one place that says the bar pinned above is an editor too, next to what decides how many beats it has.
        description = stringResource(Res.string.metronome_accent_hint),
    ) {
        TimeSignaturePicker(
            timeSignature = settings.timeSignatureOrDefault,
            onChange = { value -> viewModel.metronomeController.updateMetronomeSettings { copy(timeSignature = value.toString()) } },
        )
    }
    SettingsSubsection(
        title = stringResource(Res.string.metronome_subdivision),
        description = stringResource(Res.string.metronome_subdivision_description),
    ) {
        SubdivisionChoice(
            selected = settings.subdivision,
            onSelected = { viewModel.metronomeController.updateMetronomeSettings { copy(subdivisionId = it.id) } },
        )
    }
}

/**
 * How a click reaches the player: what it sounds like, how loud, and whether it is seen and felt. Unlike the section
 * before it none of this is the tab's own: it is how every click is played, a song's included.
 */
@Composable
private fun MetronomeSoundOptions(
    viewModel: CampfireViewModel,
) = SettingsSection(title = stringResource(Res.string.metronome_group_playback)) {
    val settings by viewModel.metronomeController.metronomeSettings.collectAsStateWithLifecycle()
    SettingsSubsection(title = stringResource(Res.string.metronome_sound)) {
        SoundChoice(
            selected = settings.sound,
            onSelected = { sound ->
                viewModel.metronomeController.updateMetronomeSettings { copy(soundId = sound.id) }
                viewModel.metronomeController.previewMetronomeSound(sound)
            },
        )
    }
    SettingsSubsection(title = stringResource(Res.string.metronome_volume)) {
        VolumeSlider(
            volume = settings.volume,
            onVolumeChanged = { value -> viewModel.metronomeController.updateMetronomeSettings { copy(volume = value) } },
        )
    }
    SwitchListItem(
        title = stringResource(Res.string.metronome_animate),
        isChecked = settings.isVisualBeatEnabled,
        onCheckedChange = { value -> viewModel.metronomeController.updateMetronomeSettings { copy(isVisualBeatEnabled = value) } },
    )
    if (rememberBeatHaptics() != null) {
        SwitchListItem(
            title = stringResource(Res.string.metronome_vibrate),
            isChecked = settings.isHapticBeatEnabled,
            onCheckedChange = { value -> viewModel.metronomeController.updateMetronomeSettings { copy(isHapticBeatEnabled = value) } },
        )
    }
}

/** What the bar keeps free above itself under the tabs, the gap the other tool screens leave above their first control. */
private val PANEL_TOP_PADDING = 16.dp
