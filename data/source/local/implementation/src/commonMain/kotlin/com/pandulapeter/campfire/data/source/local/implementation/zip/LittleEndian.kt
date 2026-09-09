package com.pandulapeter.campfire.data.source.local.implementation.zip

/** Reads the unsigned byte at [offset]. */
internal fun ByteArray.u8(offset: Int): Int {
    requireBytes(offset, 1)
    return this[offset].toInt() and 0xFF
}

/** Reads the little-endian unsigned 16 bit integer at [offset]. */
internal fun ByteArray.u16(offset: Int): Int {
    requireBytes(offset, 2)
    return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}

/** Reads the little-endian unsigned 32 bit integer at [offset]. Returned as a [Long] so that it stays unsigned. */
internal fun ByteArray.u32(offset: Int): Long {
    requireBytes(offset, 4)
    return (this[offset].toLong() and 0xFF) or
            ((this[offset + 1].toLong() and 0xFF) shl 8) or
            ((this[offset + 2].toLong() and 0xFF) shl 16) or
            ((this[offset + 3].toLong() and 0xFF) shl 24)
}

private fun ByteArray.requireBytes(offset: Int, count: Int) {
    if (offset < 0 || offset > size - count) {
        throw ZipException("Truncated archive: $count byte(s) at offset $offset are outside the $size byte input.")
    }
}
