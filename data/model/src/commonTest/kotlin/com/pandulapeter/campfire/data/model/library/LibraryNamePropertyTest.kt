/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The promises of [LibraryFiles.normalizedName] and [LibraryFiles.identityKey] held against generated names: titles
 * mixing Latin with accents and loose marks, apostrophes, `&` and `+`, credits, other scripts, emoji, surrogate pairs,
 * and runs long enough to reach the byte cap. Each seed is fixed, so a failure names the input that broke the rule.
 */
internal class LibraryNamePropertyTest {

    @Test
    fun `a normalized name is composed, capped, and survives being normalized again`() = forEachGenerated { base ->
        val once = LibraryFiles.normalizedName(base)
        val twice = LibraryFiles.normalizedName(once)
        assertEquals(once, twice, "Not idempotent for ${base.escaped()}: ${once.escaped()} became ${twice.escaped()}")
        assertEquals(once.normalizedToNfc(), once, "Not composed for ${base.escaped()}")
        assertTrue(once.encodeToByteArray().size <= LibraryFiles.MAX_NAME_BYTES, "Over the cap for ${base.escaped()}")
        assertTrue(once.isNotEmpty(), "Empty for ${base.escaped()}")
        assertTrue(once.hasOnlyWholePairs(), "Split pair for ${base.escaped()}")
    }

    @Test
    fun `a song name survives being built from its own halves again`() = forEachGenerated { base ->
        val title = base.take(base.length / 2)
        val artist = base.drop(base.length / 2)
        val name = LibraryFiles.songBaseName(title = title, artist = artist)
        val normalizedTitle = LibraryFiles.normalizedName(title)
        val normalizedArtist = LibraryFiles.normalizedName(artist)
        assertEquals(name, LibraryFiles.songBaseName(title = normalizedTitle, artist = if (artist.isBlank()) "" else normalizedArtist))
    }

    @Test
    fun `names differing only by case or composition share an identity key`() = forEachGenerated { base ->
        // Each letter's case chosen by the letter itself, so that a shrunk input still meets the same variant. Only a
        // letter whose two cases map back to each other, and that no mark follows, is flipped. The key composes before
        // it folds, as the JVM's ignore-case comparison of composed names does, and the two do not commute: I and a dot
        // above compose to İ, which is not i with a dot above, and j with a caron composes to ǰ, which has no capital.
        val variant = decomposed(
            base.mapIndexed { index, character ->
                val isCaseRoundTrip = character.uppercaseChar().lowercaseChar() == character.lowercaseChar() &&
                    character.lowercaseChar().uppercaseChar() == character.uppercaseChar()
                val isBeforeMark = base.getOrNull(index + 1)?.category in MARK_CATEGORIES
                when {
                    !isCaseRoundTrip || isBeforeMark -> character
                    character.code % 3 == 0 -> character.uppercaseChar()
                    character.code % 3 == 1 -> character.lowercaseChar()
                    else -> character
                }
            }.joinToString(""),
        )
        assertTrue(LibraryFiles.isSameLibraryName(base, variant), "${base.escaped()} and ${variant.escaped()} are two names")
    }

    /** Runs [check] on every generated name, reporting the seed and the input of the first one that fails. */
    private fun forEachGenerated(check: (String) -> Unit) = SEEDS.forEach { seed ->
        val random = Random(seed)
        repeat(ITERATIONS) { iteration ->
            val base = random.nextName()
            if (!base.passes(check)) {
                val shrunk = base.shrunk(check)
                val message = runCatching { check(shrunk) }.exceptionOrNull()?.message
                fail("Seed $seed, iteration $iteration, shrunk to ${shrunk.escaped()}: $message")
            }
        }
    }

    private fun String.passes(check: (String) -> Unit) = runCatching { check(this) }.exceptionOrNull() !is AssertionError

