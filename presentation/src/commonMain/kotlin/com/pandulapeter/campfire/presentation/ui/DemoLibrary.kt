/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The handful of songs and the one setlist the app is shipped with, so that a library that has never been filled has
 * something in it to read, transpose and play from.
 *
 * They are bundled as the plain ChordPro and setlist files they are, and they reach the library through the very
 * import the file picker and a dropped archive go through — nothing here writes anything itself. That is what makes
 * them ordinary: they collide, they are numbered, they are skipped when the same file is already there, and the user
 * can edit or delete any of them without the app ever putting one back on its own.
 *
 * Every song is public domain, and every file is named exactly as the library would name the song inside it
 * (`LibraryFiles.normalizedName(artist)-normalizedName(title).cho`), so that the one list below serves both reading
 * the resources and asking whether they are already in the library.
 */
internal object DemoLibrary {

    /**
     * What the demo consists of, as library file names. The setlist is last for the reason an import writes the
     * setlists last: it points at the songs above it by name.
     */
    private val songFileNames = listOf(
        "traditional_american-house_of_the_rising_sun.cho",
        "traditional_american-red_river_valley.cho",
        "traditional_american-wayfaring_stranger.cho",
        "traditional_english-scarborough_fair.cho",
        "traditional_hymn-amazing_grace.cho",
        "traditional_spiritual-down_by_the_riverside.cho",
        "traditional_spiritual-swing_low_sweet_chariot.cho",
        "traditional_spiritual-when_the_saints_go_marching_in.cho",
    )

    private const val SETLIST_FILE_NAME = "getting_started.setlist.json"

    /** Whether the library already holds all of it, which is what takes the offer to load it out of the settings. */
    fun isPresentIn(songs: List<Song>, setlists: List<Setlist>): Boolean {
        val storedSongFileNames = songs.mapTo(mutableSetOf()) { it.fileName }
        return songFileNames.all { it in storedSongFileNames } && setlists.any { it.fileName == SETLIST_FILE_NAME }
    }

    /**
     * The bundled files, read as the import wants them. It throws the way reading any resource does, and the caller
     * reports that as a failed import: a demo that cannot be read is nothing worse than one that was not loaded.
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun read(): List<ImportedFile> = (songFileNames + SETLIST_FILE_NAME).map { fileName ->
        ImportedFile(
            name = fileName,
            bytes = Res.readBytes("$DIRECTORY/$fileName"),
        )
    }

    private const val DIRECTORY = "files/demo"
}
