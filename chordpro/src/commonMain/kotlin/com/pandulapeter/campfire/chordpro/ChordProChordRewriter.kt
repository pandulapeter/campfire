/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.ChordProVocabulary.ACCIDENTALS
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.ANNOTATION_MARKER
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.BRACKET_CLOSE
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.BRACKET_OPEN
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.GRID
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.KEY
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.TAB
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.TRANSPOSE
import com.pandulapeter.campfire.chordpro.model.ChordDefinition
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken

/**
 * The engine both [ChordProTransposer] and [ChordProNotation] rewrite chords with: it finds every chord of a song or of a
 * raw document and hands it to a rename, leaving everything around it as it was.
 */
internal object ChordProChordRewriter {

    /**
     * What [rewriteChords] does to the chords of one stretch of a song. A definition is renamed like any chord unless
     * [rewriteDefinition] says otherwise, which is what a change of notation wants and a transposition does not, and so
     * is the note of a key unless [renameInKey] does, which is what a numbering wants: a key is what the numbers are
     * counted from, so it stays in letters.
     */
    internal class ChordRewrite(
        val rewriteTabLines: (List<String>) -> List<String>,
        val rename: (String) -> String,
        val renameInKey: (String) -> String = rename,
        val rewriteDefinition: (ChordDefinition) -> ChordDefinition = { it.copy(name = rename(it.name)) },
    )

    /**
     * Applies [rename] to every chord of a song — its key, the chords over its lyrics and the chords of its grids,
     * never an annotation — and hands the raw lines of each run of tablature to [rewriteTabLines]. A run and not a
     * section: tablature is a way of writing lines down, so a single section may hold several of them with lyrics
     * in between, and each one is a fingerboard of its own to move.
     *
     * Both the transposition and [ChordProNotation] are written in terms of it, and they differ in exactly one thing:
     * what a tab is. To the transposition it is a fingerboard, so the frets move; to a notation it is a page, so only
     * the chord names above the staff are rewritten.
     */
    internal fun rewriteChords(
        song: ChordProSong,
        rewriteTabLines: (List<String>) -> List<String>,
        rename: (String) -> String,
    ): ChordProSong = ChordRewrite(rewriteTabLines, rename).let { rewrite -> rewriteChords(song) { rewrite } }

    /**
     * [rewriteChords] for a rewrite that depends on where in the song a chord is: [rewriteAt] is asked for the one of
     * each stretch a `{transpose}` moved by the given offset, 0 before the first. A recall is rewritten with the
     * offset in effect where it stands, which is what makes a chorus recalled after `{transpose: 2}` the key change it
     * is written as.
     */
    internal fun rewriteChords(song: ChordProSong, rewriteAt: (Int) -> ChordRewrite): ChordProSong {
        var offset = 0
        val opening = rewriteAt(0)
        return song.copy(
            metadata = song.metadata.copy(
                key = song.metadata.key?.let { key -> renameKey(key, opening.renameInKey) },
                // Moved by what the whole song is moved by: a modulation further down does not move them again, and a
                // chord after it is matched by the name it is shown under.
                definitions = song.metadata.definitions.map(opening.rewriteDefinition),
            ),
            blocks = song.blocks.map { block ->
                if (block is ChordProBlock.Transpose) offset = block.semitones
                rewriteBlock(block, rewriteAt(offset))
            },
        )
    }

    /**
     * A comment and a label are where an intro or an outro is written down as a row of chords (`Intro: [G] [Em]`), and
     * a Campfire 3 file wrote its headings as comments, so their brackets are chords to be moved like those of a line of
     * lyrics. They are not in [writtenChordNames]: the library scan never reads a directive's value, and letting them
     * decide the notation or the spelling would make the song list and the viewer disagree about the song.
     */
    private fun rewriteBlock(block: ChordProBlock, rewrite: ChordRewrite): ChordProBlock = when (block) {
        is ChordProBlock.Section -> block.copy(
            label = block.label?.let { rewriteLyricsLineChords(it, rewrite.rename) },
            lines = rewriteLines(block.lines, rewrite.rewriteTabLines, rewrite.rename),
        )
        // The chorus a recall repeats travels inside it, so it is spelled and moved the way the chorus itself is.
        is ChordProBlock.ChorusRecall -> block.copy(
            label = block.label?.let { rewriteLyricsLineChords(it, rewrite.rename) },
            blocks = block.blocks.map { rewriteBlock(it, rewrite) },
        )
        is ChordProBlock.Comment -> block.copy(text = rewriteLyricsLineChords(block.text, rewrite.rename))
        // Handed the rewrite of the stretch it starts, so the key it names is the one that stretch is in.
        is ChordProBlock.Transpose -> block.copy(key = block.key?.let { renameKey(it, rewrite.renameInKey) })
        else -> block
    }

