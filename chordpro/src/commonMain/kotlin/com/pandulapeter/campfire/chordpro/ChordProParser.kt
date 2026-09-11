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

    /** Only scans directive lines, so that the song list can afford to call it for every file in the library. */
    fun parseMetadata(text: String): ChordProMetadata {
        val metadata = MetadataBuilder()
        ChordProSyntax.splitLines(text).forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.startsWith(SOURCE_COMMENT) || !trimmedLine.startsWith("{")) return@forEach
            val directive = ChordProSyntax.matchDirective(trimmedLine) ?: return@forEach
            if (!ChordProSyntax.hasSelectorSuffix(directive.name)) {
                metadata.consume(directive)
            }
        }
        return metadata.build()
    }

    /** True as soon as a real chord (not an `[*annotation]`, not an empty `[]`) shows up outside a tab environment. */
    fun hasChords(text: String): Boolean {
        var environment: String? = null
        ChordProSyntax.splitLines(text).forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.startsWith(SOURCE_COMMENT)) return@forEach
            val directive = ChordProSyntax.matchDirective(trimmedLine)
            when {
                directive != null -> if (!ChordProSyntax.hasSelectorSuffix(directive.name)) {
                    ChordProSyntax.startOfEnvironment(directive.name)?.let { environment = it.lowercase() }
                    ChordProSyntax.endOfEnvironment(directive.name)?.let { environment = null }
                }

                environment == TAB -> Unit
                environment == GRID -> if (ChordProSyntax.parseGridTokens(trimmedLine).any { it is GridToken.Chord }) return true
                else -> if (parseLyrics(rawLine).chords.any { !it.isAnnotation }) return true
            }
        }
        return false
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
            section.close()
            section.open(sectionType(environment), ChordProSyntax.label(directive.value), isExplicit = true)
            return
        }
        if (ChordProSyntax.endOfEnvironment(name) != null) {
            section.close()
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
        TAB -> SectionType.Tab
        GRID -> SectionType.Grid
        else -> SectionType.Custom(name)
    }

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
        private val lines = mutableListOf<ChordProLine>()

        var isExplicit = false
            private set

        fun open(type: SectionType, label: String?, isExplicit: Boolean) {
            this.type = type
            this.label = label
            this.isExplicit = isExplicit
            lines.clear()
        }

        fun close() {
            val type = type ?: return
            while (lines.isNotEmpty() && lines.last() == ChordProLine.Blank) {
                lines.removeAt(lines.lastIndex)
            }
            if (lines.isNotEmpty() || type == SectionType.Tab || type == SectionType.Grid) {
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
                    isExplicit -> lines += ChordProLine.Blank
                    else -> close() // A blank line ends an implicit paragraph or a legacy heading section.
                }
                return
            }
            if (type == null) {
                open(SectionType.Paragraph, label = null, isExplicit = false)
            }
            lines += when (type) {
                SectionType.Tab -> ChordProLine.Tab(rawLine)
                SectionType.Grid -> ChordProLine.Grid(ChordProSyntax.parseGridTokens(trimmedLine))
                else -> parseLyrics(rawLine)
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
                "meta" -> {
                    val name = value.substringBefore(' ').trim()
                    when {
                        name.equals(ChordProSyntax.TAG_NAME, ignoreCase = true) -> ChordProSyntax.tag(directive)?.let(::addTag)
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
            custom = custom.mapValues { it.value.toList() },
        )
    }

    private const val SOURCE_COMMENT = "#"
    private const val ANNOTATION_MARKER = "*"
    private const val CUSTOM_PREFIX = "x_"
    private const val VERSE = "verse"
    private const val CHORUS = "chorus"
    private const val BRIDGE = "bridge"
    private const val TAB = "tab"
    private const val GRID = "grid"
}
