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

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.onboarding_next
import com.pandulapeter.campfire.presentation.resources.onboarding_start_playing
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.setlists_actions
import com.pandulapeter.campfire.presentation.resources.setlists_edit_details
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_create_song
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.tools

/** Taps the node [matcher] finds as a finger does, which leaves no pointer hovering over the screen to draw a highlight. */
internal fun AppTestScope.tap(matcher: SemanticsMatcher) {
    onNode(matcher).performTouchInput { click() }
    waitForIdle()
}

internal fun AppTestScope.openSong() = tap(hasText(RISING_SUN_TITLE) and hasClickAction())

internal fun AppTestScope.openEditor() {
    openSong()
    tap(hasContentDescription(string(Res.string.songs_actions)))
    tap(hasText(string(Res.string.songs_edit_song)) and hasClickAction())
}

internal fun AppTestScope.openAboutTheSong() {
    openSong()
    tap(hasText(RISING_SUN_TITLE, substring = true) and hasClickAction())
}

internal fun AppTestScope.openSongMetadata() {
    openAboutTheSong()
    tap(hasText(string(Res.string.song_details_duration), substring = true) and hasClickAction())
    keyboardHeight = KEYBOARD_HEIGHT
}

internal fun AppTestScope.openCoverSearch() {
    openAboutTheSong()
    tap(hasClickAction() and hasAnyAncestor(hasText(string(Res.string.song_details_duration), substring = true)))
    keyboardHeight = KEYBOARD_HEIGHT
}

internal fun AppTestScope.openNewSong() {
    tap(hasContentDescription(string(Res.string.songs_new_song)))
    tap(hasText(string(Res.string.songs_create_song)))
    keyboardHeight = KEYBOARD_HEIGHT
}

internal fun AppTestScope.openSetlistDetails() {
    tap(hasText(string(Res.string.setlists)) and hasClickAction())
    tap(hasContentDescription(string(Res.string.setlists_actions)))
    tap(hasText(string(Res.string.setlists_edit_details)) and hasClickAction())
    keyboardHeight = KEYBOARD_HEIGHT
}

/** Opens the Scales tab of a phone, behind the Tools item of its navigation bar. */
internal fun AppTestScope.openScales() {
    tap(hasText(string(Res.string.tools)) and hasClickAction())
    // The family row's "Scales" title is not clickable, so the click action picks the tab, which a large text size
    // leaves off the end of the scrolling tab row.
    val scalesTab = hasText(string(Res.string.scales)) and hasClickAction()
    onNode(scalesTab).performScrollTo()
    tap(scalesTab)
}

/** Opens the Chords tab of a phone, behind the Tools item of its navigation bar. */
internal fun AppTestScope.openChords() {
    tap(hasText(string(Res.string.tools)) and hasClickAction())
    tap(hasText(string(Res.string.chords)) and hasClickAction())
}

/** Turns the onboarding of a first run with Next until its last page, the one Start playing finishes it from. */
internal fun AppTestScope.openOnboardingLastPage() {
    val startPlaying = hasText(string(Res.string.onboarding_start_playing)) and hasClickAction()
    repeat(MAX_ONBOARDING_PAGES) {
        if (onAllNodes(startPlaying).fetchSemanticsNodes().isNotEmpty()) return
        tap(hasText(string(Res.string.onboarding_next)) and hasClickAction())
    }
    error("The onboarding did not reach Start playing in $MAX_ONBOARDING_PAGES pages.")
}

internal const val RISING_SUN_TITLE = "House of the Rising Sun"
internal const val DATED_SETLIST = "campfire_tests-an_evening_by_the_fire.setlist.json"

/** A phone keyboard's height, which leaves a 360 by 640 dp phone the 330 dp the sheets' pinned height is budgeted for. */
internal val KEYBOARD_HEIGHT = 310.dp

/** More pages than the onboarding has with every one of them shown, so that a Next that does nothing fails rather than loops. */
private const val MAX_ONBOARDING_PAGES = 10
