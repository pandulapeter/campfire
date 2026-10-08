/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.data.model.domain.Setlist

/** A song as it is opened: from the library, or from one setlist. */
internal data class SongPlace(
    val songFileName: String,
    val setlistFileName: String?,
)

/**
 * Where an override of how a song is played is kept, folded into one lookup: a song opened from a setlist reads the
 * setlist's entry, one opened from the library reads the preferences, and neither ever reads the other, so that a
 * setlist reads the same on every device. Looked up by how the song was opened rather than by a composite key, so
 * callers cannot accidentally mix the two up.
 */
internal data class SongOverrides<T : Any>(
    private val library: Map<String, T> = emptyMap(),
    private val bySetlist: Map<String, Map<String, T>> = emptyMap(),
) {

    operator fun get(songFileName: String, setlistFileName: String?): T? = if (setlistFileName == null) {
        library[songFileName]
    } else {
        bySetlist[setlistFileName]?.get(songFileName)
    }

    operator fun get(place: SongPlace): T? = get(place.songFileName, place.setlistFileName)

    /** These overrides with the one of [place] set to [value], or removed where that is null. */
    fun with(place: SongPlace, value: T?) = if (place.setlistFileName == null) {
        copy(library = if (value == null) library - place.songFileName else library + (place.songFileName to value))
    } else {
        val entries = bySetlist[place.setlistFileName].orEmpty()
        copy(bySetlist = bySetlist + (place.setlistFileName to if (value == null) entries - place.songFileName else entries + (place.songFileName to value)))
    }

    companion object {

        /** The overrides [library] holds and the ones every setlist's entries hold, as [read] finds them in an entry. */
        fun <T : Any> of(library: Map<String, T>, setlists: List<Setlist>, read: (Setlist.Entry) -> T?) = SongOverrides(
            library = library,
            bySetlist = setlists.associate { setlist ->
                setlist.fileName to setlist.entries.mapNotNull { entry -> read(entry)?.let { entry.songFileName to it } }.toMap()
            },
        )
    }
}
