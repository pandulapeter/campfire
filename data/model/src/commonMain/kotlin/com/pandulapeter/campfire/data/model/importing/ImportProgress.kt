/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/**
 * Progress within one phase, rather than an estimate for a whole import whose size is unknown until archives and
 * collections have been opened. A null total means that the phase has no measurable file count.
 *
 * The phases that move an archive's bytes ([Phase.RECEIVING], [Phase.DOWNLOADING], and [Phase.UNPACKING]) may count
 * those instead: [completedBytes] so far, against [totalBytes] where the size is known (a download without a
 * `Content-Length` has none). Bytes, where a phase reports them, are what the screens show rather than the file count,
 * since one large file is most of such a phase's time.
 */
data class ImportProgress(
    val phase: Phase,
    val completed: Int = 0,
    val total: Int? = null,
    val fileName: String? = null,
    val completedBytes: Long? = null,
    val totalBytes: Long? = null,
) {
    enum class Phase {
        /** An archive that arrived being copied into the staging folder before it is read. */
        RECEIVING,
        DOWNLOADING,

        /** A web page shared or pasted by its address being read on the device (`LoadPageUseCase`); it counts nothing. */
        READING_PAGE,
        UNPACKING,
        DOWNLOADING_PICTURES,
        READING,
        COMPARING,
        IMPORTING,
        FINISHING,
    }
}
