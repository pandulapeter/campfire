/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

/**
 * Where a platform keeps what its home screen widgets read: the snapshot, and the folder of cover thumbnails beside it
 * that the snapshot's songs name ([WidgetSnapshot.Song.cover]). It only writes what it is handed; which covers there are,
 * and when one goes, is [WidgetPublisher]'s.
 */
interface WidgetFiles {

    /**
     * Writes [snapshot] where the widgets read it, in one step, and has them drawn again from it, unless it is what the
     * file already holds. Returns once it is written: a write begun is finished even where the caller is cancelled.
     */
    suspend fun writeSnapshot(snapshot: WidgetSnapshot)

    /** The names of the thumbnails in the covers folder, none where there is no folder yet. */
    suspend fun coverNames(): Set<String>

    /** Writes [bytes] as the thumbnail [name], in one step; whether it could. */
    suspend fun writeCover(name: String, bytes: ByteArray): Boolean

    /** Deletes the thumbnail [name], if it is there. */
    suspend fun deleteCover(name: String)
}
