package com.pandulapeter.campfire.data.source.local.implementation.zip

/** Table driven CRC-32 (polynomial 0xEDB88320), the checksum zip archives store for every entry. */
internal object Crc32 {

    private val table = IntArray(256) { index ->
        var value = index
        repeat(8) {
            value = if (value and 1 != 0) (value ushr 1) xor POLYNOMIAL else value ushr 1
        }
        value
    }

    /** The unsigned CRC-32 of [bytes]. */
    fun of(bytes: ByteArray): Long {
        var crc = -1
        for (byte in bytes) {
            crc = (crc ushr 8) xor table[(crc xor byte.toInt()) and 0xFF]
        }
        return crc.inv().toLong() and 0xFFFFFFFFL
    }
}

private const val POLYNOMIAL = 0xEDB88320.toInt()
