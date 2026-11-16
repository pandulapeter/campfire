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

interface IsExportedWithPicturesUseCase {

    /**
     * Whether a song of [text] exported on its own leaves as a zip of itself and its pictures rather than as its file:
     * whether it names a picture the library holds, as [ExportSongsUseCase] decides it. What the export screen names the
     * file it is about to write by.
     */
    suspend operator fun invoke(text: String): Boolean
}
