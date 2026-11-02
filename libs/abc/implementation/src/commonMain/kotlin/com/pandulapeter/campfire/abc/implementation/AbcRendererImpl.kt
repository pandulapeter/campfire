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

    override suspend fun render(tune: String, width: Int, transposition: Int): AbcRendering = when {
        tune.length > MAX_TUNE_LENGTH -> AbcRendering.Failed("A tune longer than $MAX_TUNE_LENGTH characters")
        voiceCountOf(tune) > MAX_TUNE_VOICES -> AbcRendering.Failed("A tune of more than $MAX_TUNE_VOICES voices")
        else -> rendered(tune, width, transposition)
    }

    private suspend fun rendered(tune: String, width: Int, transposition: Int): AbcRendering = try {
        val output = engine.render(source = headerOf(width, transposition) + tune)
        if (output.startsWith(SEPARATOR)) {
            AbcRendering.Failed(output.drop(1).ifBlank { "abc2svg rendered nothing" })
        } else if (output.count { it == SEPARATOR } + 1 > MAX_SYSTEMS) {
            AbcRendering.Failed("A rendering of more than $MAX_SYSTEMS systems")
        } else {
            val systems = output.split(SEPARATOR).map { svg -> AbcSystem(svg = svg, height = heightOf(svg) ?: return AbcRendering.Failed("An SVG with no height")) }
            AbcRendering.Systems(systems)
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (throwable: Throwable) {
        // A native library that cannot be loaded (a noexec temporary folder, an install missing its ABI) is an Error, not
        // an Exception, and the contract is that the tune is then shown as its text rather than the app going down.
        AbcRendering.Failed(throwable.message ?: throwable.toString())
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

    /**
     * The distinct voices [tune] names: in `V:` fields, inline `[V:` fields, and `%%score` / `%%staves` lines (or their
     * `I:` forms), as abc2svg creates every voice those lines name.
     */
    private fun voiceCountOf(tune: String) = buildSet {
        VOICE_FIELD.findAll(tune).forEach { add(it.groupValues[1]) }
        SCORE_LINE.findAll(tune).forEach { line -> SCORE_VOICE.findAll(line.groupValues[1]).forEach { add(it.value) } }
    }.size

    /** The root element's own height, which abc2svg always writes in pixels (`height="76.00px"`). */
    private fun heightOf(svg: String) = HEIGHT.find(svg.substringBefore('>'))?.groupValues?.get(1)?.toFloatOrNull()

    internal companion object {
        /**
         * The longest tune rendered, in characters (about 3,800 dense bars). abc2svg's memory grows with the text, and iOS
         * and the web have no engine limit; QuickJsAbcScriptEngine's memory limit sits above what this lets through.
         */
        const val MAX_TUNE_LENGTH = 50_000

        /**
         * The most voices a rendered tune may name. abc2svg lays every staff out in every system, so its time grows with
         * the voices times the systems, not with the text: this keeps the web's main thread and iOS's engine thread, which
         * nothing can interrupt, to under a second on a desktop. QuickJsAbcScriptEngine's timeout sits above it.
         */
        const val MAX_TUNE_VOICES = 32

        /** The most systems of a rendering read: what the app parses, paints, and lays out (one note a line is a system a line). */
        const val MAX_SYSTEMS = 2_000

        /** What `campfire-abc-host.js` joins the systems with, and puts in front of the messages when there are none. */
        private const val SEPARATOR = '\u0000'

        /**
         * The size chord symbols, annotations, and lyrics are meant to be drawn at: the page's text size (bodyLarge), as
         * the app draws one SVG unit as 1 dp times the reader's text size.
         */
        private const val TEXT_SIZE = 16f
        private val HEIGHT = Regex("""\sheight="([\d.]+)(?:px)?"""")
        private val VOICE_FIELD = Regex("""(?m)(?:^|\[)V:[ \t]*([^\s\]]+)""")
        private val SCORE_LINE = Regex("""(?m)^(?:%%|I:)(?:score|staves)\b(.*)$""")
        private val SCORE_VOICE = Regex("""[^\s()\[\]{}|*]+""")
    }
}