    /**
     * [rename] applied to a key: a chord name is renamed whole, and a key spelled out in words (`G major`, `A minor`,
     * `Bb-Dur`, `C-dúr`, `Sol mayor`) has its note renamed and its words kept. Anything else — `Dm (capo 2)` — is handed to [rename]
     * as it is, which leaves what is not a chord name alone.
     */
    internal fun renameKey(key: String, rename: (String) -> String): String {
        val noteLength = spelledOutKeyNoteLength(key) ?: return rename(key)
        return rename(key.substring(0, noteLength)) + key.substring(noteLength)
    }

    /**
     * The length of the note a key spelled out in words starts with, or null for a key that is not one. The note has
     * to start with a capital, in the standard notation or the Latin one: a lowercase root is how a Central European
     * chart writes a minor chord (`a` is `Am`), which the normalization would expand into an `Am-moll`, so `a-moll` is
     * left as it is written.
     */
    private fun spelledOutKeyNoteLength(key: String): Int? {
        val letterLength = if (key.firstOrNull() in 'A'..'H') 1 else ChordProChordNames.latinNoteLength(key) ?: return null
        val noteLength = if (key.getOrNull(letterLength)?.let { it in ACCIDENTALS } == true) letterLength + 1 else letterLength
        return noteLength.takeIf { keyWordOf(key.substring(noteLength)) in keyWords }
    }

    /** The word a key spelled out in words names its mode with, in lowercase: `minor` for `A minor`, `moll` for `a-moll`. */
    internal fun keyWordOf(suffix: String) = suffix.trimStart { it.isWhitespace() || it == '-' }.lowercase()

    /**
     * The names [rewriteChords] hands to its rename that decide how a song is written — its key and the chords of the lines
     * of its sections, without rewriting anything. The brackets of comments and labels and the chorus a recall repeats are
     * left out, for the reason [rewriteBlock] gives, and so are the definitions, which follow their chords.
     */
    internal fun writtenChordNames(song: ChordProSong): List<String> = buildList {
        visitWrittenChordNames(song) { name ->
            add(name)
            true
        }
    }

    /** Whether any of the [writtenChordNames] of [song] is one [predicate] holds for, stopping at the first that is. */
    internal fun anyWrittenChordName(song: ChordProSong, predicate: (String) -> Boolean): Boolean {
        var isFound = false
        visitWrittenChordNames(song) { name ->
            isFound = predicate(name)
            !isFound
        }
        return isFound
    }

    private fun visitWrittenChordNames(song: ChordProSong, visit: (String) -> Boolean) =
        ChordProChords.visitChordNames(song, includeKey = true, includeBracketedText = false, includeRecalls = false) { name, _ -> visit(name) }

    /**
     * Splits the lines into runs of tablature and everything else, and rewrites each the way it has to be.
     *
     * A blank line does not end a run, a second environment does: a tab environment keeps its blank lines, and the text
     * path moves an environment as one fingerboard however many systems it is written in, and every environment as a
     * fingerboard of its own, so the model has to make the same octave decisions for the same lines. Only the tab lines
     * are handed to [rewriteTabLines]; the blanks go back where they were.
     */
    private fun rewriteLines(
        lines: List<ChordProLine>,
        rewriteTabLines: (List<String>) -> List<String>,
        rename: (String) -> String,
    ): List<ChordProLine> {
        if (lines.none { it is ChordProLine.Tab }) return lines.map { line -> rewriteLine(line, rename) }
        val rewritten = mutableListOf<ChordProLine>()
        var run = mutableListOf<ChordProLine>()
        fun flushRun() {
            if (run.isEmpty()) return
            val tabLines = rewriteTabLines(run.filterIsInstance<ChordProLine.Tab>().map { it.text }).iterator()
            rewritten += run.map { line ->
                if (line is ChordProLine.Tab) {
                    line.copy(text = tabLines.next(), label = line.label?.let { rewriteLyricsLineChords(it, rename) })
                } else {
                    line
                }
            }
            run = mutableListOf()
        }
        lines.forEach { line ->
            when {
                line is ChordProLine.Tab -> {
                    // A second environment is a fingerboard of its own, whatever stands between the two.
                    if (!line.continuesEnvironment) flushRun()
                    run += line
                }
                line == ChordProLine.Blank && run.isNotEmpty() -> run += line
                else -> {
                    flushRun()
                    rewritten += rewriteLine(line, rename)
                }
            }
        }
        flushRun()
        return rewritten
    }

