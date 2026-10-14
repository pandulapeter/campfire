/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.runtime.saveable.Saver
import com.pandulapeter.campfire.presentation.resources.save

/**
 * Which of the two panes the editor shows. [SPLIT] needs a window wide enough for both and is the choice a window
 * that has the room opens with, since seeing the song take shape is the reason the preview exists at all.
 */
internal enum class EditorPanes {
    EDIT, PREVIEW, SPLIT;

    companion object {
        /** Saved by name rather than left to the automatic saver, which only stores what a platform can serialize. */
        val Saver = Saver<EditorPanes, String>(save = { it.name }, restore = ::valueOf)
    }
}

/**
 * The panes shown for the [selected] ones: Split only where two panes fit, the choice surviving a window that narrows
 * and widens again rather than being forgotten the moment it cannot be honored.
 */
internal fun shownPanes(selected: EditorPanes, hasRoomForSplitPanes: Boolean) =
    if (selected == EditorPanes.SPLIT && !hasRoomForSplitPanes) EditorPanes.EDIT else selected
