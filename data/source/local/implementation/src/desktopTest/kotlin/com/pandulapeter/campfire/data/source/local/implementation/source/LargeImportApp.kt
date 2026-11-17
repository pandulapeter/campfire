/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.importing.ImportBookkeeping
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.importing.ImportResult
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.domain.api.export.ExportLibraryUseCase
import com.pandulapeter.campfire.domain.api.importing.ApplyImportUseCase
import com.pandulapeter.campfire.domain.api.importing.ClearImportStagingUseCase
import com.pandulapeter.campfire.domain.api.importing.DeleteImportStagingUseCase
import com.pandulapeter.campfire.domain.api.importing.PrepareImportUseCase
import java.io.File
import java.security.MessageDigest
import kotlin.test.fail

/**
 * One process of the app over [storage], wired as the app wires it ([importGraph]), driving the staged import as
 * `ImportWork` does: an archive [receive]d into the staging folder a slice at a time, its preparation, Import, and the
 * settling that deletes what the request staged. A second instance over the same storage is the app started again.
 */
internal class LargeImportApp(val storage: MeteredFileStorage) : AutoCloseable {

    private val koinApplication = importGraph(storage = storage, downloads = NoDownloads)
    private val koin = koinApplication.koin
    private val staging = koin.get<ImportStaging>()

    /**
     * Copies [file] into the staging folder as an intake does, a slice at a time with its size announced, abandoning the
     * copy however it fails.
     */
    suspend fun receive(file: File): StagedFile {
        val received = koin.get<ImportStagingLocalSource>().receiveArchive(announcedSize = file.length())
        try {
            file.inputStream().use { input ->
                val slice = ByteArray(RECEIVE_SLICE_SIZE)
                while (true) {
                    val read = input.read(slice).takeIf { it >= 0 } ?: break
                    received.write(slice, 0, read)
                }
            }
            return received.finish()
        } catch (exception: Throwable) {
            received.abandon()
            throw exception
        }
    }

    suspend fun prepare(archive: StagedFile, name: String = "library.zip"): ImportPlan =
        koin.get<PrepareImportUseCase>()(listOf(ImportedFile(name = name, bytes = ByteArray(0), staged = archive)))

    suspend fun apply(plan: ImportPlan): ImportResult = koin.get<ApplyImportUseCase>()(plan = plan, choices = ImportChoices(), bookkeeping = ImportBookkeeping())

    /** What `ImportWork.settle` deletes once a request is over: the received archive's request and every plan's files. */
    suspend fun settle(archive: StagedFile, plan: ImportPlan? = null) = koin.get<DeleteImportStagingUseCase>()(listOf(archive) + plan?.stagedFiles.orEmpty())

    /** Receives, prepares, imports, and settles [file], as one import of a picked archive goes. */
    suspend fun import(file: File): ImportResult {
        val archive = receive(file)
        var plan: ImportPlan? = null
        try {
            plan = prepare(archive, file.name)
            return apply(plan)
        } finally {
            settle(archive, plan)
        }
    }

    /** What the launch deletes: an earlier process's staged files. */
    suspend fun clearStaging() = koin.get<ClearImportStagingUseCase>()()

    suspend fun exportLibrary(): File {
        val result = koin.get<ExportLibraryUseCase>()() ?: fail("The library did not export")
        val content = result.file.content as? ExportedContent.TemporaryFile ?: fail("The export was held in memory")
        if (result.skippedFileNames.isNotEmpty()) fail("The export left out ${result.skippedFileNames}")
        return File(content.path)
    }

    /**
     * What every request with a file in the staging folder has reserved of the disk budget: what the staging folder may
     * hold. Asked of the request numbers the files carry, which is all the ledger is keyed by.
     */
    suspend fun stagingReserved(): Long = storage.stagingRequests.sumOf { staging.ledger.reservedBy(it) }

    /** What the ledger holds for every request, written or not: nothing once every request has settled. */
    suspend fun ledgerOutstanding() = staging.ledger.outstanding()

    /** Every file under `library/` by its path, as the SHA-256 of its bytes, read a slice at a time. */
    fun library(): Map<String, String> = libraryOf(storage.root)

    override fun close() = koinApplication.close()

    private object NoDownloads : ImportDownloadRepository {

        override suspend fun download(url: String, onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit): ImportDownload =
            throw NotImplementedError("An import of a file never downloads one.")

        override suspend fun downloadPicture(url: String, maxSizeBytes: Long): ImportDownload = ImportDownload.Unreachable
    }

    companion object {

        private const val RECEIVE_SLICE_SIZE = 1024 * 1024

        /** Every file under [root]'s `library/` by its path, as the SHA-256 of its bytes, read a slice at a time. */
        fun libraryOf(root: File): Map<String, String> {
            val library = File(root, "library")
            return library.walkTopDown().filter { it.isFile }.associate { it.relativeTo(library).invariantSeparatorsPath to sha256Of(it) }
        }

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val slice = ByteArray(RECEIVE_SLICE_SIZE)
                while (true) {
                    val read = input.read(slice).takeIf { it >= 0 } ?: break
                    digest.update(slice, 0, read)
                }
            }
            return digest.digest().toHex()
        }

        fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}

/** Every picture and attachment [ImportPlan.songs] bring from the staging folder. */
internal val ImportPlan.stagedFiles get() = songs.flatMap { it.images }.mapNotNull { (it.content as? IncomingContent.Staged)?.file }
