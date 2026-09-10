package com.pandulapeter.campfire.data.source.remote.implementation.crypto

import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256
import com.pandulapeter.campfire.data.source.remote.api.hashing.Sha256.toHex

/**
 * Dropbox's own hash of a file: SHA-256 of each 4 MB block, concatenated, hashed again. It is only ever compared
 * against a hash Dropbox itself reported, and only to find out that a transfer can be skipped - the sync engine
 * never uses it to decide what changed, which is why every other provider is free to have a different format or
 * none at all.
 */
internal fun dropboxContentHash(bytes: ByteArray): String {
    val blockHashes = ByteArray(((bytes.size + BLOCK_SIZE - 1) / BLOCK_SIZE) * 32)
    var offset = 0
    var written = 0
    while (offset < bytes.size) {
        val end = minOf(offset + BLOCK_SIZE, bytes.size)
        Sha256.hash(bytes.copyOfRange(offset, end)).copyInto(blockHashes, written)
        written += 32
        offset = end
    }
    return Sha256.hash(blockHashes).toHex()
}

private const val BLOCK_SIZE = 4 * 1024 * 1024
