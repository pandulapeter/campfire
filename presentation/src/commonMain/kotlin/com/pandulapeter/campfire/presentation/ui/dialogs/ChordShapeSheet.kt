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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_moved
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning_hide
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning_show
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_choose
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_next
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_position
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_position_description
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_previous
import com.pandulapeter.campfire.presentation.resources.song_details_chord_tuning
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.chordCellDescription
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.chordTuning
import com.pandulapeter.campfire.presentation.ui.chords.chordTuningLabel
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.components.Stepper
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.playing.effectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.readsCapoAsTransposition
import com.pandulapeter.campfire.presentation.ui.playing.soundingTransposition
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

/**
 * One chord of a song, opened by tapping its diagram in the Chords section: the chord larger than the section draws it,
 * with its fingers and its notes, on a pager through the other ways it can be played. The page it settles on is the
 * player's choice for that chord in that tuning in every song, written at once like the page's own steppers write,
 * so the sheet has nothing to save and its close button cancels nothing; the section behind it follows. It reads the song
 * as the page shows it: transposed (on the keyboard by the capo too, see `withCapoSounding`) and spelled the same way,
 * and finds the chord by the name the page gave it. A tuning other than the standard one is named under the title, so
 * that a shape is never read for the wrong strings, and a chord the song defines for standard tuning, which such a
 * tuning does not draw, says so and offers to show the definition, named as standard tuning while it is shown.
 */
