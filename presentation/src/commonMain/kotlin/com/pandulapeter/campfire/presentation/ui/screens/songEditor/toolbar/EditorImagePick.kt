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
 * What the editor's Image button does: asks in a menu hanging from it ([menuState]) where from, then picks pictures, or
 * asks for one's address, whose directives arrive at the caret once they are kept as draft pictures
 * (`EditorImages.pickImages`, `EditorImages.downloadImage`).
 *
 * @param canPickPhotos Whether the menu offers Photos, the platform having a photo library apart from its files.
 * @param menuState Held by the screen rather than the button, since Ctrl / Cmd + Shift + I opens the same menu
 *   ([onShortcut]) from wherever the focus is.
 * @param onPick Picks from the source handed.
 * @param onLink Asks for the address of a picture to download.
 */
internal class EditorImagePick(
    val canPickPhotos: Boolean,
    val menuState: OverflowMenuState,
    val onPick: (PictureSource) -> Unit,
    val onLink: () -> Unit,
) {

    /**
     * What Ctrl / Cmd + Shift + I does: what a press of the button does. A menu can only hang from a button that is
     * there, so [revealButton] first unfolds the Shortcuts rows it is in; the button brings itself into view as the
     * menu opens.
     */
    fun onShortcut(revealButton: () -> Unit) {
        revealButton()
        menuState.open()
    }
}
