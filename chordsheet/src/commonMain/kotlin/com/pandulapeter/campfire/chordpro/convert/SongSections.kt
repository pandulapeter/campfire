/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import kotlin.math.abs

/**
 * The looks a song's lyrics were set in so far, read in order: what sets a section apart from the verses, where the
 * lyrics return from it, and which section a later heading recalls. It writes nothing; [SongStructure] tells it where
 * sections open and close.
 */
internal class SongSections {

    /** The lyrics outside a section since one last closed: the verses a section may be set apart from. */
    private val verseLooks = mutableListOf<ChordSheetLook>()
    private var isVerseInterrupted = false

    /** The open section's lyrics so far, and the verses where it opened. */
    private val sectionLooks = mutableListOf<ChordSheetLook>()
    private var sectionVerses: List<ChordSheetLook> = emptyList()

    /** The look each kind of section was first set apart from the verses in, and the words of its lines. */
    private val setApart = mutableMapOf<String, Pair<ChordSheetLook, Set<String>>>()
    private val sectionWords = mutableSetOf<String>()

    /**
     * Whether a heading of [type] whose first lyric is [text] set in [look] recalls the section: the song set that
     * section apart before, and the lyric is neither set that way nor one of its lines written out again.
     */
    fun isRecall(type: String, look: ChordSheetLook?, text: String) = setApart[type]?.let { (setLook, words) ->
        look != null && setLook.isSetApartFrom(look) && words(text) !in words
    } == true

    private fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString(" ")

    fun opened() {
        sectionLooks.clear()
        sectionVerses = verseLooks.toList()
        isVerseInterrupted = true
        sectionWords.clear()
    }

    /** A chorus no heading names, set apart by its type alone, which a return to plain type ends. */
    fun openedUnlabelled() {
        opened()
        sectionVerses = emptyList()
    }

    /**
     * Whether a lyric of [look] ends the open section: a section set apart from the verses ends where the lyrics return to
     * their look. With no verse before it to compare with, only a return from bold or italic to plain type ends it, since
     * lines of a centred sheet each start somewhere else and a line in another type inside it is a backing vocal or an
     * aside. Only a section that holds lyrics ends this way.
     */
    fun endsAt(look: ChordSheetLook): Boolean {
        if (sectionLooks.isEmpty()) return false
        val opened = sectionLooks.first()
        // With no verse to compare with, plain type is the one look known to be the verses'.
        return if (sectionVerses.isEmpty()) (opened.isBold || opened.isItalic) && !look.isBold && !look.isItalic else {
            isSetApart(sectionVerses, sectionLooks) && returnsToVerses(look, sectionVerses)
        }
    }

    /** A lyric of [look] reading [text], sung inside a section where [isInSection], else among the verses. */
    fun record(look: ChordSheetLook, text: String, isInSection: Boolean) {
        if (!isInSection) {
            if (isVerseInterrupted) {
                verseLooks.clear()
                isVerseInterrupted = false
            }
            verseLooks += look
        } else {
            sectionLooks += look
            sectionWords += words(text)
        }
    }

    /**
     * Whether [section] is set apart from [verses]: its first lyric in another type, or two lyrics or more all starting
     * further in (or out) than the edge the verses share. A lyric alone shows no edge, so one indented by accident
     * sets nothing apart, and a centred sheet's verses share none.
     */
    private fun isSetApart(verses: List<ChordSheetLook>, section: List<ChordSheetLook>): Boolean {
        val verse = verses.last()
        if (section.first().differsInType(verse)) return true
        val edge = ChordSheetLook.sharedEdge(verses) ?: return false
        if (section.size < 2) return false
        val margin = maxOf(section.maxOf { it.size }, verse.size) * 1.5
        val insets = section.map { it.left - edge }
        return insets.all { it > margin } || insets.all { it < -margin }
    }

    /** Whether [look] is back in the verses' type and, where they share an edge, at it. */
    private fun returnsToVerses(look: ChordSheetLook, verses: List<ChordSheetLook>): Boolean {
        val verse = verses.last()
        if (look.differsInType(verse)) return false
        val edge = ChordSheetLook.sharedEdge(verses) ?: return true
        return abs(look.left - edge) <= maxOf(look.size, verse.size) * 1.5
    }

    /**
     * Closes the open section of [type], recording its look only where the song showed it set apart: against verses
     * outside any section, or by a lyric returning from it ([isReturn]). In a song that heads every section the next
     * heading closes a chorus, so nothing is recorded and a later chorus heading over new words is that chorus written
     * out.
     */
    fun closed(type: String, isReturn: Boolean) {
        if (sectionLooks.isNotEmpty() && (isReturn || sectionVerses.isNotEmpty() && isSetApart(sectionVerses, sectionLooks))) {
            setApart.getOrPut(type) { sectionLooks.first() to sectionWords.toSet() }
        }
        sectionLooks.clear()
    }
}
