/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory

internal const val SONG_EXTENSION = LibraryFiles.SONG_EXTENSION
internal const val SETLIST_EXTENSION = LibraryFiles.SETLIST_EXTENSION

/**
 * The name a song's own metadata gives it: the artist and the title normalized one at a time and joined by
 * [LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR], a song with no artist named after its title alone. It is the
 * name an export hands out as well, so a song that is named this way leaves the app under the name it already has.
 *
 * @param extension The one the file is stored under, since the library reads the whole ChordPro family and renaming
 *   a file is no reason to claim its contents are written differently than they are.
 */
internal fun songFileName(title: String, artist: String, extension: String = SONG_EXTENSION): String {
    val name = LibraryFiles.normalizedName(title)
    val base = if (artist.isBlank()) name else LibraryFiles.normalizedName(artist) + LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR + name
    return base + extension
}

/**
 * The name a setlist is stored under, normalized the way a song's is ([LibraryFiles.normalizedName]). A setlist
 * carries its title inside the document rather than in its file name, which is why its file simply follows the title
 * whenever that changes (`SetlistLocalSource.renameSetlist`), where a song's has to be asked for.
 */
internal fun setlistFileName(title: String) = LibraryFiles.normalizedName(title) + SETLIST_EXTENSION

/**
 * The first free variant of [desired], numbered before the extension. Nothing in the library is ever overwritten
 * implicitly, so imports and new songs land next to a name they collide with rather than replacing it.
 *
 * @param collisionSuffix How the number is written. Every name Campfire gives a file is normalized, so the default is
 *   the underscore that name is already built out of: `tukorfurogep-arviz_2`, rather than a space and a pair of
 *   brackets in a name that has neither. [arrivingCollisionSuffix] is for the one caller that writes a file under a
 *   name it did not invent.
 * @param currentName The name of the file being renamed, if this is a rename. A case-insensitive file system (macOS,
 *   Windows and iOS by default) says a name that differs from it only in case is taken, and it is taken by this very
 *   file, which is not a reason to number it. Such a candidate is held against the directory's listing instead, which
 *   carries the names exactly as they are: on a case-sensitive file system a different file may well be there under it
 *   - and only then is the directory listed.
 */
internal suspend fun FileStorage.uniqueName(
    directory: StorageDirectory,
    desired: String,
    collisionSuffix: (index: Int) -> String = ::normalizedCollisionSuffix,
    currentName: String? = null,
): String {
    fun isOwnName(candidate: String) = candidate.equals(currentName, ignoreCase = true)
    if (!isOwnName(desired) && !exists(directory, desired)) return desired
    // Only a rename needs the exact names (see currentName): for anything else, exists() already says all a listing would.
    val takenNames = if (currentName == null) emptySet() else listNames(directory).toHashSet()
    suspend fun isFree(candidate: String) = candidate !in takenNames && (isOwnName(candidate) || !exists(directory, candidate))
    if (isFree(desired)) return desired
    val extension = desired.knownExtension()
    val base = desired.removeSuffix(extension)
    var index = 2
    while (true) {
        val candidate = base + collisionSuffix(index) + extension
        if (isFree(candidate)) return candidate
        index++
    }
}

/**
 * Moves a file from [currentName] to [newName] (a name [uniqueName] gave out) by writing it anew with [write], then
 * removing the old one. Written before the old one is removed, so that a move that fails halfway leaves the file twice
 * over rather than not at all.
 *
 * A name that differs from the current one only in case goes through a temporary name in between: on a
 * case-insensitive file system writing it is writing the current file, and the deletion that follows would remove the
 * only copy. That costs three writes, for a move that happens once to a file somebody named by hand.
 */
internal suspend fun FileStorage.moveFile(
    directory: StorageDirectory,
    currentName: String,
    newName: String,
    write: suspend (name: String) -> Unit,
) {
    if (newName.equals(currentName, ignoreCase = true)) {
        val extension = newName.knownExtension()
        val temporaryName = uniqueName(directory, newName.removeSuffix(extension) + TEMPORARY_MOVE_SUFFIX + extension)
        write(temporaryName)
        delete(directory, currentName)
        write(newName)
        delete(directory, temporaryName)
    } else {
        write(newName)
        delete(directory, currentName)
    }
}

/** The collision suffix of a name the app derived itself, which is every name it writes into the library. */
internal fun normalizedCollisionSuffix(index: Int) = LibraryFiles.NAME_SEPARATOR + index

/**
 * The collision suffix of a file arriving under a name of someone else's making: a copy that sync brings down of a
 * file changed on both sides. Its name is whatever the other device called it - Campfire may not even be able to
 * parse the file - so the number is added the way it would be to any document, without pretending the name it is
 * joined to was built out of underscores.
 */
internal fun arrivingCollisionSuffix(index: Int) = " ($index)"

/**
 * Whether this file is already named [desired], the number a collision may have added included: a file that had to
 * make way for another one is named as well as it can be, and an offer to rename it again would be one that never
 * goes away however often it is taken.
 *
 * The file's own name is composed before it is compared, since [desired] always is: a file named on a Mac arrives
 * decomposed and would otherwise never be named as it should be, offering a rename that, once taken, offers itself
 * again. Composing it does not rename anything - an existing file keeps the form it was written in until the user
 * asks for its name to be updated.
 */
internal fun String.isNamed(desired: String): Boolean {
    val extension = knownExtension()
    if (!extension.equals(desired.knownExtension(), ignoreCase = true)) return false
    val base = removeSuffix(extension).normalizedToNfc()
    val desiredBase = desired.removeSuffix(desired.knownExtension())
    return base == desiredBase || LibraryFiles.withoutCollisionSuffix(base) == desiredBase
}

/**
 * ".setlist.json" has to win over ".json", so the compound extensions are matched first and only then the part after
 * the last dot.
 */
internal fun String.knownExtension() = when {
    endsWith(SETLIST_EXTENSION, ignoreCase = true) -> takeLast(SETLIST_EXTENSION.length)
    contains('.') -> substring(lastIndexOf('.'))
    else -> ""
}

internal fun String.withoutExtension() = removeSuffix(knownExtension())

private const val TEMPORARY_MOVE_SUFFIX = "_renaming"
