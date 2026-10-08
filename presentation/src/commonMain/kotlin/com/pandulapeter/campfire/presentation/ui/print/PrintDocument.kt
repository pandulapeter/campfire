/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.secondaryName
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels

/**
 * What a PDF is made of, read once when the export screen opens: a snapshot, so that the preview and the file agree even
 * if the library changes underneath the screen. [date] is already formatted in the app's language.
 */
internal data class PrintSource(
    val title: String,
    val description: String = "",
    val date: String? = null,
    val isSetlist: Boolean = false,
    val songs: List<PrintSong>,
)

/**
 * One song of a [PrintSource], [index] being its one-based slot in a setlist, kept when songs are left out, and
 * [transposition] the semitones the viewer moves it by, which [song] is already rendered in.
 */
internal data class PrintSong(
    val fileName: String,
    val title: String,
    val artist: String?,
    val index: Int? = null,
    val transposition: Int = 0,
    /** Null for an unreadable or missing file; its place remains visible in the running order. */
    val song: ChordProSong?,
    /** The file as it is stored, which the export screen shows for a song exported as ChordPro, and null when it could not be read. */
    val text: String? = null,
    /**
     * Every chord the song plays, in the order it is first played, with the shape its Chords section draws it with on
     * the reader's instrument; empty where the chord diagrams are switched off in the app, whatever the export asks for.
     */
    val chords: List<PrintChord> = emptyList(),
)

/**
 * One chord diagram of a [PrintSong]: the chord as the page names it, the one it sounds as where that differs or else
 * its letters where the page counts it ([SongChord.secondaryName]), and its shape.
 */
internal data class PrintChord(
    val name: String,
    val secondaryName: String? = null,
    val geometry: ChordDiagramGeometry,
)

/**
 * The chords of [song], transposed as it is printed and still in the standard notation, named in [notation] and drawn
 * the way the song details screen's Chords section draws them
 * on [instrument]: the song's own definition, the player's shape from [storedShapes], or the app's own, and an empty
 * frame for a chord with none, so that the page and the screen finger every chord alike.
 */
internal fun printChordsOf(
    song: ChordProSong,
    notation: ChordNotation,
    instrument: ChordInstrument,
    storedShapes: Map<String, String>,
): List<PrintChord> = songChordsOf(song, notation, instrument, song.metadata.capo ?: 0).map { chord ->
    PrintChord(
        name = chord.name,
        secondaryName = chord.secondaryName,
        geometry = selectShape(chord, instrument, storedShapes).shape?.let { chordDiagramGeometryOf(it, instrument, chord.chord.root) }
            ?: emptyChordDiagramGeometryOf(instrument),
    )
}

/**
 * How a text is set: [size] in PDF points, [monospace] for the tablature and grids whose characters have to line up
 * (everything else is in the app's text font, as in the viewer), and [gray] from 0 (black) to 255 (white).
 */
internal data class PrintStyle(
    val size: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val monospace: Boolean = false,
    val gray: Int = 0,
)

/**
 * Coordinates and sizes are PDF points (1/72 inch), independent of screen density and accessibility text size.
 *
 * @property isSelectable False for the names over the chord diagrams, which are left out of the file's selectable text:
 *   read back by an import, a row of them would be a line of chords above the song's first line.
 */
internal data class PrintText(
    val text: String,
    val x: Float,
    val y: Float,
    val style: PrintStyle,
    val isSelectable: Boolean = true,
)

/** A filled rectangle, the way frames and bars are drawn; in PDF points like the texts, [gray] from 0 (black) to 255. */
internal data class PrintRule(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val gray: Int = 0,
)

/** A chord diagram drawn into the [width] by [height] box at [x], [y], in PDF points like the texts. */
internal data class PrintDiagram(
    val geometry: ChordDiagramGeometry,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** Everything on one page, in PDF points from its top left corner; the rules are drawn under the texts and the diagrams. */
internal data class PrintPage(
    val texts: List<PrintText>,
    val rules: List<PrintRule> = emptyList(),
    val diagrams: List<PrintDiagram> = emptyList(),
)

/** The pages of one export, all of one size, which is the paper's in PDF points turned for the orientation. */
internal data class PrintDocument(
    val width: Float,
    val height: Float,
    val pages: List<PrintPage>,
)

/**
 * The words the layout prints, resolved by the export screen in the app's language, since the layout runs off the composition
 * where no string resource can be read.
 */
internal data class PrintLabels(
    val key: String,
    val transposition: String,
    val capo: String,
    val tempo: String,
    /** A tempo's value as the app writes it, [TEMPO_VALUE] standing for the number: "96 BPM". */
    val tempoValue: String,
    val time: String,
    val missing: String,
    /** The headings of the sections the file leaves unnamed, the viewer's own. */
    val sections: DefaultSectionLabels,
)

/** What [PrintLabels.tempoValue] holds in place of the number. */
internal const val TEMPO_VALUE = "\u0001"
