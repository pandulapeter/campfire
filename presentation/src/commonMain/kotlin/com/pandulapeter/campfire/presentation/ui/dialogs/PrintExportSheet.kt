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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.*
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.print.*
import com.pandulapeter.campfire.presentation.ui.theme.LocalMonospaceFontFamily
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

@Composable
internal fun PrintExportSheet(viewModel: CampfireViewModel, dialog: CampfireViewModel.DialogType.PrintExport) {
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val initialSettings = preferences?.printSettings ?: return
    var settings by remember(dialog) { mutableStateOf(initialSettings) }
    var source by remember(dialog) { mutableStateOf<PrintSource?>(null) }
    var failed by remember(dialog) { mutableStateOf(false) }
    var attempt by remember(dialog) { mutableIntStateOf(0) }
    var selected by remember(dialog) { mutableStateOf<Set<Int>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val filePicker = LocalFilePicker.current
    val fontResolver = LocalFontFamilyResolver.current
    val fontFamily = LocalMonospaceFontFamily.current
    fun newRenderer() = PrintRenderer(TextMeasurer(fontResolver, Density(1f), LayoutDirection.Ltr, cacheSize = 256), fontFamily)
    val renderer = remember(fontResolver, fontFamily) { newRenderer() }
    val labels = PrintLabels(stringResource(Res.string.print_key), stringResource(Res.string.print_capo),
        stringResource(Res.string.print_tempo), stringResource(Res.string.print_time), stringResource(Res.string.print_missing), stringResource(Res.string.print_verse),
        stringResource(Res.string.print_chorus), stringResource(Res.string.print_bridge))
    LaunchedEffect(dialog, attempt) {
        failed = false
        try {
            source = viewModel.preparePrintSource(dialog)
            if (selected == null) selected = source!!.songs.indices.toSet()
        } catch (exception: CancellationException) { throw exception
        } catch (exception: Exception) { failed = true }
    }
    val chosenSource = remember(source, selected) { source?.let { it.copy(songs = it.songs.filterIndexed { index, _ -> index in selected.orEmpty() }) } }
    var layoutFailed by remember { mutableStateOf(false) }
    val document by produceState<PrintDocument?>(null, chosenSource, settings, labels, renderer) {
        value = null
        layoutFailed = false
        val input = chosenSource
        if (input != null && input.songs.isNotEmpty()) try {
            value = withContext(Dispatchers.Default) {
                val measurements = newRenderer()
                layoutPrintDocument(input, settings, labels) { text, size, bold ->
                    coroutineContext.ensureActive()
                    measurements.width(text, size, bold)
                }
            }
        } catch (exception: CancellationException) { throw exception
        } catch (exception: Exception) { layoutFailed = true }
    }
    val update: (PrintSettings) -> Unit = {
        val normalized = it.normalized()
        if (normalized != settings) { settings = normalized; viewModel.setPrintSettings(normalized) }
    }
    CampfireBottomSheet(
        title = stringResource(Res.string.print_export),
        subtitle = dialog.setlist?.title ?: dialog.song?.title.orEmpty(),
        sheetMaxWidth = 1100.dp,
        onDismiss = { viewModel.dismissSheet(dialog) },
        actions = {
            TextButton(enabled = document?.pages?.isNotEmpty() == true && !exporting, onClick = {
                val snapshot = document ?: return@TextButton
                exporting = true
                val title = source!!.title
                viewModel.exportPdf(filePicker, title, onFinished = { exporting = false }) { newRenderer().pdf(snapshot, title) }
            }) {
                if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(Res.string.print_save))
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 300.dp).padding(padding)) {
            if (failed || layoutFailed) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.print_load_failed))
                    TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
                }
            } else if (source == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val options: @Composable (Modifier) -> Unit = { modifier ->
                    PrintOptions(modifier, source!!, settings, selected.orEmpty(), onSelected = { selected = it }, onSettings = update)
                }
                val preview: @Composable (Modifier) -> Unit = { modifier ->
                    PrintPreview(modifier, document, renderer, selected.orEmpty().isEmpty())
                }
                if (maxWidth >= 760.dp) {
                    Row(Modifier.fillMaxSize()) {
                        options(Modifier.width(330.dp).fillMaxHeight())
                        preview(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    var showOptions by rememberSaveable { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !showOptions, onClick = { showOptions = false }, label = { Text(stringResource(Res.string.print_preview)) })
                            FilterChip(selected = showOptions, onClick = { showOptions = true }, label = { Text(stringResource(Res.string.print_options)) })
                        }
                        Crossfade(showOptions, modifier = Modifier.weight(1f)) { optionsVisible ->
                            if (optionsVisible) options(Modifier.fillMaxSize()) else preview(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrintOptions(modifier: Modifier, source: PrintSource, settings: PrintSettings, selected: Set<Int>, onSelected: (Set<Int>) -> Unit, onSettings: (PrintSettings) -> Unit) {
    val state = rememberLazyListState()
    LazyColumn(modifier.fadingVerticalEdges(state), state = state, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item {
            Text(stringResource(Res.string.print_paper), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrintSettings.Paper.entries.forEach { paper ->
                    FilterChip(settings.paper == paper, { onSettings(settings.copy(paper = paper)) }, label = {
                        Text(stringResource(if (paper == PrintSettings.Paper.A4) Res.string.print_a4 else Res.string.print_letter))
                    })
                }
            }
            CheckboxListItem(title = stringResource(Res.string.print_landscape), isChecked = settings.isLandscape, onCheckedChange = { onSettings(settings.copy(isLandscape = it)) })
            PrintSlider(stringResource(Res.string.print_font_size, settings.fontSize), settings.fontSize, 8..20) { onSettings(settings.copy(fontSize = it)) }
            PrintSlider(stringResource(Res.string.print_margin, settings.marginMm), settings.marginMm, 10..25) { onSettings(settings.copy(marginMm = it)) }
            Text(stringResource(Res.string.print_columns), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..2).forEach { count -> FilterChip(settings.columns == count, { onSettings(settings.copy(columns = count)) }, label = { Text(count.toString()) }) }
            }
            CheckboxListItem(title = stringResource(Res.string.print_chords), isChecked = settings.showChords, onCheckedChange = { onSettings(settings.copy(showChords = it)) })
            CheckboxListItem(title = stringResource(Res.string.print_comments), isChecked = settings.showComments, onCheckedChange = { onSettings(settings.copy(showComments = it)) })
            CheckboxListItem(title = stringResource(Res.string.print_metadata), isChecked = settings.showMetadata, onCheckedChange = { onSettings(settings.copy(showMetadata = it)) })
            CheckboxListItem(title = stringResource(Res.string.print_page_numbers), isChecked = settings.showPageNumbers, onCheckedChange = { onSettings(settings.copy(showPageNumbers = it)) })
            Text(stringResource(Res.string.print_key_hint), style = MaterialTheme.typography.bodySmall)
        }
        if (source.isSetlist) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(Res.string.print_setlist_content), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrintSettings.SetlistMode.entries.forEach { mode ->
                        FilterChip(settings.setlistMode == mode, { onSettings(settings.copy(setlistMode = mode)) }, label = {
                            Text(stringResource(if (mode == PrintSettings.SetlistMode.SONG_SHEETS) Res.string.print_song_sheets else Res.string.print_running_order))
                        })
                    }
                }
                if (settings.setlistMode == PrintSettings.SetlistMode.SONG_SHEETS) {
                    CheckboxListItem(title = stringResource(Res.string.print_overview), isChecked = settings.includeSetlistOverview, onCheckedChange = { onSettings(settings.copy(includeSetlistOverview = it)) })
                    CheckboxListItem(title = stringResource(Res.string.print_new_page), isChecked = settings.startSongsOnNewPage, onCheckedChange = { onSettings(settings.copy(startSongsOnNewPage = it)) })
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.print_songs), style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(onClick = { onSelected(source.songs.indices.toSet()) }) { Text(stringResource(Res.string.print_select_all)) }
                    TextButton(onClick = { onSelected(emptySet()) }) { Text(stringResource(Res.string.print_select_none)) }
                }
            }
            itemsIndexed(source.songs) { index, entry ->
                CheckboxListItem(title = "${entry.index}. ${entry.title}", description = if (entry.song == null) stringResource(Res.string.print_missing) else entry.artist,
                    isChecked = index in selected, onCheckedChange = { onSelected(if (it) selected + index else selected - index) })
            }
        } else if (source.songs.any { it.song == null }) {
            item { Text(stringResource(Res.string.print_missing), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun PrintSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Slider(value.toFloat(), onValueChange = { onChange(it.roundToInt()) }, valueRange = range.first.toFloat()..range.last.toFloat(), steps = range.last - range.first - 1,
        modifier = Modifier.semantics { contentDescription = label })
}

@Composable
private fun PrintPreview(modifier: Modifier, document: PrintDocument?, renderer: PrintRenderer, isEmpty: Boolean) {
    var requestedPage by rememberSaveable { mutableIntStateOf(0) }
    val pageCount = document?.pages?.size ?: 0
    val pageIndex = requestedPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (document == null || document.pages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (isEmpty) Text(stringResource(Res.string.print_no_songs)) else CircularProgressIndicator()
            }
        } else {
            val pageLabel = stringResource(Res.string.print_page, pageIndex + 1, pageCount)
            val page = document.pages[pageIndex]
            val description = pageLabel + "\n" + page.texts.joinToString("\n") { it.text }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val pageWidth = minOf(maxWidth, maxHeight * (document.width / document.height))
                Canvas(Modifier.width(pageWidth).aspectRatio(document.width / document.height).border(1.dp, MaterialTheme.colorScheme.outlineVariant).semantics { contentDescription = description }) {
                    renderer.draw(this, page, size.width / document.width)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = pageIndex > 0, onClick = { requestedPage = pageIndex - 1 }) { Text(stringResource(Res.string.print_previous)) }
                Text(pageLabel, style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = pageIndex + 1 < pageCount, onClick = { requestedPage = pageIndex + 1 }) { Text(stringResource(Res.string.print_next)) }
            }
        }
    }
}
