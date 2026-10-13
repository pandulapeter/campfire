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
import com.pandulapeter.campfire.presentation.resources.settings_notation_german_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard_description
import org.jetbrains.compose.resources.painterResource

/**
 * "How do you write chords?", the notations of the settings screen's list as cards with the same example under each name,
 * which is what tells them apart to somebody who has only ever read one, under a few lines of a song that are rewritten
 * in each one as it is picked. Only asked with the chords on: it is how they are shown, and the files are always written
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
        Triple(UserPreferences.Notation.STANDARD, Res.string.settings_notation_standard, Res.string.settings_notation_standard_description),
        Triple(UserPreferences.Notation.GERMAN, Res.string.settings_notation_german, Res.string.settings_notation_german_description),
        Triple(UserPreferences.Notation.LATIN, Res.string.settings_notation_latin, Res.string.settings_notation_latin_description),
        Triple(UserPreferences.Notation.NASHVILLE, Res.string.settings_notation_nashville, Res.string.settings_notation_nashville_description),
        Triple(UserPreferences.Notation.ROMAN, Res.string.settings_notation_roman, Res.string.settings_notation_roman_description),
    )
    OnboardingChoiceGrid(
        modifier = Modifier.selectableGroup(),
        columns = 1,
        count = notations.size,
    ) { index, itemModifier ->
        val (notation, title, description) = notations[index]
        OnboardingChoiceCard(
            modifier = itemModifier,
            title = stringResource(title),
            description = stringResource(description),
            isSelected = notation == selected,
            role = Role.RadioButton,
            onClick = { onSelected(notation) },
        )
    }
}
