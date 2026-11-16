/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenuState
import com.pandulapeter.campfire.presentation.ui.platform.PictureSource

/**
 * What the editor's Image button does: picks pictures, whose directives arrive at the caret once they are kept as draft
 * pictures (`EditorImages.pickImages`).
 *
 * @param canPickPhotos Whether the button asks which [PictureSource] to pick from first, in a menu hanging from it
 *   ([menuState]); where it does not, it picks from [PictureSource.FILES].
 * @param menuState Held by the screen rather than the button, since Ctrl / Cmd + Shift + I opens the same menu
 *   ([onShortcut]) from wherever the focus is.
 * @param onPick Picks from the source handed.
 */
internal class EditorImagePick(
    val canPickPhotos: Boolean,
    val menuState: OverflowMenuState,
    val onPick: (PictureSource) -> Unit,
) {

    /**
     * What Ctrl / Cmd + Shift + I does: what a press of the button does. A menu can only hang from a button that is
     * there, so [revealButton] first unfolds the Shortcuts rows it is in; the button brings itself into view as the
     * menu opens.
     */
    fun onShortcut(revealButton: () -> Unit) {
        if (canPickPhotos) {
            revealButton()
            menuState.open()
        } else {
            onPick(PictureSource.FILES)
        }
    }
}
