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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_metronome_body
import com.pandulapeter.campfire.presentation.resources.ic_metronome_pendulum
import com.pandulapeter.campfire.presentation.resources.ic_scales
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.onboarding_chord_diagram_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_chord_diagrams_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_chords_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_metronome_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_scale_suggestions_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_scales_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_setlists_description
import com.pandulapeter.campfire.presentation.resources.onboarding_feature_tuner_description
import com.pandulapeter.campfire.presentation.resources.onboarding_features_message
import com.pandulapeter.campfire.presentation.resources.onboarding_features_title
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_chords
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.chords.ChordsIcon
import org.jetbrains.compose.resources.painterResource

/**
 * "What do you need?", the features a newcomer decides on as cards any of which can be kept, each the Settings → Features
 * switch it stands for and in that list's order, so they start as the app is set (every one on, on a first run). It asks
 * about the features rather than about the person, since a role would only be a guess at them: a guitarist sings too,
 * and anyone may play from a list. The chords card says that leaving it off is how a singer reads lyrics only, the one
 * hint at a role the page gives. The notation is only asked about the chords left on here.
 *
 * The chord diagrams are a card of their own under the chords, only while the chords are on and only for an instrument
 * they are drawn for ([chordTuning] not null): the instrument has been asked already, so the card ends in a G drawn for
 * it in its tuning, which shows what the feature will add to every song. The scale suggestions are a card under the
 * scales the same way, only while the scales are on.
 */
@Composable
internal fun FeaturesStep(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    features: OnboardingFeatures,
    chordTuning: ChordTuning?,
    isCompact: Boolean,
    onChordsEnabledChange: (Boolean) -> Unit,
    onChordDiagramsEnabledChange: (Boolean) -> Unit,
    onMetronomeEnabledChange: (Boolean) -> Unit,
    onTunerEnabledChange: (Boolean) -> Unit,
    onScalesEnabledChange: (Boolean) -> Unit,
    onScaleSuggestionsEnabledChange: (Boolean) -> Unit,
    onSetlistsEnabledChange: (Boolean) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    markModifier = markModifier,
    title = stringResource(Res.string.onboarding_features_title),
    message = stringResource(Res.string.onboarding_features_message),
    isCompact = isCompact,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        FeatureCard(
            title = stringResource(Res.string.settings_chords),
            description = stringResource(Res.string.onboarding_feature_chords_description),
            icon = { Icon(painter = painterResource(Res.drawable.ic_songs), contentDescription = null) },
            isEnabled = features.areChordsEnabled,
            onEnabledChange = onChordsEnabledChange,
        )
        DependentFeatureCard(target = chordTuning.takeIf { features.areChordsEnabled }) { tuning ->
            FeatureCard(
                modifier = Modifier.padding(top = FEATURE_CARD_GAP),
                title = stringResource(Res.string.settings_chord_diagrams),
                description = stringResource(Res.string.onboarding_feature_chord_diagrams_description),
                icon = { ChordsIcon(isKeyboard = tuning.instrument == ChordInstrument.KEYBOARD) },
                preview = { ChordDiagramPreview(tuning = tuning) },
                isEnabled = features.areChordDiagramsEnabled,
                onEnabledChange = onChordDiagramsEnabledChange,
            )
        }
        FeatureCard(
            modifier = Modifier.padding(top = FEATURE_CARD_GAP),
            title = stringResource(Res.string.scales),
            description = stringResource(Res.string.onboarding_feature_scales_description),
            icon = { Icon(painter = painterResource(Res.drawable.ic_scales), contentDescription = null) },
            isEnabled = features.areScalesEnabled,
            onEnabledChange = onScalesEnabledChange,
        )
        DependentFeatureCard(target = Unit.takeIf { features.areScalesEnabled }) {
            FeatureCard(
                modifier = Modifier.padding(top = FEATURE_CARD_GAP),
                title = stringResource(Res.string.settings_scale_suggestions),
                description = stringResource(Res.string.onboarding_feature_scale_suggestions_description),
                icon = { Icon(painter = painterResource(Res.drawable.ic_songs), contentDescription = null) },
                isEnabled = features.areScaleSuggestionsEnabled,
                onEnabledChange = onScaleSuggestionsEnabledChange,
            )
        }
        FeatureCard(
            modifier = Modifier.padding(top = FEATURE_CARD_GAP),
            title = stringResource(Res.string.metronome),
            description = stringResource(Res.string.onboarding_feature_metronome_description),
            // The metronome's mark is drawn in two parts, so that the navigation item can swing the pendulum alone.
            icon = {
                Box {
                    Icon(painter = painterResource(Res.drawable.ic_metronome_body), contentDescription = null)
                    Icon(painter = painterResource(Res.drawable.ic_metronome_pendulum), contentDescription = null)
                }
            },
            isEnabled = features.isMetronomeEnabled,
            onEnabledChange = onMetronomeEnabledChange,
        )
        FeatureCard(
            modifier = Modifier.padding(top = FEATURE_CARD_GAP),
            title = stringResource(Res.string.tuner),
            description = stringResource(Res.string.onboarding_feature_tuner_description),
            icon = { Icon(painter = painterResource(Res.drawable.ic_tuner), contentDescription = null) },
            isEnabled = features.isTunerEnabled,
            onEnabledChange = onTunerEnabledChange,
        )
        FeatureCard(
            modifier = Modifier.padding(top = FEATURE_CARD_GAP),
            title = stringResource(Res.string.setlists),
            description = stringResource(Res.string.onboarding_feature_setlists_description),
            icon = { Icon(painter = painterResource(Res.drawable.ic_setlists), contentDescription = null) },
            isEnabled = features.areSetlistsEnabled,
            onEnabledChange = onSetlistsEnabledChange,
        )
    }
}

