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
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.BottomSheetContentScope
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_license
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_project
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily

/**
 * One entry of the Open-source licenses list, put over it ([DialogType.OpenSourceLicense]): who holds it, the ways to
 * its project page and to its license, its NOTICE where it has one, and the license's full text, read from the app's own
 * files rather than fetched. Closing it uncovers the list where it was left.
 *
 * The texts are items of the list rather than one block: Google's list of what its Play libraries bundle runs to
 * thousands of lines, which one text would lay out whole before the sheet could open.
 */
@Composable
internal fun OpenSourceLicenseSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.OpenSourceLicense,
    urlOpener: (String) -> Unit,
) {
    val component = remember(dialog.componentName) { openSourceComponents.first { it.name == dialog.componentName } }
    CampfireBottomSheet(
        title = component.name,
        subtitle = component.licenseName,
        titleMaxLines = 2,
        onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
    ) { contentPadding ->
        OpenSourceLicenseContent(
            component = component,
            preloadedTexts = dialog.texts,
            contentPadding = contentPadding,
            urlOpener = urlOpener,
        )
    }
}

/** The sheet's list: the links, then the texts in pieces. */
@Composable
private fun BottomSheetContentScope.OpenSourceLicenseContent(
    component: OpenSourceComponent,
    preloadedTexts: List<String>?,
    contentPadding: PaddingValues,
    urlOpener: (String) -> Unit,
) {
    // Read here only when the entry was selected before the list had read them, in which case they fade in.
    val texts by produceState(preloadedTexts, component) {
        if (value == null) value = component.licenseTexts(readLicenseTexts(listOf(component)))
    }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .fadingTopEdge(listState, sheetContainerColor()),
        state = listState,
        contentPadding = contentPadding,
    ) {
        item(key = "links") {
            OpenSourceComponentLinks(
                component = component,
                urlOpener = urlOpener,
            )
        }
        itemsIndexed(texts.orEmpty(), key = { index, _ -> index }) { _, chunk ->
            LicenseText(modifier = listItemAnimation(listState), text = chunk)
        }
    }
}

/** Who holds the entry, then the ways to its project page and to its license page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenSourceComponentLinks(
    modifier: Modifier = Modifier,
    component: OpenSourceComponent,
    urlOpener: (String) -> Unit,
) = FlowRow(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        text = component.holder,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = { urlOpener(component.url) }) {
        Text(stringResource(Res.string.open_source_licenses_project))
    }
    TextButton(onClick = { urlOpener(component.licenseUrl) }) {
        Text(stringResource(Res.string.open_source_licenses_license))
    }
}

/** A piece of a NOTICE or a license, as wide as the sheet, in the monospace face the texts are laid out for. */
@Composable
private fun LicenseText(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    text = text,
    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonospaceFontFamily.current),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
