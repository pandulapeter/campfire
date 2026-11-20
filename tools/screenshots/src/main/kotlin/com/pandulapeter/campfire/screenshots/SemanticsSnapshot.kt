/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState

/**
 * What the scene shows as its semantics say it, for an agent to check a screen without reading its picture: one line a
 * node that says or does something (its role, its text or description, and its state), nested by indentation. A node
 * hidden from accessibility, not placed, or clipped to nothing is left out with what it holds, and one that neither says
 * nor does anything is left out alone, its children taking its place. Each layer (a dialog, every bottom sheet, a menu)
 * follows the window under a heading of its own.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun ImageComposeScene.semanticsSnapshot(): String = buildString {
    semanticsOwners.forEachIndexed { index, owner ->
        if (index > 0) appendLine("Layer ${index + 1}:")
        owner.rootSemanticsNode.children.forEach { appendNode(it, depth = if (index > 0) 1 else 0) }
    }
}

private fun StringBuilder.appendNode(node: SemanticsNode, depth: Int) {
    val config = node.config
    if (SemanticsProperties.HideFromAccessibility in config || !node.layoutInfo.isPlaced || node.boundsInRoot.isEmpty) return
    val line = node.describe()
    if (line != null) appendLine("  ".repeat(depth) + line)
    node.children.forEach { appendNode(it, depth = if (line == null) depth else depth + 1) }
}

/** The node's line, or null for one that neither says nor does anything. */
private fun SemanticsNode.describe(): String? {
    val config = config
    val role = config.getOrNull(SemanticsProperties.Role)?.toString()
    val editable = config.getOrNull(SemanticsProperties.EditableText)?.text
    val labels = (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() + config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
        .filter { it.isNotBlank() }
        .distinct()
    val states = buildList {
        config.getOrNull(SemanticsProperties.StateDescription)?.let(::add)
        when (config.getOrNull(SemanticsProperties.ToggleableState)) {
            ToggleableState.On -> add("checked")
            ToggleableState.Off -> add("unchecked")
            ToggleableState.Indeterminate -> add("indeterminate")
            null -> Unit
        }
        if (config.getOrNull(SemanticsProperties.Selected) == true) add("selected")
        if (SemanticsProperties.Disabled in config) add("disabled")
        if (config.getOrNull(SemanticsProperties.Focused) == true) add("focused")
        if (SemanticsProperties.Heading in config) add("heading")
        config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.let { add(if (it.range.isEmpty() || it.range.start == it.range.endInclusive) "progress" else "progress ${it.current}") }
        if (SemanticsProperties.VerticalScrollAxisRange in config) add("scrolls vertically")
        if (SemanticsProperties.HorizontalScrollAxisRange in config) add("scrolls horizontally")
        if (role == null && SemanticsActions.OnClick in config) add("clickable")
    }
    // Focus alone is a wrapper's that holds the focused control, and says nothing of its own.
    if (role == null && editable == null && labels.isEmpty() && states.all { it == "focused" }) return null
    return listOfNotNull(
        role ?: if (editable != null) "TextField" else null,
        labels.takeIf { it.isNotEmpty() }?.joinToString(" | ") { "\"${it.shortened()}\"" },
        editable?.let { "value=\"${it.shortened()}\"" },
        states.takeIf { it.isNotEmpty() }?.joinToString(prefix = "[", postfix = "]"),
    ).joinToString(" ")
}

/**
 * One line of at most [MAX_TEXT_LENGTH] characters, for a song's whole text or a long description, without the
 * direction isolates the app puts around names and numbers or the zero-width spaces a song's lines carry under chords.
 */
private fun String.shortened() = replace('\n', ' ').replace(INVISIBLE, "").let { if (it.length > MAX_TEXT_LENGTH) it.take(MAX_TEXT_LENGTH - 1) + "…" else it }

private val INVISIBLE = Regex("[\u200B-\u200F\u202A-\u202E\u2066-\u2069]")

private const val MAX_TEXT_LENGTH = 80
