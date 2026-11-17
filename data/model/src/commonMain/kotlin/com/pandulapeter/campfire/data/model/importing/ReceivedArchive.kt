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
 * An archive being received into the staging folder from outside (a download, a picked or opened file), in a staging
 * request of its own, filled front to back by one coroutine through [write], or whole through [take]. Whatever ends it
 * other than [finish] (a write refused, a cancellation, or [abandon]) deletes the file and its request before the
 * failure reaches the caller.
 */
interface ReceivedArchive {

    /** Throws [StagingRefusedException] where the room runs out, and the storage's failure where the write fails. */
    suspend fun write(bytes: ByteArray, offset: Int, length: Int)

    /**
     * Fills the archive, still empty, from [file] in one piece rather than through [write], so that its bytes never pass
     * through Kotlin: a platform's own handle of a file its storage copies or moves itself (the web's `File`, copied in
     * JavaScript; on iOS the path of a file the app owns, moved), [size] bytes long, [onProgress] told the bytes in so
     * far. Throws `UnsupportedOperationException` for a handle the platform's storage does not take, and as [write] does.
     */
    suspend fun take(file: Any, size: Long, onProgress: (receivedBytes: Long) -> Unit)

    /** The archive, whole, in the staging folder until its request settles or the next launch. */
    suspend fun finish(): StagedFile

    /** Deletes what was written and the request; does nothing once finished. Never throws, and finishes even when cancelled. */
    suspend fun abandon()
}
