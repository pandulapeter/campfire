/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SONG_METADATA_FIELDS

/** The one song a plan comes to, where it is nothing but that song: no setlist with it and nothing left out. */
internal val ImportPlan.loneSong: ImportPlan.SongEntry?
    get() = songs.singleOrNull()?.takeIf {
        setlists.isEmpty() && skippedFileNames.isEmpty() && oversizedFileNames.isEmpty() && unreadableDocumentFileNames.isEmpty()
    }

/** Whether the plan of a pasted text is one song the library already has, which is opened rather than made again. */
internal val ImportPlan.isLoneSongInLibrary get() = loneSong?.status == ImportPlan.Status.IDENTICAL

/**
 * The New song form a pasted text's [plan] becomes, or null where the plan is anything but one song the library does
 * not hold yet. A song whose name is taken is still one: the form is New song's, which numbers the name rather than
 * asking about it. The form holds what the song, as the conversion read it, declares and nothing else: a field it says
 * nothing about stays empty, the title included, rather than being filled with the name the text arrived under - that
 * is its first plain line, which is as often a row of chords or a line of lyrics as it is a title.
 */
internal fun newSongFromTextDialogOf(plan: ImportPlan): DialogType.NewSongFromText? {
    val song = plan.loneSong?.takeIf { it.status != ImportPlan.Status.IDENTICAL } ?: return null
    val summary = ChordProParser.summarize(song.text)
    return DialogType.NewSongFromText(
        text = song.text,
        values = SONG_METADATA_FIELDS.associateWith { ChordProMetadataFields.valueOf(summary.metadata, it).orEmpty() },
        isConverted = song.isConverted,
        hasChords = summary.hasChords,
    )
}
