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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.ChordProSummary
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType

/**
 * Turns ChordPro text into a [ChordProSong].
 */
object ChordProParser {

    /** The section names the Campfire 3 dialect used as `{comment}` headings. */
    private val legacyHeadings = mapOf(
        "intro" to SectionType.Custom("intro"),
        "verse" to SectionType.Verse,
        "pre-chorus" to SectionType.Custom("pre-chorus"),
        "chorus" to SectionType.Chorus,
        "bridge" to SectionType.Bridge,
        "solo" to SectionType.Custom("solo"),
        "outro" to SectionType.Custom("outro"),
    )

    /**
     * The song in the notation the app works in, [text] being written in [notation]: a file is in the standard one,
     * which an `H` chord anywhere in it overrules (see [ChordNotation]), and the editor's field in the reader's own.
     */
    fun parse(text: String, notation: ChordNotation = ChordNotation.STANDARD) = ChordProNotation.normalized(parseAsWritten(text), notation)

    /** The song with every chord spelled the way its file spells it. */
    internal fun parseAsWritten(text: String): ChordProSong {
        val metadata = MetadataBuilder()
        val blocks = mutableListOf<ChordProBlock>()
        val section = SectionBuilder(blocks)
        val transposition = Transposition()
        val timing = TimingChanges()
        ChordProLines.splitLines(text).forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            // Inside an environment handed to another program a `#` and a brace are that program's syntax.
            if (trimmedLine.startsWith(SOURCE_COMMENT) && !section.isDelegated) return@forEach
            val directive = if (section.isDelegated) ChordProDirectives.matchDelegatedDirective(trimmedLine) else ChordProDirectives.matchDirective(trimmedLine)
            if (directive == null) {
                if (trimmedLine.isNotEmpty()) transposition.startBody()
                section.addContent(rawLine, trimmedLine)
            } else {
                handleDirective(directive, metadata, blocks, section, transposition, timing)
            }
        }
        section.close()
        transposition.finish()
        val declared = metadata.build(transpose = transposition.wholeSong)
        // Only known once the whole file has been read, since a song may name its key under its last line.
        val key = declared.key?.takeIf { it.isNotBlank() }
        // A side of a change is null only where no value of its kind had been read yet, and the song's own value, which
        // may be named further down, is the one in force from the start.
        val tempo = declared.tempo?.takeIf { ChordProTempo.parse(it) != null }
        val time = declared.time?.takeIf { ChordProTime.parse(it) != null }
        val keyedBlocks = blocks.map { block ->
            when (block) {
                is ChordProBlock.Transpose -> if (key == null) block else block.copy(key = key)
                is ChordProBlock.Timing -> block.copy(tempo = block.tempo ?: tempo, time = block.time ?: time)
                else -> block
            }
        }
        return ChordProSong(metadata = declared, blocks = ChordProChorusRecalls.withChorusesRecalled(keyedBlocks))
    }

    /**
     * The semitones a non-empty `{transpose}` value asks for, or null where it is not a number. `2s` and `-3f` ask for
     * sharps or flats as well; the reader's own spelling preference decides that here.
     */
    internal fun transposeSemitones(written: String) = written.trim()
        .removePrefix("+")
        .let { if (it.lastOrNull()?.lowercaseChar() in SPELLING_SUFFIXES) it.dropLast(1) else it }
        .trim()
        .toIntOrNull()

    /** Only scans directive lines, so that it is cheap enough for a caller that has no interest in the body. */
    fun parseMetadata(text: String) = scan(text, shouldDetectChords = false, notation = ChordNotation.STANDARD).metadata

    /**
     * The directives of a song and whether it has any chords, from a single walk over the text. The library scan
     * wants both for every file it reads, and asking for them separately walks each file twice. [notation] is the one
     * [text] is written in, as for [parse].
     */
    fun summarize(text: String, notation: ChordNotation = ChordNotation.STANDARD) = scan(text, shouldDetectChords = true, notation = notation)

    /**
     * @param shouldDetectChords Whether the lines that are not directives are looked at as well. A real chord (not an
     *   `[*annotation]`, not an empty `[]`) is what counts as one, and so is a line of tablature or a row of chord names
     *   in a tab environment, since the transposition moves those too; once one has been found the rest of the body is
     *   skipped, since nothing later in the file can change the answer.
     */
    private fun scan(text: String, shouldDetectChords: Boolean, notation: ChordNotation): ChordProSummary {
        val metadata = MetadataBuilder()
        val transposition = Transposition()
        var hasChords = false
        var environment: String? = null
        var isGermanNotated = notation == ChordNotation.GERMAN
        ChordProLines.splitLines(text).forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            val isDelegated = environment in ChordProEnvironments.delegateEnvironments
            if (trimmedLine.startsWith(SOURCE_COMMENT) && !isDelegated) return@forEach
            val directive = when {
                !trimmedLine.startsWith(DIRECTIVE_START) -> null
                isDelegated -> ChordProDirectives.matchDelegatedDirective(trimmedLine)
                else -> ChordProDirectives.matchDirective(trimmedLine)
            }
            if (directive != null) {
                if (!ChordProDirectives.hasSelectorSuffix(directive.name)) {
                    if (directive.name == TRANSPOSE) {
                        transposition.consume(directive.value)
                    } else if (ChordProHeaderLayout.startsBody(directive)) {
                        transposition.startBody()
                    }
                    ChordProEnvironments.startOfEnvironment(directive.name)?.let { environment = it.lowercase() }
                    ChordProEnvironments.endOfEnvironment(directive.name)?.let { environment = null }
                    metadata.consume(directive, isInBody = transposition.isInBody)
                }
                return@forEach
            }
            // Before the early return below, which skips lines once the chords are found: the body has begun either way.
            if (trimmedLine.isNotEmpty()) transposition.startBody()
            val isLookingForChords = shouldDetectChords && !hasChords
            val isLookingForNotation = !isGermanNotated && mayHoldGermanName(rawLine, environment)
            if (!isLookingForChords && !isLookingForNotation) return@forEach
            val names = writtenChordNames(rawLine, trimmedLine, environment)
            if (isLookingForChords) {
                // A tab is chords to the transposition as long as it has a staff to move or chord names over it.
                hasChords = names.isNotEmpty() || (environment == TAB && ChordProTokens.isStaffLine(rawLine))
            }
            if (isLookingForNotation) isGermanNotated = names.any(ChordProNotation::isGermanName)
        }
        transposition.finish()
        val declared = metadata.build(transpose = transposition.wholeSong)
        val isGerman = isGermanNotated || declared.key?.let(ChordProNotation::isGermanName) == true
        val key = declared.key?.let { written -> ChordProChordRewriter.renameKey(written) { name -> ChordProNotation.read(name, isGerman) } }
        return ChordProSummary(
            metadata = declared.copy(key = key),
            hasChords = hasChords,
        )
    }

    /**
     * Whether a chord name on [rawLine] could be German-notated, judged by where [writtenChordNames] reads its names
     * from. A German name always holds an `H` or an `h`, and an English lyric line nearly always holds one outside its
     * brackets, so looking at the whole line would parse almost every line of the body for names that cannot be there.
     */
    private fun mayHoldGermanName(rawLine: String, environment: String?) = when (environment) {
        TAB, GRID -> rawLine.hasGermanLetter()
        in ChordProEnvironments.delegateEnvironments -> false
        else -> ChordProDirectives.hasBrackets(rawLine) && ChordProDirectives.brackets(rawLine).any { it.content.hasGermanLetter() }
    }

    private fun String.hasGermanLetter() = GERMAN_LETTER in this || GERMAN_LETTER.lowercaseChar() in this

    /** The names one line of the body hands to a chord rewrite, by the environment it stands in. */
    private fun writtenChordNames(rawLine: String, trimmedLine: String, environment: String?) = when (environment) {
        TAB -> ChordProTabTransposer.chordNames(listOf(rawLine))
        GRID -> ChordProTokens.parseGridTokens(trimmedLine).filterIsInstance<GridToken.Chord>().flatMap { ChordProTokens.cellChords(it.name) }
        in ChordProEnvironments.delegateEnvironments -> emptyList()
        else -> parseLyrics(rawLine).chords.filter { !it.isAnnotation }.map { it.name }
    }

    private fun handleDirective(
        directive: ChordProDirectives.Directive,
        metadata: MetadataBuilder,
        blocks: MutableList<ChordProBlock>,
        section: SectionBuilder,
        transposition: Transposition,
        timing: TimingChanges,
    ) {
        val name = directive.name
        // A definition's selector names the instrument it is for, which Campfire draws, rather than a person or a
        // device it has nothing to match against. Inside an environment handed to another program it is that
        // program's text.
        ChordProDefinitions.selectorOf(name)?.let { selector ->
            if (!section.isDelegated) directive.value?.let { ChordProDefinitions.definitionOf(it, selector) }?.let { definition ->
                metadata.addDefinition(definition, isDefine = name.substringBefore('-') == ChordProDefinitions.DEFINE)
            }
            return
        }
        if (ChordProDirectives.hasSelectorSuffix(name)) return
        if (name == TRANSPOSE) {
            // Inside a section the modulation cuts it in two, the way a comment does, and the rest is its continuation.
            transposition.consume(directive.value)?.let { semitones -> section.addBlock(ChordProBlock.Transpose(semitones)) }
            return
        }
        if (ChordProHeaderLayout.startsBody(directive)) transposition.startBody()
        ChordProEnvironments.startOfEnvironment(name)?.let { environment ->
            // Tablature and grids are how the next few lines are written, not a section of their own: they open
            // inside whatever section is running, and a song with a solo written as a line of chords over a tab is
            // one section rather than three.
            lineMode(environment)?.let { mode ->
                section.openLineMode(mode, ChordProEnvironments.label(directive.value))
                return
            }
            section.close()
            section.open(sectionType(environment), ChordProEnvironments.label(directive.value), isExplicit = true)
            if (environment.lowercase() in ChordProEnvironments.delegateEnvironments) section.openLineMode(LineMode.VERBATIM, label = null)
            return
        }
        ChordProEnvironments.endOfEnvironment(name)?.let { environment ->
            if (lineMode(environment) == null) section.close() else section.closeLineMode()
            return
        }
        when (name) {
            "chorus" -> {
                val recall = ChordProBlock.ChorusRecall(ChordProEnvironments.label(directive.value))
                if (section.isInLineMode) {
                    // The environment still has lines to come, so the recall interrupts it the way a comment does.
                    section.addBlock(recall)
                } else {
                    section.close()
                    blocks += recall
                }
            }

            "comment", "c" -> handleComment(directive.value.orEmpty().trim(), CommentStyle.PLAIN, blocks, section)
            "comment_italic", "ci" -> handleComment(directive.value.orEmpty().trim(), CommentStyle.ITALIC, blocks, section)
            "comment_box", "cb" -> handleComment(directive.value.orEmpty().trim(), CommentStyle.BOX, blocks, section)
            // Never a Campfire 3 heading: that dialect wrote its headings as `{comment}` only.
            "highlight" -> section.addBlock(ChordProBlock.Comment(directive.value.orEmpty().trim(), CommentStyle.PLAIN))
            "new_page", "np", "new_physical_page", "npp", "column_break", "colb" -> section.addBlock(ChordProBlock.Break)
            "new_song", "ns" -> Unit // Splitting is ChordProSplitter's job.
            else -> {
                // Before the metadata reads it, since whether it is a change depends on whether it becomes the song's own.
                if (transposition.isInBody) {
                    val before = timing.inForce(metadata)
                    timing.consume(directive, metadata)?.let { change -> addTiming(change, before, blocks, section, timing) }
                }
                metadata.consume(directive, isInBody = transposition.isInBody)
            }
        }
    }

    /**
     * A change with no line of the song between it and the one before is the same place in the song: a `{tempo}`
     * directly followed by a `{time}` is one change, which keeps a viewer from starting a stretch that holds nothing,
     * and a group that brings the song back to what was in force before it is no change at all. [before] is what was
     * in force before [change].
     */
    private fun addTiming(
        change: ChordProBlock.Timing,
        before: ChordProBlock.Timing,
        blocks: MutableList<ChordProBlock>,
        section: SectionBuilder,
        timing: TimingChanges,
    ) {
        val beforeGroup = timing.beforeGroup
        when {
            blocks.lastOrNull() !is ChordProBlock.Timing || section.hasContentLine || beforeGroup == null -> {
                section.addBlock(change)
                timing.beforeGroup = before
            }
            timing.isSame(change, beforeGroup) -> {
                blocks.removeAt(blocks.lastIndex)
                timing.restore(beforeGroup)
                // The first change of the group cut the section it stood in, which is one section again without it.
                section.rejoin()
            }
            else -> blocks[blocks.lastIndex] = change
        }
    }

    private fun handleComment(
        text: String,
        style: CommentStyle,
        blocks: MutableList<ChordProBlock>,
        section: SectionBuilder,
    ) {
        // Inside a tab or a grid a comment is a note to the player: a Campfire 3 file had its headings between its
        // sections, and taking this one for a heading would end the environment that is still open around it.
        if (style == CommentStyle.PLAIN && !section.isExplicit && !section.isInLineMode) {
            legacyHeading(text)?.let { type ->
                section.close()
                section.open(type, text, isExplicit = false, headingText = text)
                return
            }
        }
        section.addBlock(ChordProBlock.Comment(text, style))
    }

    /** Whether a plain `{comment}` reading [text] is a Campfire 3 heading, which opens a section rather than cutting one. */
    internal fun isLegacyHeading(text: String) = legacyHeading(text.trim()) != null

    private fun legacyHeading(text: String): SectionType? {
        val firstWord = text.takeWhile { it.isLetter() || it == '-' }
        return if (firstWord.isEmpty()) null else legacyHeadings[firstWord.lowercase()]
    }

    private fun sectionType(environment: String) = when (val name = environment.lowercase()) {
        VERSE -> SectionType.Verse
        CHORUS -> SectionType.Chorus
        BRIDGE -> SectionType.Bridge
        else -> SectionType.Custom(name)
    }

    /** The way an environment says its lines are written, or null for the environments that are sections. */
    private fun lineMode(environment: String) = when (environment.lowercase()) {
        TAB -> LineMode.TAB
        GRID -> LineMode.GRID
        else -> null
    }

    internal fun parseLyrics(rawLine: String): ChordProLine.Lyrics {
        val text = StringBuilder()
        val chords = mutableListOf<ChordProLine.Lyrics.Chord>()
        var consumedUntil = 0
        ChordProDirectives.brackets(rawLine).forEach { bracket ->
            text.append(rawLine, consumedUntil, bracket.range.first)
            val content = bracket.content.trim()
            if (content.isNotEmpty()) {
                val isAnnotation = content.startsWith(ANNOTATION_MARKER)
                chords += ChordProLine.Lyrics.Chord(
                    position = text.length,
                    name = if (isAnnotation) content.substring(1) else content,
                    isAnnotation = isAnnotation,
                )
            }
            consumedUntil = bracket.range.last + 1
        }
        text.append(rawLine, consumedUntil, rawLine.length)
        return ChordProLine.Lyrics(text = text.toString(), chords = chords)
    }

    private const val SOURCE_COMMENT = "#"
    private const val DIRECTIVE_START = "{"
    private const val ANNOTATION_MARKER = "*"
    private const val VERSE = "verse"
    private const val CHORUS = "chorus"
    private const val BRIDGE = "bridge"
    private const val TAB = "tab"
    private const val GRID = "grid"
    private const val GERMAN_LETTER = 'H'
    private const val TRANSPOSE = "transpose"
    private val SPELLING_SUFFIXES = setOf('s', 'f')
}
