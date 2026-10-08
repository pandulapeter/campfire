/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.ic_text_decrease
import com.pandulapeter.campfire.presentation.resources.ic_text_increase
import com.pandulapeter.campfire.presentation.resources.print_a4
import com.pandulapeter.campfire.presentation.resources.print_chords
import com.pandulapeter.campfire.presentation.resources.print_columns
import com.pandulapeter.campfire.presentation.resources.print_comments
import com.pandulapeter.campfire.presentation.resources.print_font_size
import com.pandulapeter.campfire.presentation.resources.print_format
import com.pandulapeter.campfire.presentation.resources.print_format_chordpro
import com.pandulapeter.campfire.presentation.resources.print_format_chordpro_song_description
import com.pandulapeter.campfire.presentation.resources.print_format_pdf
import com.pandulapeter.campfire.presentation.resources.print_format_pdf_description
import com.pandulapeter.campfire.presentation.resources.print_format_zip
import com.pandulapeter.campfire.presentation.resources.print_format_zip_description
import com.pandulapeter.campfire.presentation.resources.print_landscape
import com.pandulapeter.campfire.presentation.resources.print_letter
import com.pandulapeter.campfire.presentation.resources.print_margin
import com.pandulapeter.campfire.presentation.resources.print_margin_decrease
import com.pandulapeter.campfire.presentation.resources.print_margin_increase
import com.pandulapeter.campfire.presentation.resources.print_margins
import com.pandulapeter.campfire.presentation.resources.print_key
import com.pandulapeter.campfire.presentation.resources.print_metadata
import com.pandulapeter.campfire.presentation.resources.print_metadata_setlist
import com.pandulapeter.campfire.presentation.resources.print_missing
import com.pandulapeter.campfire.presentation.resources.print_new_page
import com.pandulapeter.campfire.presentation.resources.print_overview
import com.pandulapeter.campfire.presentation.resources.print_page_numbers
import com.pandulapeter.campfire.presentation.resources.print_paper
import com.pandulapeter.campfire.presentation.resources.print_portrait
import com.pandulapeter.campfire.presentation.resources.print_running_order
import com.pandulapeter.campfire.presentation.resources.print_select_all
import com.pandulapeter.campfire.presentation.resources.print_setlist_content
import com.pandulapeter.campfire.presentation.resources.print_song_sheets
import com.pandulapeter.campfire.presentation.resources.print_songs
import com.pandulapeter.campfire.presentation.resources.print_tempo
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import org.jetbrains.compose.resources.painterResource

/**
 * The options, in one list that scrolls on its own: the format and what it is good for, then for a PDF the paper, the
 * text and what is printed, and for a setlist what it exports, then for a setlist in either format which of its songs.
 * The library's own files are the songs as the library holds them, so the PDF's options fold away under the format while
 * it is chosen. [onSettings] takes every change, normalized and saved by the screen.
 */
