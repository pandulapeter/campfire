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

import com.pandulapeter.campfire.chordpro.ChordProDirectives.Directive
import com.pandulapeter.campfire.chordpro.ChordProVocabulary.META
import com.pandulapeter.campfire.chordpro.model.ChordProLink

/**
 * The `{meta}` items Campfire reads on top of the standard directives — the tags, the languages, the cover and the links —
 * and the web addresses the last two hold.
 */
internal object ChordProMetaItems {

    /** The name of the `{tag}` directive, which is also the `{meta}` key that spells the same thing. */
    const val TAG_NAME = "tag"

    /** The `{meta}` key a song's language is written under, since ChordPro defines no directive of its own for it. */
    const val LANGUAGE_NAME = "language"

    /** The `{meta}` key a song's cover image is written under, which ChordPro has no directive of its own for either. */
    const val COVER_NAME = "cover"

    /** The `{meta}` key a link about the song is written under, one directive per link. */
    const val LINK_NAME = "link"
    internal const val LANGUAGE_SHORT_NAME = "lang"

    /**
     * The two ISO codes that mean "there is no language here" — undetermined and no linguistic content. They say
     * exactly what a file with no language directive says, so they are read as that rather than as a language of
     * their own, which also leaves `und` free to stand for "declares none" everywhere above this module.
     */
    private val absentLanguageCodes = setOf("und", "zxx")

    /**
     * The tag a directive carries, or null if it is not a tag directive. ChordPro documents `{tag: Needs study}` and
     * `{meta: tag Needs study}` as the same thing, and both are read here; one tag per directive, repeated as many
     * times as the song has tags. The value is taken whole, since the spec calls a tag arbitrary text.
     */
    fun tag(directive: Directive): String? = when (directive.name) {
        TAG_NAME -> directive.value?.trim()
        META -> directive.takeIf(::isTagMeta)?.let(::metaValue)?.trim()

        else -> null
    }?.takeIf { it.isNotEmpty() }

    /**
     * The names the spec defines `{meta: name value}` to mean exactly what the standalone `{name: value}` means, of the
     * ones the model has a field for; the tag and the language are read by [tag] and [language].
     */
    private val standardMetaNames = setOf(
        "title", "subtitle", "artist", "composer", "lyricist", "album", "year", "key", "capo", "tempo", "time", "duration",
    )

    /**
     * The standalone directive a `{meta}` one stands for (`{meta: title Amazing Grace}` is `{title: Amazing Grace}`), or
     * null for every other directive, the custom metadata a `{meta}` may carry included. The name is matched ignoring
     * case, like every directive name.
     */
    fun standardMeta(directive: Directive): Directive? {
        if (directive.name != META) return null
        val value = directive.value?.trim() ?: return null
        val name = value.substringBefore(' ').trim().lowercase()
        return if (name in standardMetaNames) Directive(name, value.substringAfter(' ', missingDelimiterValue = "").trim()) else null
    }

    /**
     * Equal for exactly the strings `equals(ignoreCase = true)` calls equal, char by char, so that it can key a hash set.
     * Not `lowercase()`, which turns a dotted capital I into two characters that no longer match the plain i the
     * comparison takes it for.
     */
    fun caseInsensitiveKey(value: String) = CharArray(value.length) { value[it].uppercaseChar().lowercaseChar() }.concatToString()

    /**
     * The language a directive declares, or null if it is not one. ChordPro defines no directive for it, so what the
     * app writes is `{meta: language en}`; `{meta: lang en}` and the bare `{language: en}` / `{lang: en}` that a hand
     * written file may carry are read as the same thing. What comes back is normalized, see [languageCode].
     */
    fun language(directive: Directive): String? = when (directive.name) {
        LANGUAGE_NAME, LANGUAGE_SHORT_NAME -> directive.value
        META -> directive.takeIf(::isLanguageMeta)?.let(::metaValue)

        else -> null
    }?.let(::languageCode)

    /**
     * A language value as the library files it under: the primary subtag, lowercase, and the two letter code where
     * the standard has one for it.
     *
     * The region is cut off here, where the dialect is decided, for two reasons — `en-US` next to `en` would be a
     * filter group of its own for what is the same language to a song book, and the platforms disagree about what to
     * call one, a browser saying "American English" where the JDK says "English". A three letter ISO 639-2 code is
     * folded into its ISO 639-1 equivalent for the first of those reasons and one more, see
     * [ChordProLanguageCodes]; one that has no such equivalent, which is most of what 639-2 adds, is kept as it is.
     */
    fun languageCode(value: String) = value.trim()
        .substringBefore('-')
        .substringBefore('_')
        .lowercase()
        .let { ChordProLanguageCodes.twoLetterEquivalents[it] ?: it }
        .takeIf { it.isNotEmpty() && it !in absentLanguageCodes }

