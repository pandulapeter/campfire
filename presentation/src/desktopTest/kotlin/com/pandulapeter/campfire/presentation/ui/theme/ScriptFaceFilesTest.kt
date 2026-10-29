/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.allFontResources
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ScriptFaceFilesTest {

    @Test
    fun `every bundled script face holds only its row's characters, and a part of each of its ranges`() {
        scriptFaces.forEach { face ->
            val name = Res.allFontResources.entries.single { it.value == face.font }.key
            val bytes = File("src/commonMain/composeResources/font/$name.ttf").readBytes()
            val typeface = FontMgr.default.makeFromData(Data.makeFromBytes(bytes))!!
            // Every code point but the surrogates, which are no characters, and which Skia aborts on.
            val codePoints = ((0..0xD7FF) + (0xE000..0x10FFFF)).toIntArray()
            val glyphs = typeface.getUTF32Glyphs(codePoints)
            val held = codePoints.filterIndexed { index, _ -> glyphs[index] != 0.toShort() }
            assertTrue(held.all { codePoint -> face.ranges.any { codePoint in it } }, "$face holds characters outside its ranges")
            face.ranges.forEach { range -> assertTrue(held.any { it in range }, "$face holds nothing of $range") }
        }
    }
}
