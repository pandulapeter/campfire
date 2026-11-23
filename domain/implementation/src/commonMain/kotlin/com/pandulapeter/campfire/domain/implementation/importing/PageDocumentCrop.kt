/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetPageLines
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.domain.api.importing.PageCropper
import com.pandulapeter.campfire.pagesong.PageLineScores
import kotlinx.coroutines.CancellationException

/** A document's lines scored by a [PageCropper], by which the chord sheet converter crops each of its songs. */
internal object PageDocumentCrop {

    /**
     * The scores of [sheet]'s lines, or null where it is to be converted whole: no model, or a failure, which [logger]
     * hears. A document of no line is not asked about.
     */
    suspend fun scoresOf(sheet: ChordSheet, cropper: PageCropper, logger: Logger): List<PageLineScores>? {
        val lines = ChordSheetPageLines.of(sheet).takeIf { it.isNotEmpty() } ?: return null
        return try {
            cropper.documentScoresOf(lines)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not crop the document: ${exception.message}")
            null
        }
    }
}
