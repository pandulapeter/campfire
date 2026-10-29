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
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlin.system.exitProcess

/**
 * Loads and links every class of [jars] without initializing any of them, and returns each one the JVM refused, with
 * the reason. `getDeclaredMethods` is what makes HotSpot link, and so verify, a class it has only loaded; nothing runs a
 * static initializer. A class that refers to one missing from the jars counts as broken too, since the app cannot use
 * it either.
 */
fun brokenClasses(jars: List<File>): List<String> = URLClassLoader(
    jars.map { it.toURI().toURL() }.toTypedArray(),
    ClassLoader.getPlatformClassLoader(),
).use { loader ->
    jars.flatMap { jar ->
        JarFile(jar).use { file ->
            file.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith(".class") && !it.startsWith("META-INF/") && !it.endsWith("module-info.class") }
                .map { it.removeSuffix(".class").replace('/', '.') }
                .toList()
        }
    }.mapNotNull { name ->
        try {
            Class.forName(name, false, loader).declaredMethods
            null
        } catch (error: LinkageError) {
            "$name: ${error.toString().lineSequence().first()}"
        }
    }
}

/**
 * The entry point of the JVM [verifyReleaseBytecode] starts, where every argument is a jar. It lives apart from that
 * function, since that JVM has the Kotlin standard library and this file on its class path but not Gradle, which a
 * class throwing a `GradleException` needs to pass verification itself.
 */
fun main(args: Array<String>) {
    val broken = brokenClasses(args.map(::File))
    broken.forEach(::println)
    exitProcess(if (broken.isEmpty()) 0 else 1)
}
