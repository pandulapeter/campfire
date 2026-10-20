/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.scales

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.chordpro.scales.ScaleChords
import com.pandulapeter.campfire.chordpro.scales.ScaleSpelling
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_scale_degrees
import com.pandulapeter.campfire.presentation.resources.ic_scale_notes
import com.pandulapeter.campfire.presentation.resources.scales_also_called
import com.pandulapeter.campfire.presentation.resources.scales_chords
import com.pandulapeter.campfire.presentation.resources.scales_labels_degrees
import com.pandulapeter.campfire.presentation.resources.scales_labels_notes
import com.pandulapeter.campfire.presentation.resources.scales_labels_show_degrees
import com.pandulapeter.campfire.presentation.resources.scales_labels_show_notes
import com.pandulapeter.campfire.presentation.resources.scales_mode_of
import com.pandulapeter.campfire.presentation.resources.scales_play
import com.pandulapeter.campfire.presentation.resources.scales_relative_minor
import com.pandulapeter.campfire.presentation.resources.scales_sevenths
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.menu.NEW_ITEM_BUTTON_SIZE
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.instrument.storedChordShapes
import com.pandulapeter.campfire.presentation.ui.metronome.PlayStopMark
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.scales.ScaleDiagram
import com.pandulapeter.campfire.presentation.ui.scales.ScaleDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.scales.ScaleLabels
import com.pandulapeter.campfire.presentation.ui.scales.displayedDegree
import com.pandulapeter.campfire.presentation.ui.scales.scaleAliases
import com.pandulapeter.campfire.presentation.ui.scales.scaleDiagramDescription
import com.pandulapeter.campfire.presentation.ui.scales.scaleDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.scales.scaleStepsText
import com.pandulapeter.campfire.presentation.ui.scales.scaleTitle
import com.pandulapeter.campfire.presentation.ui.scales.scaleToneNotes
import com.pandulapeter.campfire.presentation.ui.scales.shownScaleNoteNames
import org.jetbrains.compose.resources.painterResource

/**
 * One scale in one key, titled with both and the notes spelled under the title: the scale on the player's instrument,
 * its notes labelled with their degrees or their names, the formula and the steps, what it is a mode of and its other
 * names, and for a seven-note scale the chords it stacks into, each opening that chord's sheet over this one. Beside the
 * formula, a button plays the scale once up through the tuner's reference tones.
 *
 * The instrument, the tuning and the notation are read from the preferences as it is drawn, never from the screen it was
 * opened over (see [DialogType.Scale]); nothing can change them under the sheet, whose chips are behind it, and if
 * something did it would only be drawn again. An id this version has no scale for closes it.
 */