@Composable
internal fun ChordShapeSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.ChordShape,
) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val song = songs.firstOrNull { it.fileName == dialog.song.fileName } ?: dialog.song
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val capos by viewModel.capos.collectAsStateWithLifecycle()
    // The sheet is opened from a song's Chords section, which Other leaves out, so only a preference changed while it was
    // open gets here without a tuning; there are no shapes to offer then.
    val tuning = userPreferences.let { if (it == null) ChordTuning.GUITAR else it.chordTuning() } ?: return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }
    val spelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val notation = spelling.notation.toChordNotation()
    val text = songTexts[song.fileName]
    val playedTransposition = transpositions[song.fileName, dialog.setlistFileName]
    val transposition = if (userPreferences.readsCapoAsTransposition) {
        soundingTransposition(playedTransposition, effectiveCapo(song = song, setlistFileName = dialog.setlistFileName, capos = capos).fret)
    } else {
        playedTransposition
    }
    val chords by produceState<List<SongChord>?>(null, text, transposition, spelling, tuning) {
        value = text?.let { withContext(Dispatchers.Default) { songChordsOf(viewModel.songRenderer.transposedSong(it, transposition, spelling), notation, tuning) } }
    }
    val chord = chords?.firstOrNull { it.name == dialog.chordName }
    // The page names the chord as the song is played there, so a transposition or an edit synced in while the sheet is up
    // may leave nothing by that name: the diagram that was tapped is gone, and with it the sheet.
    if (chords != null && chord == null) return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }
    val storedShapes = userPreferences?.chordVoicings?.get(tuning.voicingKey).orEmpty()
    CampfireBottomSheet(
        title = textResource(Res.string.song_details_chord_shape_choose, dialog.chordName),
        subtitle = songLabel(song),
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (chord == null) {
                // The chords are worked out off the main thread, which is over before the sheet has slid up: the frame
                // of a diagram and the room of the rest hold the sheet at its height meanwhile.
                ChordShapePlaceholder(instrument = tuning.instrument, chordName = dialog.chordName)
            } else {
                // The pager holds its chord's shapes while a new list of chords is worked out under the open sheet, so
                // it is keyed on the chord: its pages are never handed to another one.
                key(chord.chord.id, chord.name) {
                    var showsDefinition by rememberSaveable { mutableStateOf(false) }
                    val definition = chord.undrawnDefinition?.takeIf { showsDefinition }
                    val shownChord = if (definition == null) chord else chord.copy(definition = definition)
                    val shownTuning = if (definition == null) tuning else ChordTuning.standard(tuning.instrument)
                    if (tuning.kind != ChordTuning.Kind.Standard) {
                        ChordTuningLine(text = stringResource(Res.string.song_details_chord_tuning, chordTuningLabel(shownTuning, notation)))
                    }
                    key(showsDefinition) {
                        ChordShapePager(
                            chord = shownChord,
                            tuning = shownTuning,
                            notation = notation,
                            selection = selectShape(shownChord, shownTuning, storedShapes),
                            onShapeSelected = { shape -> viewModel.setChordVoicing(tuning.voicingKey, chord.chord.id, shape?.let(ChordVoicings::write)) },
                        )
                    }
                    if (chord.undrawnDefinition != null) {
                        UndrawnDefinitionNote(
                            isShown = showsDefinition,
                            onToggled = { showsDefinition = !showsDefinition },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The chord's letters where the page counts it, its shapes as pages of one pager, its notes, and where it has more than
 * one shape, a stepper reading which page it is on and turning it, going around like the transposition's. It is never
 * highlighted and has no reset: which shape is the app's first says nothing to a player who only picks the one their
 * hands play. A chord the song defines has one page, and says so in place of the stepper.
 */
@Composable
private fun ChordShapePager(
    chord: SongChord,
    tuning: ChordTuning,
    notation: ChordNotation,
    selection: SelectedShape,
    onShapeSelected: (ChordVoicing?) -> Unit,
) {
    // The search for every shape of a chord is quick, but not quick enough for the frame the sheet opens on.
    val shapes by produceState<List<ChordVoicing>?>(null, chord.chord, tuning, selection.source == SelectedShape.Source.DEFINED) {
        value = if (selection.source == SelectedShape.Source.DEFINED) emptyList() else withContext(Dispatchers.Default) { ChordVoicings.all(chord.chord, tuning) }
    }
    // The pages are decided once the shapes are there and kept while the sheet is up: a stored shape the app does not list
    // is still the player's, and comes first, but paging away from it must not take its page out from under the pager.
    val options = remember(shapes) {
        shapes?.let { all ->
            val current = selection.shape
            when {
                selection.source == SelectedShape.Source.DEFINED || all.isEmpty() -> listOfNotNull(current)
                current == null || all.any { it.sameShapeAs(current) } -> all
                else -> listOf(current) + all
            }
        }
    }
    chord.letterName?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    // A chord the tables have no shape for has no page to draw, only the empty frame its cell has on the page.
    if (options.isNullOrEmpty()) {
        ChordShapePlaceholder(instrument = tuning.instrument, chordName = chord.name)
        return
    }
    val initialPage = options.indexOfFirst { selection.shape != null && it.sameShapeAs(selection.shape) }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { options.size }
    // The app's own first shape is never stored as the player's: settling on it takes the chord's entry out, so that the
    // app's default is what plays there, rather than a copy of it that nothing would ever update.
    val defaultShape = shapes?.firstOrNull()
    val currentOnShapeSelected by rememberUpdatedState(onShapeSelected)
    LaunchedEffect(pagerState, options) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            currentOnShapeSelected(options[page].takeUnless { defaultShape?.sameShapeAs(it) == true })
        }
    }
    HorizontalPager(
        modifier = Modifier.fillMaxWidth(),
        state = pagerState,
        // The next shape is drawn already, so that a swipe shows it coming rather than an empty page.
        beyondViewportPageCount = 1,
    ) { page ->
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ChordShapeDiagram(
                chord = chord,
                tuning = tuning,
                shape = options[page],
            )
        }
    }
    ChordNotes(
        text = (chord.spelling?.let { ChordProChords.spelledNoteNames(it, notation) } ?: ChordProChords.noteNames(chord.chord, notation)).joinToString(" "),
    )
    ChordShapeStepper(
        chordName = chord.name,
        selection = selection,
        pagerState = pagerState,
    )
}

/** The diagram of one page: [shape] of [chord], with the fingers that hold it. */
@Composable
private fun ChordShapeDiagram(
    chord: SongChord,
    tuning: ChordTuning,
    shape: ChordVoicing?,
) = ChordDiagram(
    modifier = Modifier.diagramSize(tuning.instrument),
    geometry = remember(shape, tuning, chord.chord.root) {
        shape?.let { chordDiagramGeometryOf(it, tuning, chord.chord.root) } ?: emptyChordDiagramGeometryOf(tuning.instrument)
    },
    description = chordCellDescription(
        ChordCell(
            name = chord.name,
            letterName = chord.letterName,
            tuning = tuning,
            root = chord.chord.root,
            selection = SelectedShape(shape, SelectedShape.Source.PLAYER),
        ),
    ),
    showsFingers = true,
)

/** The tuning the shapes are drawn for, where it is not the standard one. */
@Composable
private fun ChordTuningLine(text: String) = Text(
    modifier = Modifier.padding(bottom = 4.dp),
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/**
 * That the song defines the chord for standard tuning, which the tuning the sheet is in does not draw, with a button
 * showing the definition in the pager's place, and the tuning's shapes again.
 */
@Composable
private fun UndrawnDefinitionNote(
    isShown: Boolean,
    onToggled: () -> Unit,
) {
    Text(
        modifier = Modifier.padding(top = 12.dp),
        text = stringResource(Res.string.song_details_chord_defined_standard_tuning),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onToggled) {
        Text(
            text = stringResource(
                if (isShown) Res.string.song_details_chord_defined_standard_tuning_hide else Res.string.song_details_chord_defined_standard_tuning_show,
            ),
        )
    }
}

@Composable
private fun ChordNotes(text: String) = Text(
    modifier = Modifier.padding(top = 8.dp),
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/**
 * Which page the pager is on, read as the finger moves it, and the two buttons that turn it, for a mouse, a keyboard and
 * a screen reader; or where the song defines the chord, that it does.
 */
@Composable
private fun ChordShapeStepper(
    chordName: String,
    selection: SelectedShape,
    pagerState: PagerState,
) {
    val coroutineScope = rememberCoroutineScope()
    val pageCount = pagerState.pageCount
    when {
        selection.source == SelectedShape.Source.DEFINED -> Text(
            modifier = Modifier.padding(top = 12.dp).heightIn(min = STEPPER_PLACEHOLDER_HEIGHT),
            text = if (selection.movedBy == 0) {
                stringResource(Res.string.song_details_chord_defined)
            } else {
                pluralStringResource(Res.plurals.song_details_chord_defined_moved, abs(selection.movedBy), abs(selection.movedBy))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        pageCount > 1 -> {
            val page = pagerState.currentPage
            Stepper(
                modifier = Modifier.padding(top = 12.dp),
                value = stringResource(Res.string.song_details_chord_shape_position, page + 1, pageCount),
                valueDescription = stringResource(Res.string.song_details_chord_shape_position_description, page + 1, pageCount),
                isDefault = true,
                decreaseIcon = painterResource(Res.drawable.ic_previous),
                decreaseLabel = textResource(Res.string.song_details_chord_shape_previous, chordName),
                canDecrease = true,
                onDecrease = { coroutineScope.launch { pagerState.animateScrollToPage((pagerState.targetPage - 1).mod(pageCount)) } },
                increaseIcon = painterResource(Res.drawable.ic_next),
                increaseLabel = textResource(Res.string.song_details_chord_shape_next, chordName),
                canIncrease = true,
                onIncrease = { coroutineScope.launch { pagerState.animateScrollToPage((pagerState.targetPage + 1).mod(pageCount)) } },
                resetLabel = null,
                onReset = null,
            )
        }
        else -> Spacer(modifier = Modifier.padding(top = 12.dp).height(STEPPER_PLACEHOLDER_HEIGHT))
    }
}

/** The empty frame of a diagram and the room of the notes and the stepper, while there is nothing to page through. */
@Composable
private fun ChordShapePlaceholder(
    instrument: ChordInstrument,
    chordName: String,
) {
    ChordDiagram(
        modifier = Modifier.diagramSize(instrument),
        geometry = remember(instrument) { emptyChordDiagramGeometryOf(instrument) },
        description = chordName,
        showsFingers = true,
    )
    ChordNotes(text = "")
    Spacer(modifier = Modifier.padding(top = 12.dp).height(STEPPER_PLACEHOLDER_HEIGHT))
}

private fun Modifier.diagramSize(instrument: ChordInstrument) = size(
    width = if (instrument.isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH,
    height = if (instrument.isFretted) FRETTED_HEIGHT else KEYBOARD_HEIGHT,
)

/** Whether two shapes are fingered on the same frets or keys, whatever either says about the fingers. */
private fun ChordVoicing.sameShapeAs(other: ChordVoicing) = when (this) {
    is ChordVoicing.Fretted -> other is ChordVoicing.Fretted && frets == other.frets
    is ChordVoicing.Keys -> this == other
}

private val FRETTED_WIDTH = 132.dp
private val FRETTED_HEIGHT = 164.dp
private val KEYBOARD_WIDTH = 288.dp
private val KEYBOARD_HEIGHT = 124.dp
private val STEPPER_PLACEHOLDER_HEIGHT = 40.dp
