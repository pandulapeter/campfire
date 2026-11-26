/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.pandulapeter.campfire.presentation.ui.components.list.onContextClick
import com.pandulapeter.campfire.presentation.ui.songView.model.FoldToggle

/**
 * What a fold's header ([content]) is pressed by. The song is read by tapping it to step, and a header is most of a line
 * wide, so **a header takes no tap**, folded or not: the tap steps the song, as anywhere else on it. A long press or a
 * right click folds or unfolds it at once (nobody presses a header that long for anything else), and so does a mouse's
 * click on the chevron, a pointer being exact where a finger lands wherever the song was tapped last. Screen readers
 * keep the toggle as the header's action.
 *
 * [content] is handed what to put on the header and on its chevron: the gestures above, or nothing where there is no
 * [toggle].
 */
@Composable
internal fun FoldGestures(
    toggle: FoldToggle?,
    content: @Composable (header: Modifier, chevron: Modifier) -> Unit,
) {
    if (toggle == null) return content(Modifier, Modifier)
    content(headerGestures(toggle), Modifier.onMouseClick(toggle::onMouseToggle))
}

/** A header's long press, right click, and screen reader action. */
@Composable
private fun headerGestures(toggle: FoldToggle) = Modifier
    .onContextClick {
        println("Fold: toggled by a right click")
        toggle.onToggled()
    }
    .onLongPress {
        println("Fold: toggled by a long press")
        toggle.onToggled()
    }
    .semantics(mergeDescendants = true) {
        role = Role.Button
        onClick {
            toggle.onToggled()
            true
        }
    }

private fun FoldToggle.onMouseToggle() {
    println("Fold: the chevron clicked")
    onToggled()
}
