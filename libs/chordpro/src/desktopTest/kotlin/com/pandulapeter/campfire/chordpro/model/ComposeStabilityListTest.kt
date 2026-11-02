/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

import java.io.File
import kotlin.reflect.KVisibility
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `gradle/compose-stability.conf` names the model's classes one by one, so a class missing from it compiles and silently
 * makes every composable taking it skip nothing, and a name a rename left behind vouches for nothing. This holds the list
 * to the public classes of the model and the scales packages, both ways. Enum classes, which the Compose compiler treats
 * as stable on its own, and plain objects, which hold no state and are never a parameter, are left out; a `data object`
 * is a value like any other and is kept.
 */
class ComposeStabilityListTest {

    @Test
    fun `the stability configuration names every public model class and nothing else`() {
        val listed = File("../../gradle/compose-stability.conf").readLines()
            .map { it.substringBefore("//").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val scanned = PACKAGES.flatMap(::publicValueClassesIn).toSet()
        val missing = (scanned - listed).sorted()
        val unknown = (listed - scanned).sorted()
        assertTrue(
            missing.isEmpty(),
            "Not in gradle/compose-stability.conf: ${missing.joinToString()}. Add each to the list once it is immutable (libs/chordpro/CLAUDE.md).",
        )
        assertTrue(unknown.isEmpty(), "gradle/compose-stability.conf names classes that are not public model classes: ${unknown.joinToString()}.")
    }

    /**
     * The public classes compiled into [packageName] by the module's main source sets, nested ones included, named as
     * the configuration names them. The tests' own classes share the packages and are left out by where they were loaded from.
     */
    private fun publicValueClassesIn(packageName: String): List<String> {
        val loader = javaClass.classLoader
        val tests = File(javaClass.protectionDomain.codeSource.location.toURI())
        return loader.getResources(packageName.replace('.', '/')).toList()
            .map { File(it.toURI()) }
            .filterNot { it.startsWith(tests) }
            .flatMap { directory -> directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".class") } }
            .map { "$packageName.${it.name.removeSuffix(".class")}" }
            .map { Class.forName(it, false, loader) }
            .filter { it.isValueClassOfTheModel() }
            .map { it.name.replace('$', '.') }
    }

    /**
     * Whether this is a class a composable can be handed: public to Kotlin (the `internal` classes of `scales` are public
     * to the JVM), and neither an enum, a companion, a file facade, nor a compiler-generated class or a plain object.
     */
    private fun Class<*>.isValueClassOfTheModel(): Boolean {
        if (isSynthetic || isAnonymousClass || isEnum || simpleName.endsWith("Kt")) return false
        // Kotlin reflection refuses the classes the compiler generates (lambdas, when mappings), which are none of these.
        val type = runCatching { kotlin }.getOrNull() ?: return false
        val visibility = runCatching { type.visibility }.getOrNull() ?: return false
        return when {
            visibility != KVisibility.PUBLIC -> false
            type.isCompanion -> false
            type.objectInstance != null -> type.isData
            else -> true
        }
    }

    private companion object {
        val PACKAGES = listOf("com.pandulapeter.campfire.chordpro.model", "com.pandulapeter.campfire.chordpro.scales")
    }
}
