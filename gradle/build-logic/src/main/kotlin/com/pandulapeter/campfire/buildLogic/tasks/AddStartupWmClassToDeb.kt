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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Unpacks each .deb, adds the StartupWMClass key to its one desktop entry and builds it again. It fails rather than
 * doing nothing when there is no entry to add the key to, since a package without it is wrong in a way only an
 * installation shows. `fakeroot` keeps the repacked files owned by root, as they were; the entry's line in the
 * package's md5sums is brought up to date with it, so that a check of the installed files does not report it.
 */
abstract class AddStartupWmClassToDeb : DefaultTask() {

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val packages: ConfigurableFileCollection

    @get:Input
    abstract val windowClassName: Property<String>

    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    @TaskAction
    fun addStartupWmClass() {
        val debs = packages.files.filter { it.isFile }
        if (debs.isEmpty()) throw GradleException("There is no .deb to add StartupWMClass to.")
        debs.forEach { deb ->
            val extracted = workingDirectory.get().asFile.resolve(deb.nameWithoutExtension)
            extracted.deleteRecursively()
            // dpkg-deb creates the extraction target itself, but not its parent, which a clean checkout does not have yet.
            workingDirectory.get().asFile.mkdirs()
            execOperations.exec { commandLine("dpkg-deb", "--raw-extract", deb.absolutePath, extracted.absolutePath) }
            val entries = extracted.walkTopDown()
                .filter { it.isFile && it.extension == "desktop" && !it.relativeTo(extracted).startsWith("DEBIAN") }
                .toList()
            val entry = entries.singleOrNull()
                ?: throw GradleException("Expected one desktop entry in ${deb.name}, found ${entries.size}.")
            entry.writeText(withStartupWmClass(entry.readLines(), windowClassName.get()))
            val md5sums = extracted.resolve("DEBIAN/md5sums")
            if (md5sums.isFile) {
                val entryPath = entry.relativeTo(extracted).invariantSeparatorsPath
                val entryHash = MessageDigest.getInstance("MD5").digest(entry.readBytes()).joinToString("") { "%02x".format(it) }
                md5sums.writeText(withUpdatedMd5(md5sums.readLines(), entryPath, entryHash))
            }
            execOperations.exec { commandLine("fakeroot", "dpkg-deb", "--build", extracted.absolutePath, deb.absolutePath) }
            extracted.deleteRecursively()
        }
    }
}
