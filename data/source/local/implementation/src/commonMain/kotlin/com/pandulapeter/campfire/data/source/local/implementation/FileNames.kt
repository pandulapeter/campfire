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
 */
internal suspend fun FileStorage.uniqueName(
    directory: StorageDirectory,
    desired: String,
    collisionSuffix: (index: Int) -> String = ::normalizedCollisionSuffix,
): String {
    if (!exists(directory, desired)) return desired
    val extension = desired.knownExtension()
    val base = desired.removeSuffix(extension)
    var index = 2
    while (true) {
        val candidate = base + collisionSuffix(index) + extension
        if (!exists(directory, candidate)) return candidate
        index++
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
 */
internal fun String.isNamed(desired: String): Boolean {
    val extension = knownExtension()
    if (!extension.equals(desired.knownExtension(), ignoreCase = true)) return false
    val base = removeSuffix(extension)
    val desiredBase = desired.removeSuffix(desired.knownExtension())
    if (base == desiredBase) return true
    val suffix = base.removePrefix(desiredBase)
    return suffix.length < base.length && COLLISION_SUFFIX.matches(suffix)
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

/** Both shapes [uniqueName] writes: the underscored one of a normalized name, and the bracketed one of every other. */
private val COLLISION_SUFFIX = Regex("""_\d+| \(\d+\)""")
