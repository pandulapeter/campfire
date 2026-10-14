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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.presentation.resources.onboarding_language
import com.pandulapeter.campfire.presentation.resources.onboarding_settings_note
import com.pandulapeter.campfire.presentation.resources.onboarding_welcome_message
import com.pandulapeter.campfire.presentation.resources.onboarding_welcome_title
import com.pandulapeter.campfire.presentation.ui.components.appLanguageName
import org.jetbrains.compose.resources.painterResource

/**
 * The welcome: under the greeting the language, behind a button that opens the list in a sheet, so that somebody
 * greeted in the wrong one can change it before reading on; then the note that every answer of the onboarding can be
 * changed in Settings, said once here for every page, so that none of the later ones has to say it again. The look is
 * the next page's ([LookStep]), so that this one is read rather than worked through.
 */
@Composable
internal fun WelcomeStep(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    language: UserPreferences.Language?,
    isCompact: Boolean,
    onLanguageClicked: () -> Unit,
) = OnboardingPage(
    modifier = modifier,
    markModifier = markModifier,
    title = stringResource(Res.string.onboarding_welcome_title),
    message = stringResource(Res.string.onboarding_welcome_message),
    isCompact = isCompact,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        OutlinedButton(
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            onClick = onLanguageClicked,
        ) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                painter = painterResource(Res.drawable.ic_language),
                contentDescription = null,
            )
            Text(
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                text = stringResource(Res.string.onboarding_language, appLanguageName(language ?: UserPreferences.Language.SYSTEM_DEFAULT)),
            )
        }
    }
    SettingsNote(modifier = Modifier.padding(top = 8.dp))
}

/**
 * The one place the onboarding says that nothing it asks is final, on a tonal card with the settings mark, so that it is
 * read as a promise about the whole of what follows rather than as a line about this page. It closes the page, after
 * the language, as wide as its text and centered under the greeting, wrapping (still centered) where a phone is
 * narrower than the sentence.
 */
@Composable
private fun SettingsNote(
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_settings),
                contentDescription = null,
            )
            Text(
                modifier = Modifier.weight(1f, fill = false),
                text = stringResource(Res.string.onboarding_settings_note),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
