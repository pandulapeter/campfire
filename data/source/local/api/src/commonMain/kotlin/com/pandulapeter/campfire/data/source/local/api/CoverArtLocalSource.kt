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

/**
 * The copies of the cover images the songs name, kept so that a cover that was seen once is there without a network.
 * Each copy is addressed by a key the caller derives from the image's address, and holds nothing but the image's
 * bytes. Outside the library: never exported, synced or backed up, since the address travels in the song file and
 * the image can always be downloaded again.
 */
interface CoverArtLocalSource {

    /** The copy stored under [key], or null when there is none or it cannot be read: a cover is never worth failing over. */
    suspend fun loadCoverArt(key: String): ByteArray?

    /** Stores [bytes] under [key], replacing what was there. A copy that cannot be written is simply not kept. */
    suspend fun saveCoverArt(key: String, bytes: ByteArray)

    /** Deletes every copy whose key is not in [keys], which is how covers no song names any more leave the device. */
    suspend fun keepOnlyCoverArt(keys: Set<String>)

    /** The bytes all the copies take up together, or null when they cannot be listed. */
    suspend fun getCoverArtCacheSize(): Long?
}