/**
 * A feature's card that only stands while the feature it is part of is on ([target] not null), as the chord diagrams
 * under the chords and the scale suggestions under the scales. Keyed on whether there is a card, so that the card
 * leaving still draws what it had. The empty state is as wide as the card, so that only the height changes: the card
 * collapses and fades rather than also shrinking sideways, and the gap above it collapses with it, so that the cards
 * under it close up evenly.
 */
@Composable
private fun <T : Any> DependentFeatureCard(
    target: T?,
    card: @Composable (T) -> Unit,
) = AnimatedContent(
    targetState = target,
    contentKey = { it != null },
    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = true) },
    contentAlignment = Alignment.TopCenter,
) { shown ->
    if (shown == null) Spacer(modifier = Modifier.fillMaxWidth()) else card(shown)
}

/** One feature's card. */
@Composable
private fun FeatureCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    preview: (@Composable () -> Unit)? = null,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) = OnboardingChoiceCard(
    modifier = modifier.fillMaxWidth(),
    title = title,
    description = description,
    icon = icon,
    preview = preview,
    isSelected = isEnabled,
    role = Role.Checkbox,
    onClick = { onEnabledChange(!isEnabled) },
)

/** A G, as the song page draws it at the top of a song in [tuning], or nothing for a tuning it has no shape in. */
@Composable
private fun ChordDiagramPreview(
    tuning: ChordTuning,
) {
    val geometry = rememberChordGeometry(name = PREVIEW_CHORD, tuning = tuning) ?: return
    ChordDiagram(
        modifier = if (tuning.instrument.isFretted) Modifier.size(width = 36.dp, height = 48.dp) else Modifier.size(width = 56.dp, height = 26.dp),
        geometry = geometry,
        description = stringResource(Res.string.onboarding_chord_diagram_description, PREVIEW_CHORD),
    )
}

private val FEATURE_CARD_GAP = 8.dp

/** The chord drawn for the instrument picked: one of the first a player learns, with a shape on every instrument. */
private const val PREVIEW_CHORD = "G"
