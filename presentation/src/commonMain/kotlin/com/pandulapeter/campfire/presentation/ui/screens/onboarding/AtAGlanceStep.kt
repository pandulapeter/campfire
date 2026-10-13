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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.onboarding_at_a_glance_message
import com.pandulapeter.campfire.presentation.resources.onboarding_at_a_glance_title
import com.pandulapeter.campfire.presentation.resources.settings_setlist_cards
import com.pandulapeter.campfire.presentation.resources.settings_song_cards
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.CardDetailsChoice
import com.pandulapeter.campfire.presentation.ui.components.LabelsOnEverySong
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.minSongColumnWidth
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.seconds

/**
 * "What matters at a glance?", what a song card says after its artist, asked for the Songs screen and, with the setlists
 * on, for the Setlists screen on its own (`UserPreferences.songCardDetails`, `setlistCardDetails`): each a card drawn by
 * the lists' own [SongListItem] over the chips of [CardDetailsChoice], so that a chip shows exactly what it changes. The
 * key is only offered with the chords on and the tempo with the metronome. Each card is as wide as the narrowest column
 * its list would lay it out in ([minSongColumnWidth]), so that it narrows as it is told to say less, as the lists do.
 */
@Composable
internal fun AtAGlanceStep(
    modifier: Modifier = Modifier,
    features: OnboardingFeatures,
    songCardDetails: UserPreferences.CardDetails,
    setlistCardDetails: UserPreferences.CardDetails,
    notation: UserPreferences.Notation?,
    isCompact: Boolean,
    onSongCardDetailsChanged: (UserPreferences.CardDetails) -> Unit,
    onSetlistCardDetailsChanged: (UserPreferences.CardDetails) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_info),
    title = stringResource(Res.string.onboarding_at_a_glance_title),
    message = stringResource(Res.string.onboarding_at_a_glance_message),
    isCompact = isCompact,
) {
    val key = remember(notation) { ChordProNotation.shownName(PREVIEW_SONG.key.orEmpty(), (notation ?: UserPreferences.Notation.STANDARD).toChordNotation()) }
    CardDetailsPreview(
        title = stringResource(Res.string.settings_song_cards),
        details = songCardDetails,
        features = features,
        shownKey = key,
        isInSetlist = false,
        onDetailsChanged = onSongCardDetailsChanged,
    )
    AnimatedVisibility(
        visible = features.areSetlistsEnabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        CardDetailsPreview(
            title = stringResource(Res.string.settings_setlist_cards),
            details = setlistCardDetails,
            features = features,
            shownKey = key,
            isInSetlist = true,
            onDetailsChanged = onSetlistCardDetailsChanged,
        )
    }
}

/** One list's card and the chips under it. */
@Composable
private fun CardDetailsPreview(
    title: String,
    details: UserPreferences.CardDetails,
    features: OnboardingFeatures,
    shownKey: String,
    isInSetlist: Boolean,
    onDetailsChanged: (UserPreferences.CardDetails) -> Unit,
) = SettingsSubsection(
    title = title,
    shouldApplyPadding = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val cardWidth by animateDpAsState(
            minSongColumnWidth(
                details = details,
                areChordsEnabled = features.areChordsEnabled,
                isMetronomeEnabled = features.isMetronomeEnabled,
                isInSetlist = isInSetlist,
            ),
        )
        // A picture of a card rather than one to press, so it takes no taps and says nothing to a screen reader: the
        // chips under it are what can be changed.
        SongListItem(
            modifier = Modifier.widthIn(max = cardWidth).clearAndSetSemantics { },
            song = PREVIEW_SONG,
            index = if (isInSetlist) 1 else null,
            key = shownKey.takeIf { features.areChordsEnabled && details.isKeyShown },
            tempo = PREVIEW_SONG.tempo.takeIf { features.isMetronomeEnabled && details.isTempoShown },
            shouldShowChords = features.areChordsEnabled,
            duration = PREVIEW_SONG.duration.takeIf { details.isDurationShown },
            labelsOnEverySong = LabelsOnEverySong(),
            onClick = {},
        )
        CardDetailsChoice(
            details = details,
            areChordsEnabled = features.areChordsEnabled,
            isMetronomeEnabled = features.isMetronomeEnabled,
            isUnavailableHidden = true,
            onDetailsChanged = onDetailsChanged,
        )
    }
}

/**
 * The card the chips are shown on: one of the demo songs, as it is filed in the library. Sample content rather than
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
    duration = 270.seconds,
)
