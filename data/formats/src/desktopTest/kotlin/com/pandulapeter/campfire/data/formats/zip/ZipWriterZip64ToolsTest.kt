/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

import com.pandulapeter.campfire.data.model.hashing.Sha256
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Small ZIP64 archives from [ZipWriter], its limits lowered so they need no gigabytes, read by other tools: Python's
 * `zipfile` and Info-ZIP's `unzip -t` where the machine has them (skipped, not failed, where not), and `java.util.zip`.
 */
internal class ZipWriterZip64ToolsTest {

    private val entries = listOf(
        ZipEntry("songs/riverlight.cho", "{title: Riverlight}\n[Am]Down by the [C]river\n".repeat(30).encodeToByteArray()),
        ZipEntry("covers/cover.jpg", Random(7).nextBytes(20_000)),
        ZipEntry("empty.cho", ByteArray(0)),
        ZipEntry("setlists/Árvíztűrő.setlist.json", "{\"title\": \"Árvíztűrő\"}".encodeToByteArray()),
    )

    /** Every offset, size, and count at its marker, and an archive whose later entries alone pass the offset limit. */
    private val archives = listOf(
        write(offsetLimit = 0, entryLimit = 0),
        write(offsetLimit = 1_000, entryLimit = 0xFFFF),
    )

    @Test
    fun `python's zipfile reads the writer's ZIP64 archives`() {
        assumeTrue("python3 is not installed.", isInstalled("python3", "--version"))
        // -I ignores PYTHONIOENCODING, so the script asks for UTF-8 itself, which a Windows console would not give.
        val script = "import hashlib, sys, zipfile\n" +
            "sys.stdout.reconfigure(encoding='utf-8', newline='\\n')\n" +
            "with zipfile.ZipFile(sys.argv[1]) as z:\n" +
            "    assert z.testzip() is None\n" +
            "    for info in z.infolist(): print(info.filename, hashlib.sha256(z.read(info)).hexdigest())\n"
        val expected = entries.joinToString("") { "${it.name} ${Sha256.hashToHex(it.bytes)}\n" }

        archives.forEach { archive ->
            val (exitCode, output) = run(archive, "python3", "-I", "-c", script)

            assertEquals(0, exitCode, output)
            assertEquals(expected, output)
        }
    }

    @Test
    fun `info-zip's unzip tests the writer's ZIP64 archives as sound`() {
        assumeTrue("unzip is not installed.", isInstalled("unzip", "-v"))
        // Git for Windows' unzip decodes a local header's UTF-8 name in the console's code page while it decodes the
        // central one as UTF-8, and counts the mismatch as an error in any archive with a name outside ASCII, ZIP64 or
        // not, so it cannot judge these archives; Python and Java still read them on Windows.
        assumeTrue("Windows' unzip misreads UTF-8 names.", !System.getProperty("os.name").startsWith("Windows"))

        archives.forEach { archive ->
            val (exitCode, output) = run(archive, "unzip", "-t")

            assertEquals(0, exitCode, output)
            assertTrue("No errors detected" in output, output)
        }
    }

    @Test
    fun `java's zip file reads the writer's ZIP64 archives`() {
        archives.forEach { archive ->
            val file = File.createTempFile("zip64", ".zip").apply { writeBytes(archive) }
            try {
                java.util.zip.ZipFile(file).use { zip ->
                    val read = zip.entries().toList().map { it.name to zip.getInputStream(it).readBytes() }

                    assertEquals(entries.map { it.name }, read.map { it.first })
                    entries.forEachIndexed { index, entry -> assertContentEquals(entry.bytes, read[index].second) }
                }
            } finally {
                file.delete()
            }
        }
    }

    private fun write(offsetLimit: Long, entryLimit: Int) = runBlocking {
        val sink = BufferZipSink(64 * 1024)
        ZipWriter.write(
            entries = entries.map { entry -> { entry } },
            sink = sink,
            modifiedAt = DosTimestamp.EARLIEST,
            offsetLimit = offsetLimit,
            entryLimit = entryLimit,
        )
        sink.build()
    }

    private fun isInstalled(vararg command: String) = try {
        ProcessBuilder(*command).redirectErrorStream(true).start().run {
            inputStream.readBytes()
            waitFor(30, TimeUnit.SECONDS) && exitValue() == 0
        }
    } catch (_: IOException) {
        false
    }

    /** Runs [command] with the archive's path last, and returns its exit code and its output. */
    private fun run(archive: ByteArray, vararg command: String): Pair<Int, String> {
        val file = File.createTempFile("zip64", ".zip").apply { writeBytes(archive) }
        try {
            val process = ProcessBuilder(*command, file.absolutePath).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().decodeToString()
            check(process.waitFor(60, TimeUnit.SECONDS)) { "${command.first()} did not finish." }
            return process.exitValue() to output
        } finally {
            file.delete()
        }
    }
}
