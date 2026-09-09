package com.pandulapeter.campfire.data.source.local.implementation.zip

/** A minimal growable byte buffer with the little-endian primitives the zip format is built from. */
internal class ByteArrayBuilder(initialCapacity: Int = 64) {

    private var buffer = ByteArray(if (initialCapacity < MINIMUM_CAPACITY) MINIMUM_CAPACITY else initialCapacity)

    var size = 0
        private set

    fun u8(value: Int) {
        ensureCapacity(1)
        buffer[size++] = (value and 0xFF).toByte()
    }

    fun u16(value: Int) {
        u8(value)
        u8(value ushr 8)
    }

    fun u32(value: Long) {
        u8((value and 0xFF).toInt())
        u8(((value ushr 8) and 0xFF).toInt())
        u8(((value ushr 16) and 0xFF).toInt())
        u8(((value ushr 24) and 0xFF).toInt())
    }

    fun bytes(value: ByteArray) {
        ensureCapacity(value.size)
        value.copyInto(buffer, size)
        size += value.size
    }

    fun build(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(additional: Int) {
        val required = size + additional
        if (required > buffer.size) {
            var newSize = buffer.size
            while (newSize < required) {
                newSize *= 2
            }
            buffer = buffer.copyOf(newSize)
        }
    }
}

private const val MINIMUM_CAPACITY = 16
