/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.byteSizeOf
import com.pandulapeter.campfire.presentation.ui.platform.ImportNotification

/**
 * How far an import's work is in bytes, for a platform's progress bar: [completed] of [total], where the total is known.
 */
data class ImportBytes(
    val completed: Long,
    val total: Long?,
)

/**
 * The bytes [progress] counts for the work in [state], or null where it counts none: before the first count, in a phase
 * that counts files, and while writing, so that a notification never shows the numbers of a phase that has ended.
 */
fun importBytesOf(state: ImportWorkState, progress: ImportProgress?): ImportBytes? {
    val phase = state.bytePhase ?: return null
    if (progress == null || progress.phase != phase) return null
    val completed = progress.completedBytes ?: return null
    return ImportBytes(completed = completed, total = progress.totalBytes?.takeIf { it > 0 })
}

/**
 * What the notification says about the work in [state], in the same words as the import screen's loading stage
 * (`ImportProgressContent`): the phase alone until it counts bytes, then how many. Null while the work is idle, when
 * there is nothing to show.
 */
fun ImportNotification.textOf(state: ImportWorkState, progress: ImportProgress?): String? {
    val words = when (state) {
        ImportWorkState.IDLE -> return null
        ImportWorkState.WRITING -> return writing
        ImportWorkState.RECEIVING -> receiving
        ImportWorkState.DOWNLOADING -> downloading
        ImportWorkState.UNPACKING -> unpacking
    }
    val bytes = importBytesOf(state, progress) ?: return words.line
    return if (bytes.total == null) {
        words.bytesSoFar.replace(COMPLETED, sizeOf(bytes.completed))
    } else {
        words.bytesOfTotal.replace(COMPLETED, sizeOf(bytes.completed)).replace(TOTAL, sizeOf(bytes.total))
    }
}

/**
 * What an import's notification shows: its [text], whether it offers Cancel, and its bar at [progress] of
 * [IMPORT_NOTIFICATION_PROGRESS_STEPS], or none (null) where it runs without one. Two equal ones look the same, so a
 * shell posts only a change: every post rebuilds the notification, and a tap on its Cancel while it is being rebuilt
 * can be lost.
 */
data class ImportNotificationContent(
    val text: String,
    val isCancellable: Boolean,
    val progress: Int?,
)

/** What the notification shows of the work in [state], see [ImportNotificationContent]. */
fun ImportNotification.contentOf(state: ImportWorkState, progress: ImportProgress?): ImportNotificationContent {
    val bytes = importBytesOf(state, progress)
    val total = bytes?.total
    return ImportNotificationContent(
        text = textOf(state, progress) ?: title,
        isCancellable = state.isCancellable,
        progress = if (bytes == null || total == null) null else (bytes.completed.coerceIn(0, total) * IMPORT_NOTIFICATION_PROGRESS_STEPS / total).toInt(),
    )
}

/** How many steps a notification's progress bar moves in: more would only re-post it for what nobody can see. */
const val IMPORT_NOTIFICATION_PROGRESS_STEPS = 1000

/** [bytes] as the screen writes a size (`formattedSize`), in the words handed over. */
internal fun ImportNotification.sizeOf(bytes: Long): String {
    val size = byteSizeOf(bytes)
    val number = size.tenths?.let { "${size.whole}$decimalSeparator$it" } ?: size.whole.toString()
    return sizeUnits.getOrElse(size.unit) { sizeUnits.last() }.replace(SIZE, number)
}

/** The phase of [ImportProgress] whose bytes the work in this state counts. */
private val ImportWorkState.bytePhase
    get() = when (this) {
        ImportWorkState.RECEIVING -> ImportProgress.Phase.RECEIVING
        ImportWorkState.DOWNLOADING -> ImportProgress.Phase.DOWNLOADING
        ImportWorkState.UNPACKING -> ImportProgress.Phase.UNPACKING
        ImportWorkState.IDLE, ImportWorkState.WRITING -> null
    }

/** The placeholders of [ImportNotification]'s sentences, which `ui/ImportNotificationEffect.kt` resolves them with. */
internal const val COMPLETED = "{completed}"
internal const val TOTAL = "{total}"
internal const val SIZE = "{size}"
