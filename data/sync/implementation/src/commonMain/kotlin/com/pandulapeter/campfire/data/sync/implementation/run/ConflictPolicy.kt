/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

/**
 * What lets a collection's documents changed on both sides be one document rather than a conflict, where every device
 * writes parts of a document on its own that nobody chose (a value a read fills in on each device, say): two versions
 * that differ only there take the cloud folder's version, which needs no upload and is what every other device has or
 * will download. Optional, one per collection; without one, every such difference is a conflict, and both versions are
 * kept. Each answer must be safe in the direction of a conflict: a document it cannot read is never the same.
 */
internal interface ConflictPolicy {

    /** Whether [first] and [second] are the same document apart from their volatile parts; false where either is unreadable. */
    fun isSameApartFromVolatileParts(first: ByteArray, second: ByteArray): Boolean

    /**
     * [bytes] as the app writes the document with no volatile parts, or null where they are not such a document: what a
     * file was before a read filled them in, byte for byte, so a local change of nothing else reads as no change.
     */
    fun withoutVolatileParts(bytes: ByteArray): ByteArray?
}
