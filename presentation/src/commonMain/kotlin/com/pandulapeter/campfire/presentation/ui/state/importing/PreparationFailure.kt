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

import com.pandulapeter.campfire.data.formats.zip.WrappedArchiveException
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.isOutOfSpace
import com.pandulapeter.campfire.presentation.ui.messages.Message

/**
 * What a preparation that threw [exception] tells the user: the one place a thrown failure becomes an [ImportFailure].
 * A device out of room is [ImportFailure.NO_SPACE], with the space needed where staging said how much
 * ([StagingRefusedException.NoSpace]; a write the storage itself refused cannot say); more than one import may stage
 * ([StagingRefusedException.TooLarge]) is [ImportFailure.TOO_LARGE]; an archive past 4 GB its tool wrote without ZIP64
 * ([WrappedArchiveException]) is [ImportFailure.WRAPPED_ARCHIVE], never a damaged one; anything else is a file that
 * could not be read.
 */
internal fun preparationFailureOf(exception: Exception): Preparation.Failed {
    val causes = generateSequence<Throwable>(exception) { it.cause.takeIf { cause -> cause !== it } }.take(MAX_CAUSE_DEPTH)
    return when {
        exception.isOutOfSpace() -> Preparation.Failed(
            reason = ImportFailure.NO_SPACE,
            neededBytes = causes.firstNotNullOfOrNull { it as? StagingRefusedException.NoSpace }?.neededBytes,
        )
        causes.any { it is StagingRefusedException.TooLarge } -> Preparation.Failed(ImportFailure.TOO_LARGE)
        causes.any { it is WrappedArchiveException } -> Preparation.Failed(ImportFailure.WRAPPED_ARCHIVE)
        else -> Preparation.Failed(ImportFailure.UNREADABLE)
    }
}

/** The snackbar a failure is told with where no import screen is up to say it in full. */
internal val Preparation.Failed.message get() = if (reason == ImportFailure.NO_SPACE) Message.StorageFull else Message.ImportFailed

/** As deep as `isOutOfSpace` looks, so that both read the same chain. */
private const val MAX_CAUSE_DEPTH = 16
