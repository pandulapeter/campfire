/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.share

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType

/**
 * The export screen of [share]'s item, opened on the Link format to show its QR code, for this opening only, the format
 * the user exports in kept, or null where the library lacks it.
 */
internal fun exportDialogOf(share: Share, songsByFileName: Map<String, Song>, setlists: List<Setlist>) = when (share.kind) {
    SharedLinkKind.SONG -> songsByFileName.let { songs ->
        songs[share.fileName] ?: songs.entries.firstOrNull { LibraryFiles.isSameLibraryName(it.key, share.fileName) }?.value
    }?.let { DialogType.Export(song = it, format = PrintSettings.Format.LINK) }
    SharedLinkKind.SETLIST -> setlists
        .firstOrNull { LibraryFiles.isSameLibraryName(it.fileName, share.fileName) }
        ?.let { DialogType.Export(setlist = it, format = PrintSettings.Format.LINK) }
}
