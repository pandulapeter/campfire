/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.opensTempoSheet

/** Where the dock draws its tempo chip. */
@OptIn(ExperimentalTestApi::class)
internal fun AppTestScope.tempoChip(): Rect = onNode(opensTempoSheet()).fetchSemanticsNode().boundsInRoot

/**
 * Where the dock draws the diagram of [DOCK_CHORD], the one control said as that chord's shape, its name read without
 * the direction isolates around it.
 */
@OptIn(ExperimentalTestApi::class)
internal fun AppTestScope.diagram(): Rect = onAllNodes(
    hasClickAction() and SemanticsMatcher("is the diagram of $DOCK_CHORD") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            .any { description -> description.filterNot { it in DIRECTION_ISOLATES }.startsWith("$DOCK_CHORD: ") }
    },
).onFirst().fetchSemanticsNode().boundsInRoot

/** A chord of House of the Rising Sun that is not its key. */
private const val DOCK_CHORD = "C"

private const val DIRECTION_ISOLATES = "⁦⁧⁨⁩"
