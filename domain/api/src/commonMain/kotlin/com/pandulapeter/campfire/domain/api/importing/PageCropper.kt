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

/**
 * Finds the song among the lines of a text that came from a web page or another app (a paste, a drop, a share), so that
 * [PrepareImportUseCase] converts the song rather than the page's menus, comments, and calls to action around it. It
 * only ever answers which lines to keep, never a line's text, so nothing it decides can change what the song says.
 *
 * Provided by `:presentation`, which ships the page model's weights (`:libs:pagesong`) as a resource the domain cannot
 * read. The weights are large, so an implementation reads them once, on its first call, and never on the main thread.
 */
interface PageCropper {

    /**
     * The indices of [lines] to convert, in ascending order: the song, its details, and the lines naming its title and
     * artist. Null where every line is to be kept: the lines hold no song as far as the model can tell, or the model is
     * not there. May throw; the import then keeps every line too, since a crop must never cost the song its own text.
     */
    suspend fun songLinesOf(lines: List<String>): List<Int>?
}
