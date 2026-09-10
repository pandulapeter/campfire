/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.crypto

import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentHashTest {

    /** A file smaller than one block hashes to the hash of the hash of its only block. */
    @Test
    fun `computes the Dropbox content hash of a small file`() {
        val bytes = "abc".encodeToByteArray()
        assertEquals(
            expected = Sha256.hashToHex(Sha256.hash(bytes)),
            actual = dropboxContentHash(bytes)
        )
    }

    /** No blocks at all, so the hash is that of an empty list of block hashes. */
    @Test
    fun `computes the Dropbox content hash of an empty file`() = assertEquals(
        expected = Sha256.hashToHex(ByteArray(0)),
        actual = dropboxContentHash(ByteArray(0))
    )

    @Test
    fun `computes the Dropbox content hash of a file spanning several blocks`() {
        val blockSize = 4 * 1024 * 1024
        val bytes = ByteArray(blockSize + 1) { 'a'.code.toByte() }
        val blockHashes = Sha256.hash(bytes.copyOfRange(0, blockSize)) + Sha256.hash(bytes.copyOfRange(blockSize, bytes.size))
        assertEquals(
            expected = Sha256.hashToHex(blockHashes),
            actual = dropboxContentHash(bytes)
        )
    }
}
