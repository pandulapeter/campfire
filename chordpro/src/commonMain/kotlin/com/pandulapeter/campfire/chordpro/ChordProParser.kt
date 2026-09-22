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
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
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

    /** The song in the notation the app works in, whichever one its file is written in. */
    fun parse(text: String) = ChordProNotation.normalized(parseAsWritten(text))

    /** The song with every chord spelled the way its file spells it. */
    internal fun parseAsWritten(text: String): ChordProSong {
        val metadata = MetadataBuilder()
        val blocks = mutableListOf<ChordProBlock>()
        val section = SectionBuilder(blocks)
        ChordProSyntax.splitLines(text).forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.startsWith(SOURCE_COMMENT)) return@forEach
            val directive = ChordProSyntax.matchDirective(trimmedLine)
            if (directive == null) {
                section.addContent(rawLine, trimmedLine)
            } else {
                handleDirective(directive, metadata, blocks, section)
            }
        }
        section.close()
        return ChordProSong(metadata = metadata.build(), blocks = blocks)
    }

    /** Only scans directive lines, so that it is cheap enough for a caller that has no interest in the body. */
    fun parseMetadata(text: String) = scan(text, shouldDetectChords = false).metadata

    /**
     * The directives of a song and whether it has any chords, from a single walk over the text. The library scan
     * wants both for every file it reads, and asking for them separately walks each file twice.
     */
    fun summarize(text: String) = scan(text, shouldDetectChords = true)

    /**
     * @param shouldDetectChords Whether the lines that are not directives are looked at as well. A real chord (not an
     *   `[*annotation]`, not an empty `[]`) outside a tab environment is what counts as one; once one has been found
     *   the rest of the body is skipped, since nothing later in the file can change the answer.
     */
    private fun scan(text: String, shouldDetectChords: Boolean): ChordProSummary {
        val metadata = MetadataBuilder()
        var hasChords = false
        var environment: String? = null
        var isGermanNotated = false
        ChordProSyntax.splitLines(text).forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.startsWith(SOURCE_COMMENT)) return@forEach
            val directive = if (trimmedLine.startsWith(DIRECTIVE_START)) ChordProSyntax.matchDirective(trimmedLine) else null
            if (directive != null) {
                if (!ChordProSyntax.hasSelectorSuffix(directive.name)) {
                    ChordProSyntax.startOfEnvironment(directive.name)?.let { environment = it.lowercase() }
                    ChordProSyntax.endOfEnvironment(directive.name)?.let { environment = null }
                    metadata.consume(directive)
                }
                return@forEach
            }
            val isLookingForChords = shouldDetectChords && !hasChords
            val isLookingForNotation = !isGermanNotated && GERMAN_LETTER in rawLine
            if (!isLookingForChords && !isLookingForNotation) return@forEach
            val names = writtenChordNames(rawLine, trimmedLine, environment)
            if (isLookingForChords && environment != TAB) hasChords = names.isNotEmpty()
            if (isLookingForNotation) isGermanNotated = names.any(ChordProNotation::isGermanName)
        }
        val declared = metadata.build()
        val isGermanKey = declared.key?.let(ChordProNotation::isGermanName) == true
        val key = declared.key?.let { key ->
            ChordProNotation.withAsciiAccidentals(if (isGermanNotated || isGermanKey) ChordProNotation.fromGerman(key) else key)
        }
        return ChordProSummary(
            metadata = declared.copy(key = key),
            hasChords = hasChords,
        )
    }

    /** The names one line of the body hands to a chord rewrite, by the environment it stands in. */
    private fun writtenChordNames(rawLine: String, trimmedLine: String, environment: String?) = when (environment) {
        TAB -> ChordProTabTransposer.chordNames(listOf(rawLine))
        GRID -> ChordProSyntax.parseGridTokens(trimmedLine).filterIsInstance<GridToken.Chord>().map { it.name }
        else -> parseLyrics(rawLine).chords.filter { !it.isAnnotation }.map { it.name }
    }

    private fun handleDirective(
        directive: ChordProSyntax.Directive,
        metadata: MetadataBuilder,
        blocks: MutableList<ChordProBlock>,
        section: SectionBuilder,
    ) {
        val name = directive.name
        if (ChordProSyntax.hasSelectorSuffix(name)) return
        ChordProSyntax.startOfEnvironment(name)?.let { environment ->
            // Tablature and grids are how the next few lines are written, not a section of their own: they open
            // inside whatever section is running, and a song with a solo written as a line of chords over a tab is
            // one section rather than three.
            lineMode(environment)?.let { mode ->
                section.openLineMode(mode, ChordProSyntax.label(directive.value))
                return
            }
            section.close()
            section.open(sectionType(environment), ChordProSyntax.label(directive.value), isExplicit = true)
            return
        }
        ChordProSyntax.endOfEnvironment(name)?.let { environment ->
            if (lineMode(environment) == null) section.close() else section.closeLineMode()
            return
        }
        when (name) {
            "chorus" -> {
                val recall = ChordProBlock.ChorusRecall(ChordProSyntax.label(directive.value))
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
            "new_page", "np", "new_physical_page", "npp", "column_break", "colb" -> section.addBlock(ChordProBlock.Break)
            "new_song", "ns" -> Unit // Splitting is ChordProSplitter's job.
            else -> metadata.consume(directive)
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

    private enum class LineMode { TAB, GRID }

    internal fun parseLyrics(rawLine: String): ChordProLine.Lyrics {
        val text = StringBuilder()
        val chords = mutableListOf<ChordProLine.Lyrics.Chord>()
        var consumedUntil = 0
        ChordProSyntax.brackets(rawLine).forEach { bracket ->
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

    /**
     * Accumulates the lines of the section that is currently open. Sections are appended to [blocks] when they close,
     * so that a comment or a break appearing inside one can be emitted in the right order.
     */
    private class SectionBuilder(private val blocks: MutableList<ChordProBlock>) {

        private var type: SectionType? = null
        private var label: String? = null
        private var lineMode: LineMode? = null
        private val lines = mutableListOf<ChordProLine>()

        /**
         * The `{comment}` a legacy heading section was opened by, which is what is left of it if no line ever
         * follows: a comment that happens to start with a section name is still a line of the user's file, and a
         * section with nothing in it has nowhere to show it.
         */
        private var headingText: String? = null

        var isExplicit = false
            private set

        /** Whether a `{start_of_tab}` or a `{start_of_grid}` is open, which says how lines are read and not what section they are in. */
        val isInLineMode get() = lineMode != null

        fun open(type: SectionType, label: String?, isExplicit: Boolean, headingText: String? = null) {
            this.type = type
            this.label = label
            this.isExplicit = isExplicit
            this.headingText = headingText
            lines.clear()
        }

        /**
         * A tab or grid environment starts. It never opens a section of its own, but it does need one to live in,
         * so a file that puts tablature outside every environment gets the same implicit paragraph a bare line of
         * lyrics would get - carrying the environment's own label, which is the only place `{start_of_tab: Riff}`
         * can still say "Riff". Inside a section that is already running there is nowhere for a second label to go,
         * and the section's own wins.
         */
        fun openLineMode(mode: LineMode, label: String?) {
            if (type == null) open(SectionType.Paragraph, label = label, isExplicit = false)
            lineMode = mode
        }

        fun closeLineMode() {
            lineMode = null
        }

        fun close() {
            lineMode = null
            val type = type ?: return
            while (lines.isNotEmpty() && lines.last() == ChordProLine.Blank) {
                lines.removeAt(lines.lastIndex)
            }
            if (lines.isNotEmpty()) {
                blocks += ChordProBlock.Section(type = type, label = label, lines = lines.toList())
            } else {
                headingText?.let { blocks += ChordProBlock.Comment(it, CommentStyle.PLAIN) }
            }
            this.type = null
            label = null
            isExplicit = false
            headingText = null
            lines.clear()
        }

        /**
         * Emits a standalone block without losing the section around it: the section is flushed and then reopened.
         * Only a section with lines in it is flushed, so the heading of the one being reopened has already been shown
         * as its label, and the reopened half does not carry [headingText]: were nothing to follow the block, it
         * would otherwise come back as a comment repeating that label. A tab or grid environment that is open carries
         * on in the reopened half.
         */
        fun addBlock(block: ChordProBlock) {
            if (type != null && lines.isNotEmpty()) {
                val type = this.type!!
                val label = this.label
                val isExplicit = this.isExplicit
                // The block interrupts the section and not the tab or grid environment the file is in the middle of:
                // that one ends at its own `{end_of_…}`, which is where the text transposition, the summary and the
                // highlighter end it as well.
                val lineMode = this.lineMode
                close()
                blocks += block
                open(type, label, isExplicit)
                this.lineMode = lineMode
            } else {
                blocks += block
            }
        }

        fun addContent(rawLine: String, trimmedLine: String) {
            if (trimmedLine.isEmpty()) {
                when {
                    type == null -> Unit
                    isExplicit || lineMode != null -> lines += ChordProLine.Blank
                    else -> close() // A blank line ends an implicit paragraph or a legacy heading section.
                }
                return
            }
            if (type == null) {
                open(SectionType.Paragraph, label = null, isExplicit = false)
            }
            lines += when (lineMode) {
                LineMode.TAB -> ChordProLine.Tab(rawLine)
                LineMode.GRID -> ChordProLine.Grid(ChordProSyntax.parseGridTokens(trimmedLine))
                null -> parseLyrics(rawLine)
            }
        }
    }

    private class MetadataBuilder {

        private var title: String? = null
        private var subtitle: String? = null
        private var artist: String? = null
        private var composer: String? = null
        private var lyricist: String? = null
        private var album: String? = null
        private var year: String? = null
        private var key: String? = null
        private var capo: Int? = null
        private var tempo: String? = null
        private var time: String? = null
        private var duration: String? = null
        private var transpose = 0
        private val tags = mutableListOf<String>()
        private val tagKeys = mutableSetOf<String>()
        private val languages = mutableListOf<String>()
        private val languageSet = mutableSetOf<String>()
        private val custom = mutableMapOf<String, MutableList<String>>()

        fun consume(directive: ChordProSyntax.Directive) {
            val value = directive.value?.trim().orEmpty()
            when (directive.name) {
                "title", "t" -> title = value
                "subtitle", "st" -> subtitle = value
                "artist" -> artist = value
                "composer" -> composer = value
                "lyricist" -> lyricist = value
                "album" -> album = value
                "year" -> year = value
                "key" -> key = value
                "capo" -> value.toIntOrNull()?.let { capo = it }
                "tempo" -> tempo = value
                "time" -> time = value
                "duration" -> duration = value
                "transpose" -> value.removePrefix("+").toIntOrNull()?.let { transpose = it }
                "tag" -> ChordProSyntax.tag(directive)?.let(::addTag)
                "language", "lang" -> ChordProSyntax.language(directive)?.let(::addLanguage)
                "meta" -> {
                    // The spec defines these as the standalone directive, so they are read as one: a song whose header is
                    // all `{meta: title …}` lines is titled, named and keyed by it like any other.
                    ChordProSyntax.standardMeta(directive)?.let {
                        consume(it)
                        return
                    }
                    val name = value.substringBefore(' ').trim()
                    when {
                        name.equals(ChordProSyntax.TAG_NAME, ignoreCase = true) -> ChordProSyntax.tag(directive)?.let(::addTag)
                        // The language is read as its own thing rather than as one more custom item, so that it is
                        // not carried twice; an unusable value drops out here instead of coming back as a filter
                        // group nothing can be named.
                        ChordProSyntax.isLanguageMeta(directive) -> ChordProSyntax.language(directive)?.let(::addLanguage)
                        name.isNotEmpty() -> custom.getOrPut(name) { mutableListOf() } += value.substringAfter(' ', missingDelimiterValue = "").trim()
                    }
                }

                else -> if (directive.name.startsWith(CUSTOM_PREFIX) && directive.name.length > CUSTOM_PREFIX.length) {
                    custom.getOrPut(directive.name) { mutableListOf() } += value
                }
            }
        }

        /** A tag the song already carries in another spelling is not a second tag, see [ChordProMetadata.tags]. */
        private fun addTag(value: String) {
            if (tagKeys.add(ChordProSyntax.caseInsensitiveKey(value))) tags += value
        }

        /** The codes are normalized before they get here, so a repeated language is a repeated string. */
        private fun addLanguage(value: String) {
            if (languageSet.add(value)) languages += value
        }

        fun build() = ChordProMetadata(
            title = title,
            subtitle = subtitle,
            artist = artist,
            composer = composer,
            lyricist = lyricist,
            album = album,
            year = year,
            key = key,
            capo = capo,
            tempo = tempo,
            time = time,
            duration = duration,
            transpose = transpose,
            tags = tags.toList(),
            languages = languages.toList(),
            custom = custom.mapValues { it.value.toList() },
        )
    }

    private const val SOURCE_COMMENT = "#"
    private const val DIRECTIVE_START = "{"
    private const val ANNOTATION_MARKER = "*"
    private const val CUSTOM_PREFIX = "x_"
    private const val VERSE = "verse"
    private const val CHORUS = "chorus"
    private const val BRIDGE = "bridge"
    private const val TAB = "tab"
    private const val GRID = "grid"
    private const val GERMAN_LETTER = 'H'
}
