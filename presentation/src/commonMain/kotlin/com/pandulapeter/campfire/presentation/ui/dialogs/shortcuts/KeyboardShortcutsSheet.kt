/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.shortcuts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.isolatedLeftToRight
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.keyboard_shortcuts
import com.pandulapeter.campfire.presentation.ui.chrome.label
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyNames
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.ShortcutRow
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.commandScrollDescription
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.commandScrollLabel
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.description
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.keyNames
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.label
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.shortcutGroups
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.title
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.screenOf
import com.pandulapeter.campfire.presentation.ui.platform.appBuild
import com.pandulapeter.campfire.presentation.ui.platform.isAppleKeyboard
import com.pandulapeter.campfire.presentation.ui.platform.isCtrlWheelZoomOwnedByApp
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/**
 * Every keyboard shortcut that works on this platform ([shortcutGroups]), grouped by where it is heard, the keys no
 * button names among them (the song's arrows, the print preview's pages, Escape, Ctrl / Cmd + the scroll wheel). A
 * number names the screen it opens of those switched on, and a number that opens none is left out. Opened with
 * Ctrl / Cmd + slash or `?` from anywhere, and from Settings → About where there is a keyboard.
 */
@Composable
internal fun KeyboardShortcutsSheet(
    preferencesController: PreferencesController,
    onDismiss: () -> Unit,
) = CampfireBottomSheet(
    title = stringResource(Res.string.keyboard_shortcuts),
    onDismiss = onDismiss,
) { contentPadding ->
    val destinations by preferencesController.topLevelDestinations.collectAsStateWithLifecycle()
    val features = shortcutFeatures(preferencesController)
    val groups = remember(features) {
        shortcutGroups(build = appBuild, features = features, isApple = isAppleKeyboard, isPreviewWheelZoomed = isCtrlWheelZoomOwnedByApp)
    }
    val names = keyNames()
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .fadingTopEdge(listState, sheetContainerColor()),
        state = listState,
        contentPadding = contentPadding,
    ) {
        groups.forEach { group ->
            item(key = group.scope.name) { SettingsSectionTitle(text = stringResource(group.scope.title)) }
            items(group.rows, key = { "${group.scope.name}-${it.lead.name}" }) { row ->
                ShortcutRowItem(row = row, destinations = destinations, names = names)
            }
            if (group.isCommandScrollListed) {
                item(key = "${group.scope.name}-scroll") {
                    ShortcutItem(
                        description = stringResource(group.scope.commandScrollDescription),
                        keys = listOf(names.commandScrollLabel(isApple = isAppleKeyboard)),
                    )
                }
            }
        }
    }
}

/** A row of the table, or nothing for a number that opens no screen of those switched on. */
@Composable
private fun ShortcutRowItem(
    row: ShortcutRow,
    destinations: List<CampfireDestination.TopLevel>,
    names: KeyNames,
) {
    val description = row.lead.description ?: return
    ShortcutItem(
        description = if (row.lead in KeyShortcut.screens) {
            textResource(description, stringResource((screenOf(row.lead, destinations) ?: return).label))
        } else {
            stringResource(description)
        },
        keys = row.shortcuts.map { it.chord.label(isApple = isAppleKeyboard, names = names) },
    )
}

/** What a shortcut does, and the keys that do it, each on a pill of its own and left to right in every language. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutItem(
    modifier: Modifier = Modifier,
    description: String,
    keys: List<String>,
) = Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = LIST_ITEM_KEYLINE, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = Modifier.weight(1f),
        text = description,
        style = MaterialTheme.typography.bodyLarge,
    )
    FlowRow(
        modifier = Modifier.padding(start = 16.dp).widthIn(max = KEYS_MAX_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { key ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    text = key.isolatedLeftToRight(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val KEYS_MAX_WIDTH = 200.dp
