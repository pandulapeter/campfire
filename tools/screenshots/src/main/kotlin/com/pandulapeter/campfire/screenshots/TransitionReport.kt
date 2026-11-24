/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import java.lang.management.ManagementFactory
import java.util.Locale

/**
 * Prints one row per scenario of [results]: pass 1's worst frame (cold), and over the warm passes (3 and later, once the
 * code is compiled) the median of the trigger frame and of the worst frame with its usual index, its compose / layout /
 * draw split and what it allocated, the median count of frames over a [hz] display's budget (the frame and the pause
 * after it together), and the median garbage collection pause per pass. Then what the figures were taken on. With
 * [options]' CSV path, every frame is also written there, one row each: the only thing the suite writes.
 */
internal fun printTransitionReport(results: TransitionResults, options: TransitionOptions) {
    val budgetNanos = 1_000_000_000.0 / options.hz
    val width = results.scenarios.keys.maxOf { it.length }.coerceAtLeast("Scenario".length)
    val format = "%-${width}s  %15s  %19s  %8s  %15s  %19s  %6s  %4s  %5s"
    println()
    println(format.rootFormat("Scenario", "Cold worst (#)", "C / L / D", "Warm 1st", "Warm worst (#)", "C / L / D", "MB", "Over", "GC ms"))
    results.scenarios.forEach { (name, passes) ->
        val cold = passes.first().worst()
        val warm = passes.drop(WARM_FROM_PASS - 1)
        val warmWorst = warm.map { it.worst() }.sortedBy { it.record.timing.mainNanos }.getOrNull(warm.size / 2)
        val usualIndex = warm.map { it.worst().index }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        println(
            format.rootFormat(
                name,
                "${cold.record.timing.mainNanos.millis()} (${cold.index})",
                cold.record.timing.split(results.isSplit),
                warm.map { it.first().timing.mainNanos }.median()?.millis() ?: "-",
                warmWorst?.let { "${it.record.timing.mainNanos.millis()} ($usualIndex)" } ?: "-",
                warmWorst?.record?.timing?.split(results.isSplit) ?: "-",
                warmWorst?.let { "%.1f".rootFormat(it.record.timing.allocatedBytes / MEGABYTE) } ?: "-",
                warm.map { frames -> frames.count { it.timing.mainNanos + it.betweenNanos > budgetNanos }.toLong() }.median()?.toString() ?: "-",
                warm.map { frames -> frames.sumOf { it.timing.gcPauseMillis } }.median()?.toString() ?: "-",
            ),
        )
    }
    println()
    printFooter(results = results, options = options, budgetNanos = budgetNanos)
    options.csv?.let { file ->
        file.absoluteFile.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            writer.appendLine("scenario,pass,frame,compose_ms,layout_ms,draw_ms,between_ms,allocated_bytes,gc_pause_ms")
            results.scenarios.forEach { (name, passes) ->
                passes.forEachIndexed { pass, frames ->
                    frames.forEachIndexed { index, (timing, between) ->
                        writer.appendLine(
                            "%s,%d,%d,%.3f,%.3f,%.3f,%.3f,%d,%d".rootFormat(
                                name, pass + 1, index + 1, timing.composeNanos / NANOS_PER_MILLI, timing.layoutNanos / NANOS_PER_MILLI,
                                timing.drawNanos / NANOS_PER_MILLI, between / NANOS_PER_MILLI, timing.allocatedBytes, timing.gcPauseMillis,
                            ),
                        )
                    }
                }
            }
        }
        println("Every frame written to ${file.absolutePath}.")
    }
}

private fun printFooter(results: TransitionResults, options: TransitionOptions, budgetNanos: Double) {
    val runtime = ManagementFactory.getRuntimeMXBean()
    println(
        "Machine: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}, " +
            "${Runtime.getRuntime().availableProcessors()} cores; JVM: ${runtime.vmName} ${runtime.vmVersion}; " +
            "collectors: ${ManagementFactory.getGarbageCollectorMXBeans().joinToString { it.name }}; max heap ${Runtime.getRuntime().maxMemory() / MEGABYTE.toLong()} MB.",
    )
    println(
        "Window ${options.widthPx} × ${options.heightPx} px at a density of ${options.density}, frames of a ${options.hz} Hz display " +
            "(a budget of ${(budgetNanos.toLong()).millis()} ms), $FRAMES_AFTER_TRIGGER frames after each trigger, ${options.runs} passes.",
    )
    println(
        "One app for the whole run: pass 1 is cold (the first open in the process), passes 2 and later reopen the same songs " +
            "(push:fresh a new long song each time), warm is the median of passes $WARM_FROM_PASS and later. Times are milliseconds, " +
            "(#) the frame's index after the trigger.",
    )
    if (!results.isSplit) println("The compose / layout / draw split was unavailable on this Compose: each frame is timed whole, as compose.")
    println(
        "Draw is recording only: a real window also rasterizes on the GPU and presents, which this does not see. " +
            "Timings are this machine's, under whatever else it was doing.",
    )
}

/** A frame of a scenario's pass and its index after the trigger, counted from 1. */
private class IndexedFrame(val index: Int, val record: FrameRecord)

private fun List<FrameRecord>.worst() = withIndex().maxBy { it.value.timing.mainNanos }.let { IndexedFrame(index = it.index + 1, record = it.value) }

private operator fun FrameRecord.component1() = timing

private operator fun FrameRecord.component2() = betweenNanos

private fun FrameTiming.split(isSplit: Boolean) = if (isSplit) "${composeNanos.millis()} / ${layoutNanos.millis()} / ${drawNanos.millis()}" else "-"

private fun List<Long>.median() = sorted().getOrNull(size / 2)

private fun Long.millis() = "%.1f".rootFormat(this / NANOS_PER_MILLI)

private fun String.rootFormat(vararg arguments: Any?) = String.format(Locale.ROOT, this, *arguments)

private const val WARM_FROM_PASS = 3
private const val NANOS_PER_MILLI = 1_000_000.0
private const val MEGABYTE = 1024.0 * 1024.0
