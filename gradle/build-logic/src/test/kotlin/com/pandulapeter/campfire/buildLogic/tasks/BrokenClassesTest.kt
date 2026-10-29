/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.tasks

import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrokenClassesTest {

    private val directory = createTempDirectory("broken-classes").toFile()

    @AfterTest
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun `classes the verifier accepts are not reported`() {
        assertEquals(emptyList(), brokenClasses(listOf(jarOf(FIXTURE_PATH to fixtureBytes()))))
    }

    @Test
    fun `a class the verifier refuses is reported by name`() {
        val bytes = fixtureBytes()
        // The fixture's method is `iconst_1, ireturn`; returning null from it instead is bytecode no verifier accepts.
        val instruction = (0 until bytes.size - 1).single { bytes[it] == ICONST_1 && bytes[it + 1] == IRETURN }
        bytes[instruction] = ACONST_NULL
        val broken = brokenClasses(listOf(jarOf(FIXTURE_PATH to bytes)))
        assertEquals(1, broken.size)
        assertTrue(broken.single().startsWith("${VerificationFixture::class.java.name}: java.lang.VerifyError"), broken.single())
    }

    @Test
    fun `a class whose superclass is missing is reported`() {
        val broken = brokenClasses(listOf(jarOf(SUBCLASS_PATH to classBytes(VerificationFixtureSubclass::class.java))))
        assertEquals(1, broken.size)
        assertTrue(broken.single().contains("NoClassDefFoundError"), broken.single())
    }

    private fun fixtureBytes() = classBytes(VerificationFixture::class.java)

    private fun classBytes(type: Class<*>) =
        type.getResourceAsStream("/${type.name.replace('.', '/')}.class")!!.use { it.readBytes() }

    private fun jarOf(vararg entries: Pair<String, ByteArray>) = File(directory, "classes.jar").also { jar ->
        JarOutputStream(jar.outputStream()).use { output ->
            entries.forEach { (path, bytes) ->
                output.putNextEntry(JarEntry(path))
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    private companion object {
        val FIXTURE_PATH = "${VerificationFixture::class.java.name.replace('.', '/')}.class"
        val SUBCLASS_PATH = "${VerificationFixtureSubclass::class.java.name.replace('.', '/')}.class"
        const val ICONST_1 = 0x04.toByte()
        const val IRETURN = 0xAC.toByte()
        const val ACONST_NULL = 0x01.toByte()
    }
}

/** Put into a jar of its own, where the class loader of [brokenClasses] finds nothing else. */
open class VerificationFixture {
    fun one(): Int = 1
}

/** Put into a jar without [VerificationFixture], so its superclass cannot be found. */
class VerificationFixtureSubclass : VerificationFixture()
