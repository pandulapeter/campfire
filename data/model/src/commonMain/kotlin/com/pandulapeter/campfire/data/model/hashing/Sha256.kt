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

    fun hash(bytes: ByteArray): ByteArray {
        val state = INITIAL_STATE.copyOf()
        val padded = pad(bytes)
        val schedule = IntArray(64)
        var offset = 0
        while (offset < padded.size) {
            for (index in 0 until 16) {
                schedule[index] = (padded[offset + index * 4].toInt() and 0xFF shl 24) or
                        (padded[offset + index * 4 + 1].toInt() and 0xFF shl 16) or
                        (padded[offset + index * 4 + 2].toInt() and 0xFF shl 8) or
                        (padded[offset + index * 4 + 3].toInt() and 0xFF)
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
            offset += 64
        }
        return ByteArray(32) { index -> (state[index / 4] ushr (24 - (index % 4) * 8)).toByte() }
    }

    fun hashToHex(bytes: ByteArray) = hash(bytes).toHex()

    fun ByteArray.toHex() = buildString(size * 2) {
        this@toHex.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

    /** The message, a 1 bit, zeroes up to 56 bytes modulo 64, and the bit length as a big endian 64 bit number. */
    private fun pad(bytes: ByteArray): ByteArray {
        val bitLength = bytes.size.toLong() * 8
        val paddingLength = ((56 - (bytes.size + 1) % 64) + 64) % 64
        val padded = ByteArray(bytes.size + 1 + paddingLength + 8)
        bytes.copyInto(padded)
        padded[bytes.size] = 0x80.toByte()
        for (index in 0 until 8) {
            padded[padded.size - 1 - index] = (bitLength ushr (8 * index)).toByte()
        }
        return padded
    }

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
