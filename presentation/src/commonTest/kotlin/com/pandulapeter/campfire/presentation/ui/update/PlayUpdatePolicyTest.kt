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

class PlayUpdatePolicyTest {

    private fun stateOf(
        priority: Int,
        isUpdateAvailable: Boolean = true,
        isUpdateInProgress: Boolean = false,
        installStatus: PlayInstallStatus = PlayInstallStatus.OTHER,
        isPostponed: Boolean = false,
        isFlexibleAllowed: Boolean = true,
        isImmediateAllowed: Boolean = true,
    ) = playUpdateStateOf(
        priority = priority,
        isUpdateAvailable = isUpdateAvailable,
        isUpdateInProgress = isUpdateInProgress,
        installStatus = installStatus,
        isPostponed = isPostponed,
        isFlexibleAllowed = isFlexibleAllowed,
        isImmediateAllowed = isImmediateAllowed,
    )

    @Test
    fun `a priority under 2 is left to Play`() = (0..1).forEach { assertEquals(AppUpdateState.NotAvailable, stateOf(it)) }

    @Test
    fun `a priority of 2 or 3 is optional`() = (2..3).forEach { assertEquals(AppUpdateState.Optional, stateOf(it)) }

    @Test
    fun `a postponed optional update is not offered again`() =
        (2..3).forEach { assertEquals(AppUpdateState.NotAvailable, stateOf(it, isPostponed = true)) }

    @Test
    fun `an optional update Play cannot run flexibly is not offered`() =
        (2..3).forEach { assertEquals(AppUpdateState.NotAvailable, stateOf(it, isFlexibleAllowed = false)) }

    @Test
    fun `a priority of 4 or 5 is required`() = (4..5).forEach { assertEquals(AppUpdateState.Required, stateOf(it)) }

    @Test
    fun `postponing does not lift a required update`() =
        (4..5).forEach { assertEquals(AppUpdateState.Required, stateOf(it, isPostponed = true)) }

    @Test
    fun `a required update Play cannot run immediately is not offered`() =
        (4..5).forEach { assertEquals(AppUpdateState.NotAvailable, stateOf(it, isImmediateAllowed = false)) }

    @Test
    fun `no update available offers nothing`() =
        (0..5).forEach { assertEquals(AppUpdateState.NotAvailable, stateOf(it, isUpdateAvailable = false)) }

    @Test
    fun `a required update in progress is required whatever its download says`() = PlayInstallStatus.entries.forEach {
        assertEquals(
            AppUpdateState.Required,
            stateOf(4, isUpdateAvailable = false, isUpdateInProgress = true, installStatus = it, isPostponed = true, isImmediateAllowed = false),
        )
    }

    @Test
    fun `an optional update in progress is read from its download`() =
        assertEquals(AppUpdateState.NotAvailable, stateOf(2, isUpdateAvailable = false, isUpdateInProgress = true))

    @Test
    fun `a downloaded update is ready to install`() =
        (0..3).forEach { assertEquals(AppUpdateState.ReadyToInstall, stateOf(it, isUpdateAvailable = false, installStatus = PlayInstallStatus.DOWNLOADED)) }

    @Test
    fun `a postponed downloaded update waits`() =
        assertEquals(AppUpdateState.NotAvailable, stateOf(2, installStatus = PlayInstallStatus.DOWNLOADED, isPostponed = true))

    @Test
    fun `a pending or running download is downloading whatever the priority`() =
        listOf(PlayInstallStatus.PENDING, PlayInstallStatus.DOWNLOADING).forEach { status ->
            (0..3).forEach { assertEquals(AppUpdateState.Downloading, stateOf(it, isUpdateAvailable = false, installStatus = status, isPostponed = true)) }
        }
}
