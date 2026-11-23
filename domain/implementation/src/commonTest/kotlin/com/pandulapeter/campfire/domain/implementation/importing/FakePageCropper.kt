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

import com.pandulapeter.campfire.domain.api.importing.PageCropper

/** A [PageCropper] answering [songLinesOf] for every page, recording the pages it was asked about. */
internal class FakePageCropper(
    private val songLinesOf: (lines: List<String>) -> List<Int>? = { null },
) : PageCropper {

    val asked = mutableListOf<List<String>>()

    override suspend fun songLinesOf(lines: List<String>): List<Int>? {
        asked += lines
        return songLinesOf.invoke(lines)
    }
}
