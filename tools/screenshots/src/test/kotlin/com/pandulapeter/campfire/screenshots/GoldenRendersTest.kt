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

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.setlists_actions
import com.pandulapeter.campfire.presentation.resources.setlists_edit_details
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.resources.songs_create_song
import com.pandulapeter.campfire.presentation.resources.songs_edit_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The screens most likely to break without anyone noticing, rendered and compared with the images committed under
 * `src/test/resources/goldens` (see `Goldens`): the smallest phone, Hungarian's longer words, a large text size, and the
 * keyboard up. A golden says only that a screen changed; whether the change looks right is still a person's call.
 *
 * Every case is here, each with the bug it would have caught. The set is kept small, so that the images an intended
 * change rewrites are quick to look through: a case is added for a screen that has broken, not for every screen.
 */
@OptIn(ExperimentalTestApi::class)
internal class GoldenRendersTest {

    @Test
    fun `every screen looks as its golden does`() {
        assertTrue(cases.map { it.name }.toSet().size == cases.size, "Two golden cases share a name.")
        val goldens = Goldens(
            directory = File(System.getProperty("campfire.screenshots.goldens")),
            output = File(System.getProperty("campfire.screenshots.goldenOutput")),
            isUpdating = System.getProperty("campfire.screenshots.updateGoldens").toBoolean(),
        )
        val failures = mutableListOf<String>()
        cases.forEach { case ->
            runAppTest(
                window = case.window,
                setlists = case.setlists,
                appearance = case.appearance,
                isCoverArtEnabled = case.isCoverArtEnabled,
            ) {
                case.drive(this)
                goldens.check(case.name, settledWindow())?.let(failures::add)
            }
        }
        if (!goldens.wereCompared) println("The golden renders were drawn but not compared, which only happens on Linux.")
        if (failures.isNotEmpty()) fail(failures.joinToString(separator = "\n", postfix = "\nThe new images are in build/goldens."))
    }

    private val cases = listOf(
        GoldenCase(
            name = "songs",
            reason = "Cards whose title, artist, key, and tags cut each other off at the smallest phone's width.",
        ) {},
        GoldenCase(
            name = "songs-dark",
            reason = "A color of the dark theme lost on the list (the tags, the key, the bar).",
            appearance = TestAppearance(isDark = true),
        ) {},
        GoldenCase(
            name = "songs-rail",
            reason = "Settings out of reach on a phone on its side, before the tools were grouped behind one Tools item.",
            window = TestWindow.PHONE_ON_ITS_SIDE,
        ) {},
        GoldenCase(
            name = "song-details",
            reason = "The song's controls and chords crowding a phone's first screen of the song.",
        ) { openSong() },
        GoldenCase(
            name = "song-details-dark",
            reason = "The chord diagrams and the song's text drawn in colors meant for the light theme.",
            appearance = TestAppearance(isDark = true),
        ) { openSong() },
        GoldenCase(
            name = "song-details-hungarian-130",
            reason = "Labels broken in the middle of a word at a large text size, as the chips and the steppers were.",
            appearance = TestAppearance(language = UserPreferences.Language.HUNGARIAN, textScale = 1.3f),
        ) { openSong() },
        GoldenCase(
            name = "song-details-hungarian-200",
            reason = "Steppers that no longer fit their labels at the largest text size, in the longer Hungarian words.",
            appearance = TestAppearance(language = UserPreferences.Language.HUNGARIAN, textScale = 2f),
        ) { openSong() },
        GoldenCase(
            name = "editor",
            reason = "The editor's controls taking the lines a phone has for the song's text.",
        ) { openEditor() },
        GoldenCase(
            name = "editor-dark",
            reason = "The editor's syntax colors unreadable on the dark theme's background.",
            appearance = TestAppearance(isDark = true),
        ) { openEditor() },
        GoldenCase(
            name = "new-song-keyboard",
            reason = "A sheet's fields left under the keyboard, as they were before the sheets padded above it.",
        ) { openNewSong() },
        GoldenCase(
            name = "new-song-keyboard-dark",
            reason = "The sheet's fields and header in the dark theme, over the keyboard.",
            appearance = TestAppearance(isDark = true),
        ) { openNewSong() },
        GoldenCase(
            name = "new-song-keyboard-hungarian-130",
            reason = "The header's Create pushed off the sheet by a longer title at a large text size.",
            appearance = TestAppearance(language = UserPreferences.Language.HUNGARIAN, textScale = 1.3f),
        ) { openNewSong() },
        GoldenCase(
            name = "new-song-keyboard-hungarian-200",
            reason = "A pinned header taller than the room the keyboard leaves at the largest text size.",
            appearance = TestAppearance(language = UserPreferences.Language.HUNGARIAN, textScale = 2f),
        ) { openNewSong() },
        GoldenCase(
            name = "song-metadata-keyboard",
            reason = "The Edit song details form's Save and fields kept above the keyboard, as Next needs them.",
        ) { openSongMetadata() },
        GoldenCase(
            name = "setlist-details-keyboard",
            reason = "The setlist sheet's title field under the keyboard on the smallest phone.",
            // A new setlist starts on today's date, which would make the golden a day's; this one has a date of its own.
            setlists = listOf(DATED_SETLIST),
        ) { openSetlistDetails() },
        GoldenCase(
            name = "cover-search-keyboard",
            reason = "The cover search's field and results under the keyboard, as they were before its keyboard handling was fixed.",
            isCoverArtEnabled = true,
        ) { openCoverSearch() },
    )
}

