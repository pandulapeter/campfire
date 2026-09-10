/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.hashing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Sha256Test {

    @Test
    fun `hashes the empty input to the published digest`() = assertEquals(
        expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        actual = Sha256.hashToHex(ByteArray(0))
    )

    @Test
    fun `hashes a short input to the published digest`() = assertEquals(
        expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        actual = Sha256.hashToHex("abc".encodeToByteArray())
    )

    /** 56 bytes, which is exactly the length that forces a second padding block. */
    @Test
    fun `hashes an input that straddles the block boundary`() = assertEquals(
        expected = "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
        actual = Sha256.hashToHex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())
    )

    @Test
    fun `hashes an input longer than one block`() = assertEquals(
        expected = "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
        actual = Sha256.hashToHex(ByteArray(1_000_000) { 'a'.code.toByte() })
    )

    @Test
    fun `a single changed byte changes the local content hash`() = assertNotEquals(
        illegal = localContentHash("{title: Song}".encodeToByteArray()),
        actual = localContentHash("{title: Songs}".encodeToByteArray())
    )
}
