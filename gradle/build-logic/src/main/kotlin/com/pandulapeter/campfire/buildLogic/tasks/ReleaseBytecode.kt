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

import org.gradle.api.GradleException
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs [brokenClasses] over [jars] in a JVM of [java], the runtime the release ships with, and fails with what it
 * found. ProGuard's optimizer can write bytecode the JVM's verifier refuses, and the JVM verifies a class only the first
 * time something needs it: a class only an open song reaches starts the app fine and takes down the window later, so no
 * start of the release build shows it. Run in a JVM of its own, so that neither the build's JVM nor its metaspace
 * decides the outcome.
 */
fun verifyReleaseBytecode(java: File, jars: List<File>) {
    val classPath = listOf(Class.forName(MAIN_CLASS), Unit::class.java)
        .map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }
        .distinct()
        .joinToString(File.pathSeparator)
    val output = File.createTempFile("release-bytecode", ".log")
    try {
        // Into a file rather than a pipe, so that a long list of broken classes never blocks the process on a full one.
        val process = ProcessBuilder(listOf(java.absolutePath, "-cp", classPath, MAIN_CLASS) + jars.map { it.absolutePath })
            .redirectErrorStream(true)
            .redirectOutput(output)
            .start()
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw GradleException("Verifying the release bytecode did not end in five minutes:\n${output.readText()}")
        }
        if (process.exitValue() != 0) {
            throw GradleException("The release jars hold classes the JVM refuses to load:\n${output.readText()}")
        }
    } finally {
        output.delete()
    }
}

private const val MAIN_CLASS = "com.pandulapeter.campfire.buildLogic.tasks.BrokenClassesKt"
