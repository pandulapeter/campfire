/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

/**
 * Which entry of an archive a song's `{image: src=…}` means, among the archive's entry [names]: the one ChordPro's
 * portable form means first, then where other tools put it.
 *
 * 1. The file the value names beside the song (`x.png` next to `songs/a.cho` is `songs/x.png`).
 * 2. In an `images/` folder, at the archive's root (where Campfire's own export puts it) or beside the song.
 * 3. The one entry anywhere of that file name, or else the one of that file name in another case: a song written on a
 *    system that does not tell `Chart.PNG` from `chart.png` names a file the archive spells otherwise. Two entries
 *    answering by name alone are no answer, since picking one would show a song the wrong picture.
 *
 * Paths compare with `\` read as `/` (PowerShell writes it) and `.` and `..` segments resolved, and only ever among the
 * archive's own entries: a path climbing out of the archive, or an absolute one (the author's own disk), is found by its
 * file name alone, and an address (`https://…`) is never an entry, since nothing outside the archive is looked at.
 */
internal class ArchiveImagePaths(names: Collection<String>) {

    private val byPath = names.mapNotNull { name -> normalized(name)?.let { it to name } }.toMap()
    private val byFileName = byPath.entries.groupBy({ it.key.substringAfterLast('/') }, { it.value })
    private val byFoldedFileName = byPath.entries.groupBy({ it.key.substringAfterLast('/').lowercase() }, { it.value })

    /** The entry name [source], named by the song stored as [songPath], means, or null where there is none. */
    fun resolve(songPath: String, source: String): String? {
        if (source.contains("://")) return null
        val value = source.replace('\\', '/')
        val folder = normalized(songPath)?.substringBeforeLast('/', "")?.let { if (it.isEmpty()) "" else "$it/" } ?: ""
        val isAbsolute = value.startsWith('/')
        if (!isAbsolute) {
            listOf("$folder$value", "$IMAGES_FOLDER$value", "$folder$IMAGES_FOLDER$value").forEach { candidate ->
                normalized(candidate)?.let(byPath::get)?.let { return it }
            }
        }
        val fileName = value.substringAfterLast('/').takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: return null
        return byFileName[fileName]?.singleOrNull() ?: byFileName[fileName]?.let { return null } ?: byFoldedFileName[fileName.lowercase()]?.singleOrNull()
    }

    private companion object {
        const val IMAGES_FOLDER = "images/"

        /** [path] with `/` alone separating, no empty, `.` or `..` segment, and no leading `/`; null where it climbs above the root. */
        fun normalized(path: String): String? {
            val segments = ArrayDeque<String>()
            path.replace('\\', '/').split('/').forEach { segment ->
                when (segment) {
                    "", "." -> Unit
                    ".." -> segments.removeLastOrNull() ?: return null
                    else -> segments.addLast(segment)
                }
            }
            return segments.joinToString("/").takeIf { it.isNotEmpty() }
        }
    }
}