@Composable
internal fun ScaleSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.Scale,
) {
    val scale = Scales.fromId(dialog.scaleId) ?: return LaunchedEffect(Unit) { viewModel.dialogHost.dismissSheet(dialog) }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    val instrument = userPreferences?.instrument ?: UserPreferences.Instrument.GUITAR
    val strings = userPreferences?.instrumentTuning?.strings
    val rootName = dialog.rootName
    val root = ChordProChords.parse(rootName)?.root ?: 0
    val geometry = remember(scale, root, strings, instrument) { scaleDiagramGeometryOf(scale, root, strings, instrument) }
    val state = viewModel.scaleLibrary
    val playingScaleNote by viewModel.tunerController.playingScaleNote.collectAsStateWithLifecycle()
    // A scale sounds only while its sheet can be seen, as the tuner's tones do (TunerListeningEffect): out of sight the
    // run would play on, and a hidden web tab stretches each of its notes to a second.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.tunerController.stopScale() }
    CampfireBottomSheet(
        title = scaleTitle(scale, rootName, notation),
        subtitle = shownScaleNoteNames(scale, rootName, notation).joinToString(" "),
        titleMaxLines = 2,
        // A keyboard carries no labels, so only a neck has the toggle: the subtitle names a keyboard's notes.
        actions = if (geometry is ScaleDiagramGeometry.Fretboard) {
            {
                ScaleLabelsToggle(
                    labels = state.labels,
                    onLabelsSelected = { state.labels = it },
                )
            }
        } else {
            null
        },
        onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            ScaleSheetDiagram(
                scale = scale,
                rootName = rootName,
                notation = notation,
                geometry = geometry,
                labels = state.labels,
            )
            ScaleFormula(
                scale = scale,
                playingNote = playingScaleNote,
                onTogglePlaying = {
                    // The tones and the click share one audio session on iOS, which the tones reconfigure for the tuner,
                    // so the two never sound together: a scale played over a song's metronome stops the click, as opening
                    // the tuner sheet does.
                    viewModel.metronomeController.stopMetronome()
                    viewModel.tunerController.toggleScale(scaleToneNotes(scale, root))
                },
            )
            ScaleRelations(scale = scale, rootName = rootName, notation = notation)
            val tuning = userPreferences?.chordTuning()
            if (dialog.showsChords && tuning != null && FeatureSwitches.of(userPreferences).isChordLibraryEnabled && scale.intervals.size == 7) {
                val storedShapes = userPreferences?.storedChordShapes(tuning).orEmpty()
                val onChordClicked = { chord: SongChord ->
                    viewModel.dialogHost.showDialog(DialogType.ChordReference(chord, showsScales = false))
                }
                ScaleChordsRow(
                    modifier = Modifier.padding(top = 24.dp),
                    title = stringResource(Res.string.scales_chords),
                    chords = remember(scale, rootName, notation, tuning) { scaleSheetChords(ScaleChords.triads(scale, rootName), notation, tuning) },
                    tuning = tuning,
                    storedShapes = storedShapes,
                    onChordClicked = onChordClicked,
                )
                ScaleChordsRow(
                    modifier = Modifier.padding(top = 16.dp),
                    title = stringResource(Res.string.scales_sevenths),
                    chords = remember(scale, rootName, notation, tuning) { scaleSheetChords(ScaleChords.sevenths(scale, rootName), notation, tuning) },
                    tuning = tuning,
                    storedShapes = storedShapes,
                    onChordClicked = onChordClicked,
                )
            }
        }
    }
}

/**
 * The scale on the instrument, as wide as the sheet and as tall as its strings need, a neck's dots carrying its degrees
 * or its notes, which crossfade into each other as the header's choice changes. A keyboard carries no labels: the
 * subtitle names its notes.
 */
@Composable
private fun ScaleSheetDiagram(
    scale: Scale,
    rootName: String,
    notation: ChordNotation,
    geometry: ScaleDiagramGeometry,
    labels: ScaleLabels,
) {
    val description = scaleDiagramDescription(scale, rootName, notation)
    val noteNames = remember(scale, rootName, notation) { shownScaleNoteNames(scale, rootName, notation) }
    val degrees = remember(scale) { scale.degrees.map(::displayedDegree) }
    Crossfade(
        targetState = labels,
        label = "scaleSheetLabels",
    ) { shownLabels ->
        ScaleDiagram(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(geometry.sheetHeight),
            geometry = geometry,
            labels = when {
                geometry is ScaleDiagramGeometry.Keyboard -> null
                shownLabels == ScaleLabels.DEGREES -> degrees
                else -> noteNames
            },
            description = description,
        )
    }
}

/**
 * The header's toggle between a neck's degrees and its notes, one icon button rather than a segmented row, which took
 * most of a phone's header from the title. It shows the mode the neck is in now, as the app's other toggles show their
 * state, and tells a screen reader both: the tap's result as its description and the mode shown now as its state.
 */
@Composable
private fun ScaleLabelsToggle(
    labels: ScaleLabels,
    onLabelsSelected: (ScaleLabels) -> Unit,
) {
    val shownLabel = stringResource(if (labels == ScaleLabels.DEGREES) Res.string.scales_labels_degrees else Res.string.scales_labels_notes)
    IconButton(
        modifier = Modifier.semantics { stateDescription = shownLabel },
        onClick = { onLabelsSelected(if (labels == ScaleLabels.DEGREES) ScaleLabels.NOTES else ScaleLabels.DEGREES) },
    ) {
        Crossfade(
            targetState = labels,
            label = "scaleLabelsToggle",
        ) { shownLabels ->
            if (shownLabels == ScaleLabels.DEGREES) {
                Icon(
                    painter = painterResource(Res.drawable.ic_scale_degrees),
                    contentDescription = stringResource(Res.string.scales_labels_show_notes),
                )
            } else {
                Icon(
                    painter = painterResource(Res.drawable.ic_scale_notes),
                    contentDescription = stringResource(Res.string.scales_labels_show_degrees),
                )
            }
        }
    }
}

