/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

/**
 * What opening [editor] puts on the back stack: the editor alone over a details screen showing its song
 * ([isSongShownOnTop]), and otherwise the song's details screen under it. Leaving the editor always lands on the song it
 * edited - from a new song, a copy, the import screen or a draft reopened on launch alike - rather than on whatever list
 * it was opened from, which is what the song's own address on the web (`song/{song}/edit`) has always been made of.
 * The editor of a song with no file yet has no song to land on: it goes alone, and its first Save puts the song under it
 * (`Navigator.followNewSongCreated`), so a song discarded unsaved leaves for where it was opened from.
 */
internal fun songEditorEntries(editor: CampfireDestination.SongEditor, isSongShownOnTop: Boolean): List<CampfireDestination> =
    if (isSongShownOnTop || editor.isNewSong) {
        listOf(editor)
    } else {
        listOf(CampfireDestination.SongDetails(songFileNames = listOf(editor.fileName), setlistFileName = null, initialIndex = 0), editor)
    }
