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

import com.pandulapeter.campfire.presentation.ui.songView.model.FoldToggle
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_grid_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_section_collapse
import com.pandulapeter.campfire.presentation.resources.song_details_tab_collapse
import com.pandulapeter.campfire.presentation.ui.components.controls.ExpandChevron
import com.pandulapeter.campfire.presentation.ui.components.list.onContextClick
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenu
import com.pandulapeter.campfire.presentation.ui.components.menu.rememberOverflowMenuState
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind

/**
 * What a fold's header ([content]) is pressed by, and the menu its long press opens. The song is read by tapping it to
 * step, and a header is most of a line wide, so **an unfolded header takes no tap**: the tap steps the song, as anywhere
 * else on it. A long press, or a right click, opens a dropdown hanging from the header with the one entry that folds it
 * (named for a tab, a grid, or a section by [kind]), and a mouse's click on the chevron folds it at once, a pointer being
 * exact where a finger lands wherever the song was tapped last. A folded header is still unfolded by any press on it,
 * which costs a reader nothing they were reading. Screen readers keep the toggle as the header's action either way, the
 * menu as its long press.
 *
 * [content] is handed what to put on the header and on its chevron: the gestures above while it is unfolded, an ordinary
 * press while it is folded (which a header that is a [androidx.compose.material3.Surface] of its own may take as its
 * `onClick` instead, where [FoldToggle.isExpanded] is false), and nothing where there is no [toggle].
 */
@Composable
internal fun FoldMenuAnchor(
    toggle: FoldToggle?,
    kind: FoldableKind?,
    content: @Composable (header: Modifier, chevron: Modifier) -> Unit,
) {
    if (toggle == null) return content(Modifier, Modifier)
    val state = rememberOverflowMenuState()
    val foldText = stringResource(
        when (kind) {
            FoldableKind.TAB -> Res.string.song_details_tab_collapse
            FoldableKind.GRID -> Res.string.song_details_grid_collapse
            FoldableKind.VERBATIM, null -> Res.string.song_details_section_collapse
        },
    )
    // The header is composed in the same place folded or not, so that its chevron turns over rather than being drawn anew.
    OverflowMenu(
        state = state,
        button = { open ->
            val header = if (toggle.isExpanded) {
                unfoldedHeaderGestures(toggle = toggle, foldText = foldText, open = open)
            } else {
                Modifier.clickable(role = Role.Button, onClick = toggle.onToggled)
            }
            content(header, if (toggle.isExpanded) Modifier.onMouseClick(toggle::onMouseFold) else Modifier)
        },
    ) { select ->
        DropdownMenuItem(
            text = { Text(foldText) },
            leadingIcon = { ExpandChevron(isExpanded = true, contentDescription = null) },
            onClick = {
                select {
                    println("Fold menu: folded")
                    toggle.onToggled()
                }
            },
        )
    }
}

/** An unfolded header's long press, right click, and screen reader actions. */
@Composable
private fun unfoldedHeaderGestures(toggle: FoldToggle, foldText: String, open: () -> Unit) = Modifier
    .onContextClick {
        println("Fold menu: opened by a right click")
        open()
    }
    .onLongPress {
        println("Fold menu: opened by a long press")
        open()
    }
    .semantics(mergeDescendants = true) {
        role = Role.Button
        onClick {
            toggle.onToggled()
            true
        }
        onLongClick(label = foldText) {
            open()
            true
        }
    }

private fun FoldToggle.onMouseFold() {
    println("Fold: the chevron clicked")
    onToggled()
}
