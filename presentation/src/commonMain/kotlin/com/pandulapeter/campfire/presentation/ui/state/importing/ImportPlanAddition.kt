/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.chordpro.ChordProSplitter
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * This plan with [added], a batch prepared on its own while this one was under review, appended to it as though the two
 * had been picked together, so that the review on screen grows rather than a second one waiting behind it. Every entry
 * of this plan keeps its place, which is what the review's choices are keyed by; what [added] holds comes after it.
 *
 * Whatever the review lists already is left out rather than listed again, so the same archive dropped twice changes
 * nothing: a song whose text an entry has, a setlist an entry is, and a file name already skipped. The one exception is
 * a song a setlist of [added] names, which stays as a repeat of the entry it matches (unlisted, as a repeat within one
 * batch is), since the setlist finds its songs by the names they arrived under.
 *
 * Each name taken in the library is still one question: an added entry asking about a name an entry here already asks
 * about, or whose library file either batch brings back unchanged, goes in numbered, and so does an entry here whose
 * library file [added] brings back unchanged.
 *
 * @return This plan itself where [added] brings nothing it does not list.
 */
internal fun ImportPlan.withAdded(added: ImportPlan): ImportPlan {
    val firstIndexByText = mutableMapOf<String, Int>()
    songs.forEachIndexed { index, entry -> firstIndexByText.getOrPut(ChordProSplitter.comparable(entry.text)) { index } }
    val addedSetlists = added.setlists.filter { entry -> setlists.none { it.setlist == entry.setlist && it.isDated == entry.isDated } }
    val namedByAddedSetlists = addedSetlists.flatMapTo(hashSetOf()) { entry -> entry.setlist.entries.map { it.songFileName } }
    val originOffset = sourceNames.size
    val addedIndices = arrayOfNulls<Int>(added.songs.size)
    val addedSongs = mutableListOf<ImportPlan.SongEntry>()
    added.songs.forEachIndexed { index, entry ->
        val comparable = ChordProSplitter.comparable(entry.text)
        val match = firstIndexByText[comparable]
        val shifted = entry.copy(origin = entry.origin?.plus(originOffset))
        val kept = when {
            // A repeat within the added batch keeps pointing at its own first copy, which is kept in its new place.
            match == null || match >= songs.size -> shifted.copy(repeatedEntryIndex = entry.repeatedEntryIndex?.let(addedIndices::get))
            match < songs.size && entry.sourceFileName in namedByAddedSetlists -> shifted.copy(
                fileName = songs[match].fileName,
                status = ImportPlan.Status.IDENTICAL,
                repeatedEntryIndex = match,
                replacesFileName = null,
            )

            else -> null
        } ?: return@forEachIndexed
        val newIndex = songs.size + addedSongs.size
        addedIndices[index] = newIndex
        if (match == null) firstIndexByText[comparable] = newIndex
        addedSongs += kept
    }
    val newSkipped = added.skippedFileNames.filterNot { it in skippedFileNames }
    val newOversized = added.oversizedFileNames.filterNot { it in oversizedFileNames }
    val newUnreadable = added.unreadableDocumentFileNames.filterNot { it in unreadableDocumentFileNames }
    if (addedSongs.isEmpty() && addedSetlists.isEmpty() && newSkipped.isEmpty() && newOversized.isEmpty() && newUnreadable.isEmpty()) return this
    val keptLibrarySongs = (songs + addedSongs).filter { it.isLibraryCopy }.mapTo(hashSetOf()) { LibraryFiles.identityKey(it.fileName) }
    val askedSongs = songs.filter { it.status == ImportPlan.Status.CONFLICTING }.mapTo(hashSetOf()) { LibraryFiles.identityKey(it.takenFileName) }
    val keptLibrarySetlists = (setlists + addedSetlists).filter { it.status == ImportPlan.Status.IDENTICAL }.mapTo(hashSetOf()) { LibraryFiles.identityKey(it.fileName) }
    val askedSetlists = setlists.filter { it.status == ImportPlan.Status.CONFLICTING }.mapTo(hashSetOf()) { LibraryFiles.identityKey(it.fileName) }
    return ImportPlan(
        songs = songs.map { if (it.isConflictAbout(keptLibrarySongs)) it.numbered() else it } +
            addedSongs.map { if (it.isConflictAbout(keptLibrarySongs) || it.isConflictAbout(askedSongs)) it.numbered() else it },
        setlists = setlists.map { if (it.isConflictAbout(keptLibrarySetlists)) it.numbered() else it } +
            addedSetlists.map { entry ->
                val shifted = entry.copy(origin = entry.origin?.plus(originOffset))
                if (shifted.isConflictAbout(keptLibrarySetlists) || shifted.isConflictAbout(askedSetlists)) shifted.numbered() else shifted
            },
        skippedFileNames = skippedFileNames + newSkipped,
        oversizedFileNames = oversizedFileNames + newOversized,
        unreadableDocumentFileNames = unreadableDocumentFileNames + newUnreadable,
        sourceNames = sourceNames + added.sourceNames,
    )
}

/** The library file an entry the import does not write already is, rather than a repeat of another entry. */
private val ImportPlan.SongEntry.isLibraryCopy get() = status == ImportPlan.Status.IDENTICAL && repeatedEntryIndex == null

/** The library file a conflicting entry would replace, under the spelling the library lists it by. */
private val ImportPlan.SongEntry.takenFileName get() = replacesFileName ?: fileName

private fun ImportPlan.SongEntry.isConflictAbout(identityKeys: Set<String>) =
    status == ImportPlan.Status.CONFLICTING && LibraryFiles.identityKey(takenFileName) in identityKeys

private fun ImportPlan.SongEntry.numbered() = copy(status = ImportPlan.Status.NEW, replacesFileName = null)

private fun ImportPlan.SetlistEntry.isConflictAbout(identityKeys: Set<String>) =
    status == ImportPlan.Status.CONFLICTING && LibraryFiles.identityKey(fileName) in identityKeys

private fun ImportPlan.SetlistEntry.numbered() = copy(status = ImportPlan.Status.NEW)

/** Whether the entry [key] names is a question about a name the library has, which only such an entry's choice answers. */
internal fun ImportPlan.isConflicting(key: ImportEntryKey) = when (key.kind) {
    ImportEntryKey.Kind.SONG -> songs.getOrNull(key.index)?.status
    ImportEntryKey.Kind.SETLIST -> setlists.getOrNull(key.index)?.status
} == ImportPlan.Status.CONFLICTING
