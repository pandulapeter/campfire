/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.export

import com.pandulapeter.campfire.data.model.library.ExportedFile

interface ExportSetlistUseCase {

    /**
     * A zip of the setlist, its document unchanged, and the songs in it, so that importing it elsewhere restores both.
     * [isDated] stamps the zip's entries with the time, as a file the user saves has; undated, the same files always make
     * the same bytes, as a shared copy compared by its bytes needs.
     */
    suspend operator fun invoke(setlistFileName: String, isDated: Boolean): ExportedFile?
}
