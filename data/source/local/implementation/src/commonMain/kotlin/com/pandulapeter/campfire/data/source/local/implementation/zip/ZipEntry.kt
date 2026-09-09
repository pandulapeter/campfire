package com.pandulapeter.campfire.data.source.local.implementation.zip

/**
 * One file inside a zip archive. [name] is the path as stored in the archive: forward slashes, possibly containing
 * sub-directories, which callers strip when they only want the file name.
 */
internal data class ZipEntry(
    val name: String,
    val bytes: ByteArray
) {

    override fun equals(other: Any?) = this === other || (other is ZipEntry && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode() = 31 * name.hashCode() + bytes.contentHashCode()
}