/**
 * One golden render: the app in [window] and [appearance], driven to a state by [drive] and compared with the golden
 * called [name], over a library that also holds [setlists] (see [runAppTest]). [reason] is the bug the case would have
 * caught, which is what earns it its place.
 */
private class GoldenCase(
    val name: String,
    val reason: String,
    val window: TestWindow = TestWindow.PHONE,
    val appearance: TestAppearance = TestAppearance(),
    val isCoverArtEnabled: Boolean = false,
    val setlists: List<String> = emptyList(),
    val drive: AppTestScope.() -> Unit,
)

/**
 * The window once it has stopped changing, with the keyboard drawn over its bottom where one is up: the app leaves that
 * part to the keyboard, so a golden shows what it has kept clear of it.
 */
private fun AppTestScope.settledWindow(): BufferedImage {
    var previous = captureWindow().toPixelMap().buffer
    var unchangedCaptures = 0
    waitUntil(timeoutMillis = SETTLE_TIMEOUT_MILLIS) {
        val current = captureWindow().toPixelMap().buffer
        unchangedCaptures = if (current contentEquals previous) unchangedCaptures + 1 else 0
        previous = current
        unchangedCaptures >= STABLE_CAPTURES
    }
    return captureWindow().toAwtImage().apply {
        val keyboard = keyboardHeight.value.roundToInt()
        if (keyboard > 0) {
            createGraphics().apply {
                color = KEYBOARD_COLOR
                fillRect(0, height - keyboard, width, keyboard)
                dispose()
            }
        }
    }
}

/** Taps the node [matcher] finds as a finger does, which leaves no pointer hovering over the screen to draw a highlight. */
private fun AppTestScope.tap(matcher: SemanticsMatcher) {
    onNode(matcher).performTouchInput { click() }
    waitForIdle()
}

private fun AppTestScope.openSong() = tap(hasText(RISING_SUN_TITLE) and hasClickAction())

private fun AppTestScope.openEditor() {
    openSong()
    tap(hasContentDescription(string(Res.string.songs_actions)))
    tap(hasText(string(Res.string.songs_edit_song)) and hasClickAction())
}

private fun AppTestScope.openAboutTheSong() {
    openSong()
    tap(hasText(RISING_SUN_TITLE, substring = true) and hasClickAction())
}

private fun AppTestScope.openSongMetadata() {
    openAboutTheSong()
    tap(hasText(string(Res.string.song_details_duration), substring = true) and hasClickAction())
    keyboardHeight = KEYBOARD_HEIGHT
}

private fun AppTestScope.openCoverSearch() {
    openAboutTheSong()
    tap(hasClickAction() and hasAnyAncestor(hasText(string(Res.string.song_details_duration), substring = true)))
    keyboardHeight = KEYBOARD_HEIGHT
}

private fun AppTestScope.openNewSong() {
    tap(hasContentDescription(string(Res.string.songs_new_song)))
    tap(hasText(string(Res.string.songs_create_song)))
    keyboardHeight = KEYBOARD_HEIGHT
}

private fun AppTestScope.openSetlistDetails() {
    tap(hasText(string(Res.string.setlists)) and hasClickAction())
    tap(hasContentDescription(string(Res.string.setlists_actions)))
    tap(hasText(string(Res.string.setlists_edit_details)) and hasClickAction())
    keyboardHeight = KEYBOARD_HEIGHT
}

private const val RISING_SUN_TITLE = "House of the Rising Sun"
private const val DATED_SETLIST = "campfire_tests-an_evening_by_the_fire.setlist.json"

/** A phone keyboard's height, which leaves a 360 by 640 dp phone the 330 dp the sheets' pinned height is budgeted for. */
private val KEYBOARD_HEIGHT = 310.dp
private val KEYBOARD_COLOR = Color(0x9E9E9E)
private const val SETTLE_TIMEOUT_MILLIS = 10_000L

/** How many captures in a row must be the same before the window counts as settled. */
private const val STABLE_CAPTURES = 3
