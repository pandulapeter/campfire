/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.hashing

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun `hashes the empty input to the published digest`() = assertEquals(
        expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        actual = Sha256.hashToHex(ByteArray(0)),
    )

    @Test
    fun `hashes a short input to the published digest`() = assertEquals(
        expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        actual = Sha256.hashToHex("abc".encodeToByteArray()),
    )

    /** 56 bytes, which is exactly the length that forces a second padding block. */
    @Test
    fun `hashes an input that straddles the block boundary`() = assertEquals(
        expected = "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
        actual = Sha256.hashToHex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
    )

    @Test
    fun `hashes an input longer than one block`() = assertEquals(
        expected = "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
        actual = Sha256.hashToHex(ByteArray(1_000_000) { 'a'.code.toByte() }),
    )

    @Test
    fun `hashes the 896-bit message to the published digest`() = assertEquals(
        expected = "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
        actual = Sha256.hashToHex(
            "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
                .encodeToByteArray(),
        ),
    )

    @Test
    fun `a digest fed in pieces equals the one-shot hash at every block boundary`() {
        val random = Random(seed = 42)
        (listOf(0, 1, 55, 56, 63, 64, 65) + (119..129) + listOf(1_000, 70_000)).forEach { size ->
            val bytes = random.nextBytes(size)
            repeat(20) {
                val digest = Sha256.Digest()
                var offset = 0
                while (offset < size) {
                    val length = random.nextInt(1, size - offset + 1).coerceAtMost(random.nextInt(1, 200))
                    digest.update(bytes, offset, length)
                    offset += length
                }
                assertEquals(Sha256.hashToHex(bytes), digest.finishToHex(), "size $size")
            }
        }
    }

    @Test
    fun `a digest reads only the range it is given of a larger array`() {
        val bytes = Random(seed = 7).nextBytes(300)
        assertEquals(
            expected = Sha256.hashToHex(bytes.copyOfRange(13, 213)),
            actual = Sha256.Digest().apply { update(bytes, 13, 200) }.finishToHex(),
        )
    }

    @Test
    fun `a finished digest takes no more input`() {
        val digest = Sha256.Digest().apply { finish() }
        assertFailsWith<IllegalStateException> { digest.update(ByteArray(1)) }
    }

    @Test
    fun `the yielding hash equals the one-shot hash across several slices`() = runTest {
        val bytes = Random(seed = 3).nextBytes(Sha256.YIELD_CHUNK_SIZE * 2 + 17)
        assertEquals(Sha256.hashToHex(bytes), Sha256.hashToHexYielding(bytes))
    }
}