    /**
     * [rename] for a chord as the file writes it: a lowercase minor is spelled out for it and folded back afterwards,
     * so that the file keeps its own convention.
     */
    internal fun keepingLowercaseMinors(rename: (String) -> String) = { name: String ->
        ChordProChordNames.lowercaseMinorExpanded(name)?.let { ChordProChordNames.lowercaseMinorFolded(rename(it)) } ?: rename(name)
    }

    /**
     * Applies [rename] to every chord a raw document names - in brackets, in grids, in its key and in directives whose
     * value holds chords - and [rewriteTab] to each run of tablature as a whole, leaving every other character of the
     * text exactly where it was.
     *
     * Every `{define}` and `{chord}` line is handed to [rewriteDefinition], with the directive's selector: a change of
     * notation renames the chords it names, and a transposition moves its shape along the neck with them (see
     * [ChordProDefinitions.rewrittenLine]).
     */
    internal fun rewriteChordNamesInText(
        text: String,
        rewriteTab: (List<String>) -> List<String>,
        rename: (String) -> String,
        rewriteDefinition: (rawLine: String, selector: String) -> String = { rawLine, _ -> rawLine },
    ): String {
        // The scanner reads the lines as the file has them, and the loop writes its rewrites into a copy, so a line is
        // never read after it was rewritten.
        val writtenLines = ChordProLines.splitLines(text)
        val lines = writtenLines.toMutableList()
        val tabLineIndices = mutableListOf<Int>() // The tab environment being collected: it is rewritten as a whole.
        ChordProLineScanner.scan(writtenLines).forEach { line ->
            val index = line.index
            val rawLine = line.raw
            val environment = line.environment
            val directive = line.directive
            when {
                line.isSourceComment -> Unit
                directive != null -> {
                    // A definition inside an environment handed to another program is that program's text. One with a
                    // selector is read whichever instrument it names, so it is renamed with the rest.
                    if (!line.isDelegated) ChordProDefinitions.selectorOf(directive.name)?.let { lines[index] = rewriteDefinition(rawLine, it) }
                    if (ChordProDirectives.hasSelectorSuffix(directive.name)) return@forEach
                    if (ChordProEnvironments.startOfEnvironment(directive.name) != null || ChordProEnvironments.endOfEnvironment(directive.name) != null) {
                        lines.rewriteTab(tabLineIndices, rewriteTab)
                    }
                    if (directive.name in ChordProHeaderLayout.blockNames || directive.name == TRANSPOSE) {
                        // The parser cuts the section in two here, and each half of the tab is a run of its own in the model;
                        // moving them as one fingerboard would let the viewer and the editor disagree about the octave.
                        lines.rewriteTab(tabLineIndices, rewriteTab)
                    }
                    (ChordProMetaItems.standardMeta(directive) ?: directive).takeIf { it.name == KEY }?.value?.takeIf { it.isNotEmpty() }?.let { key ->
                        lines[index] = rewriteKeyLine(rawLine, key, rename)
                    }
                    // The directive's name holds no brackets, so the line can be read as a line of lyrics whole.
                    if (ChordProDirectives.hasChordsInValue(directive.name)) lines[index] = rewriteLyricsLineChords(rawLine, rename)
                }

                environment == TAB -> tabLineIndices += index
                environment == GRID -> lines[index] = rewriteGridLine(rawLine, line.trimmed, rename)
                environment in ChordProEnvironments.delegateEnvironments -> Unit
                else -> lines[index] = rewriteLyricsLineChords(rawLine, rename)
            }
        }
        lines.rewriteTab(tabLineIndices, rewriteTab) // An environment the file never closes.
        return ChordProLines.joinLines(lines, text)
    }

