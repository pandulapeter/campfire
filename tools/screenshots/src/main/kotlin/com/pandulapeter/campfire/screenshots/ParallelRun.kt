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

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.concurrent.thread

/** The [index]-th of [count] equal parts of a run: every image whose place in the run's list leaves [index] when divided by [count]. */
internal data class Shard(
    val index: Int,
    val count: Int,
) {
    init {
        require(count > 0 && index in 0 until count) { "A shard is <index>/<count>, the index from 0 to count - 1." }
    }

    /** The [part]-th of [parts] parts this shard is split into: the images of this shard that are every [parts]-th of it. */
    fun split(part: Int, parts: Int) = Shard(index = index + count * part, count = count * parts)

    override fun toString() = "$index/$count"

    companion object {
        fun parse(text: String) = text.split('/').let { (index, count) -> Shard(index = index.trim().toInt(), count = count.trim().toInt()) }
    }
}

/**
 * Renders [shard] in [processes] processes of the tool, each started with this one's JVM options (the paths the build
 * passes in) and the same arguments, but its own part of the shard, the output folder [output] spelled out (so that no
 * part clears it), and no further split. A process holds one app start at a time, since Koin, the home folder and the
 * impersonated platform are the process's, but a render spends most of its time waiting for the app to settle rather
 * than drawing, so several of them side by side take about as long as one.
 *
 * Their output is printed as it comes, each line marked with its part; returns the exit code for the run, which fails
 * when any part does.
 */
internal fun renderInParallel(options: Map<String, String>, output: File, shard: Shard, processes: Int): Int {
    val java = ProcessHandle.current().info().command().orElseThrow { IllegalStateException("The path of this JVM is unknown.") }
    // A debugger's agent would make every part bind this process's port and fail before rendering anything.
    val jvmOptions = ManagementFactory.getRuntimeMXBean().inputArguments.filterNot { it.startsWith("-agentlib:jdwp") || it.startsWith("-Xrunjdwp") }
    val parts = (0 until processes).map { part ->
        val arguments = options + mapOf("--out" to output.absolutePath, "--shard" to shard.split(part, processes).toString(), "--parallel" to "1")
        val command = listOf(java) + jvmOptions + listOf("-cp", System.getProperty("java.class.path"), MAIN_CLASS) +
            arguments.flatMap { (key, value) -> listOf(key, value) }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process to thread(name = "part ${part + 1}") {
            process.inputStream.bufferedReader().forEachLine { line -> println("(${part + 1}/$processes) $line") }
        }
    }
    // A run stopped from Gradle or the IDE ends only this process; its parts would go on writing into the next run's folder.
    val stopParts = thread(start = false) { parts.forEach { (process, _) -> process.descendants().forEach(ProcessHandle::destroy); process.destroy() } }
    Runtime.getRuntime().addShutdownHook(stopParts)
    val failed = parts.count { (process, printer) ->
        val exitCode = process.waitFor()
        printer.join()
        exitCode != 0
    }
    Runtime.getRuntime().removeShutdownHook(stopParts)
    println(if (failed == 0) "Every part of the run finished." else "$failed of $processes parts of the run had a failed screenshot.")
    return if (failed == 0) 0 else 1
}

private const val MAIN_CLASS = "com.pandulapeter.campfire.screenshots.MainKt"
