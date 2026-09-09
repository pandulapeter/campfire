package com.pandulapeter.campfire.data.model.domain

/**
 * A file on its way into the library: what a file picker, a drop or an archive hands over, before anything has been
 * decided about it. [name] is a plain file name; entries coming out of an archive have their path stripped.
 */
data class ImportedFile(
    val name: String,
    val bytes: ByteArray
) {

    override fun equals(other: Any?) = this === other || (other is ImportedFile && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode() = 31 * name.hashCode() + bytes.contentHashCode()
}
