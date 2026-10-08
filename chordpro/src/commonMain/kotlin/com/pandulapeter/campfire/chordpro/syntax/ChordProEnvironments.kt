/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.syntax

/** The `{start_of_…}` and `{end_of_…}` environments and the labels they give their sections, see [startOfEnvironment]. */
internal object ChordProEnvironments {

    /**
     * The `\\s` in these two means the ASCII spaces on the JVM and on Kotlin/Native and every Unicode space in a
     * browser, unlike [ChordProTokens.words]. They are left that way because the difference only ever fails in one direction: a value
     * with a non-breaking space in front of `label=` is not read as attributes on some platforms, and [label] then
     * shows the user's own text as the heading rather than nothing, with nothing half-rewritten.
     */
    private val labelAttributeRegex = Regex("(?:^|\\s)label\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    private val attributeRegex = Regex("\\s*[A-Za-z_][A-Za-z0-9_-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*')")

    const val START_OF_PREFIX = "start_of_"
    const val END_OF_PREFIX = "end_of_"

    internal val startShortNames = mapOf(
        "soc" to "chorus",
        "sov" to "verse",
        "sob" to "bridge",
        "sot" to "tab",
        "sog" to "grid",
    )
    internal val endShortNames = mapOf(
        "eoc" to "chorus",
        "eov" to "verse",
        "eob" to "bridge",
        "eot" to "tab",
        "eog" to "grid",
    )

    /**
     * The environments ChordPro hands to another program — ABC and LilyPond notation, SVG, a block of preformatted
     * text. Their lines are that program's input rather than lyrics: `[CEG]` is an ABC chord of three notes, and moving
     * it as a ChordPro chord would corrupt music nobody asked to change. They are kept line for line and nothing in
     * them is a chord.
     */
    val delegateEnvironments = setOf("abc", "ly", "svg", "textblock")

    /**
     * The label an environment directive gives its section: the whole value (`{sov: Verse 1}`), or its `label`
     * attribute where the value is written as attributes (`{sov label="Verse 1"}`, in either quotes). A value made of
     * attributes that has no label (`{start_of_grid shape="1+4x2+4"}`) gives none, rather than showing the attributes
     * as a heading. An empty label is null.
     */
    fun label(value: String?): String? {
        val trimmedValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!isAttributes(trimmedValue)) return trimmedValue
        val match = labelAttributeRegex.find(trimmedValue) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }.takeIf { it.isNotEmpty() }
    }

    /**
     * Whether [trimmedValue] is nothing but `name="value"` attributes. It is matched one attribute at a time rather than
     * with a single repeated group, which the JVM's regex engine matches recursively and overflows the stack on for a
     * long enough line.
     */
    private fun isAttributes(trimmedValue: String): Boolean {
        var index = 0
        while (index < trimmedValue.length) {
            index = (attributeRegex.matchAt(trimmedValue, index) ?: return false).range.last + 1
        }
        return true
    }

    /** The environment name of a `{start_of_x}` / `{soc}` style directive, or null if this is not one. */
    fun startOfEnvironment(name: String) = startShortNames[name]
        ?: name.takeIf { it.startsWith(START_OF_PREFIX) && it.length > START_OF_PREFIX.length }
            ?.substring(START_OF_PREFIX.length)
            ?.removeSuffix("!")
            ?.let(::withoutSelector)

    /** The environment name of an `{end_of_x}` / `{eoc}` style directive, or null if this is not one. */
    fun endOfEnvironment(name: String) = endShortNames[name]
        ?: name.takeIf { it.startsWith(END_OF_PREFIX) && it.length > END_OF_PREFIX.length }
            ?.substring(END_OF_PREFIX.length)
            ?.removeSuffix("!")
            ?.let(::withoutSelector)

    /**
     * The environments a selector suffix is looked for on. A custom environment may have a dash in its own name
     * (`start_of_pre-chorus`), so only these are taken to have a selector after theirs.
     */
    private val selectableEnvironments = setOf("chorus", "verse", "bridge", "tab", "grid") + delegateEnvironments

    /**
     * [environment] without a selector suffix. Campfire has nothing to match a selector against, and an environment
     * is part of the song itself, so `{start_of_chorus-guitar}` is shown as the chorus it selects rather than left
     * out; its `{end_of_chorus}` carries no selector, as the spec has it.
     */
    private fun withoutSelector(environment: String): String {
        val base = environment.substringBeforeLast('-', missingDelimiterValue = environment)
        return if (base in selectableEnvironments) base else environment
    }
}
