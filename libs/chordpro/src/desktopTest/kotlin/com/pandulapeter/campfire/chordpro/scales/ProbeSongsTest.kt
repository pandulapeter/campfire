/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import java.io.File
import kotlin.test.Test

/**
 * A probe for reviewing the suggestions as a player would, not a check: with `PROBE_CORPUS` naming a file of songs
 * separated by `## Title` lines, each in ChordPro, it writes what the Scales section would show for every one of them
 * next to the corpus as `<name>-output.txt` (the raw scale lists; the page cuts them to four chips a row and one per
 * exception). Without the variable it does nothing, so a test run never depends on a file outside the repository.
 * Gradle serves it from the cache when only the variable changed, so run it with `--rerun`.
 */
class ProbeSongsTest {

    @Test
    fun probe() {
        val corpusFile = System.getenv("PROBE_CORPUS")?.let(::File)?.takeIf { it.isFile } ?: return
        val out = StringBuilder()
        corpusFile.readText().split(Regex("\n(?=## )")).filter { it.isNotBlank() }.forEach { entry ->
            val lines = entry.trim().lines()
            val song = ChordProTransposer.transpose(ChordProParser.parse(lines.drop(1).joinToString("\n")), 0)
            out.append("## ").append(lines.first().removePrefix("## ").trim()).append('\n')
            val groups = SongScaleSuggestions.of(song)
            if (groups.isEmpty()) out.append("  (nothing)\n")
            groups.forEach { group ->
                val labels = group.blocks.joinToString(", ") { block ->
                    when (block) {
                        is ChordProBlock.Section -> (block.label ?: block.type.toString()) + if (block.isContinuation) "…" else ""
                        is ChordProBlock.ChorusRecall -> "Recall"
                        else -> "?"
                    }
                }
                out.append("  ").append(if (group.isHome) "[HOME] " else "").append('[').append(labels).append("] ")
                    .append(group.rootName).append(": ").append(group.scales.take(4).joinToString(", ") { it.id })
                    .append(if (group.scales.size > 4) " (+${group.scales.size - 4})" else "").append('\n')
                group.exceptions.forEach { exception ->
                    out.append("      over ").append((listOf(exception.chordName) + exception.otherChordNames).joinToString(" & "))
                        .append(" → ").append(exception.rootName).append(' ').append(exception.scales.joinToString(", ") { it.id }).append('\n')
                }
            }
            val raw = SongScaleGroups.of(song)
            if (raw.size != groups.size) {
                out.append("  (raw groups: ").append(raw.joinToString(" | ") { "${it.rootName} ${it.scales.first().id} ×${it.blocks.size}" }).append(")\n")
            }
        }
        File(corpusFile.path.removeSuffix(".txt") + "-output.txt").writeText(out.toString())
    }
}
