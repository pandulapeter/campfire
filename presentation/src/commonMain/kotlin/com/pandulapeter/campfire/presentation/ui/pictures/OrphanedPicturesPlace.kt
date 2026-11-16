/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/** Where pictures picked for a view model that is gone are put, see [orphanedPicturesPlace]. */
internal sealed interface OrphanedPicturesPlace {

    /** At the caret of the editor of [fileName], as its Image button would have put them. */
    data class Editor(val fileName: String) : OrphanedPicturesPlace

    /** The first one, in the place of the missing picture [pick] names, as its Add image would have put it. */
    data class MissingImage(val pick: MissingImagePick) : OrphanedPicturesPlace

    /** Nowhere: let go, and the user picks again. */
    data object Nowhere : OrphanedPicturesPlace
}

/**
 * Where pictures picked for a view model that is gone belong, from the screen on top of the restored back stack ([top])
 * and the missing picture a pick was under way for ([missingImagePick], `PendingMissingImagePick`): the editor, whose
 * Image button is that screen's only picker; a missing picture, where a song details screen holding its song is on top
 * (a preview's pages are files nobody may write); nowhere in read only mode, which hides both buttons, nor anywhere else,
 * which the user has gone on to since. Whether the song still names that missing picture is the store's to say, as it
 * reads the song.
 */
internal fun orphanedPicturesPlace(top: CampfireDestination?, missingImagePick: MissingImagePick?, isReadOnly: Boolean) = when {
    isReadOnly -> OrphanedPicturesPlace.Nowhere
    top is CampfireDestination.SongEditor -> OrphanedPicturesPlace.Editor(top.fileName)
    top is CampfireDestination.SongDetails && top.importPreview == null && missingImagePick != null && missingImagePick.fileName in top.songFileNames ->
        OrphanedPicturesPlace.MissingImage(missingImagePick)
    else -> OrphanedPicturesPlace.Nowhere
}
