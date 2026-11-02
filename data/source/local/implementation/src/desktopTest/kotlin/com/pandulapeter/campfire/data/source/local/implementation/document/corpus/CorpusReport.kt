/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document.corpus

import java.util.Locale

/** The scores file of one corpus run and the report comparing it with the last run that scored differently. */
internal object CorpusReport {
    private const val HEADER = "document\tlines\tchords\tsections\tsongs\theader\toverall"

    /** The scores as `scores.tsv` keeps them: one row per document, `program/name`, four decimals each. */
    fun table(scores: Map<String, CorpusScore>) = (listOf(HEADER) + scores.toSortedMap().map { (document, score) ->
        (listOf(document) + score.values().map(::formatted)).joinToString("\t")
    }).joinToString("\n", postfix = "\n")

    /** The rows of a `scores.tsv`, each value read back as written; a row that does not parse is left out. */
    fun parse(table: String): Map<String, CorpusScore> = table.lines().drop(1).mapNotNull { row ->
        val cells = row.split('\t')
        val values = cells.drop(1).take(5).mapNotNull(String::toDoubleOrNull)
        if (cells.size < 6 || values.size < 5) null else cells[0] to CorpusScore(values[0], values[1], values[2], values[3], values[4])
    }.toMap()

    /**
     * The totals (each component's mean, the corpus's and each program's) with their change, the documents whose scores
     * changed, worst overall change first, then the documents without an expected file and those that failed.
     */
    fun report(current: Map<String, CorpusScore>, previous: Map<String, CorpusScore>, unscored: List<String>, failures: Map<String, String>) = buildString {
        appendLine("Totals (change since the last run that scored differently)")
        appendLine("program\tdocuments\tlines\tchords\tsections\tsongs\theader\toverall")
        val programs = listOf("all" to { _: String -> true }) + current.keys.map(::programOf).distinct().sorted().map { program ->
            program to { document: String -> programOf(document) == program }
        }
        for ((program, includes) in programs) {
            val now = current.filterKeys(includes).values
            val before = previous.filterKeys(includes).values
            val cells = means(now).zip(means(before)) { mean, old -> "${formatted(mean)} (${signed(mean - old, before.isEmpty())})" }
            appendLine((listOf(program, now.size.toString()) + cells).joinToString("\t"))
        }
        appendLine()
        appendLine("Changed documents (overall change, then the new scores)")
        val changed = current.filter { (document, score) -> previous[document] != score }
            .toList()
            .sortedBy { (document, score) -> score.overall - (previous[document]?.overall ?: 0.0) }
        if (changed.isEmpty()) appendLine("none")
        for ((document, score) in changed) {
            val old = previous[document]
            val change = if (old == null) "new" else signed(score.overall - old.overall, isNew = false)
            appendLine((listOf(document, change) + score.values().map(::formatted)).joinToString("\t"))
        }
        appendLine()
        appendLine("Unscored (no expected .cho beside them; their output is in .runs/out)")
        appendLine(unscored.sorted().joinToString("\n").ifEmpty { "none" })
        appendLine()
        appendLine("Failed")
        appendLine(failures.toSortedMap().map { (document, error) -> "$document\t$error" }.joinToString("\n").ifEmpty { "none" })
    }

    /** The program a document was written by: the first folder of its path in the corpus. */
    fun programOf(document: String) = document.substringBefore('/')

    private fun CorpusScore.values() = listOf(lines, chords, sections, songs, header, overall)

    private fun means(scores: Collection<CorpusScore>) = (0 until 6).map { index ->
        if (scores.isEmpty()) 0.0 else scores.sumOf { it.values()[index] } / scores.size
    }

    /** The score as the table keeps it, so a run compares equal to the table of a run that scored the same. */
    fun rounded(score: CorpusScore) = score.values().take(5).map { Math.round(it * 10_000) / 10_000.0 }.let { CorpusScore(it[0], it[1], it[2], it[3], it[4]) }

    private fun formatted(value: Double) = String.format(Locale.ROOT, "%.4f", value)

    private fun signed(value: Double, isNew: Boolean) = when {
        isNew -> "new"
        value >= 0.00005 -> "+${formatted(value)}"
        value <= -0.00005 -> formatted(value)
        else -> "="
    }
}