    /** True for a `{meta}` directive whose key names the language, whatever its value then turns out to be worth. */
    fun isLanguageMeta(directive: Directive) = metaKey(directive)?.isLanguageKey == true

    /** The same for a `{meta}` directive whose key names a tag, which is the spelling ChordPro documents next to `{tag}`. */
    internal fun isTagMeta(directive: Directive) = metaKey(directive)?.equals(TAG_NAME, ignoreCase = true) == true

    /**
     * The cover image a directive names, or null if it is not a `{meta: cover …}` one or names nothing the app can load.
     * Any `http` or `https` URL is taken: the library is the user's, and so are the addresses written into it.
     */
    fun cover(directive: Directive): String? = directive.takeIf(::isCoverMeta)?.let(::metaValue)?.let(::webUrl)

    /**
     * [value] as a web address, or null where it is not one: trimmed, and taken only when it is an `http` or `https`
     * URL with no whitespace inside it, since nothing else is a picture the app can ask for or a page a browser opens.
     */
    fun webUrl(value: String): String? = value.trim().takeIf { url ->
        (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) &&
                url.substringAfter("//").isNotEmpty() && url.none { it.isWhitespace() }
    }

    /**
     * [value] as a web address the way a field the user types one into reads it: [webUrl] of the trimmed text, or, for
     * text that starts with a host (and perhaps a port) but no scheme - how a browser's address bar shows most
     * addresses - that text as `https`, since a page or a picture only served over plain `http` is rare enough to be
     * typed out in full. Anything else (`mailto:`, `me@…`, a mistyped `https:/`) is null rather than an `https`
     * address naming nothing the user meant. Files are read with [webUrl] alone: a scheme is only completed for what
     * is being typed.
     */
    fun typedWebUrl(value: String): String? {
        val trimmed = value.trim()
        return webUrl(trimmed) ?: trimmed.takeIf(::startsWithHost)?.let { webUrl("https://$it") }
    }

    /**
     * Whether what [value] holds before its path, query or fragment is a plain host with an optional port: letters,
     * digits, `-` and `.`, with a dot somewhere other than at either end, then at most a `:` and a port number. Written
     * out by hand rather than as a regex, whose character classes the JVM, Kotlin/Native and the browser read differently.
     */
    private fun startsWithHost(value: String): Boolean {
        val authority = value.takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringBefore(':')
        val port = authority.substringAfter(':', missingDelimiterValue = "")
        if (':' in authority && (port.isEmpty() || !port.all { it in '0'..'9' })) return false
        return host.all { it.isLetterOrDigit() || it == '-' || it == '.' } && host.indexOf('.') > 0 && host.lastIndexOf('.') < host.lastIndex
    }

    /** True for a `{meta}` directive whose key names the cover image, whatever its value then turns out to be worth. */
    fun isCoverMeta(directive: Directive) = metaKey(directive)?.equals(COVER_NAME, ignoreCase = true) == true

    /**
     * The address and optional name a `{meta: link …}` directive holds, or null if it is not one or names nothing a browser opens. Any
     * `http` or `https` URL is taken, for the same reason as a cover's.
     */
    fun link(directive: Directive): ChordProLink? {
        val value = directive.takeIf(::isLinkMeta)?.let(::metaValue)?.trim() ?: return null
        val url = webUrl(value.takeWhile { !it.isWhitespace() }) ?: return null
        return ChordProLink(url = url, name = value.drop(url.length).trim().takeIf { it.isNotEmpty() })
    }

    /** True for a `{meta}` directive whose key names a link, whatever its value then turns out to be worth. */
    fun isLinkMeta(directive: Directive) = metaKey(directive)?.equals(LINK_NAME, ignoreCase = true) == true

    /** The key of a `{meta}` directive, the first word of its value, or null for every other directive. */
    private fun metaKey(directive: Directive) = if (directive.name == META) directive.value?.trim()?.substringBefore(' ')?.trim() else null

    /**
     * What a `{meta}` directive holds after its key, with the spaces that separate the two left in front of it, or an empty
     * string where it holds only the key; null where it has no value at all. Meant for a directive whose key has been checked.
     */
    internal fun metaValue(directive: Directive) = directive.value?.trim()?.substringAfter(' ', missingDelimiterValue = "")

    private val String.isLanguageKey get() = equals(LANGUAGE_NAME, ignoreCase = true) || equals(LANGUAGE_SHORT_NAME, ignoreCase = true)
}
