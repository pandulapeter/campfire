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

class ProcessLogUserNameTest {

    @Test
    fun `the home directory becomes a tilde`() {
        val log = "Could not read /Users/anna/Library/Application Support/Campfire/library/songs/a.cho"

        assertEquals(
            "Could not read ~/Library/Application Support/Campfire/library/songs/a.cho",
            log.withoutUserName(home = "/Users/anna", userName = "anna"),
        )
    }

    @Test
    fun `the user name is left out of other paths, but not out of words`() {
        val log = "Opened /Volumes/Backup/anna/songs.zip\nanna_karenina.cho\nC:\\Users\\anna\\Downloads"

        assertEquals(
            "Opened /Volumes/Backup/<user>/songs.zip\nanna_karenina.cho\nC:\\Users\\<user>\\Downloads",
            log.withoutUserName(home = "/Users/anna/Library/Containers/campfire/Data", userName = "anna"),
        )
    }
}
