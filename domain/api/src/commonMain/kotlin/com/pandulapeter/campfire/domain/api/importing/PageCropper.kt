/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.importing

import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageLineScores
import com.pandulapeter.campfire.pagesong.PageSong

/**
 * Finds the song among the lines of a text that came from a web page or another app (a paste, a drop, a share), or of a
 * PDF or Word document, so that [PrepareImportUseCase] converts the song rather than the page's menus, comments, and
 * calls to action around it, or a document's running headers and page numbers. It only ever answers which lines to keep
 * or how sure it is of each, never a line's text, so nothing it decides can change what the song says.
 *
 * Provided by `:presentation`, which ships the page model's weights (`:libs:pagesong`) as a resource the domain cannot
 * read. The weights are large, so an implementation reads them once, on its first call, and never on the main thread.
 */
interface PageCropper {

    /**
     * Which of [lines] are the song, its details, and the lines naming its title and artist. Null where the lines hold no
     * song as far as the model can tell, which a page read from its address is refused for. Throws where the model cannot
     * run (its weights could not be read) or fails, never answering null for it, so that a page is then converted whole,
     * as a paste is, rather than refused: a crop must never cost the song its own text.
     */
    suspend fun songLinesOf(lines: List<String>): PageSongLines?

    /**
     * The model's scores of a document's [lines] (`ChordSheetPageLines`, with their layout), one for each of the first
     * [PageSong.MAX_LINES], read as a whole as the model was trained to; the converter crops each song of the document by
     * them. Null where the model is not there, and the document is then converted whole. May throw, with the same outcome.
     */
    suspend fun documentScoresOf(lines: List<PageLine>): List<PageLineScores>?
}
