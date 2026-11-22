/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

import kotlinx.coroutines.yield
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

internal class CompletedWithoutSuspendingTest {

    @Test
    fun `a block that yields returns its value`() {
        assertEquals(
            42,
            completedWithoutSuspending {
                repeat(3) { yield() }
                42
            },
        )
    }

    @Test
    fun `a block's exception arrives as itself`() {
        val exception = ZipException("Damaged.")

        assertSame(exception, assertFailsWith<ZipException> { completedWithoutSuspending<Unit> { throw exception } })
    }

    @Test
    fun `a block that really suspends is an assertion error`() {
        assertFailsWith<AssertionError> { completedWithoutSuspending { suspendCoroutine<Unit> { } } }
    }
}