/**
 * The degrees of the scale with their accidental signs, and the steps between them, centered on the sheet with the
 * button that plays the scale at their end: an empty space as wide as the button at their start keeps them centered.
 * While the scale plays, a circle stands behind the degree sounding ([playingNote] indexes the notes played, whose last
 * is the root an octave up, so it comes back to the first degree).
 */
@Composable
private fun ScaleFormula(
    scale: Scale,
    playingNote: Int?,
    onTogglePlaying: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Spacer(modifier = Modifier.size(NEW_ITEM_BUTTON_SIZE))
    Column(
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
    ) {
        ScaleDegrees(
            scale = scale,
            playingDegree = playingNote?.rem(scale.degrees.size),
        )
        Text(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            text = scaleStepsText(scale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    ScalePlayButton(
        isPlaying = playingNote != null,
        onTogglePlaying = onTogglePlaying,
    )
}

/** The Tuner tab's reference tone toggle, here playing the scale once up and turning back to Play when it ends. */
@Composable
private fun ScalePlayButton(
    isPlaying: Boolean,
    onTogglePlaying: () -> Unit,
) {
    val description = stringResource(Res.string.scales_play)
    FilledIconToggleButton(
        modifier = Modifier.size(NEW_ITEM_BUTTON_SIZE),
        checked = isPlaying,
        onCheckedChange = { onTogglePlaying() },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        PlayStopMark(
            modifier = Modifier.semantics { contentDescription = description },
            isPlaying = isPlaying,
            size = 20.dp,
            color = LocalContentColor.current,
        )
    }
}

/**
 * How the scale relates to others, each line only where it says something: the scale it is a mode of, in the key that
 * makes it this one, the relative minor of a major scale, and the other names it goes by.
 */
@Composable
private fun ScaleRelations(
    scale: Scale,
    rootName: String,
    notation: ChordNotation,
) {
    val parent = scale.modeOf?.let { (parentId, degree) -> Scales.fromId(parentId)?.let { it to degree } }
    val parentRootName = ScaleSpelling.parentRootName(scale, rootName)
    if (parent != null && parentRootName != null) {
        RelationLine(stringResource(Res.string.scales_mode_of, parent.second.toString(), scaleTitle(parent.first, parentRootName, notation)))
    }
    if (scale.id == MAJOR_ID) {
        Scales.fromId(NATURAL_MINOR_ID)?.let { minor ->
            val relativeRoot = ScaleSpelling.noteNames(scale, rootName)[RELATIVE_MINOR_DEGREE]
            RelationLine(stringResource(Res.string.scales_relative_minor, scaleTitle(minor, relativeRoot, notation)))
        }
    }
    scaleAliases(scale)?.let { aliases ->
        RelationLine(stringResource(Res.string.scales_also_called, aliases))
    }
}

@Composable
private fun RelationLine(text: String) = Text(
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp),
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/**
 * How tall the sheet draws a scale: a neck by its strings, each row about as tall as a fret of a phone's sheet is wide,
 * so that a dot fills its fret and its label is legible, plus the band of fret numbers under them; a keyboard as tall
 * as it reads well at the sheet's width.
 */
private val ScaleDiagramGeometry.sheetHeight: Dp
    get() = when (this) {
        is ScaleDiagramGeometry.Fretboard -> (16 + 22 * strings).dp
        is ScaleDiagramGeometry.Keyboard -> 96.dp
    }

private const val MAJOR_ID = "major"
private const val NATURAL_MINOR_ID = "natural_minor"
private const val RELATIVE_MINOR_DEGREE = 5
