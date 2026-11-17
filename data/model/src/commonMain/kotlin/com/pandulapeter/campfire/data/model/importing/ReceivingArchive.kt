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

import kotlinx.coroutines.flow.StateFlow

/**
 * An archive that arrived (picked, dropped, or handed over by the system) and is being copied into the staging folder
 * by a job of its own, started the moment it arrived, so that whatever lets the app read it (an Android URI grant, an
 * iOS inbox copy, a web `File`) is used while it is valid: what an [ImportedFile] carries in [ImportedFile.receiving]
 * until the import's preparation awaits it.
 */
interface ReceivingArchive {

    /** How large the platform said the file is, null where it could not say. */
    val announcedSize: Long?

    /** How many bytes are in the staging folder so far. */
    val receivedBytes: StateFlow<Long>

    /**
     * The archive, once whole. Throws what ended the copy: a [StagingRefusedException] where it did not fit, the
     * storage's failure (with an `OutOfSpaceException` among its causes where the disk filled), any other failure for a
     * file that could not be read, and an `IllegalStateException` once [discard]ed. Waiting may be cancelled without
     * cancelling the copy.
     */
    suspend fun await(): StagedFile

    /**
     * Stops the copy and deletes what it staged, whether it was still running or had finished: the batch it belongs to
     * was cancelled, or let go of before its preparation took the archive. Returns once nothing of it is left. Never
     * throws, and finishes even when cancelled.
     */
    suspend fun discard()
}
