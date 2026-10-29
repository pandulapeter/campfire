/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.about

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_intro
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_show
import com.pandulapeter.campfire.presentation.resources.settings_open_source_licenses
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.appBuild
import org.jetbrains.compose.resources.painterResource

/**
 * Settings → About's Open-source licenses: Campfire's own license, then everything it is built with
 * ([openSourceComponents]) that this build ships ([appBuild]), each naming who holds it and its license; the README lists
 * every build's. Selecting an entry puts its own sheet over the list ([OpenSourceLicenseSheet]), so the list stays a
 * list and going back finds it where it was left.
 *
 * The texts are read as the list opens and handed to the entry's sheet with it, so that the sheet opens whole on the
 * frame it is selected rather than growing as its texts arrive. A file this build does not carry (the screenshot tool
 * draws the desktop build as the other platforms) leaves the entry with its links alone.
 */
@Composable
internal fun OpenSourceLicensesSheet(
    viewModel: CampfireViewModel,
) = CampfireBottomSheet(
    title = stringResource(Res.string.settings_open_source_licenses),
    onDismiss = { viewModel.dialogHost.dismissSheet(DialogType.OpenSourceLicenses) },
) { contentPadding ->
    val components = remember { openSourceComponents.filter { appBuild in it.builds } }
    val texts by produceState<Map<String, List<String>>?>(null) { value = readLicenseTexts(components) }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .fadingTopEdge(listState, sheetContainerColor()),
        state = listState,
        contentPadding = contentPadding,
    ) {
        item(key = "intro") {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(Res.string.open_source_licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(components, key = { it.name }) { component ->
            OpenSourceComponentItem(
                component = component,
                onSelected = {
                    viewModel.dialogHost.showDialog(
                        DialogType.OpenSourceLicense(componentName = component.name, texts = texts?.let { component.licenseTexts(it) }),
                    )
                },
            )
        }
    }
}

/** One entry of the list: its name, holder, and license, opening its own sheet. */
@Composable
private fun OpenSourceComponentItem(
    modifier: Modifier = Modifier,
    component: OpenSourceComponent,
    onSelected: () -> Unit,
) = ListItem(
    modifier = modifier.clickable(
        onClickLabel = stringResource(Res.string.open_source_licenses_show),
        onClick = onSelected,
    ),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(component.name) },
    supportingContent = { Text("${component.holder}\n${component.licenseName}") },
    trailingContent = {
        Icon(
            painter = painterResource(Res.drawable.ic_next),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    },
)
