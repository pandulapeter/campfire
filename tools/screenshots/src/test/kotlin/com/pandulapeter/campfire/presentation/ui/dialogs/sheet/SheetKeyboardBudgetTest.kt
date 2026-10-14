/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.create
import com.pandulapeter.campfire.presentation.resources.delete
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_library
import com.pandulapeter.campfire.presentation.resources.settings_library_delete
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.setlists_create_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_playing_edit
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.song_editor_find_and_replace
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.DATED_SETLIST
import com.pandulapeter.campfire.screenshots.KEYBOARD_HEIGHT
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openAboutTheSong
import com.pandulapeter.campfire.screenshots.openCoverSearch
import com.pandulapeter.campfire.screenshots.openEditor
import com.pandulapeter.campfire.screenshots.openNewSong
import com.pandulapeter.campfire.screenshots.openSetlistDetails
import com.pandulapeter.campfire.screenshots.openSongMetadata
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.fail

/**
 * Root `CLAUDE.md`'s pinned height budget, checked by semantics on every sheet with a text field: on the smallest phone
 * with the keyboard up, the header's finishing action and the field being typed into are both inside the room the
 * keyboard leaves. A golden only says a sheet changed; this says it broke, and runs on every system.
 */
@OptIn(ExperimentalTestApi::class)
internal class SheetKeyboardBudgetTest {

    @Test
    fun `every text field sheet keeps its finishing action and its focused field above the keyboard`() {
        val failures = mutableListOf<String>()
        cases.forEach { case ->
            if (case.knownFailure != null) {
                println("${case.name} is skipped: ${case.knownFailure}")
                return@forEach
            }
            runCatching {
                runAppTest(
                    window = TestWindow.PHONE,
                    setlists = case.setlists,
                    isCoverArtEnabled = case.isCoverArtEnabled,
                ) {
                    case.open(this)
                    onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[0].performClick()
                    keyboardHeight = KEYBOARD_HEIGHT
                    waitForIdle()
                    val room = TestWindow.PHONE.height.value - KEYBOARD_HEIGHT.value
                    val action = if (case.action == Res.string.close) {
                        onNode(hasContentDescription(string(Res.string.close)) and hasAnyAncestor(isDialog()))
                    } else {
                        onNode(hasText(string(case.action)) and hasClickAction() and hasAnyAncestor(isDialog()))
                    }
                    val field = onNode(isFocused and hasSetTextAction())
                    failures += listOfNotNull(
                        outsideOf(room, "${case.name}: the header's ${string(case.action)}", action.fetchSemanticsNode().boundsInRoot),
                        outsideOf(room, "${case.name}: the focused field", field.fetchSemanticsNode().boundsInRoot),
                    )
                }
            }.onFailure { failures += "${case.name} could not be checked: ${it.stackTraceToString()}" }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString(separator = "\n"))
    }

    private val cases = listOf(
        SheetCase(name = "New song", action = Res.string.create) { openNewSong() },
        SheetCase(name = "Edit song details", action = Res.string.save) { openSongMetadata() },
        SheetCase(name = "Cover search", action = Res.string.save, isCoverArtEnabled = true) { openCoverSearch() },
        SheetCase(name = "Edit setlist", action = Res.string.save, setlists = listOf(DATED_SETLIST)) { openSetlistDetails() },
        SheetCase(name = "New setlist", action = Res.string.create, setlists = listOf(DATED_SETLIST)) {
            tap(hasText(string(Res.string.setlists)) and hasClickAction())
            tap(hasContentDescription(string(Res.string.setlists_new_setlist)))
            tap(hasText(string(Res.string.setlists_create_setlist)) and hasClickAction())
        },
        SheetCase(name = "Links", action = Res.string.save) { openAboutTheSongTile(Res.string.song_details_links_edit) },
        SheetCase(name = "Tags", action = Res.string.save) { openAboutTheSongTile(Res.string.song_details_tags_manage) },
        SheetCase(name = "Languages", action = Res.string.save) { openAboutTheSongTile(Res.string.song_details_languages_edit) },
        SheetCase(name = "Song defaults", action = Res.string.save) { openAboutTheSongTile(Res.string.song_details_playing_edit) },
        // The search is read while it is typed, so there is nothing to finish and the header's close is what has to stay.
        SheetCase(name = "Find and replace", action = Res.string.close) {
            openEditor()
            val findAndReplace = string(Res.string.song_editor_find_and_replace)
            if (onAllNodes(hasContentDescription(findAndReplace)).fetchSemanticsNodes().isEmpty()) {
                tap(hasContentDescription(string(Res.string.songs_actions)))
                tap(hasText(findAndReplace) and hasClickAction())
            } else {
                tap(hasContentDescription(findAndReplace))
            }
        },
        // Nothing is typed into it and its Delete is never pressed: the check only reads where they are.
        SheetCase(name = "Delete library", action = Res.string.delete) {
            tap(hasText(string(Res.string.settings)) and hasClickAction())
            tap(hasText(string(Res.string.settings_library)) and hasClickAction())
            // The row's own text is the library's size, and its click is announced as what it does.
            onNode(hasClickLabel(string(Res.string.settings_library_delete))).performScrollTo()
            tap(hasClickLabel(string(Res.string.settings_library_delete)))
        },
    )
}

/**
 * One sheet with a text field: its [name] in a failure, how to [open] it over a library that also holds [setlists], and
 * the [action] that finishes it in its header (or its close, for a sheet with nothing to finish). [knownFailure] says why
 * a case that fails today is skipped until its fix lands.
 */
private class SheetCase(
    val name: String,
    val action: StringResource,
    val setlists: List<String> = emptyList(),
    val isCoverArtEnabled: Boolean = false,
    val knownFailure: String? = null,
    val open: AppTestScope.() -> Unit,
)

/**
 * Opens one of the About the song sheet's groups by the label its click is announced with. The click is the group's
 * action rather than a tap at its middle, where the Links group has its links, which a tap would open instead.
 */
private fun AppTestScope.openAboutTheSongTile(clickLabel: StringResource) {
    openAboutTheSong()
    onNode(hasClickLabel(string(clickLabel))).performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
}

private fun hasClickLabel(label: String) = SemanticsMatcher("is clicked to $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

private val isFocused = SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)

/** What is wrong with [bounds] (in dp, since the tests' density is one) not being inside the window's top [room] dp. */
private fun outsideOf(room: Float, what: String, bounds: Rect) = when {
    bounds.top < 0f -> "$what is above the window (top at ${bounds.top} dp)."
    bounds.bottom > room -> "$what reaches under the keyboard (bottom at ${bounds.bottom} dp, room for $room dp)."
    else -> null
}
