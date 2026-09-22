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

/** Linear recognition and rewriting of the notes in a chord name. */
internal object ChordProChordNames {

    fun isChordName(word: String): Boolean {
        val name = unwrapped(word)
        if (name.isEmpty()) return false
        var index = noteEnd(name, 0)
        if (index < 0) return false
        qualityAt(name, index)?.let { index += it.length }
        val numberStart = index
        index = digitsEnd(name, index)
        if (name.startsWith("alt", index) && index > numberStart) index += 3
        while (index < name.length) {
            when (name[index]) {
                '(' -> index = groupEnd(name, index + 1)
                '/' -> {
                    val digits = digitsEnd(name, index + 1)
                    if (digits > index + 1) index = digits else return noteEnd(name, index + 1) == name.length
                }
                else -> index = alterationEnd(name, index)
            }
            if (index < 0) return false
        }
        return true
    }

    fun notes(name: String): List<String> {
        val chord = unwrapped(name)
        val separator = chord.lastIndexOf('/')
        return if (separator >= 0 && noteEnd(chord, separator + 1) >= 0) listOf(chord.substring(0, separator), chord.substring(separator + 1)) else listOf(chord)
    }

    fun rewriteNotes(name: String, rewrite: (String) -> String): String {
        val rewritten = notes(name).joinToString("/", transform = rewrite)
        return if (isParenthesized(name)) "($rewritten)" else rewritten
    }

    /**
     * [word] as the minor chord a lowercase root stands for in Central European charts — `a` is `Am`, `h7` is `Hm7`,
     * `f#` is `F#m`, `(e)` is `(Em)` — or null for anything else. A suffix that starts with an `m` is not taken, since
     * `am` would say minor twice and `amaj7` would be neither. The bass note after `/` is a note and not a chord, so it
     * is written in capitals as everywhere else.
     */
    fun lowercaseMinorExpanded(word: String): String? {
        val name = unwrapped(word)
        val root = name.firstOrNull()?.takeIf { it in 'a'..'h' } ?: return null
        val noteLength = if (name.getOrNull(1)?.let { it in "#b♯♭" } == true) 2 else 1
        if (name.startsWith("m", noteLength)) return null
        val expanded = root.uppercaseChar() + name.substring(1, noteLength) + "m" + name.substring(noteLength)
        if (!isChordName(expanded)) return null
        return if (isParenthesized(word)) "($expanded)" else expanded
    }

    /** The other way: [name], a minor chord, written with a lowercase root and no `m`. */
    fun lowercaseMinorFolded(name: String): String {
        val chord = unwrapped(name)
        val noteLength = if (chord.getOrNull(1)?.let { it in "#b♯♭" } == true) 2 else 1
        val folded = chord[0].lowercaseChar() + chord.substring(1, noteLength) + chord.substring(noteLength).removePrefix("m")
        return if (isParenthesized(name)) "($folded)" else folded
    }

    private fun isParenthesized(name: String) = name.length > 2 && name.first() == '(' && name.last() == ')'
    private fun unwrapped(name: String) = if (isParenthesized(name)) name.substring(1, name.length - 1) else name
    private fun noteEnd(name: String, index: Int): Int {
        if (name.getOrNull(index) !in 'A'..'H') return -1
        return if (name.getOrNull(index + 1)?.let { it in "#b♯♭" } == true) index + 2 else index + 1
    }
    private fun digitsEnd(name: String, start: Int): Int { var index = start; while (name.getOrNull(index)?.isDigit() == true) index++; return index }
    private fun qualityAt(name: String, index: Int) = qualities.firstOrNull { name.startsWith(it, index) }
    private fun alterationEnd(name: String, index: Int): Int {
        val word = alterations.firstOrNull { name.startsWith(it, index) }
        if (word != null) return digitsEnd(name, index + word.length)
        if (name[index] == '+') return digitsEnd(name, index + 1)
        if (name[index] == '-') return digitsEnd(name, index + 1).takeIf { it > index + 1 } ?: -1
        return -1
    }
    private fun groupEnd(name: String, start: Int): Int {
        var index = start
        var requiresItem = true
        while (index < name.length && name[index] != ')') {
            if (!requiresItem && name[index] == ',') { index++; while (name.getOrNull(index) == ' ') index++; requiresItem = true; continue }
            val number = digitsEnd(name, index)
            index = if (number > index) number else {
                val omission = omissions.firstOrNull { name.startsWith(it, index) }
                if (omission != null) digitsEnd(name, index + omission.length).takeIf { it > index + omission.length } ?: return -1 else alterationEnd(name, index)
            }
            if (index < 0) return -1
            requiresItem = false
        }
        return if (index > start && !requiresItem && name.getOrNull(index) == ')') index + 1 else -1
    }

    private val qualities = listOf("maj", "Maj", "min", "mi", "dim", "aug", "sus", "add", "m", "M", "+", "-", "°", "ø", "Δ", "∆")
    private val alterations = listOf("maj", "Maj", "min", "mi", "dim", "aug", "sus", "add", "M", "#", "b", "♯", "♭")
    private val omissions = listOf("no", "omit")
}
