/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.welcome_get_started
import com.pandulapeter.campfire.presentation.resources.welcome_message
import com.pandulapeter.campfire.presentation.resources.welcome_open_settings
import com.pandulapeter.campfire.presentation.resources.welcome_settings_hint
import com.pandulapeter.campfire.presentation.resources.welcome_settings_hint_sync
import com.pandulapeter.campfire.presentation.resources.welcome_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.THEME_COLOR_CHOICE_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.ThemeColorChoice
import com.pandulapeter.campfire.presentation.ui.components.UiModeChoice
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsSubsection

/**
 * The first run's one screen of its own: a line about what the app is, the two choices that decide how all of it
 * looks, and where everything else is. It is kept to exactly that on purpose - the library behind it already holds
 * the demo songs, which say more about the app than a tour could, and a first run that opened on a series of pages
 * would stand between somebody and the songbook they came for. The colors are the settings screen's own controls,
 * and the dialog is drawn over the app in the app's theme, so every tap on them shows its answer on the songs behind.
 *
 * Sync is the one setting it names, and only where this build has any: it is the only thing in the app a new user
 * cannot find by using it, since it stays off and silent until somebody goes to Settings to connect it.
 */
@Composable
internal fun WelcomeDialog(
    viewModel: CampfireViewModel,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        var isSmallScreen by remember { mutableStateOf(maxWidth < 500.dp || maxHeight < 500.dp) }
        LaunchedEffect(maxWidth, maxHeight) {
            isSmallScreen = maxWidth < 500.dp || maxHeight < 500.dp
        }
        if (isSmallScreen) {
            CampfireBottomSheet(
                title = stringResource(Res.string.welcome_title),
                // Every color in one row, which is as wide as the sheet is ever worth being: the other rows are a line
                // of text and a choice of three.
                sheetMaxWidth = THEME_COLOR_CHOICE_WIDTH,
                onDismiss = { viewModel.dismissSheet(CampfireViewModel.DialogType.Welcome) },
            ) { contentPadding ->
                WelcomeContent(
                    viewModel = viewModel,
                    contentPadding = contentPadding,
                    onGetStarted = { close() },
                    onOpenSettings = {
                        viewModel.openSettingsFromWelcome()
                        close()
                    },
                )
            }
        } else {
            AlertDialog(
                modifier = Modifier.widthIn(max = 366.dp),
                onDismissRequest = viewModel::dismissDialog,
                title = { Text(stringResource(Res.string.welcome_title)) },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier.fadingVerticalEdges(scrollState).bounceVerticalScroll(scrollState),
                    ) {
                        Text(
                            text = stringResource(Res.string.welcome_message),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsSubsection(
                            shouldApplyPadding = false,
                        ) {
                            UiModeChoice(
                                shouldApplyPadding = false,
                                selected = userPreferences?.uiMode,
                                onSelected = viewModel::setUiMode,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsSubsection(
                            shouldApplyPadding = false,
                        ) {
                            ThemeColorChoice(
                                shouldApplyPadding = false,
                                uiMode = userPreferences?.uiMode,
                                selected = userPreferences?.themeColor,
                                onSelected = viewModel::setThemeColor,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(if (viewModel.syncProviders.isEmpty()) Res.string.welcome_settings_hint else Res.string.welcome_settings_hint_sync),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissDialog,
                    ) { Text(stringResource(Res.string.welcome_get_started)) }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            viewModel.openSettingsFromWelcome()
                            viewModel.dismissDialog()
                        },
                    ) { Text(stringResource(Res.string.welcome_open_settings)) }
                },
            )
        }
    }
}

/**
 * The first run's one screen of its own: a line about what the app is, the two choices that decide how all of it
 * looks, and where everything else is. It is kept to exactly that on purpose - the library behind it already holds
 * the demo songs, which say more about the app than a tour could, and a first run that opened on a series of pages
 * would stand between somebody and the songbook they came for. The colors are the settings screen's own controls,
 * and the sheet is drawn over the app in the app's theme, so every tap on them shows its answer on the songs behind.
 *
 * Sync is the one setting it names, and only where this build has any: it is the only thing in the app a new user
 * cannot find by using it, since it stays off and silent until somebody goes to Settings to connect it.
 */
@Composable
private fun ColumnScope.WelcomeContent(
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues,
    onGetStarted: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.weight(1f, fill = false).fadingTopEdge(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding),
    ) {
        val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(Res.string.welcome_message),
            style = MaterialTheme.typography.bodyLarge,
        )
        SettingsSubsection(
            shouldApplyPadding = false,
        ) {
            UiModeChoice(
                selected = userPreferences?.uiMode,
                onSelected = viewModel::setUiMode,
            )
        }
        Spacer(
            modifier = Modifier.height(8.dp),
        )
        SettingsSubsection(
            shouldApplyPadding = false,
        ) {
            ThemeColorChoice(
                uiMode = userPreferences?.uiMode,
                selected = userPreferences?.themeColor,
                onSelected = viewModel::setThemeColor,
            )
        }
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(if (viewModel.syncProviders.isEmpty()) Res.string.welcome_settings_hint else Res.string.welcome_settings_hint_sync),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(Res.string.welcome_open_settings)) }
            Button(onClick = onGetStarted) { Text(stringResource(Res.string.welcome_get_started)) }
        }
    }
}
