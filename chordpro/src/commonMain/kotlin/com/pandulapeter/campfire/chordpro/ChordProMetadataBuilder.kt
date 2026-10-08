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

import com.pandulapeter.campfire.chordpro.model.ChordDefinition
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata

/**
 * The value of a field a song can change mid-song — its key, tempo or time signature — of which a later line is a
 * change from where it stands rather than a second value: the song starts in the first one its header names. An
 * empty line, the new song template's, says nothing rather than taking back what another one said, but it still
 * keeps the body out of the header's place, which is how a value cleared in the Song defaults sheet stays cleared
 * rather than taking the first change in the body for the song's own. A line in the body counts only for a song
 * whose header has no line of that field at all, so that a file that writes its metadata at the bottom is still
 * read.
 */
internal class ChangeableValue(private val isReadable: (String) -> Boolean = { true }) {
    var value: String? = null
        private set
    private var hasHeaderLine = false

    fun consume(written: String, isInBody: Boolean) {
        if (!isInBody) hasHeaderLine = true
        if (takes(written, isInBody)) value = written
    }

    /** Whether [written] would become the song's own value, which makes it no change from where it stands. */
    fun takes(written: String, isInBody: Boolean): Boolean {
        if (isInBody && hasHeaderLine) return false
        val current = value
        // A value the song cannot read is as good as missing (the editor marks it so), and the next readable one stands in.
        return current.isNullOrEmpty() || (!isReadable(current) && isReadable(written))
    }
}

/**
 * The value of a field a song can only say once — its title, its year, its capo — which is the first line of it
 * that says anything, wherever in the file it stands: a later one contradicts it, and the editor marks it so. An
 * empty line or one the song cannot read is as good as missing, and the next one that says something stands in.
 */
internal class OnceValue(private val isReadable: (String) -> Boolean = { true }) {
    var value: String? = null
        private set

    fun consume(written: String) {
        val current = value
        if (current.isNullOrEmpty() || (!isReadable(current) && isReadable(written))) value = written
    }
}

internal class MetadataBuilder {

    private val title = OnceValue()
    private val subtitle = OnceValue()
    private val artist = OnceValue()
    private val composer = OnceValue()
    private val lyricist = OnceValue()
    private val album = OnceValue()
    private val year = OnceValue()
    private var coverArt: String? = null
    private val key = ChangeableValue()
    private var capo: Int? = null
    val tempo = ChangeableValue { ChordProTempo.parse(it) != null }
    val time = ChangeableValue { ChordProTime.parse(it) != null }
    private val duration = OnceValue { ChordProDuration.parse(it) != null }
    private val tags = mutableListOf<String>()
    private val tagKeys = mutableSetOf<String>()
    private val languages = mutableListOf<String>()
    private val languageSet = mutableSetOf<String>()
    private val links = mutableListOf<ChordProLink>()
    private val definitions = mutableListOf<ChordDefinition>()
    private val defined = mutableSetOf<Pair<String, ChordInstrument>>()
    private val custom = mutableMapOf<String, MutableList<String>>()

    /** @param isInBody Whether [directive] stands in the body of the song rather than in its header. */
    fun consume(directive: ChordProDirectives.Directive, isInBody: Boolean) {
        val value = directive.value?.trim().orEmpty()
        when (directive.name) {
            "title", "t" -> title.consume(value)
            "subtitle", "st" -> subtitle.consume(value)
            "artist" -> artist.consume(value)
            "composer" -> composer.consume(value)
            "lyricist" -> lyricist.consume(value)
            "album" -> album.consume(value)
            "year" -> year.consume(value)
            "key" -> key.consume(value, isInBody)
            "capo" -> if (capo == null) capo = value.toIntOrNull()?.takeIf { it >= 0 }
            "tempo" -> tempo.consume(value, isInBody)
            "time" -> time.consume(value, isInBody)
            "duration" -> duration.consume(value)
            "tag" -> ChordProMetaItems.tag(directive)?.let(::addTag)
            "language", "lang" -> ChordProMetaItems.language(directive)?.let(::addLanguage)
            "meta" -> {
                // The spec defines these as the standalone directive, so they are read as one: a song whose header is
                // all `{meta: title …}` lines is titled, named and keyed by it like any other.
                ChordProMetaItems.standardMeta(directive)?.let {
                    consume(it, isInBody)
                    return
                }
                val name = value.substringBefore(' ').trim()
                when {
                    name.equals(ChordProMetaItems.TAG_NAME, ignoreCase = true) -> ChordProMetaItems.tag(directive)?.let(::addTag)
                    // The language is read as its own thing rather than as one more custom item, so that it is
                    // not carried twice; an unusable value drops out here instead of coming back as a filter
                    // group nothing can be named.
                    ChordProMetaItems.isLanguageMeta(directive) -> ChordProMetaItems.language(directive)?.let(::addLanguage)
                    // The same goes for the cover, the first usable one of which is the song's.
                    ChordProMetaItems.isCoverMeta(directive) -> if (coverArt == null) coverArt = ChordProMetaItems.cover(directive)
                    // And for the links, every usable one of which is kept, once each.
                    ChordProMetaItems.isLinkMeta(directive) -> ChordProMetaItems.link(directive)?.let { if (links.none { link -> link.url == it.url }) links += it }
                    name.isNotEmpty() -> custom.getOrPut(name) { mutableListOf() } += value.substringAfter(' ', missingDelimiterValue = "").trim()
                }
            }

            else -> if (directive.name.startsWith(CUSTOM_PREFIX) && directive.name.length > CUSTOM_PREFIX.length) {
                custom.getOrPut(directive.name) { mutableListOf() } += value
            }
        }
    }

    /**
     * A later `{define}` of the same chord on the same instrument takes the place of the earlier shape, where that
     * stood. A `{chord}`, which the specification has show a diagram only where it stands, counts only where the song
     * defines none: it takes the place of another `{chord}`'s shape, never of a `{define}`'s.
     */
    fun addDefinition(definition: ChordDefinition, isDefine: Boolean) {
        val key = definition.name to definition.instrument
        if (!isDefine && key in defined) return
        if (isDefine) defined += key
        val index = definitions.indexOfFirst { it.name == definition.name && it.instrument == definition.instrument }
        if (index < 0) definitions += definition else definitions[index] = definition
    }

    /** A tag the song already carries in another spelling is not a second tag, see [ChordProMetadata.tags]. */
    private fun addTag(value: String) {
        if (tagKeys.add(ChordProMetaItems.caseInsensitiveKey(value))) tags += value
    }

    /** The codes are normalized before they get here, so a repeated language is a repeated string. */
    private fun addLanguage(value: String) {
        if (languageSet.add(value)) languages += value
    }

    fun build(transpose: Int) = ChordProMetadata(
        title = title.value,
        subtitle = subtitle.value,
        artist = artist.value,
        composer = composer.value,
        lyricist = lyricist.value,
        album = album.value,
        year = year.value,
        coverArt = coverArt,
        key = key.value,
        capo = capo,
        tempo = tempo.value,
        time = time.value,
        duration = duration.value,
        transpose = transpose,
        tags = tags.toList(),
        languages = languages.toList(),
        links = links.toList(),
        definitions = definitions.toList(),
        custom = custom.mapValues { it.value.toList() },
    )
}

private const val CUSTOM_PREFIX = "x_"
