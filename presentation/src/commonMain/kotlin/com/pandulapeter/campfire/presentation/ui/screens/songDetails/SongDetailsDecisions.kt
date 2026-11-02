/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/**
 * Whether the song details screen [destination] closes itself: every song it was opened on is gone from a library that
 * has been read, and indexed ([resolvedSongs] is empty, not null), so there is nothing left to show, and the screen goes
 * the way it would have if the song had been deleted from there. Only while it is still the screen on top ([top]), since
 * one that has just been popped goes on being composed for as long as its exit transition runs, and going back from
 * there would close the screen underneath it too. A preview of an import closes by its own rule
 * (`CloseImportPreviewWithoutReview`).
 */
internal fun shouldCloseSongDetails(
    isImportPreview: Boolean,
    resolvedSongs: List<Song>?,
    top: CampfireDestination?,
    destination: CampfireDestination.SongDetails,
) = !isImportPreview && resolvedSongs?.isEmpty() == true && top == destination

/** Whether the song being read is what the keys and the scroll are for: no dialog over it, and a song on top. */
internal fun isSongDetailsUncovered(visibleDialog: DialogType?, top: CampfireDestination?) =
    visibleDialog == null && top is CampfireDestination.SongDetails
