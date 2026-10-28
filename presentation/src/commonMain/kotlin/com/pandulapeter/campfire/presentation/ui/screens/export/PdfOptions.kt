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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.ic_text_decrease
import com.pandulapeter.campfire.presentation.resources.ic_text_increase
import com.pandulapeter.campfire.presentation.resources.print_a4
import com.pandulapeter.campfire.presentation.resources.print_chords
import com.pandulapeter.campfire.presentation.resources.print_columns
import com.pandulapeter.campfire.presentation.resources.print_columns_decrease
import com.pandulapeter.campfire.presentation.resources.print_columns_increase
import com.pandulapeter.campfire.presentation.resources.print_comments
import com.pandulapeter.campfire.presentation.resources.print_font_size
import com.pandulapeter.campfire.presentation.resources.print_key
import com.pandulapeter.campfire.presentation.resources.print_landscape
import com.pandulapeter.campfire.presentation.resources.print_letter
import com.pandulapeter.campfire.presentation.resources.print_margin
import com.pandulapeter.campfire.presentation.resources.print_margin_decrease
import com.pandulapeter.campfire.presentation.resources.print_margin_increase
import com.pandulapeter.campfire.presentation.resources.print_margins
import com.pandulapeter.campfire.presentation.resources.print_metadata
import com.pandulapeter.campfire.presentation.resources.print_new_page
import com.pandulapeter.campfire.presentation.resources.print_overview
import com.pandulapeter.campfire.presentation.resources.print_overview_details
import com.pandulapeter.campfire.presentation.resources.print_page_numbers
import com.pandulapeter.campfire.presentation.resources.print_portrait
import com.pandulapeter.campfire.presentation.resources.print_running_order
import com.pandulapeter.campfire.presentation.resources.print_section_page
import com.pandulapeter.campfire.presentation.resources.print_song_sheet
import com.pandulapeter.campfire.presentation.resources.print_song_sheets
import com.pandulapeter.campfire.presentation.resources.print_song_sheets_include
import com.pandulapeter.campfire.presentation.resources.print_sheet_music
import com.pandulapeter.campfire.presentation.resources.print_tabs
import com.pandulapeter.campfire.presentation.resources.print_tempo
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.ui.components.checklist.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.controls.LabeledControlRow
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.controls.Stepper
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSectionTitle
import org.jetbrains.compose.resources.painterResource

/**
 * A PDF's options in the sections of the document they shape, each under one heading: the page every sheet is printed
 * on, then a setlist's running order, then the song sheets. Within a section the options follow the order the PDF prints
 * what they choose, and each section has its own columns where it has any, so that nothing chosen for one is found among
 * the options of another. Each of what is printed is offered only while its feature is on in Settings, so that somebody
 * who switched off the chords or the metronome is not asked about them here either.
 */
@Composable
internal fun PdfOptions(
    source: PrintSource,
    settings: PrintSettings,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    areScaleSuggestionsEnabled: Boolean,
    onSettings: (PrintSettings) -> Unit,
) {
    PageOptions(settings = settings, onSettings = onSettings)
    // Several songs that are not a setlist have no running order to print before their sheets.
    if (source.hasRunningOrder) RunningOrderOptions(settings = settings, onSettings = onSettings)
    SongSheetOptions(
        source = source,
        settings = settings,
        areChordsEnabled = areChordsEnabled,
        isMetronomeEnabled = isMetronomeEnabled,
        areScaleSuggestionsEnabled = areScaleSuggestionsEnabled,
        onSettings = onSettings,
    )
}

/** The page every sheet is printed on: the paper and its orientation, the text size and the margins, and its numbers. */
@Composable
private fun PageOptions(
    settings: PrintSettings,
    onSettings: (PrintSettings) -> Unit,
) {
    SettingsSectionTitle(text = stringResource(Res.string.print_section_page))
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
            // To the next multiple of the step either way, so that a stored value off the steps joins them rather than
            // keeping its offset (12 goes to 10 or 15, never to 7 or 17); normalized() clamps it.
            onDecrease = { onSettings(settings.copy(marginMm = (settings.marginMm - 1) / MARGIN_STEP_MM * MARGIN_STEP_MM)) },
            increaseIcon = painterResource(Res.drawable.ic_add),
            increaseLabel = stringResource(Res.string.print_margin_increase),
            canIncrease = settings.marginMm < MAX_MARGIN_MM,
            onIncrease = { onSettings(settings.copy(marginMm = (settings.marginMm / MARGIN_STEP_MM + 1) * MARGIN_STEP_MM)) },
            resetLabel = null,
            onReset = null,
        )
    }
    CheckboxListItem(
        title = stringResource(Res.string.print_page_numbers),
        isChecked = settings.showPageNumbers,
        onCheckedChange = { onSettings(settings.copy(showPageNumbers = it)) },
    )
}

/**
 * A setlist's running order page: whether it is printed, and, folding out while it is, its own columns and whether it
 * says the setlist's date and description and the songs' artists, apart from the song sheets' artist. It cannot be left
 * out while the song sheets are, which would print nothing.
 */
@Composable
private fun RunningOrderOptions(
    settings: PrintSettings,
    onSettings: (PrintSettings) -> Unit,
) {
    SettingsSectionTitle(text = stringResource(Res.string.print_running_order))
    CheckboxListItem(
        title = stringResource(Res.string.print_overview),
        isChecked = settings.includeSetlistOverview,
        isEnabled = settings.includeSongSheets,
        onCheckedChange = { onSettings(settings.copy(includeSetlistOverview = it)) },
    )
    FoldingOption(modifier = Modifier, isVisible = settings.includeSetlistOverview) {
        ColumnsStepperRow(columns = settings.overviewColumns) { onSettings(settings.copy(overviewColumns = it)) }
        CheckboxListItem(
            title = stringResource(Res.string.print_overview_details),
            isChecked = settings.showOverviewDetails,
            onCheckedChange = { onSettings(settings.copy(showOverviewDetails = it)) },
        )
    }
}

