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

import com.pandulapeter.campfire.presentation.ui.notation.NotationChordSpelling
import com.pandulapeter.campfire.presentation.ui.notation.NotationDrawing
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.chords.groupedByKey
import com.pandulapeter.campfire.presentation.ui.chords.withDegrees
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.songView.model.LayoutBudget
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleAnalysis
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup
import com.pandulapeter.campfire.presentation.ui.songView.model.chordKeysOf
import com.pandulapeter.campfire.presentation.ui.songView.model.songScaleAnalysisOf
import com.pandulapeter.campfire.presentation.ui.songView.model.songScaleGroupsOf

/**
 * What a PDF is made of, read once when the export screen opens: a snapshot, so that the preview and the file agree even
 * if the library changes underneath the screen. [date] is already formatted in the app's language.
 */
internal data class PrintSource(
    val title: String,
    val description: String = "",
    val date: String? = null,
    val kind: Kind = Kind.SONG,
    val songs: List<PrintSong>,
) {
    /** What is being exported: one song, a setlist, or several songs ticked in the songs screen's selection mode. */
    enum class Kind { SONG, SETLIST, SONGS }

    /** Whether the songs are in an order of somebody's making, which is what a running order and a manifest are of. */
    val hasRunningOrder get() = kind == Kind.SETLIST

    /** Whether the songs can be put on pages of their own and zipped together. */
    val hasSeveralSongs get() = kind != Kind.SONG
}

/**
 * One song of a [PrintSource], [index] being its one-based slot in a setlist, kept when songs are left out, and
 * [transposition] the semitones the reader moves it by, printed beside its key; [song] is already rendered in the chords
 * that sound, the file's capo counted in as on the screen (`SongPlayback.soundingTransposition`).
 */
internal data class PrintSong(
    val fileName: String,
    val title: String,
    val artist: String?,
    val index: Int? = null,
    val transposition: Int = 0,
    /** Null for an unreadable or missing file; its place remains visible in the running order. */
    val song: ChordProSong?,
    /** The file as it is stored, which the library's own files are written from, and null when it could not be read. */
    val text: String? = null,
    /**
     * Every chord the song plays, in the order it is first played, with the shape its Chords section draws it with in
     * the reader's tuning; empty where the chord diagrams are switched off in the app, whatever the export asks for.
     */
    val chords: List<PrintChord> = emptyList(),
    /**
     * The scales the song's parts are played over, as its Scales section suggests them in the key it is printed in;
     * empty where the app suggests none (the Scales, the Scale suggestions, or the Chords switch off), whatever the
     * export asks for.
     */
    val scaleGroups: List<SongScaleGroup> = emptyList(),
    /** [scaleGroups] in words, which the export screen puts them in only while they are to be printed. */
    val scales: List<PrintScaleRow> = emptyList(),
    /** How the chords over the lyrics are named, which an engraved ABC tune's staff and chord symbols follow. */
    val notationChords: NotationChordSpelling = NotationChordSpelling.AS_WRITTEN,
)

/**
 * One line of a song's suggested scales: the parts it is for ([title], none for the home, which most of the song is
 * played over) and its scales, or what says no scale fits them.
 */
internal data class PrintScaleRow(
    val title: String?,
    val text: String,
)

/**
 * One chord diagram of a [PrintSong]: the chord as the page names it, its letters where the page counts it
 * ([SongChord.letterName]), else its step of the key ([SongChord.degree]), its shape, and the key its group is headed
 * with where the song's chords are in more than one key ([SongChord.keyName]).
 */
internal data class PrintChord(
    val name: String,
    val letterName: String? = null,
    val degree: String? = null,
    val geometry: ChordDiagramGeometry,
    val keyName: String? = null,
)

/**
 * The chords of [song], transposed as it is printed and still in the standard notation, named in [notation] and drawn
 * the way the song details screen's Chords section draws them
 * in [tuning]: the song's own definition, the player's shape from [storedShapes], or the app's own, and an empty
 * frame for a chord with none, so that the page and the screen finger every chord alike, and grouped by the key they
 * are first played in as the screen groups them ([groupedByKey], [chordKeysOf], from [analysis]). With [showsDegrees],
 * each is labelled with its step of that key. Under a [capo] each is the shape fingered above it, drawn where it is
 * played with the capo across it, as on the screen.
 */
