/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse

/** The limits QuickJS renders under, which only this engine has, set small enough to trip on a tune of test size. */
class QuickJsAbcScriptEngineTest {

    @Test
    fun `a tune that runs out of memory fails and the next one renders`() = runTest {
        val engine = QuickJsAbcScriptEngine(memoryLimitBytes = 16L * 1024 * 1024, evaluationTimeoutMillis = 10_000)
        assertFails { engine.render("X:1\nL:1/8\nK:D\n" + "\"D\"d2fd AdfA|".repeat(2_000)) }
        assertFalse(engine.render(REEL).startsWith('\u0000'))
    }

    @Test
    fun `a render past its time is stopped and the next one renders`() = runTest {
        val engine = QuickJsAbcScriptEngine(memoryLimitBytes = 128L * 1024 * 1024, evaluationTimeoutMillis = 1_000)
        val voices = "X:1\n%%score ${(1..400).joinToString(" ")}\nL:1/8\nK:C\n"
        assertFails { engine.render(voices + "[V:1]C|\n".repeat((6_000 - voices.length) / 8)) }
        assertFalse(engine.render(REEL).startsWith('\u0000'))
    }

    private companion object {
        const val REEL = "X:1\nT:The Test Reel\nM:4/4\nL:1/8\nK:D\n|:\"D\"d2fd AdfA|\"G\"B2dB \"D\"AFDF|\"Em\"EFGA \"A\"Bcde|\"D\"fdec \"G\"d2cB:|\n"
    }
}
