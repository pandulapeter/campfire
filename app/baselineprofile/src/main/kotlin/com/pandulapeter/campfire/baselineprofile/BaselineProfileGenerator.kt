/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Records the Baseline Profile and the startup profile of the release build, walking the screens every launch goes
 * through: the start up itself, the song list and its search, a song with its About the song sheet and its editor, a
 * setlist, the tools (Metronome, Tuner, Chords, and Scales, behind the Tools item on a phone), and Settings. It is not a
 * test: it asserts nothing, CI never runs it, and it is started by hand on an emulator, see this module's `CLAUDE.md`.
 *
 * The screens are found by their visible English text, which Compose exposes to UI Automator, so the app carries no test
 * tags for it. Every step waits for its text and is skipped where the text never appears, so that a renamed string never
 * stops the recording; `generateBaselineProfile` then fails naming the screen the written profile lacks, from the list of
 * walked screens in `app/android/build.gradle.kts`, which a new step adds its screen to.
 *
 * The walk is collected for the Baseline Profile only, and the startup profile from a cold start of its own: with dex
 * layout optimization the startup profile decides which classes share the first pages of the DEX, and the screens a
 * launch never opens - the onboarding and the demo library's planting, which run once per installation, above all -
 * would only spread the ones it needs over more of them. The two run in a fixed order, the walk first: JUnit's default
 * order is a hash order, and the app's data is never cleared between them, so only the first one ever sees the first
 * run, which belongs to the walk.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun a_journeys() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        // A fresh installation is a first run, which plants the demo library and opens the onboarding over it, walked
        // page by page so that every page is in the profile. The collection runs this block several times without
        // clearing the app's data, so only the first one sees it, and the rest wait for its Skip once.
        if (waitFor(By.text("Skip")) != null) {
            while (true) {
                val next = device.findObject(By.text("Next")) ?: break
                // The pager's page change recomposes the button, which can leave the node found a moment ago stale by the
                // time it is clicked; the loop then simply looks for it again.
                try {
                    next.click()
                } catch (_: StaleObjectException) {
                }
                device.waitForIdle()
            }
            waitFor(By.text("Start playing"))?.click()
        }
        waitFor(By.textContains("Rising Sun"))?.let {
            flingScrollable(Direction.DOWN)
            flingScrollable(Direction.UP)
        }
        waitFor(By.textContains("Rising Sun"))?.let { song ->
            song.click()
            device.waitForIdle()
            // About the song is the bottom sheet of the walk, and the editor is opened from the same menu with nothing
            // typed, so that their first use is not the one that compiles them. Back leaves an untouched editor at once.
            songAction("About the song")?.let { about ->
                about.click()
                device.waitForIdle()
                device.pressBack()
                device.waitForIdle()
            }
            songAction("Edit song file")?.let { edit ->
                edit.click()
                device.waitForIdle()
                device.pressBack()
                waitFor(By.desc("Song actions"))
            }
            flingScrollable(Direction.DOWN)
            device.pressBack()
            device.waitForIdle()
        }
        waitFor(By.desc("Search in songs"))?.let { search ->
            search.click()
            waitFor(By.clazz(TEXT_FIELD_CLASS))?.text = "rising"
            device.waitForIdle()
            // The first Back may only hide the keyboard, and the second then closes the search.
            repeat(2) {
                if (device.findObject(By.clazz(TEXT_FIELD_CLASS)) != null) {
                    device.pressBack()
                    device.waitForIdle()
                }
            }
        }
        waitFor(By.text("Setlists"))?.let { tab ->
            tab.click()
            waitFor(By.text("Getting started"))?.let { setlist ->
                setlist.click()
                device.waitForIdle()
                device.pressBack()
                device.waitForIdle()
            }
        }
        // With six screens on, a phone's bar groups Metronome, Tuner, Chords, and Scales behind one Tools item, which
        // opens the Metronome on a fresh install with a button group over the tools; a taller rail gives each its own item.
        (waitFor(By.text("Tools")) ?: waitFor(By.text("Metronome")))?.click()
        device.waitForIdle()
        // Nothing on the Tuner asks for anything without a tap, so it is visited like the others.
        listOf("Tuner", "Chords", "Scales").forEach { tool ->
            toolButton(tool)?.click()
            device.waitForIdle()
        }
        waitFor(By.text("Settings"))?.click()
        device.waitForIdle()
    }

    @Test
    fun b_startup() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // Only there if the walk was skipped, and then dismissed rather than walked, since it is no part of a cold start.
        waitFor(By.text("Skip"))?.click()
        // The first frame is all startActivityAndWait waits for; the library read, its indexing and the cards' first
        // composition come after it, and are as much a part of the start up.
        waitFor(By.textContains("Rising Sun"))
    }

    private fun MacrobenchmarkScope.waitFor(selector: BySelector): UiObject2? = device.wait(Until.findObject(selector), STEP_TIMEOUT)

    /** A tool's button in the group, which names an unselected tool only in its content description, or its rail item. */
    private fun MacrobenchmarkScope.toolButton(label: String): UiObject2? = device.findObject(By.desc(label)) ?: waitFor(By.text(label))

    /** A song action, shown in the bar where it is wide enough and otherwise behind the song's overflow button. */
    private fun MacrobenchmarkScope.songAction(label: String): UiObject2? = device.findObject(By.desc(label))
        ?: waitFor(By.desc("Song actions"))?.let { overflow ->
            // The song's bar is still recomposing just after the song opens, which can leave the button found a moment ago
            // stale by the time it is clicked; it is then found again.
            try {
                overflow.click()
            } catch (_: StaleObjectException) {
                waitFor(By.desc("Song actions"))?.click()
            }
            waitFor(By.text(label))
        }

    private fun MacrobenchmarkScope.flingScrollable(direction: Direction) {
        device.findObject(By.scrollable(true))?.let { list ->
            // Kept off the system gesture areas, which would take the fling as a back or a home gesture.
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(direction)
        }
        device.waitForIdle()
    }
}

private const val PACKAGE_NAME = "com.pandulapeter.campfire"
private const val STEP_TIMEOUT = 5_000L
private const val TEXT_FIELD_CLASS = "android.widget.EditText"