    /** Rewrites the collected lines of one tab environment in place and starts collecting the next one. */
    private fun MutableList<String>.rewriteTab(indices: MutableList<Int>, rewriteTab: (List<String>) -> List<String>) {
        if (indices.isEmpty()) return
        val transposed = rewriteTab(indices.map { this[it] })
        indices.forEachIndexed { index, lineIndex -> this[lineIndex] = transposed[index] }
        indices.clear()
    }

    private fun rewriteLine(line: ChordProLine, rename: (String) -> String) = when (line) {
        is ChordProLine.Lyrics -> line.copy(
            chords = line.chords.map { chord ->
                if (chord.isAnnotation) chord else chord.copy(name = rename(chord.name))
            }
        )

        is ChordProLine.Grid -> line.copy(
            tokens = line.tokens.map { token ->
                if (token is GridToken.Chord) {
                    GridToken.Chord(ChordProTokens.cellChords(token.name).joinToString(ChordProTokens.GRID_CELL_CHORD_SEPARATOR, transform = rename))
                } else {
                    token
                }
            },
            label = line.label?.let { rewriteLyricsLineChords(it, rename) },
        )

        is ChordProLine.Tab -> line.copy(label = line.label?.let { rewriteLyricsLineChords(it, rename) })

        ChordProLine.Blank -> line
    }

    /** Applies [rename] to every `[chord]` of a raw line, leaving the annotations, the empty brackets and the text. */
    internal fun rewriteLyricsLineChords(rawLine: String, rename: (String) -> String): String {
        val brackets = ChordProDirectives.brackets(rawLine)
        if (brackets.isEmpty()) return rawLine
        return buildString(rawLine.length) {
            var consumedUntil = 0
            brackets.forEach { bracket ->
                val content = bracket.content.trim()
                val isChord = content.isNotEmpty() && !content.startsWith(ANNOTATION_MARKER)
                append(rawLine, consumedUntil, if (isChord) bracket.range.first else bracket.range.last + 1)
                if (isChord) {
                    // The spaces a file puts inside its brackets are its own formatting, and the transposition keeps it.
                    val leading = bracket.content.length - bracket.content.trimStart().length
                    append(BRACKET_OPEN)
                        .append(bracket.content, 0, leading)
                        .append(rename(content))
                        .append(bracket.content, bracket.content.trimEnd().length, bracket.content.length)
                        .append(BRACKET_CLOSE)
                }
                consumedUntil = bracket.range.last + 1
            }
            append(rawLine, consumedUntil, rawLine.length)
        }
    }

    private fun rewriteGridLine(rawLine: String, trimmedLine: String, rename: (String) -> String): String {
        // The ranges and the tokens are zipped by index, which is only safe because both are the same list of words:
        // parseGridTokens reads the line through ChordProTokens.words as well.
        val matches = ChordProTokens.words(trimmedLine)
        val tokens = ChordProTokens.parseGridTokens(trimmedLine)
        val body = buildString {
            var consumedUntil = 0
            matches.forEachIndexed { index, match ->
                append(trimmedLine, consumedUntil, match.range.first)
                append(
                    if (tokens[index] is GridToken.Chord) {
                        ChordProTokens.cellChords(match.value).joinToString(ChordProTokens.GRID_CELL_CHORD_SEPARATOR, transform = rename)
                    } else {
                        match.value
                    }
                )
                consumedUntil = match.range.last + 1
            }
            append(trimmedLine, consumedUntil, trimmedLine.length)
        }
        return rawLine.replaceTrimmedPart(trimmedLine, body)
    }

    /**
     * Renames the value of a key directive in place. Only the value's own characters are replaced, so the directive
     * keeps the spelling and the spacing the file gives it; the value is the last thing before the closing brace, give
     * or take whitespace, however the directive is written.
     */
    private fun rewriteKeyLine(rawLine: String, key: String, rename: (String) -> String): String {
        val valueEnd = rawLine.substring(0, rawLine.trimEnd().lastIndex).trimEnd().length
        return rawLine.substring(0, valueEnd - key.length) + renameKey(key, rename) + rawLine.substring(valueEnd)
    }

    /** Swaps the trimmed part of a line for [replacement], keeping the surrounding whitespace. */
    private fun String.replaceTrimmedPart(trimmedLine: String, replacement: String): String {
        val start = length - trimStart().length
        return substring(0, start) + replacement + substring(start + trimmedLine.length)
    }

    internal val keyWords = setOf("major", "minor", "maj", "min", "dur", "dúr", "moll", "mayor", "menor", "majeur", "mineur", "maggiore", "minore")
}
