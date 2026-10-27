/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
plugins {
    id("campfire-library")
    id("campfire-koin")
}

/**
 * The scripts the JVM targets read as resources (abc2svg, its modules, and the host), as Kotlin strings for the targets
 * that have no resources of their own: iOS and the web. Generated from the very files in jvmSharedMain/resources, so
 * abc2svg is bundled unmodified everywhere; one copy per target, since a source folder belongs to one source set.
 */
val abcScriptSources = layout.projectDirectory.dir("src/jvmSharedMain/resources")
val generateAbcScripts = listOf("iosMain", "wasmJsMain").associateWith { sourceSet ->
    tasks.register("generateAbcScripts${sourceSet.removeSuffix("Main").replaceFirstChar(Char::uppercase)}") {
        val sources = abcScriptSources
        val outputDirectory = layout.buildDirectory.dir("generated/abcScripts/$sourceSet/kotlin")
        inputs.dir(sources)
        outputs.dir(outputDirectory)
        doLast {
            val root = sources.asFile
            val scripts = root.walkTopDown().filter { it.isFile && it.extension == "js" }.sortedBy { it.path }.toList()
            // Pieces short enough for every compiler's limit on one string constant, joined once when first read.
            fun literal(text: String) = text.chunked(16_000).joinToString(separator = ",\n        ", prefix = "listOf(\n        ", postfix = ",\n    ).joinToString(\"\")") { piece ->
                buildString {
                    append('"')
                    piece.forEach { character ->
                        when {
                            character == '\\' -> append("\\\\")
                            character == '"' -> append("\\\"")
                            character == '$' -> append("\\$")
                            character == '\n' -> append("\\n")
                            character == '\r' -> append("\\r")
                            character == '\t' -> append("\\t")
                            // Escaped, a pair split across two pieces still joins into its character.
                            character.code < 0x20 || character.isSurrogate() -> append("\\u%04x".format(character.code))
                            else -> append(character)
                        }
                    }
                    append('"')
                }
            }
            val entries = scripts.joinToString(separator = "\n") { script ->
                "    \"${script.relativeTo(root).invariantSeparatorsPath}\" to lazy {\n    ${literal(script.readText())}\n    },"
            }
            outputDirectory.get().asFile.resolve("com/pandulapeter/campfire/abc/implementation").apply { mkdirs() }.resolve("AbcScripts.kt").writeText(
                "package com.pandulapeter.campfire.abc.implementation\n\n" +
                    "/** Generated from src/jvmSharedMain/resources by generateAbcScripts: every bundled script by its path there. */\n" +
                    "internal val abcScripts: Map<String, Lazy<String>> = mapOf(\n$entries\n)\n",
            )
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":abc:api"))
            implementation(libs.kotlin.coroutines)
        }
        // abc2svg runs in QuickJS on the two JVM targets, see QuickJsAbcScriptEngine.
        getByName("jvmSharedMain").dependencies {
            implementation(libs.quickjs)
        }
        generateAbcScripts.forEach { (sourceSet, task) -> getByName(sourceSet).kotlin.srcDir(task) }
    }
}
