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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportBookkeeping
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.repository.api.ImportDownloadRepository
import com.pandulapeter.campfire.data.repository.implementation.DataRepositoryModule
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.DataLocalSourceModule
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.remote.implementation.DataRemoteSourceModule
import com.pandulapeter.campfire.data.sync.implementation.DataSyncModule
import com.pandulapeter.campfire.domain.api.importing.ApplyImportUseCase
import com.pandulapeter.campfire.domain.api.importing.DeleteImportStagingUseCase
import com.pandulapeter.campfire.domain.api.importing.PrepareImportUseCase
import com.pandulapeter.campfire.domain.implementation.DomainModule
import kotlinx.coroutines.CancellationException
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.plugin.module.dsl.modules
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals

/**
 * One device with an empty library in a folder of its own, wired as the app wires it (every data module and the domain's
 * use cases over a [JvmFileStorage]), but for the network: a picture an imported song names by its address is answered
 * by [LinkedPictures] rather than downloaded. [import] takes an archive the way a picked file reaches the import, held in
 * memory or received into the staging folder, through the review's preparation and Import with every entry chosen, and
 * then settles it as the app does.
 */
internal class ImportPathApp : AutoCloseable {

    private val root: File = Files.createTempDirectory("campfire-import-path").toFile()

    /**
     * As much free space as a phone often has, whatever this machine has, so that what the streaming path sets aside
     * for an archive is judged against a phone's room, as the import would judge it there.
     */
    private val storage = object : FileStorage by JvmFileStorage(root) {
        override suspend fun availableSpace(directory: StorageDirectory) = PHONE_FREE_SPACE
    }
    private val koinApplication = koinApplication {
        modules(
            DataLocalSourceModule::class,
            DataRemoteSourceModule::class,
            DataRepositoryModule::class,
            DataSyncModule::class,
            DomainModule::class,
            PresentationStandIns::class,
        )
        modules(
            module {
                single<FileStorage> { storage }
                single<ImportDownloadRepository> { LinkedPictures }
                // Every unpack prints what it leaves out; a few thousand imports would bury the test's own report.
                single<Logger> { Logger { } }
            },
        )
    }
    private val koin = koinApplication.koin

    suspend fun import(name: String, archive: ByteArray, isStaged: Boolean): ImportOutcome {
        val staged = if (isStaged) {
            koin.get<ImportStagingLocalSource>().receiveArchive(announcedSize = archive.size.toLong()).run {
                write(archive, 0, archive.size)
                finish()
            }
        } else {
            null
        }
        val file = if (staged == null) ImportedFile(name = name, bytes = archive) else ImportedFile(name = name, bytes = ByteArray(0), staged = staged)
        val outcome = try {
            val plan = koin.get<PrepareImportUseCase>()(listOf(file))
            val result = koin.get<ApplyImportUseCase>()(plan = plan, choices = ImportChoices(), bookkeeping = ImportBookkeeping())
            ImportOutcome(plan = plan, result = result, library = library(), failure = null)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ImportOutcome(plan = null, result = null, library = library(), failure = exception::class.simpleName + ": " + exception.message)
        }
        koin.get<DeleteImportStagingUseCase>()(listOfNotNull(staged))
        assertEquals(emptyList(), storage.listNames(StorageDirectory.IMPORTS), "The staging folder after importing $name settled")
        return outcome
    }

    /** Every file under `library/` by its path, as the SHA-256 of its bytes. */
    private fun library(): Map<String, String> {
        val library = File(root, "library")
        return library.walkTopDown().filter { it.isFile }.associate { it.relativeTo(library).invariantSeparatorsPath to Sha256.hashToHex(it.readBytes()) }
    }

    /** The text of the library file at [path] (relative to `library/`), for a difference to be explained by. */
    fun libraryText(path: String) = File(File(root, "library"), path).readText()

    override fun close() {
        koinApplication.close()
        root.deleteRecursively()
    }

    private companion object {
        const val PHONE_FREE_SPACE = 1L shl 30
    }

    /**
     * Each address answered with a small picture of its own, the same for both paths, so that the pictures songs name by
     * address are planned and written as the app would plan them, and charged against the same budgets.
     */
    private object LinkedPictures : ImportDownloadRepository {

        override suspend fun download(url: String, onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit): ImportDownload =
            throw NotImplementedError("An import of a file never downloads one.")

        override suspend fun downloadPicture(url: String, maxSizeBytes: Long) =
            ImportDownload.File(name = url.substringAfterLast('/'), bytes = TestPictures.png(seed = url.hashCode(), size = 2_000))
    }
}
