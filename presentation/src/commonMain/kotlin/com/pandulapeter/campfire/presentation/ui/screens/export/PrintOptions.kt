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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.pandulapeter.campfire.presentation.resources.print_comments
import com.pandulapeter.campfire.presentation.resources.print_font_size
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
import com.pandulapeter.campfire.presentation.resources.print_tempo
import com.pandulapeter.campfire.presentation.resources.settings_chord_diagrams
import com.pandulapeter.campfire.presentation.resources.settings_scale_suggestions
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_decrease
import com.pandulapeter.campfire.presentation.resources.song_details_text_size_increase
import com.pandulapeter.campfire.presentation.ui.components.checklist.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.controls.LabeledControlRow
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.components.controls.Stepper
import org.jetbrains.compose.resources.painterResource

/**
 * The options, in one list that scrolls on its own: the format and what it is good for, then the preview where it is
 * stacked with them ([header]), then for a PDF the paper, the text and what is printed, and for several songs how their
 * sheets follow one another. Every song goes out, a setlist whole. The library's own files are the songs as the library
 * holds them, so the PDF's options fold away under the format while it is chosen; a link offers [linkOptions] instead.
 * [onSettings] takes every change, normalized and saved by the screen.
 */
@Composable
internal fun PrintOptions(
    modifier: Modifier,
    source: PrintSource,
    settings: PrintSettings,
    formats: List<PrintSettings.Format>,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    areScaleSuggestionsEnabled: Boolean,
    bottomPadding: Dp,
    header: (@Composable () -> Unit)? = null,
    onSettings: (PrintSettings) -> Unit,
    linkOptions: @Composable () -> Unit,
) {
    val state = rememberLazyListState()
    // Every row here is moved only by the options above it folding out or away, which it follows as they fold, so its
    // animation is the fade alone.
    val isPdf = settings.format == PrintSettings.Format.PDF
    val isLink = settings.format == PrintSettings.Format.LINK
    LazyColumn(modifier.bounceScrollableContent(state).fadingTopEdge(state, MaterialTheme.colorScheme.background), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        // The format comes before the preview it decides, so that what the preview shows follows from what is chosen
        // above it, and the choice is the first thing on the screen.
        item(key = "format") {
            FormatChoice(
                modifier = listItemAnimation(state, placementSpec = null),
                format = settings.format,
                formats = formats,
                kind = source.kind,
                onSelected = { onSettings(settings.copy(format = it)) },
            )
        }
        if (header != null) {
            item(key = "preview") { header() }
        }
        if (isLink) {
            item(key = "link") { Column(listItemAnimation(state, placementSpec = null)) { linkOptions() } }
        }
        item(key = "pdf") {
            PdfOption(modifier = listItemAnimation(state, placementSpec = null), isPdf = isPdf) {
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
                // Only where a song has a scale to suggest, which none has with the suggestions switched off in the app;
                // disabled with the chords, as the diagrams are, since the scales are read off them.
                if (areChordsEnabled && areScaleSuggestionsEnabled && source.songs.any { it.scaleGroups.isNotEmpty() }) {
                    CheckboxListItem(
                        title = stringResource(Res.string.settings_scale_suggestions),
                        isChecked = settings.showScales,
                        isEnabled = settings.showChords,
                        onCheckedChange = { onSettings(settings.copy(showScales = it)) },
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
                    title = stringResource(if (source.hasRunningOrder) Res.string.print_metadata_setlist else Res.string.print_metadata),
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
        if (source.hasSeveralSongs) {
            item(key = "songSheets") {
                PdfOption(modifier = listItemAnimation(state, placementSpec = null), isPdf = isPdf) {
                    // Several songs that are not a setlist have no running order to print before their sheets.
                    if (source.hasRunningOrder) {
                        CheckboxListItem(
                            title = stringResource(Res.string.print_overview),
                            isChecked = settings.includeSetlistOverview,
                            onCheckedChange = { onSettings(settings.copy(includeSetlistOverview = it)) },
                        )
                    }
                    CheckboxListItem(
                        title = stringResource(Res.string.print_new_page),
                        isChecked = settings.startSongsOnNewPage,
                        onCheckedChange = { onSettings(settings.copy(startSongsOnNewPage = it)) },
                    )
                }
            }
        } else if (source.songs.any { it.song == null }) {
            item(key = "missing") {
                Text(
                    text = stringResource(Res.string.print_missing),
                    modifier = listItemAnimation(state, placementSpec = null).padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** One group of the options that only a PDF has, folding out under the format while it is chosen and away again. */
@Composable
private fun PdfOption(
    modifier: Modifier,
    isPdf: Boolean,
    content: @Composable () -> Unit,
) = AnimatedVisibility(isPdf, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
    Column { content() }
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
