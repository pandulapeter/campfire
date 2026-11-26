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

import androidx.compose.foundation.clickable
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
 * wide, so **an unfolded header takes no tap**: the tap steps the song, as anywhere else on it. A long press or a right
 * click folds it at once (nobody presses a header that long for anything else), and so does a mouse's click on the
 * chevron, a pointer being exact where a finger lands wherever the song was tapped last. A folded header is still
 * unfolded by any press on it, which costs a reader nothing they were reading. Screen readers keep the toggle as the
 * header's action either way.
 *
 * [content] is handed what to put on the header and on its chevron: the gestures above while it is unfolded, an ordinary
 * press while it is folded (which a header that is a [androidx.compose.material3.Surface] of its own may take as its
 * `onClick` instead, where [FoldToggle.isExpanded] is false), and nothing where there is no [toggle].
 */
@Composable
internal fun FoldGestures(
    toggle: FoldToggle?,
    content: @Composable (header: Modifier, chevron: Modifier) -> Unit,
) {
    if (toggle == null) return content(Modifier, Modifier)
    // The header is composed in the same place folded or not, so that its chevron turns over rather than being drawn anew.
    val header = if (toggle.isExpanded) {
        unfoldedHeaderGestures(toggle)
    } else {
        Modifier.clickable(role = Role.Button, onClick = toggle.onToggled)
    }
    content(header, if (toggle.isExpanded) Modifier.onMouseClick(toggle::onMouseFold) else Modifier)
}

/** An unfolded header's long press, right click, and screen reader action. */
@Composable
private fun unfoldedHeaderGestures(toggle: FoldToggle) = Modifier
    .onContextClick {
        println("Fold: folded by a right click")
        toggle.onToggled()
    }
    .onLongPress {
        println("Fold: folded by a long press")
        toggle.onToggled()
    }
    .semantics(mergeDescendants = true) {
        role = Role.Button
        onClick {
            toggle.onToggled()
            true
        }
    }

private fun FoldToggle.onMouseFold() {
    println("Fold: the chevron clicked")
    onToggled()
}
