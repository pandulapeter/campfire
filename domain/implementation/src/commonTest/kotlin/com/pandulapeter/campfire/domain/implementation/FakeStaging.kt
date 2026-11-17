/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.data.formats.zip.WrappedArchiveException
import com.pandulapeter.campfire.data.formats.zip.ZipReader
import com.pandulapeter.campfire.data.formats.zip.ZipSource
import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.ImportedImage
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.repository.api.ImportStagingRepository
import kotlin.test.assertFailsWith

/**
 * The staging folder in memory, named as the real one names its files (by request and content), standing in for
 * `ImportStagingRepository` too. [files] is what the folder holds; a file [moveOut] took into the library is found where
 * it went, as the real staging finds it, until [deleteRequestsOf] settles its request.
 */
internal open class FakeStaging : ImportStagingRepository {

    val files = mutableMapOf<String, ByteArray>()
    private val moved = mutableMapOf<String, ByteArray>()

    /** Every file the settled requests held, in the order [deleteRequestsOf] was told of them. */
    val settled = mutableListOf<StagedFile>()

    /** Runs before every [moveOut], for a test to fail one. */
    var beforeMove: (StagedFile) -> Unit = {}

    /** [bytes] staged as an unpacked file of request [REQUEST] with [extension], one file however often the same bytes come. */
    fun stage(bytes: ByteArray, extension: String = ".bin"): StagedFile {
        val hex = Sha256.hashToHex(bytes)
        return StagedFile(name = "$SESSION-$REQUEST-$hex$extension", size = bytes.size.toLong(), sha256Hex = hex).also { files[it.name] = bytes }
    }

    /** [bytes] as an archive received into request [REQUEST]. */
    fun receive(bytes: ByteArray) = StagedFile(name = "$SESSION-$REQUEST-${files.size}.zip", size = bytes.size.toLong(), sha256Hex = null)
        .also { files[it.name] = bytes }

    /** The bytes of [file] where the staging keeps it now, or null where it is gone or no longer of its size. */
    fun bytesOf(file: StagedFile) = (files[file.name] ?: moved[file.name])?.takeIf { it.size.toLong() == file.size }

    /** Takes [file] out of the folder, as a move into the library does, and answers its bytes; throws where it is gone. */
    fun moveOut(file: StagedFile): ByteArray {
        beforeMove(file)
        val bytes = bytesOf(file) ?: throw IllegalStateException("The staged file \"${file.name}\" is gone.")
        files -= file.name
        moved[file.name] = bytes
        return bytes
    }

    /** [file] with its archive's bytes put into the staging folder, as the intake hands a received archive over. */
    fun received(file: ImportedFile) = ImportedFile(name = file.name, bytes = ByteArray(0), staged = receive(file.bytes))

    /** [file] as an archive of [kind] reaches the preparation: as it is, or [received]. */
    fun arrived(file: ImportedFile, kind: ArchiveKind) = if (kind == ArchiveKind.STAGED) received(file) else file

    /** [files] with every picture and attachment in memory staged instead, as the streaming unpack hands them over. */
    fun staged(files: List<ImportedFile>) = files.map { file ->
        file.copy(
            images = file.images.map { image ->
                val bytes = (image.content as? IncomingContent.Bytes)?.bytes ?: return@map image
                val extension = image.source.substringAfterLast('.', "").lowercase().let { if (it.isEmpty()) ".bin" else ".$it" }
                ImportedImage(source = image.source, content = IncomingContent.Staged(stage(bytes, extension)), problem = image.problem, isAttachment = image.isAttachment)
            },
        )
    }

    override suspend fun deleteOtherSessions() = throw NotImplementedError("Not expected to be called by this test.")

    override suspend fun receiveArchive(announcedSize: Long?) = throw NotImplementedError("Not expected to be called by this test.")

    override suspend fun isIntact(file: StagedFile) = bytesOf(file) != null

    override suspend fun deleteRequestsOf(files: Collection<StagedFile>) {
        settled += files
        if (files.any { it.name.startsWith("$SESSION-$REQUEST-") }) {
            this.files.keys.removeAll { it.startsWith("$SESSION-$REQUEST-") }
            moved.clear()
        }
    }

    private companion object {
        const val SESSION = "0123456789abcdef"
        const val REQUEST = 1
    }
}

/**
 * What the zip reader throws for an archive past 4 GB written without ZIP64, made the one way there is: by reading such
 * an archive, here one described by its end record alone, 5 GiB long.
 */
internal suspend fun wrappedArchiveException(): WrappedArchiveException {
    val endRecord = byteArrayOf(0x50, 0x4B, 5, 6) + ByteArray(18)
    val size = 5L shl 30
    val source = object : ZipSource {
        override val size = size

        override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
            for (index in 0 until length) {
                val at = position + index - (size - endRecord.size)
                buffer[offset + index] = if (at >= 0) endRecord[at.toInt()] else 0
            }
        }
    }
    return assertFailsWith<WrappedArchiveException> { ZipReader.open(source) }
}

/** How an archive's files reach the import: read into memory, or staged on disk by the streaming unpack. */
internal enum class ArchiveKind {
    IN_MEMORY,
    STAGED,
}
