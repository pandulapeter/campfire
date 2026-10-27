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

import com.pandulapeter.campfire.abc.api.AbcRendering
import com.pandulapeter.campfire.abc.api.AbcSystem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** abc2svg in the real engine of each platform the tests run on: QuickJS on the desktop (as on Android), JavaScriptCore on the iOS simulator. */
class AbcRendererImplTest {

    private val renderer = AbcRendererImpl()

    @Test
    fun `a tune renders as staff systems with their heights`() = runTest {
        val rendering = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 800, transposition = 0))
        assertTrue(rendering.systems.size > 1)
        rendering.systems.forEach { system ->
            assertTrue(system.svg.startsWith("<svg"))
            assertTrue(system.height > 0f)
        }
    }

    @Test
    fun `a narrower page gives more systems`() = runTest {
        val wide = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 800, transposition = 0))
        val narrow = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 300, transposition = 0))
        assertTrue(narrow.systems.size > wide.systems.size)
    }

    @Test
    fun `chord symbols carry their own class`() = runTest {
        val rendering = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 800, transposition = 0))
        assertTrue(rendering.systems.any { """class="f\d+ ${AbcSystem.CHORD_SYMBOL_CLASS}"""".toRegex().containsMatchIn(it.svg) })
    }

    @Test
    fun `a tune using a module of abc2svg loads it`() = runTest {
        val rendering = assertIs<AbcRendering.Systems>(renderer.render("%%chordnames Do,Re,Mi,Fa,Sol,La,Si\n$REEL", width = 800, transposition = 0))
        assertTrue(rendering.systems.any { ">Re<" in it.svg })
    }

    @Test
    fun `a transposition moves the key signature and the chord symbols`() = runTest {
        val rendering = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 800, transposition = 2))
        assertTrue(rendering.systems.any { ">E<" in it.svg && ">F♯m<" in it.svg })
        assertTrue(rendering.systems.none { ">D<" in it.svg })
    }

    @Test
    fun `lyrics carry their own class`() = runTest {
        val rendering = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 800, transposition = 0))
        assertTrue(rendering.systems.any { """class="f\d+ ${AbcSystem.LYRICS_CLASS}"""".toRegex().containsMatchIn(it.svg) })
    }

    @Test
    fun `text that is no tune fails rather than throwing`() = runTest {
        assertIs<AbcRendering.Failed>(renderer.render("This is not ABC notation.", width = 800, transposition = 0))
    }

    @Test
    fun `a failed tune leaves the engine working`() = runTest {
        renderer.render("X:1\nK:", width = 800, transposition = 0)
        val first = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 800, transposition = 0))
        val again = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 800, transposition = 0))
        assertEquals(first.systems.size, again.systems.size)
    }

    @Test
    fun `a script in a tune is not run`() = runTest {
        val hostile = "X:1\n%%beginjs\ncampfireRender = function () { return \"<svg height=\\\"1px\\\"></svg>\" }\n%%endjs\n${REEL.substringAfter('\n')}"
        assertIs<AbcRendering.Systems>(renderer.render(hostile, width = 800, transposition = 0))
        val after = assertIs<AbcRendering.Systems>(renderer.render(REEL, width = 800, transposition = 0))
        assertTrue(after.systems.first().height > 1f)
    }

    @Test
    fun `a tune whose script never ends still renders`() = runTest {
        assertIs<AbcRendering.Systems>(renderer.render("X:1\n%%beginjs\nwhile (true) {}\n%%endjs\n${REEL.substringAfter('\n')}", width = 800, transposition = 0))
    }

    private companion object {
        val REEL = """
            X:1
            T:The Test Reel
            M:4/4
            L:1/8
            K:D
            |:"D"d2fd AdfA|"G"B2dB "D"AFDF|"Em"EFGA "A"Bcde|"D"fdec "G"d2cB|
            w:la la la la la la la la la la la la la la la la la la la la la la la la la la la la
            "D"d2fd AdfA|"G"B2dB "D"AFDF|"Em"EFGA "A7"Bcde|1 "D"fded "D"d4:|2 "D"fded "D"d2fg||
            |:"D"afdf afdf|"G"gbgb "D"afdf|"Em"egeg "A"fafa|"Bm"gbag "A"fede|
            "D"afdf afdf|"G"gbgb "D"afdf|"Em"egfe "A7"dcBA|1 "D"d2fd "D"d2fg:|2 "D"d2fd "D"d4|]
        """.trimIndent()
    }
}
