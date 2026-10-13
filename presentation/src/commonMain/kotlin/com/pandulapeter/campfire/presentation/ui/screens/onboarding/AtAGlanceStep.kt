/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.onboarding_at_a_glance_message
import com.pandulapeter.campfire.presentation.resources.onboarding_at_a_glance_title
import com.pandulapeter.campfire.presentation.resources.settings_show_key
import com.pandulapeter.campfire.presentation.resources.settings_show_key_description
import com.pandulapeter.campfire.presentation.resources.settings_show_tempo
import com.pandulapeter.campfire.presentation.resources.settings_show_tempo_description
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.LabelsOnEverySong
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import org.jetbrains.compose.resources.painterResource

/**
 * "What matters at a glance?", the key and the tempo every song card and the top of every song show, each a switch
 * (`UserPreferences.isKeyShown`, `isTempoShown`) under a song card drawn by the song list's own [SongListItem], so that a
 * switch shows exactly what it changes. Only asked while there is one of them to show: the key goes with the chords and
 * the tempo with the metronome, so each switch is only offered with its feature on.
 */
@Composable
internal fun AtAGlanceStep(
    modifier: Modifier = Modifier,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    isKeyShown: Boolean,
    isTempoShown: Boolean,
    notation: UserPreferences.Notation?,
    isCompact: Boolean,
    onKeyShownChange: (Boolean) -> Unit,
    onTempoShownChange: (Boolean) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_info),
    title = stringResource(Res.string.onboarding_at_a_glance_title),
    message = stringResource(Res.string.onboarding_at_a_glance_message),
    isCompact = isCompact,
) {
    val key = remember(notation) { ChordProNotation.shownName(PREVIEW_SONG.key.orEmpty(), (notation ?: UserPreferences.Notation.STANDARD).toChordNotation()) }
    // A picture of a card rather than one to press, so it takes no taps and says nothing to a screen reader: the switches
    // under it are what can be changed.
    SongListItem(
        modifier = Modifier.clearAndSetSemantics { },
        song = PREVIEW_SONG,
        key = key.takeIf { areChordsEnabled && isKeyShown },
        tempo = PREVIEW_SONG.tempo.takeIf { isMetronomeEnabled && isTempoShown },
        shouldShowChords = areChordsEnabled,
        labelsOnEverySong = LabelsOnEverySong(),
        onClick = {},
    )
    Column {
        if (areChordsEnabled) {
            SwitchListItem(
                title = stringResource(Res.string.settings_show_key),
                description = stringResource(Res.string.settings_show_key_description),
                isChecked = isKeyShown,
                onCheckedChange = onKeyShownChange,
            )
        }
        if (isMetronomeEnabled) {
            SwitchListItem(
                title = stringResource(Res.string.settings_show_tempo),
                description = stringResource(Res.string.settings_show_tempo_description),
                isChecked = isTempoShown,
                onCheckedChange = onTempoShownChange,
            )
        }
    }
}

/**
 * The card the switches are shown on: one of the demo songs, as it is filed in the library. Sample content rather than
 * interface text, so it is not translated, as the demo songs are not.
 */
private val PREVIEW_SONG = Song(
    fileName = "traditional-house_of_the_rising_sun.cho",
    title = "House of the Rising Sun",
    artist = "Traditional",
    key = "Am",
    transpose = 0,
    tags = emptyList(),
    languages = emptyList(),
    coverArtUrl = null,
    hasChords = true,
    canUpdateFileName = false,
    lastModified = 0,
    size = 0,
    tempo = 76,
)