@Composable
internal fun PrintOptions(
    modifier: Modifier,
    source: PrintSource,
    settings: PrintSettings,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    selected: Set<Int>,
    bottomPadding: Dp,
    header: (@Composable () -> Unit)? = null,
    onSelected: (Set<Int>) -> Unit,
    onSettings: (PrintSettings) -> Unit,
) {
    val state = rememberLazyListState()
    val isPdf = settings.format == PrintSettings.Format.PDF
    LazyColumn(modifier.bounceScrollableContent(state).fadingTopEdge(state, MaterialTheme.colorScheme.background), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        if (header != null) {
            item(key = "preview") { header() }
        }
        item {
            FormatChoice(
                format = settings.format,
                isSetlist = source.isSetlist,
                onSelected = { onSettings(settings.copy(format = it)) },
            )
        }
        item {
            PdfOption(isPdf) {
                SettingsSectionTitle(text = stringResource(Res.string.print_paper))
                SegmentedChoice(
                    options = PrintSettings.Paper.entries.map { paper ->
                        paper to stringResource(if (paper == PrintSettings.Paper.A4) Res.string.print_a4 else Res.string.print_letter)
                    },
                    selected = settings.paper,
                    onSelected = { onSettings(settings.copy(paper = it)) },
                )
                Spacer(Modifier.height(8.dp))
                SegmentedChoice(
                    options = listOf(false to stringResource(Res.string.print_portrait), true to stringResource(Res.string.print_landscape)),
                    selected = settings.isLandscape,
                    onSelected = { onSettings(settings.copy(isLandscape = it)) },
                )
                Spacer(Modifier.height(8.dp))
                PrintStepperRow(stringResource(Res.string.song_details_text_size)) {
                    Stepper(
                        value = stringResource(Res.string.print_font_size, settings.fontSize),
                        isDefault = true,
                        decreaseIcon = painterResource(Res.drawable.ic_text_decrease),
                        decreaseLabel = stringResource(Res.string.song_details_text_size_decrease),
                        canDecrease = settings.fontSize > MIN_FONT_SIZE,
                        onDecrease = { onSettings(settings.copy(fontSize = settings.fontSize - 1)) },
                        increaseIcon = painterResource(Res.drawable.ic_text_increase),
                        increaseLabel = stringResource(Res.string.song_details_text_size_increase),
                        canIncrease = settings.fontSize < MAX_FONT_SIZE,
                        onIncrease = { onSettings(settings.copy(fontSize = settings.fontSize + 1)) },
                        resetLabel = null,
                        onReset = null,
                    )
                }
                PrintStepperRow(stringResource(Res.string.print_margins)) {
                    Stepper(
                        value = stringResource(Res.string.print_margin, settings.marginMm),
                        isDefault = true,
                        decreaseIcon = painterResource(Res.drawable.ic_subtract),
                        decreaseLabel = stringResource(Res.string.print_margin_decrease),
                        canDecrease = settings.marginMm > MIN_MARGIN_MM,
                        // To the next multiple of the step either way, so that a stored value off the steps joins them
                        // rather than keeping its offset (12 goes to 10 or 15, never to 7 or 17); normalized() clamps it.
                        onDecrease = { onSettings(settings.copy(marginMm = (settings.marginMm - 1) / MARGIN_STEP_MM * MARGIN_STEP_MM)) },
                        increaseIcon = painterResource(Res.drawable.ic_add),
                        increaseLabel = stringResource(Res.string.print_margin_increase),
                        canIncrease = settings.marginMm < MAX_MARGIN_MM,
                        onIncrease = { onSettings(settings.copy(marginMm = (settings.marginMm / MARGIN_STEP_MM + 1) * MARGIN_STEP_MM)) },
                        resetLabel = null,
                        onReset = null,
                    )
                }
                SettingsSectionTitle(text = stringResource(Res.string.print_columns))
                SegmentedChoice(
                    options = (1..PrintSettings.MAX_COLUMNS).map { it to it.toString() },
                    selected = settings.columns,
                    onSelected = { onSettings(settings.copy(columns = it)) },
                )
                Spacer(Modifier.height(8.dp))
                // Each of what is printed is offered only while its feature is on in Settings, so that somebody who switched
                // off the chords or the metronome is not asked about them here either.
                if (areChordsEnabled) {
                    CheckboxListItem(
                        title = stringResource(Res.string.print_chords),
                        isChecked = settings.showChords,
                        onCheckedChange = { onSettings(settings.copy(showChords = it)) },
                    )
                }
                // Only where a song has a chord to draw, which none has with the diagrams switched off in the app; and like
                // the chord spelling in Settings, disabled rather than hidden with the chords, which it fingers.
                if (areChordsEnabled && source.songs.any { it.chords.isNotEmpty() }) {
                    CheckboxListItem(
                        title = stringResource(Res.string.settings_chord_diagrams),
                        isChecked = settings.showChordDiagrams,
                        isEnabled = settings.showChords,
                        onCheckedChange = { onSettings(settings.copy(showChordDiagrams = it)) },
                    )
                }
                if (areChordsEnabled) {
                    CheckboxListItem(
                        title = stringResource(Res.string.print_key),
                        isChecked = settings.showKey,
                        onCheckedChange = { onSettings(settings.copy(showKey = it)) },
                    )
                }
                if (isMetronomeEnabled) {
                    CheckboxListItem(
                        title = stringResource(Res.string.print_tempo),
                        isChecked = settings.showTempo,
                        onCheckedChange = { onSettings(settings.copy(showTempo = it)) },
                    )
                }
                CheckboxListItem(
                    title = stringResource(Res.string.print_comments),
                    isChecked = settings.showComments,
                    onCheckedChange = { onSettings(settings.copy(showComments = it)) },
                )
                CheckboxListItem(
                    title = stringResource(if (source.isSetlist) Res.string.print_metadata_setlist else Res.string.print_metadata),
                    isChecked = settings.showMetadata,
                    onCheckedChange = { onSettings(settings.copy(showMetadata = it)) },
                )
                CheckboxListItem(
                    title = stringResource(Res.string.print_page_numbers),
                    isChecked = settings.showPageNumbers,
                    onCheckedChange = { onSettings(settings.copy(showPageNumbers = it)) },
                )
            }
        }
        if (source.isSetlist) {
            item {
                PdfOption(isPdf) {
                    SettingsSectionTitle(text = stringResource(Res.string.print_setlist_content))
                    SegmentedChoice(
                        options = PrintSettings.SetlistMode.entries.map { mode ->
                            mode to stringResource(
                                if (mode == PrintSettings.SetlistMode.SONG_SHEETS) {
                                    Res.string.print_song_sheets
                                } else {
                                    Res.string.print_running_order
                                },
                            )
                        },
                        selected = settings.setlistMode,
                        onSelected = { onSettings(settings.copy(setlistMode = it)) },
                    )
                    if (settings.setlistMode == PrintSettings.SetlistMode.SONG_SHEETS) {
                        Spacer(Modifier.height(8.dp))
                        CheckboxListItem(
                            title = stringResource(Res.string.print_overview),
                            isChecked = settings.includeSetlistOverview,
                            onCheckedChange = { onSettings(settings.copy(includeSetlistOverview = it)) },
                        )
                        CheckboxListItem(
                            title = stringResource(Res.string.print_new_page),
                            isChecked = settings.startSongsOnNewPage,
                            onCheckedChange = { onSettings(settings.copy(startSongsOnNewPage = it)) },
                        )
                    }
                }
            }
            // Which songs go out is a question for either format, and one answer to both: the same ticks narrow the PDF and
            // the zip, so switching between the two does not quietly put back the songs that were left out.
            if (source.songs.isNotEmpty()) {
                item {
                    SettingsSectionTitle(text = stringResource(Res.string.print_songs))
                    SelectAllListItem(
                        state = when (selected.size) {
                            0 -> ToggleableState.Off
                            source.songs.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        },
                        onClick = { onSelected(if (selected.size == source.songs.size) emptySet() else source.songs.indices.toSet()) },
                    )
                }
            }
            itemsIndexed(source.songs) { index, entry ->
                CheckboxListItem(
                    title = "${entry.index}. ${entry.title}",
                    description = if (entry.song == null) stringResource(Res.string.print_missing) else entry.artist,
                    isChecked = index in selected,
                    onCheckedChange = { onSelected(if (it) selected + index else selected - index) },
                )
            }
        } else if (source.songs.any { it.song == null }) {
            item {
                Text(
                    text = stringResource(Res.string.print_missing),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The first of the options, PDF or the library's own files - ChordPro for a song, a zip for a setlist - and a line under
 * it saying what the chosen one is for and, for the zip, what is in it, since the two are told apart by what the person
 * receiving the file can do with it rather than by anything the names say.
 */
@Composable
private fun FormatChoice(
    format: PrintSettings.Format,
    isSetlist: Boolean,
    onSelected: (PrintSettings.Format) -> Unit,
) = Column {
    SettingsSectionTitle(
        text = stringResource(Res.string.print_format),
        contentPadding = PaddingValues(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 8.dp, bottom = 8.dp),
    )
    SegmentedChoice(
        options = PrintSettings.Format.entries.map { option ->
            option to stringResource(
                when {
                    option == PrintSettings.Format.PDF -> Res.string.print_format_pdf
                    isSetlist -> Res.string.print_format_zip
                    else -> Res.string.print_format_chordpro
                },
            )
        },
        selected = format,
        onSelected = onSelected,
    )
    val description = when {
        format == PrintSettings.Format.PDF -> stringResource(Res.string.print_format_pdf_description)
        isSetlist -> stringResource(Res.string.print_format_zip_description)
        else -> stringResource(Res.string.print_format_chordpro_song_description)
    }
    AnimatedContent(description, transitionSpec = { fadeIn() togetherWith fadeOut() }) { shown ->
        Text(
            text = shown,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One group of the options that only a PDF has, folding away while the library's own files are the format. */
@Composable
private fun PdfOption(
    isPdf: Boolean,
    content: @Composable () -> Unit,
) = AnimatedVisibility(isPdf, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
    Column { content() }
}

/**
 * One row standing for every song under it, its checkbox ticked when all of them are, empty when none is and a dash in
 * between, and a tap ticking them all or, when they all are, none: a row like the ones it acts on rather than a pair of
 * text buttons, which in the heading's color and size read as a second heading.
 */
@Composable
private fun SelectAllListItem(
    state: ToggleableState,
    onClick: () -> Unit,
) = ListItem(
    modifier = Modifier.triStateToggleable(state = state, role = Role.Checkbox, onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(stringResource(Res.string.print_select_all)) },
    leadingContent = { TriStateCheckbox(state = state, onClick = null) },
)

/** A label and a stepper on one line, built like the overflow menus' `MenuStepperRow`, at the screen's own keyline. */
@Composable
private fun PrintStepperRow(
    label: String,
    stepper: @Composable () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f).padding(end = 16.dp), style = MaterialTheme.typography.bodyLarge)
    stepper()
}

private const val MIN_FONT_SIZE = 8

private const val MAX_FONT_SIZE = 20

private const val MIN_MARGIN_MM = 10

private const val MAX_MARGIN_MM = 25

private const val MARGIN_STEP_MM = 5
