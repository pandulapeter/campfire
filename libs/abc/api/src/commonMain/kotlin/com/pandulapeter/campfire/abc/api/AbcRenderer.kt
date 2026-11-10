/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.api

/**
 * Engraves ABC notation as staff systems, each one SVG image, by a renderer bundled with the app: nothing is fetched,
 * and nothing rendered is kept beyond what the caller keeps.
 *
 * Calls may come from any thread and are run one at a time, on a thread of the renderer's own where the platform has
 * threads (on the web, on the main thread, which a long tune holds for its whole render). **A call never throws**
 * (cancellation aside): a tune the renderer cannot read or a renderer that could not start returns
 * [AbcRendering.Failed], and the caller keeps showing the ABC text as it is.
 */
public interface AbcRenderer {

    /**
     * Renders every tune in [tune] (ABC text, `X:` headers included where it has them) into a page [width] SVG user
     * units wide, with no margins: the staves are as wide as the page, so a narrower page gives more systems.
     *
     * @param transposition Semitones the notes, the key signatures, and the chord symbols are moved by, up or down;
     *   the renderer spells them for the key they land in.
     * @param tablature How a voice in the tab clef is fretted and its strings named; null frets it on the strings it
     *   names, under a TAB clef.
     */
    public suspend fun render(tune: String, width: Int, transposition: Int, tablature: AbcTablature? = null): AbcRendering

    public companion object {
        /**
         * A page wide enough that a tune's systems break where its text does, for a caller that wants the
         * [AbcRendering]'s messages about the text alone: some of the renderer's messages are about where a narrower page
         * broke a line (a tuplet cut in two by a system break), which no edit of the text can fix. Only a text line
         * longer than about two hundred dense bars is still broken by the page. It is not wider still because the
         * renderer's output grows in time with the square of a system's length.
         */
        public const val TEXT_LINE_WIDTH: Int = 50_000
    }
}
