/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.zip

import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads archives produced by `java.util.zip.ZipOutputStream`: DEFLATED entries, a sub-directory and the data
 * descriptors the JVM writes because it streams entries without knowing their size up front.
 */
internal class ZipReaderJvmTest {

    private val contents = mapOf(
        "hello.cho" to "{title: Hello}\n[Am]Line one\n".toByteArray(),
        "songs/nested.cho" to "{title: Nested}\n".repeat(500).toByteArray(),
        "dalok/Árvíztűrő.cho" to "{artist: Tükörfúrógép}\nÁÉÍÓŐÚŰ\n".toByteArray(),
        "empty.cho" to ByteArray(0),
        "random.bin" to Random(99).nextBytes(300 * 1024)
    )

    @Test
    fun readsAnArchiveWrittenByTheJvm() {
        val archive = ByteArrayOutputStream().also { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("songs/"))
                zip.closeEntry()
                contents.forEach { (name, bytes) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        // The JVM streams entry sizes in a trailing data descriptor, so the local headers carry zeroes.
        assertTrue(archive.u16(4 + 2) and 0x0008 != 0, "Expected the JVM to use data descriptors.")

        val read = ZipReader.read(archive)

        assertEquals(contents.keys.toList(), read.map { it.name })
        read.forEach { entry ->
            assertContentEquals(contents.getValue(entry.name), entry.bytes, "Entry \"${entry.name}\" did not match.")
        }
    }

    @Test
    fun readsAnArchiveWithAComment() {
        val archive = ByteArrayOutputStream().also { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.setComment("A".repeat(1000))
                zip.putNextEntry(java.util.zip.ZipEntry("hello.cho"))
                zip.write(contents.getValue("hello.cho"))
                zip.closeEntry()
            }
        }.toByteArray()

        val read = ZipReader.read(archive)

        assertEquals(1, read.size)
        assertContentEquals(contents.getValue("hello.cho"), read[0].bytes)
    }

    @Test
    fun jvmReadsAnArchiveWrittenByTheZipWriter() {
        val archive = ZipWriter.write(contents.map { ZipEntry(it.key, it.value) })

        val read = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                read[entry.name] = zip.readBytes()
            }
        }

        assertEquals(contents.keys, read.keys)
        contents.forEach { (name, bytes) -> assertContentEquals(bytes, read.getValue(name), "Entry \"$name\" did not match.") }
    }
}
