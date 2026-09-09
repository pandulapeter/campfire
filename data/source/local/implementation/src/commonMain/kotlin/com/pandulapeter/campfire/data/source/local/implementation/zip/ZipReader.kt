package com.pandulapeter.campfire.data.source.local.implementation.zip

/**
 * Reads zip archives with STORED and DEFLATE entries, following the PKWARE APPNOTE. ZIP64, encryption and every other
 * compression method are rejected with a [ZipException]; the app only ever deals with small archives of text files.
 */
internal object ZipReader {

    /**
     * Every file entry of [archive], directories skipped. Names are returned as stored (forward slashes, possibly with
     * sub-directories), decoded as UTF-8.
     */
    fun read(archive: ByteArray): List<ZipEntry> {
        val endOfCentralDirectory = findEndOfCentralDirectory(archive)
        val totalEntries = archive.u16(endOfCentralDirectory + 10)
        val centralDirectorySize = archive.u32(endOfCentralDirectory + 12)
        val centralDirectoryOffset = archive.u32(endOfCentralDirectory + 16)
        if (totalEntries == 0xFFFF || centralDirectorySize == 0xFFFFFFFFL || centralDirectoryOffset == 0xFFFFFFFFL) {
            throw ZipException("ZIP64 archives are not supported.")
        }
        if (centralDirectoryOffset + centralDirectorySize > archive.size) {
            throw ZipException("The central directory reaches past the end of the ${archive.size} byte archive.")
        }
        val entries = mutableListOf<ZipEntry>()
        var position = centralDirectoryOffset.toInt()
        repeat(totalEntries) {
            if (archive.u32(position) != CENTRAL_DIRECTORY_SIGNATURE) {
                throw ZipException("Missing central directory header at offset $position.")
            }
            val flags = archive.u16(position + 8)
            val method = archive.u16(position + 10)
            val crc = archive.u32(position + 16)
            val compressedSize = archive.u32(position + 20)
            val uncompressedSize = archive.u32(position + 24)
            val nameLength = archive.u16(position + 28)
            val extraLength = archive.u16(position + 30)
            val commentLength = archive.u16(position + 32)
            val localHeaderOffset = archive.u32(position + 42)
            val name = archive.utf8(position + 46, nameLength)
            if (flags and 0x0001 != 0) {
                throw ZipException("Encrypted entry \"$name\" is not supported.")
            }
            if (compressedSize == 0xFFFFFFFFL || uncompressedSize == 0xFFFFFFFFL || localHeaderOffset == 0xFFFFFFFFL) {
                throw ZipException("ZIP64 entry \"$name\" is not supported.")
            }
            if (!name.endsWith("/")) {
                entries += ZipEntry(
                    name = name,
                    bytes = readData(
                        archive = archive,
                        name = name,
                        method = method,
                        crc = crc,
                        compressedSize = compressedSize.toInt(),
                        uncompressedSize = uncompressedSize.toInt(),
                        localHeaderOffset = localHeaderOffset.toInt()
                    )
                )
            }
            position += 46 + nameLength + extraLength + commentLength
        }
        return entries
    }

    private fun readData(
        archive: ByteArray,
        name: String,
        method: Int,
        crc: Long,
        compressedSize: Int,
        uncompressedSize: Int,
        localHeaderOffset: Int
    ): ByteArray {
        if (archive.u32(localHeaderOffset) != LOCAL_HEADER_SIGNATURE) {
            throw ZipException("Missing local header for \"$name\" at offset $localHeaderOffset.")
        }
        // The local header's name and extra fields may differ in length from the central directory ones, but the sizes
        // there are zero whenever a data descriptor follows the data, so the central directory stays the source of truth.
        val dataOffset = localHeaderOffset + 30 + archive.u16(localHeaderOffset + 26) + archive.u16(localHeaderOffset + 28)
        if (dataOffset < 0 || compressedSize < 0 || uncompressedSize < 0 || dataOffset + compressedSize > archive.size) {
            throw ZipException("The data of \"$name\" reaches past the end of the ${archive.size} byte archive.")
        }
        val bytes = when (method) {
            METHOD_STORED -> {
                if (compressedSize != uncompressedSize) {
                    throw ZipException("Stored entry \"$name\" declares different compressed and uncompressed sizes.")
                }
                archive.copyOfRange(dataOffset, dataOffset + compressedSize)
            }

            METHOD_DEFLATE -> Inflater.inflate(archive, dataOffset, compressedSize, uncompressedSize)

            else -> throw ZipException("Unsupported compression method $method for \"$name\".")
        }
        if (Crc32.of(bytes) != crc) {
            throw ZipException("Checksum mismatch for \"$name\".")
        }
        return bytes
    }

    private fun findEndOfCentralDirectory(archive: ByteArray): Int {
        if (archive.size < 22) {
            throw ZipException("An archive of ${archive.size} bytes is too short to be a zip file.")
        }
        val lowestPossible = maxOf(0, archive.size - 22 - MAX_COMMENT_LENGTH)
        for (offset in archive.size - 22 downTo lowestPossible) {
            if (archive.u32(offset) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                return offset
            }
        }
        throw ZipException("Not a zip file: no end of central directory record found.")
    }

    private fun ByteArray.utf8(offset: Int, length: Int): String {
        if (length < 0 || offset < 0 || offset + length > size) {
            throw ZipException("Truncated archive: a $length byte name at offset $offset is outside the $size byte input.")
        }
        return copyOfRange(offset, offset + length).decodeToString()
    }

    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    private const val LOCAL_HEADER_SIGNATURE = 0x04034B50L
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATE = 8
    private const val MAX_COMMENT_LENGTH = 65535
}
