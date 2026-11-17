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

import kotlinx.coroutines.yield

/**
 * SHA-256, written out rather than taken from a library.
 *
 * Sync needs a hash in three places - the PKCE challenge, the content hash a provider compares against, and the
 * fingerprint the sync index remembers for every local file - and every multiplatform hashing library would be one
 * more dependency across four targets for eighty lines of arithmetic that will never change.
 *
 * It lives next to the sync contracts because both sides of sync need it: the engine that works out what changed,
 * and the providers that speak each service's own hashing dialect.
 */
object Sha256 {

    fun hash(bytes: ByteArray) = Digest().apply { update(bytes) }.finish()

    fun hashToHex(bytes: ByteArray) = hash(bytes).toHex()

    /**
     * [hashToHex] for a file-sized array, fed in [YIELD_CHUNK_SIZE] slices with a yield between them, so that a 50 MB
     * attachment hashed on a computation dispatcher neither holds a thread of it for seconds nor ignores cancellation.
     * The caller picks the dispatcher: this function does not move off the one it is called on.
     */
    suspend fun hashToHexYielding(bytes: ByteArray): String {
        val digest = Digest()
        var offset = 0
        while (offset < bytes.size) {
            if (offset > 0) yield()
            val length = minOf(YIELD_CHUNK_SIZE, bytes.size - offset)
            digest.update(bytes, offset, length)
            offset += length
        }
        return digest.finishToHex()
    }

    fun ByteArray.toHex() = buildString(size * 2) {
        this@toHex.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

    /**
     * SHA-256 fed in pieces, so a file of any size is hashed while it streams past and never has to be held whole.
     *
     * Whole 64-byte blocks are compressed straight from the caller's array, and only a block's remainder is buffered,
     * so feeding a large array at offsets costs no copy of it. Any chunking gives the same digest as one call.
     * It is deliberately not `suspend`: a caller hashing megabytes feeds it in slices and yields between them itself.
     * A digest is spent once finished.
     */
    class Digest {
        private val state = INITIAL_STATE.copyOf()
        private val block = ByteArray(BLOCK_SIZE)
        private val schedule = IntArray(64)
        private var blockFill = 0
        private var totalLength = 0L
        private var isFinished = false

        fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
            check(!isFinished) { "The digest is already finished." }
            require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "The range is outside the array." }
            var position = offset
            var remaining = length
            totalLength += length
            if (blockFill > 0) {
                val taken = minOf(BLOCK_SIZE - blockFill, remaining)
                bytes.copyInto(block, blockFill, position, position + taken)
                blockFill += taken
                position += taken
                remaining -= taken
                if (blockFill < BLOCK_SIZE) return
                compress(block, 0)
                blockFill = 0
            }
            while (remaining >= BLOCK_SIZE) {
                compress(bytes, position)
                position += BLOCK_SIZE
                remaining -= BLOCK_SIZE
            }
            bytes.copyInto(block, 0, position, position + remaining)
            blockFill = remaining
        }

        /** Pads the buffered remainder (a 1 bit, zeroes, and the bit length as a big endian 64 bit number) and hashes it. */
        fun finish(): ByteArray {
            check(!isFinished) { "The digest is already finished." }
            isFinished = true
            val tail = ByteArray(if (blockFill < BLOCK_SIZE - 8) BLOCK_SIZE else BLOCK_SIZE * 2)
            block.copyInto(tail, 0, 0, blockFill)
            tail[blockFill] = 0x80.toByte()
            val bitLength = totalLength * 8
            for (index in 0 until 8) {
                tail[tail.size - 1 - index] = (bitLength ushr (8 * index)).toByte()
            }
            var offset = 0
            while (offset < tail.size) {
                compress(tail, offset)
                offset += BLOCK_SIZE
            }
            return ByteArray(32) { index -> (state[index / 4] ushr (24 - (index % 4) * 8)).toByte() }
        }

        fun finishToHex() = finish().toHex()

        private fun compress(bytes: ByteArray, offset: Int) {
            for (index in 0 until 16) {
                schedule[index] = (bytes[offset + index * 4].toInt() and 0xFF shl 24) or
                        (bytes[offset + index * 4 + 1].toInt() and 0xFF shl 16) or
                        (bytes[offset + index * 4 + 2].toInt() and 0xFF shl 8) or
                        (bytes[offset + index * 4 + 3].toInt() and 0xFF)
            }
            for (index in 16 until 64) {
                val previous = schedule[index - 15]
                val recent = schedule[index - 2]
                val s0 = previous.rotateRight(7) xor previous.rotateRight(18) xor (previous ushr 3)
                val s1 = recent.rotateRight(17) xor recent.rotateRight(19) xor (recent ushr 10)
                schedule[index] = schedule[index - 16] + s0 + schedule[index - 7] + s1
            }
            var a = state[0]
            var b = state[1]
            var c = state[2]
            var d = state[3]
            var e = state[4]
            var f = state[5]
            var g = state[6]
            var h = state[7]
            for (index in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val choice = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + choice + ROUND_CONSTANTS[index] + schedule[index]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + majority
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }
            state[0] += a
            state[1] += b
            state[2] += c
            state[3] += d
            state[4] += e
            state[5] += f
            state[6] += g
            state[7] += h
        }
    }

    private const val BLOCK_SIZE = 64

    /** 256 KiB: a few milliseconds of hashing on a phone, short between yields and long enough that yielding costs nothing. */
    const val YIELD_CHUNK_SIZE = 256 * 1024

    private fun Int.rotateRight(bits: Int) = (this ushr bits) or (this shl (32 - bits))

    private const val HEX_DIGITS = "0123456789abcdef"

    private val INITIAL_STATE = intArrayOf(
        0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
        0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
    )

    private val ROUND_CONSTANTS = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
}
