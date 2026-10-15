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

import kotlin.test.Test
import kotlin.test.assertEquals

/** The expected values were computed with Python's `hashlib`, following Dropbox's published content hash description. */
class ContentHashTest {

    @Test
    fun `computes the Dropbox content hash of a small file`() = assertEquals(
        expected = "4f8b42c22dd3729b519ba6f68d2da7cc5b2d606d05daed5ad5128cc03e6c6358",
        actual = dropboxContentHash("abc".encodeToByteArray()),
    )

    /** No blocks at all, so the hash is that of an empty list of block hashes. */
    @Test
    fun `computes the Dropbox content hash of an empty file`() = assertEquals(
        expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        actual = dropboxContentHash(ByteArray(0)),
    )

    @Test
    fun `computes the Dropbox content hash of a file spanning several blocks`() = assertEquals(
        expected = "5f858b62ccd88447586305aec6fd53c96747cfebf527cbba129a6dfed47d9624",
        actual = dropboxContentHash(ByteArray(4 * 1024 * 1024 + 1) { 'a'.code.toByte() }),
    )
}
