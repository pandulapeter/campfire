/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

/**
 * Where a metadata edit lands: the file, or the text being typed in the editor open on that file. A
 * [DialogType.SongEdit] carries one, and the view model's edits are applied to it.
 */
sealed interface SongEditTarget {
    val fileName: String

    /** The file itself, written through the `SetChordPro*` use cases. */
    data class File(override val fileName: String) : SongEditTarget

    /** The editor's unsaved text, which only Save writes to the file. */
    data class EditorDraft(override val fileName: String) : SongEditTarget
}
