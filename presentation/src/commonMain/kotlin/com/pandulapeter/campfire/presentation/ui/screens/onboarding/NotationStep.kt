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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_notation_message
import com.pandulapeter.campfire.presentation.resources.onboarding_notation_title
import com.pandulapeter.campfire.presentation.ui.chords.label

/**
 * "How do you write chords?", the notations of the settings screen's list as cards named and nothing more, under a few
 * lines of a song that are rewritten in each one as it is picked, which is what tells them apart to somebody who has
 * only ever read one: the examples and the explanations are left to Settings. Only asked with the chords on: it is how
 * they are shown, and the files are always written in the standard notation whatever is picked. The song is spelled
 * with the [accidentals] Settings has, as every song page is, which the onboarding does not ask about.
 */
@Composable
internal fun NotationStep(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    selected: UserPreferences.Notation?,
    accidentals: UserPreferences.Accidentals?,
    isCompact: Boolean,
    onSelected: (UserPreferences.Notation) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    markModifier = markModifier,
    title = stringResource(Res.string.onboarding_notation_title),
    message = stringResource(Res.string.onboarding_notation_message),
    isCompact = isCompact,
) {
    OnboardingSongPreview(
        notation = selected,
        accidentals = accidentals,
    )
    val notations = UserPreferences.Notation.entries.map { it to it.label }
    // Two columns where they fit, since five cards with a name each would otherwise make the longest page of the
    // onboarding out of the shortest answers.
    BoxWithConstraints {
        OnboardingChoiceGrid(
            modifier = Modifier.selectableGroup(),
            columns = if (maxWidth >= NOTATION_TWO_COLUMN_WIDTH) 2 else 1,
            count = notations.size,
        ) { index, itemModifier ->
            val (notation, title) = notations[index]
            OnboardingChoiceCard(
                modifier = itemModifier,
                title = stringResource(title),
                isSelected = notation == selected,
                role = Role.RadioButton,
                onClick = { onSelected(notation) },
            )
        }
    }
}

/** The least width the notations are laid out in two columns at: "Nashville numbers" on one line in each. */
private val NOTATION_TWO_COLUMN_WIDTH = 480.dp
