/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_with_own_name

/**
 * The languages of the interface as a list rather than a segmented control, since it is the one choice that grows with
 * every translation, and a row of segments runs out of width after the third. Every language the app is not set to also
 * carries its name in itself, read out of its own string table, so that somebody who ended up in one they cannot read
 * can still find theirs; the one it is set to is already named in itself, and saying so twice reads as a mistake.
 */
@Composable
internal fun LanguageChoice(
    modifier: Modifier = Modifier,
    selected: UserPreferences.Language?,
    onSelected: (UserPreferences.Language) -> Unit,
) = Column(modifier = modifier.selectableGroup()) {
    listOf(
        UserPreferences.Language.SYSTEM_DEFAULT,
        UserPreferences.Language.ENGLISH,
        UserPreferences.Language.HUNGARIAN,
    ).forEach { language ->
        val isSelected = selected == language
        val localName = appLanguageName(language)
        val ownName = if (language == UserPreferences.Language.SYSTEM_DEFAULT) {
            localName
        } else {
            LocalizedStrings.get(language.nameResource, locale = AppLocale.findByCode(language.id))
        }
        RadioListItem(
            title = if (isSelected || ownName == localName) {
                localName
            } else {
                stringResource(Res.string.settings_user_interface_language_with_own_name, localName, ownName)
            },
            isSelected = isSelected,
            onSelected = { onSelected(language) },
        )
    }
}
