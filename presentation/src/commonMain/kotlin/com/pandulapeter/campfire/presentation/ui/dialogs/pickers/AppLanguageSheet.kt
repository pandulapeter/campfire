/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.pickers

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.preferences.LanguageChoice
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The languages of the interface, the Settings list in a sheet over the onboarding, so that a newcomer who was greeted
 * in the wrong one can change it before reading a question. A pick is written at once, which turns the sheet and the
 * page under it to the new language as it slides away.
 */
@Composable
internal fun AppLanguageSheet(
    viewModel: CampfireViewModel,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    CampfireBottomSheet(
        title = stringResource(Res.string.settings_user_interface_language),
        onDismiss = { viewModel.dismissSheet(DialogType.AppLanguage) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        LanguageChoice(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding),
            selected = userPreferences?.language,
            onSelected = { language ->
                viewModel.setLanguage(language)
                close()
            },
        )
    }
}
