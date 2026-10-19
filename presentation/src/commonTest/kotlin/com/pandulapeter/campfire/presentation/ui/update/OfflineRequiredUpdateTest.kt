/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineRequiredUpdateTest {

    @Test
    fun `waiting for a connection hides a required update`() =
        assertEquals(AppUpdateState.NotAvailable, visibleAppUpdateState(AppUpdateState.Required, isWaitingForConnection = true))

    @Test
    fun `waiting for a connection still shows every other state`() = AppUpdateState.entries
        .filter { it != AppUpdateState.Required }
        .forEach { assertEquals(it, visibleAppUpdateState(it, isWaitingForConnection = true)) }

    @Test
    fun `a required update is shown when nothing is being waited out`() =
        assertEquals(AppUpdateState.Required, visibleAppUpdateState(AppUpdateState.Required, isWaitingForConnection = false))

    @Test
    fun `an answer given offline keeps the wait`() = assertTrue(isStillWaitingForConnection(isWaitingForConnection = true, isOnline = false))

    @Test
    fun `an answer given online ends the wait`() = assertFalse(isStillWaitingForConnection(isWaitingForConnection = true, isOnline = true))
}
