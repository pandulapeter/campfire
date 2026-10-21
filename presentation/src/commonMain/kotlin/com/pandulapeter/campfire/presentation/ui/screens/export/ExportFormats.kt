/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.share.ShareTarget

/**
 * What [dialog] would be shared as a link: a song or a setlist, never several songs at once, which have no file of
 * their own to follow.
 */
internal fun shareTargetOf(dialog: DialogType.Export) = when {
    dialog.setlist != null -> ShareTarget(SharedLinkKind.SETLIST, dialog.setlist.fileName)
    dialog.songs.isNotEmpty() -> null
    dialog.song != null -> ShareTarget(SharedLinkKind.SONG, dialog.song.fileName)
    else -> null
}

/**
 * The formats offered for [dialog]: a link only for one song or setlist, and only where the build can connect an
 * account to make it from ([isSyncAvailable]).
 */
internal fun exportFormatsOf(dialog: DialogType.Export, isSyncAvailable: Boolean) = PrintSettings.Format.entries.filter {
    it != PrintSettings.Format.LINK || isSyncAvailable && shareTargetOf(dialog) != null
}

/** These settings in a format among [formats]: one left for an export that does not offer it is a PDF there. */
internal fun PrintSettings.withFormatIn(formats: List<PrintSettings.Format>) = if (format in formats) this else copy(format = PrintSettings.Format.PDF)

/**
 * The settings an export screen opens with: the user's [stored] ones, in the format they picked on this very screen
 * before it was composed again ([pickedFormat]), else in the one this opening asks for ([DialogType.Export.format]),
 * else in their own, and in any case in one of [formats].
 */
internal fun openingSettingsOf(
    stored: PrintSettings,
    dialog: DialogType.Export,
    pickedFormat: PrintSettings.Format?,
    formats: List<PrintSettings.Format>,
) = ((pickedFormat ?: dialog.format)?.let { stored.copy(format = it) } ?: stored).withFormatIn(formats)
