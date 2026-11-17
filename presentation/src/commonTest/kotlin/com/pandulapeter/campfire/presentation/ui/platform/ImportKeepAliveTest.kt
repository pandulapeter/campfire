/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportKeepAliveTest {

    /** The system's side, as the shell sees it: what is held, and whether the import was told to stop. */
    private class FakeSystem(var isSubmitAccepted: Boolean = true) : ImportKeepAlive.Actions {
        var continuedTasks = 0
        var isBackgroundTimeHeld = false
        var interruptions = 0

        override fun submitContinuedTask() = isSubmitAccepted.also { if (it) continuedTasks++ }
        override fun finishContinuedTask() {
            continuedTasks--
        }

        override fun beginBackgroundTime() {
            isBackgroundTimeHeld = true
        }

        override fun endBackgroundTime() {
            isBackgroundTimeHeld = false
        }

        override fun interruptImport() {
            interruptions++
        }
    }

    @Test
    fun `before iOS 26 background time is held from the start, and its expiry pauses the import rather than ending it`() {
        val system = FakeSystem()
        val keepAlive = ImportKeepAlive(hasContinuedTasks = false, actions = system)
        keepAlive.onWorking(isAppActive = true)
        assertTrue(system.isBackgroundTimeHeld)
        keepAlive.onLeaving(isWorking = true)
        keepAlive.onBackgroundTimeExpired()
        assertFalse(system.isBackgroundTimeHeld)
        assertEquals(0, system.interruptions)
    }

    @Test
    fun `after background time ran out nothing more is asked for until the app is active again`() {
        val system = FakeSystem()
        val keepAlive = ImportKeepAlive(hasContinuedTasks = false, actions = system)
        keepAlive.onWorking(isAppActive = true)
        keepAlive.onBackgroundTimeExpired()
        keepAlive.onWorking(isAppActive = false)
        assertFalse(system.isBackgroundTimeHeld)
        keepAlive.onActive()
        keepAlive.onWorking(isAppActive = true)
        assertTrue(system.isBackgroundTimeHeld)
    }

    @Test
    fun `on iOS 26 a continued task is submitted only as the app is left, and finished on return`() {
        val system = FakeSystem()
        val keepAlive = ImportKeepAlive(hasContinuedTasks = true, actions = system)
        keepAlive.onWorking(isAppActive = true)
        assertEquals(0, system.continuedTasks)
        assertFalse(system.isBackgroundTimeHeld)
        keepAlive.onLeaving(isWorking = true)
        assertEquals(1, system.continuedTasks)
        keepAlive.onActive()
        assertEquals(0, system.continuedTasks)
    }

    @Test
    fun `leaving with no import working asks for nothing`() {
        val system = FakeSystem()
        val keepAlive = ImportKeepAlive(hasContinuedTasks = true, actions = system)
        keepAlive.onLeaving(isWorking = false)
        assertEquals(0, system.continuedTasks)
        assertFalse(system.isBackgroundTimeHeld)
    }

    @Test
    fun `a continued task's expiry or stop interrupts the import, and no other task is asked for while away`() {
        val system = FakeSystem()
        val keepAlive = ImportKeepAlive(hasContinuedTasks = true, actions = system)
        keepAlive.onLeaving(isWorking = true)
        keepAlive.onContinuedTaskExpired()
        assertEquals(1, system.interruptions)
        keepAlive.onWorking(isAppActive = false)
        assertEquals(1, system.continuedTasks)
        assertFalse(system.isBackgroundTimeHeld)
    }

    @Test
    fun `a refused submit falls back on background time, whose expiry does not interrupt`() {
        val system = FakeSystem(isSubmitAccepted = false)
        val keepAlive = ImportKeepAlive(hasContinuedTasks = true, actions = system)
        keepAlive.onLeaving(isWorking = true)
        assertTrue(system.isBackgroundTimeHeld)
        keepAlive.onBackgroundTimeExpired()
        assertFalse(system.isBackgroundTimeHeld)
        assertEquals(0, system.interruptions)
    }

    @Test
    fun `release gives back whatever is held`() {
        val system = FakeSystem(isSubmitAccepted = false)
        val keepAlive = ImportKeepAlive(hasContinuedTasks = true, actions = system)
        keepAlive.onLeaving(isWorking = true)
        keepAlive.release()
        assertFalse(system.isBackgroundTimeHeld)
        assertFalse(keepAlive.isHolding)
    }
}
