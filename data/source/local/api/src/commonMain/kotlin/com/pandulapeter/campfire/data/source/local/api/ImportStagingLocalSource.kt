/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.importing.StagedFile

/**
 * The staging folder an import receives and unpacks into before anything reaches the library. Each process is a
 * session of its own, and every file in the folder carries the session that wrote it.
 */
interface ImportStagingLocalSource {

    /**
     * Deletes every staged file an earlier process left behind (a crash, a killed process, a closed tab), never one this
     * process has written, so it is safe whenever it runs. Called once on launch; never throws but cancellation.
     */
    suspend fun deleteOtherSessions()

    /**
     * Opens the file an archive arriving from outside (a download) is written into, in a request of its own, with room
     * reserved for [announcedSize] where that is known. Throws `StagingRefusedException` where the archive cannot fit,
     * having made nothing.
     */
    suspend fun receiveArchive(announcedSize: Long?): ReceivedArchive

    /**
     * An archive being received, written front to back by one coroutine. Whatever ends it other than [finish] (a write
     * refused, a cancellation, or [abandon]) deletes the file and its request before the failure reaches the caller.
     */
    interface ReceivedArchive {

        /** Throws `StagingRefusedException` where the room runs out, and the storage's failure where the write fails. */
        suspend fun write(bytes: ByteArray, offset: Int, length: Int)

        /** The archive, whole, in the staging folder until its request settles or the next launch. */
        suspend fun finish(): StagedFile

        /** Deletes what was written and the request; does nothing once finished. Never throws, and finishes even when cancelled. */
        suspend fun abandon()
    }

    /**
     * Whether [file] is still where the staging keeps it (or where an import moved it), of the size it was staged with:
     * what an import asks of every staged file its plan names before it writes anything.
     */
    suspend fun isIntact(file: StagedFile): Boolean

    /**
     * Deletes everything the requests that staged [files] hold, partial files included, and gives their room back: the
     * import those requests belong to has settled (imported, cancelled, failed and dismissed). Files of another session or
     * of a request already deleted are passed over. Never throws but cancellation, and finishes even then.
     */
    suspend fun deleteRequestsOf(files: Collection<StagedFile>)
}
