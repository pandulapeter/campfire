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

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination

/** Where a file picked as an attachment for a view model that is gone is put, see [orphanedAttachmentPlace]. */
internal sealed interface OrphanedAttachmentPlace {

    /** A new row of the Attachments sheet of [target], as its Add file would have put it. */
    data class Sheet(val target: SongEditTarget) : OrphanedAttachmentPlace

    /** An attachment line of the editor of [fileName], as its Attach would have put it. */
    data class Editor(val fileName: String) : OrphanedAttachmentPlace

    /** Nowhere: let go, with a message asking for it again. */
    data object Nowhere : OrphanedAttachmentPlace
}

/**
 * Where an attachment picked for a view model that is gone belongs, from the dialogs open once the saved one has been
 * reopened ([openDialogs], bottom first) and the screen on top of the restored back stack ([top]): the topmost
 * Attachments sheet, whose Add file is the one picker that sheet has; else the editor, whose Attach is that screen's;
 * nowhere in read only mode or with the attachments switched off, which hide both, nor anywhere else, which the user
 * has gone on to since.
 */
internal fun orphanedAttachmentPlace(
    top: CampfireDestination?,
    openDialogs: List<DialogType>,
    isReadOnly: Boolean,
    areAttachmentsEnabled: Boolean,
): OrphanedAttachmentPlace {
    if (isReadOnly || !areAttachmentsEnabled) return OrphanedAttachmentPlace.Nowhere
    val sheet = openDialogs.lastOrNull { it is DialogType.SongAttachments } as DialogType.SongAttachments?
    return when {
        sheet != null -> OrphanedAttachmentPlace.Sheet(sheet.target)
        top is CampfireDestination.SongEditor -> OrphanedAttachmentPlace.Editor(top.fileName)
        else -> OrphanedAttachmentPlace.Nowhere
    }
}