    /** The shortest input still failing [check] that dropping single characters, then whole surrogate pairs, reaches. */
    private fun String.shrunk(check: (String) -> Unit): String {
        var current = this
        var isShrinking = true
        while (isShrinking) {
            isShrinking = false
            for (width in listOf(8, 2, 1)) {
                var index = 0
                while (index < current.length) {
                    val candidate = current.removeRange(index, minOf(current.length, index + width))
                    if (!candidate.passes(check)) {
                        current = candidate
                        isShrinking = true
                    } else {
                        index++
                    }
                }
            }
        }
        return current
    }

    private fun Random.nextName(): String {
        val length = if (nextInt(10) == 0) nextInt(40, 160) else nextInt(0, 12)
        return buildString { repeat(length) { append(nextAtom()) } }
    }

    private fun Random.nextAtom(): String = when (nextInt(14)) {
        0, 1 -> ('a' + nextInt(26)).toString().let { if (nextBoolean()) it.uppercase() else it }
        2 -> LATIN_ACCENTED.random(this).toString()
        3 -> COMBINING_MARKS.random(this).toString()
        4 -> "'’ʼ".random(this).toString()
        5 -> listOf("&", "+", " & ", " + ").random(this)
        6 -> listOf(" feat. ", " ft ", " featuring ", " Feat ", "feather", "feat").random(this)
        7 -> listOf(" ", "  ", "\t", " ", "-", "_", ".", ",", "!", "(", ")").random(this)
        8 -> FOREIGN.random(this).toString()
        9 -> FOREIGN_MARKS.random(this).toString()
        10 -> SUPPLEMENTARY.random(this)
        11 -> ('0' + nextInt(10)).toString()
        12 -> SPECIAL_LETTERS.random(this).toString()
        else -> "a".repeat(nextInt(1, 40)) + (if (nextBoolean()) " " else "")
    }

    /** [text] with each composed letter of [DECOMPOSITIONS] spelled the way macOS hands it back. */
    private fun decomposed(text: String) = DECOMPOSITIONS.entries.fold(text) { result, (composed, decomposed) -> result.replace(composed, decomposed) }

    private fun String.hasOnlyWholePairs(): Boolean {
        var index = 0
        while (index < length) {
            if (this[index].isHighSurrogate()) {
                if (getOrNull(index + 1)?.isLowSurrogate() != true) return false
                index += 2
            } else if (this[index].isLowSurrogate()) {
                return false
            } else {
                index++
            }
        }
        return true
    }

    private fun String.escaped() = "\"" + map { if (it.code in 0x20..0x7E) it.toString() else "\\u" + it.code.toString(16).padStart(4, '0') }.joinToString("") + "\""

    private companion object {
        val SEEDS = 1..8
        val MARK_CATEGORIES = setOf(CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK)
        const val ITERATIONS = 500
        const val LATIN_ACCENTED = "áéíóöőúüűÁÉÍÓÖŐÚÜŰçÇñÑåÅøØăâîșțĂÂÎȘȚşţĳǅǈǋİıẞßæœþðđłŁəɛɔŋ"
        const val COMBINING_MARKS = "̧̨̀́̂̃̆̇̈̊̋̌"
        const val FOREIGN = "שלוםאבגדהкиноЖЁёЙйΩΑΒΓΔσςΣ日本語漢字한국어가ㄱㅏاليةअकि"
        const val FOREIGN_MARKS = "ְִּَّिे゙゚्ั"
        val SUPPLEMENTARY = listOf("𝄞", "😀", "👍🏽", "𠀀", "𐐀", "𐐨", "𞤀", "𝐀", "󠁁", "🇭🇺")
        const val SPECIAL_LETTERS = "ǆǳǉǌǣǽẞⱥꞠꬰ"
        val DECOMPOSITIONS = mapOf(
            "á" to "á",
            "é" to "é",
            "ö" to "ö",
            "ő" to "ő",
            "Á" to "Á",
            "й" to "й",
            "Й" to "Й",
            "ё" to "ё",
            "가" to "가",
            "한" to "한",
        )
    }
}
