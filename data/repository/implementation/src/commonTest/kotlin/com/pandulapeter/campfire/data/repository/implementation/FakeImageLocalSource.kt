/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.ImageLocalSource
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.ContinuationInterceptor

/**
 * Both shelves in memory, holding anything a test puts there and listing only what the real source lists, and the staged
 * files by name in [staged], which a move takes out of it (a second move of one copies from where the first put it).
 */
internal class FakeImageLocalSource : ImageLocalSource {

    val shelves = ImageShelf.entries.associateWith { mutableMapOf<String, ByteArray>() }
    val staged = mutableMapOf<String, ByteArray>()
    private val moved = mutableMapOf<String, Pair<ImageShelf, String>>()

    /** Puts [bytes] into [staged] as the staging would name them, and answers the staged file. */
    fun stage(bytes: ByteArray, extension: String = ".bin") =
        StagedFile(name = "session-1-${Sha256.hashToHex(bytes)}$extension", size = bytes.size.toLong(), sha256Hex = Sha256.hashToHex(bytes))
            .also { staged[it.name] = bytes }

    private fun bytesOf(file: StagedFile) = (moved[file.name]?.let { (shelf, name) -> shelves.getValue(shelf)[name] } ?: staged[file.name])
        ?.takeIf { it.size.toLong() == file.size }

    /** The dispatcher of every read made of a file that was there, which is how a name taken is compared. */
    val comparingReads = mutableListOf<ContinuationInterceptor?>()

    override suspend fun readImage(shelf: ImageShelf, name: String) = shelves.getValue(shelf)[name]
        ?.also { comparingReads += currentCoroutineContext()[ContinuationInterceptor] }

    override suspend fun hasImage(shelf: ImageShelf, name: String) = name in shelves.getValue(shelf)

    override suspend fun imageSize(shelf: ImageShelf, name: String) = shelves.getValue(shelf)[name]?.size?.toLong()

    override suspend fun hashImage(shelf: ImageShelf, name: String) = shelves.getValue(shelf)[name]?.let(Sha256::hashToHex)

    override suspend fun copyImage(from: ImageShelf, name: String, to: ImageShelf, toName: String) {
        shelves.getValue(to)[toName] = checkNotNull(shelves.getValue(from)[name]) { "$name is not there to be copied." }.copyOf()
    }

    override suspend fun writeImage(shelf: ImageShelf, name: String, bytes: ByteArray) {
        shelves.getValue(shelf)[name] = bytes
    }

    override suspend fun holdsContent(shelf: ImageShelf, name: String, content: IncomingContent): Boolean {
        val held = shelves.getValue(shelf)[name] ?: return false
        if (held.size.toLong() != content.size) return false
        comparingReads += currentCoroutineContext()[ContinuationInterceptor]
        return when (content) {
            is IncomingContent.Bytes -> held.contentEquals(content.bytes)
            is IncomingContent.Staged -> held.contentEquals(bytesOf(content.file) ?: throw LibraryStorageException("${content.file.name} is gone."))
        }
    }

    override suspend fun readStagedHead(staged: StagedFile, length: Int) = bytesOf(staged)?.let { it.copyOf(minOf(length, it.size)) }

    override suspend fun moveStagedFile(staged: StagedFile, shelf: ImageShelf, name: String) {
        val bytes = bytesOf(staged) ?: throw LibraryStorageException("${staged.name} is gone.")
        shelves.getValue(shelf).getOrPut(name) { bytes }
        this.staged -= staged.name
        if (staged.name !in moved) moved[staged.name] = shelf to name
    }

    override suspend fun listImageNames(shelf: ImageShelf) = shelves.getValue(shelf).keys.filter(LibraryFiles::isLibraryFileName)

    /** How often the library shelf's sizes were listed: a listing stats every file of the folder. */
    var librarySizeListings = 0

    override suspend fun listImageSizes(shelf: ImageShelf): Map<String, Long> {
        if (shelf == ImageShelf.LIBRARY) librarySizeListings++
        return listImageNames(shelf).associateWith { shelves.getValue(shelf).getValue(it).size.toLong() }
    }

    override suspend fun deleteImage(shelf: ImageShelf, name: String) {
        shelves.getValue(shelf).remove(name)
    }
}
