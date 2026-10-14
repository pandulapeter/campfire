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

import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_hide
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_intro
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_license
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_project
import com.pandulapeter.campfire.presentation.resources.open_source_licenses_show
import com.pandulapeter.campfire.presentation.resources.settings_open_source_licenses
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.platform.appBuild
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

/**
 * Settings → About's Open-source licenses: Campfire's own license, then everything it is built with
 * ([openSourceComponents]) that this build ships ([appBuild]), each naming who holds it and its license; the README lists
 * every build's. A tap opens an entry in place, with the ways to its project page and to its license, its NOTICE where
 * it has one, and the license's full text, read from the app's own files rather than fetched; only one entry is open at
 * a time, so the list stays a list.
 *
 * An open entry's texts are items of the list rather than one block: Google's list of what its Play libraries bundle
 * runs to thousands of lines, which one text would lay out whole before the entry could open. So opening an entry is
 * the list's own item animation, its texts fading in while the entries after it move down, and closing it the reverse.
 */
@Composable
internal fun OpenSourceLicensesSheet(
    viewModel: CampfireViewModel,
    urlOpener: (String) -> Unit,
) = CampfireBottomSheet(
    title = stringResource(Res.string.settings_open_source_licenses),
    onDismiss = { viewModel.dismissSheet(DialogType.OpenSourceLicenses) },
) { contentPadding ->
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val components = remember { openSourceComponents.filter { appBuild in it.builds } }
    // Read as the sheet opens, so that an entry opens whole on the frame it is tapped. A file this build does not carry
    // (the screenshot tool draws the desktop build as the other platforms) leaves the entry with its links alone.
    val texts by produceState(emptyMap<String, List<String>>()) {
        value = withContext(Dispatchers.Default) {
            components.flatMap { listOfNotNull(it.noticeFile, it.licenseFile) }.distinct().mapNotNull { file ->
                runCatching { Res.readBytes(file).decodeToString() }.getOrNull()?.let { file to licenseTextChunks(it) }
            }.toMap()
        }
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
        item(key = "intro") {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(Res.string.open_source_licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        components.forEach { component ->
            val isExpanded = expanded == component.name
            item(key = component.name) {
                OpenSourceComponentHeader(
                    modifier = listItemAnimation(listState),
                    component = component,
                    isExpanded = isExpanded,
                    onToggled = { expanded = if (isExpanded) null else component.name },
                )
            }
            if (isExpanded) {
                item(key = "${component.name}/links") {
                    OpenSourceComponentLinks(
                        modifier = listItemAnimation(listState),
                        component = component,
                        urlOpener = urlOpener,
                    )
                }
                listOfNotNull(component.noticeFile, component.licenseFile).forEach { file ->
                    itemsIndexed(texts[file].orEmpty(), key = { index, _ -> "${component.name}/$file/$index" }) { _, chunk ->
                        LicenseText(modifier = listItemAnimation(listState), text = chunk)
                    }
                }
            }
        }
    }
}

/**
 * [text] cut into pieces of about [linesPerChunk] lines, each ending before a blank line where the text has one in
 * reach, and otherwise at three times that, so that no piece of the list is a whole license to lay out. A piece after
 * the first starts with the blank line it was cut at, so the pieces read as the text did.
 */
internal fun licenseTextChunks(text: String, linesPerChunk: Int = 40): List<String> {
    val chunks = mutableListOf<String>()
    val current = mutableListOf<String>()
    text.trim().lines().forEach { line ->
        if (current.size >= linesPerChunk && line.isBlank() || current.size >= linesPerChunk * 3) {
            chunks += current.joinToString("\n")
            current.clear()
        }
        current += line
    }
    if (current.isNotEmpty()) chunks += current.joinToString("\n")
    return chunks
}

/** One entry of the list: its name, holder, and license, opening into the rest of it. */
@Composable
private fun OpenSourceComponentHeader(
    modifier: Modifier = Modifier,
    component: OpenSourceComponent,
    isExpanded: Boolean,
    onToggled: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
    ListItem(
        modifier = modifier.clickable(
            onClickLabel = stringResource(if (isExpanded) Res.string.open_source_licenses_hide else Res.string.open_source_licenses_show),
            onClick = onToggled,
        ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(component.name) },
        supportingContent = { Text("${component.holder}\n${component.licenseName}") },
        trailingContent = {
            Icon(
                modifier = Modifier.rotate(chevronRotation),
                painter = painterResource(Res.drawable.ic_expand),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** The project page and the license page of an open entry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenSourceComponentLinks(
    modifier: Modifier = Modifier,
    component: OpenSourceComponent,
    urlOpener: (String) -> Unit,
) = FlowRow(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    TextButton(onClick = { urlOpener(component.url) }) {
        Text(stringResource(Res.string.open_source_licenses_project))
    }
    TextButton(onClick = { urlOpener(component.licenseUrl) }) {
        Text(stringResource(Res.string.open_source_licenses_license))
    }
}

/** A piece of a NOTICE or a license, as wide as the list, in the monospace face the texts are laid out for. */
@Composable
private fun LicenseText(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
    text = text,
    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LocalMonospaceFontFamily.current),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
