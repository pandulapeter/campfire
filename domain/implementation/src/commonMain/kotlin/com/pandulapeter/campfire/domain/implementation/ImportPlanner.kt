/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.chordpro.ChordProSplitter
import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Setlist

/**
 * What an import does with each song and setlist of a batch, as a function of the batch and of what the library
 * holds. Nothing is written and nothing is numbered here: a file that goes in keeps the name its header derives,
 * and the storage layer numbers it as it is written, which is the only moment that can see what is on disk.
 *
 * A name stands for a whole family — `x`, `x_2`, `x_3`… — because the storage layer numbers a name that is taken,
 * while an incoming song is always named by its header and so always asks for the un-numbered one. Holding it
 * against that one file alone reports the second arrangement of a song as a conflict with the first on every
 * re-import of an export, and offers to replace the first with it.
 */
internal object ImportPlanner {

    /** One song of the batch, already named by its own header (`SongRepository.importFileName`). */
    data class IncomingSong(
        val fileName: String,
        val text: String,
        val sourceFileName: String?,
    )

    /** One setlist of the batch, as `SetlistRepository.parseSetlist` named it, and the name of the file it arrived in. */
    data class IncomingSetlist(
        val setlist: Setlist,
        val sourceFileName: String,
    )

    /** Plans each song by comparing only the library files in its collision family. */
    suspend fun planSongs(
        incoming: List<IncomingSong>,
        libraryFileNames: Collection<String>,
        readLibraryText: suspend (fileName: String) -> String?,
    ): List<ImportPlan.SongEntry> {
        val libraryFamilies = libraryFileNames.groupedByFamily(LibraryFiles.SONG_EXTENSIONS)
        val libraryFileNameSet = libraryFileNames.toHashSet()
        val families = mutableMapOf<String, SongFamily>()
        return incoming.inArrivingOrder(LibraryFiles.SONG_EXTENSIONS, { it.fileName }, { it.sourceFileName }).mapIndexed { index, song ->
            val family = families.getOrPut(song.fileName) {
                readSongFamily(
                    desired = song.fileName,
                    members = song.fileName.familyKeys(LibraryFiles.SONG_EXTENSIONS).firstOrNull()?.let(libraryFamilies::get).orEmpty(),
                    readLibraryText = readLibraryText,
                )
            }
            // The library file the song arrived under, where that is not its own name. An export hands its songs out under
            // their library names while the import names each one by its header, and a library file named before the rule it
            // would be named by today (a letter the table did not know yet, or a file nobody ever renamed) is still the same
            // song when it holds the same text.
            val arrivedAs = song.sourceFileName?.takeIf { it != song.fileName && it in libraryFileNameSet }
            // Most songs of most imports are the only one of their name, and folding the text of every one of them
            // for a comparison nothing asks for would copy the whole batch once more.
            val comparable = if (family.hasNothingToCompareWith && arrivedAs == null) null else ChordProSplitter.comparable(song.text)
            val libraryFileName = comparable?.let(family::libraryFileNameOf)
                ?: arrivedAs?.takeIf { readLibraryText(it)?.let(ChordProSplitter::comparable) == comparable }
            val repeatedEntryIndex = comparable?.let(family::plannedEntryIndexOf)
            when {
                libraryFileName != null -> song.toEntry(fileName = libraryFileName, status = ImportPlan.Status.IDENTICAL)
                repeatedEntryIndex != null -> song.toEntry(status = ImportPlan.Status.IDENTICAL, repeatedEntryIndex = repeatedEntryIndex)
                else -> {
                    family.plan(index = index, text = song.text)
                    // The question is about the one file the library has under this name, so it is raised once:
                    // a second song wanting the name has nothing left to replace, and goes in numbered.
                    val isConflicting = family.isNameTaken && !family.hasConflict
                    family.hasConflict = family.hasConflict || isConflicting
                    song.toEntry(status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW)
                }
            }
        }
    }

    /** Plans setlists by their collision family, without treating another file in the batch as a library conflict. */
    fun planSetlists(incoming: List<IncomingSetlist>, librarySetlists: List<Setlist>): List<ImportPlan.SetlistEntry> {
        val libraryFamilies = librarySetlists.map { it.fileName }.groupedByFamily(SETLIST_EXTENSIONS)
        val librarySetlistsByFileName = librarySetlists.associateBy { it.fileName }
        val plannedSetlists = mutableMapOf<String, MutableList<Setlist>>()
        val conflictingFileNames = mutableSetOf<String>()
        return incoming.inArrivingOrder(SETLIST_EXTENSIONS, { it.setlist.fileName }, { it.sourceFileName }).map { (setlist, sourceFileName) ->
            val members = setlist.fileName.familyKeys(SETLIST_EXTENSIONS).firstOrNull()?.let(libraryFamilies::get).orEmpty()
                .mapNotNull(librarySetlistsByFileName::get)
            val identical = members.firstOrNull { it.holdsTheSameAs(setlist) }
                // See planSongs: a setlist file named before today's rule, arriving under that name.
                ?: librarySetlistsByFileName[sourceFileName]?.takeIf { it.holdsTheSameAs(setlist) }
            val planned = plannedSetlists.getOrPut(setlist.fileName) { mutableListOf() }
            when {
                identical != null -> ImportPlan.SetlistEntry(fileName = identical.fileName, setlist = setlist, status = ImportPlan.Status.IDENTICAL)
                planned.any { it.holdsTheSameAs(setlist) } ->
                    ImportPlan.SetlistEntry(fileName = setlist.fileName, setlist = setlist, status = ImportPlan.Status.IDENTICAL)
                else -> {
                    planned += setlist
                    val isConflicting = setlist.fileName in librarySetlistsByFileName && conflictingFileNames.add(setlist.fileName)
                    ImportPlan.SetlistEntry(
                        fileName = setlist.fileName,
                        setlist = setlist,
                        status = if (isConflicting) ImportPlan.Status.CONFLICTING else ImportPlan.Status.NEW,
                    )
                }
            }
        }
    }

