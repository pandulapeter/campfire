/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutOfSpaceTest {

    @Test
    fun `a failure caused by running out of space says so however deep the cause`() {
        assertTrue(OutOfSpaceException().isOutOfSpace())
        assertTrue(IllegalStateException("Could not write.", RuntimeException(OutOfSpaceException())).isOutOfSpace())
    }

    @Test
    fun `any other failure is not out of space`() {
        assertFalse(IllegalStateException("Could not write.", RuntimeException("Permission denied")).isOutOfSpace())
    }
}
