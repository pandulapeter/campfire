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

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.onboarding_notation_message
import com.pandulapeter.campfire.presentation.resources.onboarding_notation_title
import com.pandulapeter.campfire.presentation.resources.settings_notation_german
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard
import org.jetbrains.compose.resources.painterResource

/**
 * "How do you write chords?", the notations of the settings screen's list as cards named and nothing more, under a few
 * lines of a song that are rewritten in each one as it is picked, which is what tells them apart to somebody who has
 * only ever read one: the examples and the explanations are left to Settings. Only asked with the chords on: it is how they are shown, and the files are always written
 * in the standard notation whatever is picked.
 */
@Composable
internal fun NotationStep(
    modifier: Modifier = Modifier,
    selected: UserPreferences.Notation?,
    isCompact: Boolean,
    onSelected: (UserPreferences.Notation) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_songs),
    title = stringResource(Res.string.onboarding_notation_title),
    message = stringResource(Res.string.onboarding_notation_message),
    isCompact = isCompact,
) {
    OnboardingSongPreview(notation = selected)
    val notations = listOf(
        UserPreferences.Notation.STANDARD to Res.string.settings_notation_standard,
        UserPreferences.Notation.GERMAN to Res.string.settings_notation_german,
        UserPreferences.Notation.LATIN to Res.string.settings_notation_latin,
        UserPreferences.Notation.NASHVILLE to Res.string.settings_notation_nashville,
        UserPreferences.Notation.ROMAN to Res.string.settings_notation_roman,
    )
    OnboardingChoiceGrid(
        modifier = Modifier.selectableGroup(),
        columns = 1,
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