    /** A setlist's priority belongs to the import, but every other user-facing field decides whether it is the same. */
    private fun Setlist.holdsTheSameAs(other: Setlist) =
        title == other.title && description == other.description && isArchived == other.isArchived && entries == other.entries &&
            unknownFields == other.unknownFields

    private fun IncomingSong.toEntry(
        status: ImportPlan.Status,
        fileName: String = this.fileName,
        repeatedEntryIndex: Int? = null,
    ) = ImportPlan.SongEntry(
        fileName = fileName,
        text = text,
        status = status,
        sourceFileName = sourceFileName,
        repeatedEntryIndex = repeatedEntryIndex,
    )

    /** Reads a family once per import, however many incoming songs share its desired name. */
    private suspend fun readSongFamily(
        desired: String,
        members: List<String>,
        readLibraryText: suspend (fileName: String) -> String?,
    ): SongFamily {
        val libraryFileNames = mutableMapOf<String, String>()
        var isNameTaken = false
        // The name itself first and the rest by their number, so that where the library holds the same text twice
        // an incoming copy of it is always said to be the same one of them.
        val candidates = (members + desired).distinct().sortedWith(compareBy<String>({ it != desired }, { it.length }, { it }))
        candidates.forEach { fileName ->
            val text = readLibraryText(fileName) ?: return@forEach
            if (fileName == desired) isNameTaken = true
            libraryFileNames.getOrPut(ChordProSplitter.comparable(text)) { fileName }
        }
        return SongFamily(isNameTaken = isNameTaken, libraryFileNames = libraryFileNames)
    }

    private class SongFamily(
        /** Whether the library holds a file under the derived name itself, which is what makes a different song a question. */
        val isNameTaken: Boolean,
        /** The library file each comparable text of the family is found in. */
        private val libraryFileNames: Map<String, String>,
    ) {
        private val plannedSongs = mutableListOf<IndexedValue<String>>()
        private val plannedEntryIndices = mutableMapOf<String, Int>()
        private var foldedSongCount = 0
        var hasConflict = false

        val hasNothingToCompareWith get() = libraryFileNames.isEmpty() && plannedSongs.isEmpty()

        fun libraryFileNameOf(comparable: String) = libraryFileNames[comparable]

        /** The texts planned so far are only folded once a second song asks about them, which most never see. */
        fun plannedEntryIndexOf(comparable: String): Int? {
            while (foldedSongCount < plannedSongs.size) {
                val song = plannedSongs[foldedSongCount++]
                plannedEntryIndices.getOrPut(ChordProSplitter.comparable(song.value)) { song.index }
            }
            return plannedEntryIndices[comparable]
        }

        fun plan(index: Int, text: String) {
            plannedSongs += IndexedValue(index = index, value = text)
        }
    }

    /** The desired name and the unnumbered name a numbered sibling belongs to, without an extension. */
    private fun String.familyKeys(extensions: List<String>): List<String> {
        val extension = extensions.firstOrNull { endsWith(it, ignoreCase = true) } ?: return emptyList()
        val name = dropLast(extension.length).lowercase()
        return listOfNotNull(name, LibraryFiles.withoutCollisionSuffix(name))
    }

    /** One pass over the library's names, so that looking a family up costs nothing per incoming file. */
    private fun Collection<String>.groupedByFamily(extensions: List<String>): Map<String, List<String>> {
        val families = mutableMapOf<String, MutableList<String>>()
        forEach { fileName -> fileName.familyKeys(extensions).forEach { families.getOrPut(it) { mutableListOf() } += fileName } }
        return families
    }

    /** Keeps archive siblings in their library order while preserving the archive order of every other entry. */
    private fun <T> List<T>.inArrivingOrder(
        extensions: List<String>,
        fileName: (T) -> String,
        sourceFileName: (T) -> String?,
    ) = sortedBy { item ->
        val desired = fileName(item).familyKeys(extensions).firstOrNull()
        val arrivedAs = sourceFileName(item)?.familyKeys(extensions).orEmpty()
        if (desired == null || arrivedAs.size < 2 || arrivedAs[1] != desired) {
            1
        } else {
            arrivedAs[0].removePrefix(desired).filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
        }
    }

    private val SETLIST_EXTENSIONS = listOf(LibraryFiles.SETLIST_EXTENSION)
}
