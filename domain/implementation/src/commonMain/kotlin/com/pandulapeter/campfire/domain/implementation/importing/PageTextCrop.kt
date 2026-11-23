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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.domain.api.importing.PageCropper
import kotlinx.coroutines.CancellationException

/** A page's text cropped to its song by a [PageCropper], before the chord sheet converter reads it. */
internal object PageTextCrop {

    /**
     * [text] with only the lines [cropper] keeps, or null where it is to be converted whole: the cropper keeps every line,
     * finds no song, answers nothing usable, or fails, which [logger] hears. A blank line between two kept lines stays
     * even where the model did not keep it, since the converter tells a song's verses apart by the blank lines between
     * them, and a run of clutter taken out from between two parts still leaves the parts apart.
     */
    suspend fun cropped(text: String, cropper: PageCropper, logger: Logger): String? {
        val lines = text.lines()
        val songLines = try {
            cropper.songLinesOf(lines)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not crop the page: ${exception.message}")
            null
        }?.filter { it in lines.indices }?.toSet()
        if (songLines.isNullOrEmpty()) return null
        val first = songLines.min()
        val last = songLines.max()
        val kept = lines.indices.filter { index -> index in songLines || (lines[index].isBlank() && index in first..last) }
        return if (kept.size == lines.size) null else kept.joinToString("\n") { lines[it] }
    }
}
