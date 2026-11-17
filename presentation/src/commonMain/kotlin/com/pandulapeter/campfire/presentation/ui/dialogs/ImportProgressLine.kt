/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.ui.progress.byteFraction
import com.pandulapeter.campfire.presentation.ui.progress.reportedBytes

/** What [ImportProgressContent] says under its progress line, and how far that line fills (null: it runs without a fill). */
internal sealed interface ImportProgressLine {

    val fraction: Float?

    /** [completed] of [total] files. */
    data class Files(val completed: Int, val total: Int) : ImportProgressLine {
        override val fraction get() = completed.toFloat() / total
    }

    /** [completed] bytes of [phase] so far, of [total] where it is known. */
    data class Bytes(
        val phase: ImportProgress.Phase,
        val completed: Long,
        val total: Long?,
        override val fraction: Float?,
    ) : ImportProgressLine
}

/**
 * The line [progress] is shown with: its bytes where its phase reports them, else its file count where it has one, else
 * none. Bytes win over files, since an archive's phases spend their time on its bytes rather than on its file count.
 */
internal fun importProgressLineOf(progress: ImportProgress): ImportProgressLine? = progress.reportedBytes?.let { completed ->
    ImportProgressLine.Bytes(
        phase = progress.phase,
        completed = completed,
        total = progress.totalBytes?.takeIf { it > 0 },
        fraction = progress.byteFraction,
    )
} ?: progress.total?.takeIf { it > 0 }?.let { total -> ImportProgressLine.Files(completed = progress.completed, total = total) }