internal fun printChordsOf(
    song: ChordProSong,
    notation: ChordNotation,
    tuning: ChordTuning,
    storedShapes: Map<String, String>,
    showsDegrees: Boolean = false,
    analysis: SongScaleAnalysis? = null,
    capo: Int = 0,
): List<PrintChord> = songChordsOf(song, notation, tuning, capo = capo).let { chords ->
    val keys = if (chords.isNotEmpty()) chordKeysOf(song, analysis, song.isSongbook) else null
    chords.groupedByKey(keys, notation).let { grouped -> if (showsDegrees) grouped.withDegrees(keys, notation) else grouped }
}.map { chord ->
    PrintChord(
        name = chord.name,
        letterName = chord.letterName,
        degree = chord.degree,
        geometry = selectShape(chord, tuning, storedShapes).shape?.let { chordDiagramGeometryOf(it, tuning, chord.chord.root, chord.capo) }
            ?: emptyChordDiagramGeometryOf(tuning.instrument),
        keyName = chord.keyName,
    )
}

/**
 * The scale walk of [song], transposed as it is printed and still in the standard notation, its sections numbered as
 * [labels] number them, that the scales and the degrees of the page share as the screen's do; none for a songbook,
 * which the screen never walks either.
 */
internal fun printScaleAnalysisOf(song: ChordProSong, labels: DefaultSectionLabels): SongScaleAnalysis? =
    if (song.isSongbook) null else songScaleAnalysisOf(song, labels)

/**
 * The scales [song], transposed as it is printed and still in the standard notation, is played over, as the song details
 * screen's Scales section suggests them from [analysis], its sections named by [labels] and its chords in [notation];
 * none for a songbook, for which the section suggests none either.
 */
internal fun printScaleGroupsOf(
    song: ChordProSong,
    labels: DefaultSectionLabels,
    notation: ChordNotation,
    analysis: SongScaleAnalysis? = printScaleAnalysisOf(song, labels),
): List<SongScaleGroup> = analysis?.let { songScaleGroupsOf(song, labels, notation, it) }.orEmpty()

private val ChordProSong.isSongbook get() = blocks.count { it is ChordProBlock.Section } > LayoutBudget.MAX_CUT_SECTION_COUNT

/**
 * How a text is set: [size] in PDF points, [monospace] for the grids and preformatted text whose characters have to
 * line up (everything else is in the app's text font, as in the viewer), [tabular] for the figures of engraved
 * tablature, and [gray] from 0 (black) to 255 (white).
 */
internal data class PrintStyle(
    val size: Float,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val monospace: Boolean = false,
    val tabular: Boolean = false,
    val gray: Int = 0,
)

/**
 * Coordinates and sizes are PDF points (1/72 inch), independent of screen density and accessibility text size.
 *
 * @property isSelectable False for the names over the chord diagrams, which are left out of the file's selectable text:
 *   read back by an import, a row of them would be a line of chords above the song's first line.
 * @property formatting The bold, italic, and underlined ranges of a lyric's or a comment's markup, as offsets into [text].
 *   They are drawn as spans of the one text, laid out once, so that the bidirectional algorithm sees the whole line and a
 *   right-to-left one keeps its order; the selectable text is written in [style] alone, since the importer would read a
 *   bold word that is a chord name back as a chord.
 * @property isDrawn False for a text that is only in the selectable text: engraved tablature is drawn as figures and
 *   rules, and its lines reach the text layer as the file has them, under the drawing.
 */
internal data class PrintText(
    val text: String,
    val x: Float,
    val y: Float,
    val style: PrintStyle,
    val isSelectable: Boolean = true,
    val formatting: List<ChordProLine.Lyrics.Formatting> = emptyList(),
    val isDrawn: Boolean = true,
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

/**
 * One staff system of an engraved ABC tune, the [system]-th of [drawing], its top left corner at [x], [y] and [scale]
 * points to its SVG unit, in PDF points like the texts.
 */
internal data class PrintNotation(
    val drawing: NotationDrawing,
    val system: Int,
    val x: Float,
    val y: Float,
    val scale: Float,
)

/** Everything on one page, in PDF points from its top left corner; the rules are drawn under the texts and the diagrams. */
internal data class PrintPage(
    val texts: List<PrintText>,
    val rules: List<PrintRule> = emptyList(),
    val diagrams: List<PrintDiagram> = emptyList(),
    val notations: List<PrintNotation> = emptyList(),
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
    /**
     * The line naming the instrument and the tuning the diagrams are drawn for ("Mandolin · Tuning: GDGD", "Tuning: Drop D"),
     * printed above every song's diagrams so that a sheet read by somebody who did not set it is not read for the wrong
     * strings: the instrument where it is not the guitar (the twelve-string is drawn as one) or the keyboard (whose
     * diagram says it), the tuning where it is not the standard one, and null where neither is named.
     */
    val chordTuning: String? = null,
    /** The heading over a song's suggested scales, the song details screen's own. */
    val scales: String? = null,
)

/** What [PrintLabels.tempoValue] holds in place of the number. */
internal const val TEMPO_VALUE = "\u0001"
