/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecoveringTest {

    @Test
    fun `the block's value is answered`() = runTest {
        var describeCount = 0
        val value = recovering(describe = { describeCount++; "" }, fallback = { -1 }) { 42 }

        assertEquals(42, value)
        assertEquals(0, describeCount)
    }

    @Test
    fun `an exception gives the fallback and is described once`() = runTest {
        var describeCount = 0
        val value = recovering(describe = { describeCount++; "failed: ${it.message}" }, fallback = { -1 }) {
            throw IllegalStateException("broken")
        }

        assertEquals(-1, value)
        assertEquals(1, describeCount)
    }

    @Test
    fun `a cancellation is rethrown and calls neither lambda`() = runTest {
        var lambdaCount = 0
        assertFailsWith<CancellationException> {
            recovering(describe = { lambdaCount++; "" }, fallback = { lambdaCount++ }) {
                throw CancellationException("stopped")
            }
        }
        assertEquals(0, lambdaCount)
    }

    @Test
    fun `an error is not caught`() = runTest {
        var lambdaCount = 0
        assertFailsWith<AssertionError> {
            recovering(describe = { lambdaCount++; "" }, fallback = { lambdaCount++ }) {
                throw AssertionError("not an exception")
            }
        }
        assertEquals(0, lambdaCount)
    }

    @Test
    fun `a suspending block works`() = runTest {
        val value = recovering(describe = { "" }, fallback = { -1 }) {
            delay(100)
            7
        }

        assertEquals(7, value)
    }
}
