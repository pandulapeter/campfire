/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.progress

import com.pandulapeter.campfire.data.model.importing.ImportProgress
import com.pandulapeter.campfire.data.model.sync.SyncProgress

/**
 * What [WorkProgressBar] draws: how far the work on the library has got, or a null [fraction] for work that cannot say,
 * which the bar shows as running rather than sitting at zero. Work that is not [isWorthShowing] keeps a bar already up
 * as it was, and never brings one up itself.
 */
internal data class WorkProgress(
    val fraction: Float?,
    val isWorthShowing: Boolean = true,
)

/**
 * The work the bar along the top of the window stands for, or null for none. What the user started (an import, files
 * added to a review, the selection's tag and language edits) comes before a sync run. The demo library planted on a
 * first launch is no work anybody asked for, and the bar is drawn over the launch screen that hides it. A sync run shows
 * only once it has files to transfer: the run on every return to the front mostly only lists both sides, and a line for
 * that would flash on nearly every return. Once up, the bar stays for the rest of the run, whose second pass lists again. An import fills
 * only while it writes, the one phase whose count runs once from zero to the end; the reading and comparing before it
 * each count again from zero, which would send the fill back.
 */
internal fun workProgress(
    isImporting: Boolean,
    isDemoLibraryPending: Boolean,
    importProgress: ImportProgress?,
    isAddingToReview: Boolean,
    isEditingSongs: Boolean,
    syncProgress: SyncProgress?,
): WorkProgress? = when {
    (isImporting && !isDemoLibraryPending) || isAddingToReview || isEditingSongs -> WorkProgress(
        fraction = importProgress
            ?.takeIf { it.phase == ImportProgress.Phase.IMPORTING }
            ?.total
            ?.takeIf { it > 0 }
            ?.let { total -> (importProgress.completed.toFloat() / total).coerceIn(0f, 1f) },
    )
    else -> syncProgress?.let { WorkProgress(fraction = it.fraction, isWorthShowing = it.fraction != null) }
}