/**
 * The song sheets: for a setlist whether they are printed at all, then, folding out while they are, their columns, and
 * for several songs whether each starts a page, then what each sheet prints, in the order it prints it: the artist under
 * the title, the key and the tempo, the chord diagrams and the scales, then the song's chords, tablature, sheet music, and comments.
 * Like the running order, they cannot be left out while it is.
 */
@Composable
private fun SongSheetOptions(
    source: PrintSource,
    settings: PrintSettings,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    areScaleSuggestionsEnabled: Boolean,
    onSettings: (PrintSettings) -> Unit,
) {
    SettingsSectionTitle(text = stringResource(if (source.hasSeveralSongs) Res.string.print_song_sheets else Res.string.print_song_sheet))
    if (source.hasRunningOrder) {
        CheckboxListItem(
            title = stringResource(Res.string.print_song_sheets_include),
            isChecked = settings.includeSongSheets,
            isEnabled = settings.includeSetlistOverview,
            onCheckedChange = { onSettings(settings.copy(includeSongSheets = it)) },
        )
    }
    FoldingOption(modifier = Modifier, isVisible = !source.hasRunningOrder || settings.includeSongSheets) {
        ColumnsStepperRow(columns = settings.columns) { onSettings(settings.copy(columns = it)) }
        if (source.hasSeveralSongs) {
            CheckboxListItem(
                title = stringResource(Res.string.print_new_page),
                isChecked = settings.startSongsOnNewPage,
                onCheckedChange = { onSettings(settings.copy(startSongsOnNewPage = it)) },
            )
        }
        CheckboxListItem(
            title = stringResource(Res.string.print_metadata),
            isChecked = settings.showMetadata,
            onCheckedChange = { onSettings(settings.copy(showMetadata = it)) },
        )
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
        // Only where a song has a chord to draw, which none has with the diagrams switched off in the app.
        if (areChordsEnabled && source.songs.any { it.chords.isNotEmpty() }) {
            CheckboxListItem(
                title = stringResource(Res.string.settings_chord_diagrams),
                isChecked = settings.showChordDiagrams,
                onCheckedChange = { onSettings(settings.copy(showChordDiagrams = it)) },
            )
        }
        // Only where a song has a scale to suggest, which none has with the suggestions switched off in the app.
        if (areChordsEnabled && areScaleSuggestionsEnabled && source.songs.any { it.scaleGroups.isNotEmpty() }) {
            CheckboxListItem(
                title = stringResource(Res.string.settings_scale_suggestions),
                isChecked = settings.showScales,
                onCheckedChange = { onSettings(settings.copy(showScales = it)) },
            )
        }
        if (areChordsEnabled) {
            CheckboxListItem(
                title = stringResource(Res.string.print_chords),
                isChecked = settings.showChords,
                onCheckedChange = { onSettings(settings.copy(showChords = it)) },
            )
        }
        // Only where a song has tablature to leave out, like the diagrams and the scales.
        if (areChordsEnabled && source.hasTabs()) {
            CheckboxListItem(
                title = stringResource(Res.string.print_tabs),
                isChecked = settings.showTabs,
                onCheckedChange = { onSettings(settings.copy(showTabs = it)) },
            )
        }
        if (areChordsEnabled && source.hasSheetMusic()) {
            CheckboxListItem(
                title = stringResource(Res.string.print_sheet_music),
                isChecked = settings.showSheetMusic,
                onCheckedChange = { onSettings(settings.copy(showSheetMusic = it)) },
            )
        }
        CheckboxListItem(
            title = stringResource(Res.string.print_comments),
            isChecked = settings.showComments,
            onCheckedChange = { onSettings(settings.copy(showComments = it)) },
        )
    }
}

private fun PrintSource.hasTabs() = hasLine { it is ChordProLine.Tab }

private fun PrintSource.hasSheetMusic() = hasLine { it is ChordProLine.Verbatim }

private fun PrintSource.hasLine(predicate: (ChordProLine) -> Boolean) = songs.any { song ->
    song.song?.blocks.orEmpty().any { block -> block is ChordProBlock.Section && block.lines.any(predicate) }
}

/** A section's columns, as a stepper beside its label like the page's text size and margins. */
@Composable
private fun ColumnsStepperRow(
    columns: Int,
    onColumns: (Int) -> Unit,
) = PrintStepperRow(stringResource(Res.string.print_columns)) {
    Stepper(
        value = columns.toString(),
        isDefault = true,
        decreaseIcon = painterResource(Res.drawable.ic_subtract),
        decreaseLabel = stringResource(Res.string.print_columns_decrease),
        canDecrease = columns > 1,
        onDecrease = { onColumns(columns - 1) },
        increaseIcon = painterResource(Res.drawable.ic_add),
        increaseLabel = stringResource(Res.string.print_columns_increase),
        canIncrease = columns < PrintSettings.MAX_COLUMNS,
        onIncrease = { onColumns(columns + 1) },
        resetLabel = null,
        onReset = null,
    )
}

/**
 * A label and a stepper on one line at the screen's own keyline, the label going above the stepper where a word of it
 * would no longer fit beside it.
 */
@Composable
private fun PrintStepperRow(
    label: String,
    stepper: @Composable () -> Unit,
) = LabeledControlRow(
    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
    label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
    controls = stepper,
)

private const val MIN_FONT_SIZE = 8

private const val MAX_FONT_SIZE = 20

private const val MIN_MARGIN_MM = 10

private const val MAX_MARGIN_MM = 25

private const val MARGIN_STEP_MM = 5
