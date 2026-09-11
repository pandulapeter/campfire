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

    fun parse(text: String): ChordProSong {
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
            if (!shouldDetectChords || hasChords) return@forEach
            hasChords = when (environment) {
                TAB -> false
                GRID -> ChordProSyntax.parseGridTokens(trimmedLine).any { it is GridToken.Chord }
                else -> parseLyrics(rawLine).chords.any { !it.isAnnotation }
            }
        }
        return ChordProSummary(metadata = metadata.build(), hasChords = hasChords)
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
                section.close()
                blocks += ChordProBlock.ChorusRecall(ChordProSyntax.label(directive.value))
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
        if (style == CommentStyle.PLAIN && !section.isExplicit) {
            legacyHeading(text)?.let { type ->
                section.close()
                section.open(type, text, isExplicit = false)
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
        ChordProSyntax.chordRegex.findAll(rawLine).forEach { match ->
            text.append(rawLine, consumedUntil, match.range.first)
            val content = match.groupValues[1].trim()
            if (content.isNotEmpty()) {
                val isAnnotation = content.startsWith(ANNOTATION_MARKER)
                chords += ChordProLine.Lyrics.Chord(
                    position = text.length,
                    name = if (isAnnotation) content.substring(1) else content,
                    isAnnotation = isAnnotation,
                )
            }
            consumedUntil = match.range.last + 1
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

        var isExplicit = false
            private set

        fun open(type: SectionType, label: String?, isExplicit: Boolean) {
            this.type = type
            this.label = label
            this.isExplicit = isExplicit
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
            }
            this.type = null
            label = null
            isExplicit = false
            lines.clear()
        }

        /** Emits a standalone block without losing the section around it: the section is flushed and then reopened. */
        fun addBlock(block: ChordProBlock) {
            if (type != null && lines.isNotEmpty()) {
                val type = this.type!!
                val label = this.label
                val isExplicit = this.isExplicit
                close()
                blocks += block
                open(type, label, isExplicit)
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
        private val languages = mutableListOf<String>()
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
            if (tags.none { it.equals(value, ignoreCase = true) }) tags += value
        }

        /** The codes are normalized before they get here, so a repeated language is a repeated string. */
        private fun addLanguage(value: String) {
            if (value !in languages) languages += value
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
}
