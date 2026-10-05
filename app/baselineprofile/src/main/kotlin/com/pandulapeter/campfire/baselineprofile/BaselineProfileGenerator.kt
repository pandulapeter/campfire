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
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the Baseline Profile and the startup profile of the release build, walking the screens every launch goes
 * through: the start up itself, the song list, a song, a setlist, the Metronome tab and Settings. It is not a test: it asserts nothing, CI
 * never runs it, and it is started by hand on an emulator, see this module's `CLAUDE.md`.
 *
 * The screens are found by their visible English text, which Compose exposes to UI Automator, so the app carries no test
 * tags for it. Every step waits for its text and is skipped where the text never appears, so that a renamed string
 * costs the profile a step rather than the run.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // A fresh installation is a first run, which plants the demo library and opens the welcome sheet over it. The
        // collection runs this block several times without clearing the app's data, so only the first one sees it.
        waitFor(By.text("Get started"))?.click()
        waitFor(By.textContains("Rising Sun"))?.let {
            flingScrollable(Direction.DOWN)
            flingScrollable(Direction.UP)
        }
        waitFor(By.textContains("Rising Sun"))?.let { song ->
            song.click()
            device.waitForIdle()
            flingScrollable(Direction.DOWN)
            device.pressBack()
            device.waitForIdle()
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
        waitFor(By.text("Metronome"))?.click()
        device.waitForIdle()
        waitFor(By.text("Settings"))?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.waitFor(selector: BySelector): UiObject2? = device.wait(Until.findObject(selector), STEP_TIMEOUT)

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
