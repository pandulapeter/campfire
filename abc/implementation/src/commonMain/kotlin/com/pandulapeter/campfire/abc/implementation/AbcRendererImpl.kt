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

import com.pandulapeter.campfire.abc.api.AbcRenderer
import com.pandulapeter.campfire.abc.api.AbcRendering
import com.pandulapeter.campfire.abc.api.AbcSystem
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single
import kotlin.math.roundToInt

/**
 * abc2svg in the platform's [AbcScriptEngine]. The page and the chord symbols' class are set by format lines put in
 * front of the tune, which the tune's own lines may still override, as abc2svg reads them in order.
 */
@Single
internal class AbcRendererImpl : AbcRenderer {

    private val engine = createAbcScriptEngine()

    override suspend fun render(tune: String, width: Int, transposition: Int): AbcRendering = try {
        val output = engine.render(source = headerOf(width, transposition) + tune)
        if (output.startsWith(SEPARATOR)) {
            AbcRendering.Failed(output.drop(1).ifBlank { "abc2svg rendered nothing" })
        } else {
            val systems = output.split(SEPARATOR).map { svg -> AbcSystem(svg = svg, height = heightOf(svg) ?: return AbcRendering.Failed("An SVG with no height")) }
            AbcRendering.Systems(systems)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        AbcRendering.Failed(exception.message ?: exception.toString())
    }

    // Chord symbols, annotations, and lyrics are set at the size of the page's own chords and lyrics rather than
    // abc2svg's 12 and 13, which read as small print beside them, and chord symbols in bold, as the page's chords are.
    // Lyrics are measured as sans-serif rather than abc2svg's bold serif, and larger than they are drawn (see
    // AbcSystem.LYRICS_ROOM): the app draws them in Inter Regular, which its width tables measure narrower than it is.
    private fun headerOf(width: Int, transposition: Int) = """
        %%pagewidth ${width}px
        %%leftmargin 0
        %%rightmargin 0
        %%gchordfont sans-serif-Bold ${TEXT_SIZE.roundToInt()} class=${AbcSystem.CHORD_SYMBOL_CLASS}
        %%annotationfont sans-serif ${TEXT_SIZE.roundToInt()}
        %%vocalfont sans-serif ${(TEXT_SIZE * AbcSystem.LYRICS_ROOM * 10).roundToInt() / 10.0} class=${AbcSystem.LYRICS_CLASS}
        %%transpose $transposition

    """.trimIndent()

    /** The root element's own height, which abc2svg always writes in pixels (`height="76.00px"`). */
    private fun heightOf(svg: String) = HEIGHT.find(svg.substringBefore('>'))?.groupValues?.get(1)?.toFloatOrNull()

    private companion object {
        /** What `campfire-abc-host.js` joins the systems with, and puts in front of the messages when there are none. */
        const val SEPARATOR = '\u0000'

        /**
         * The size chord symbols, annotations, and lyrics are meant to be drawn at: the page's text size (bodyLarge), as
         * the app draws one SVG unit as 1 dp times the reader's text size.
         */
        const val TEXT_SIZE = 16f
        val HEIGHT = Regex("""\sheight="([\d.]+)(?:px)?"""")
    }
}
