package com.pandulapeter.campfire.data.model.domain

/**
 * A file on its way out of the library. The name and the type are decided where the content is (a lone song leaves
 * as the `.cho` file it already is, everything else as a zip), so that the platform's save dialog only has to hand
 * the bytes over.
 */
data class ExportedFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
) {

    override fun equals(other: Any?) = this === other ||
        (other is ExportedFile && name == other.name && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

    override fun hashCode() = 31 * (31 * name.hashCode() + mimeType.hashCode()) + bytes.contentHashCode()

    companion object {
        const val TEXT_MIME_TYPE = "text/plain"
        const val ZIP_MIME_TYPE = "application/zip"
    }
}
