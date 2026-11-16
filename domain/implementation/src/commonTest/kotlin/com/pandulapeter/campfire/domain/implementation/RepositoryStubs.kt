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

import com.pandulapeter.campfire.data.model.song.EditedSongs
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.image.ImageSize
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.UnpackedArchive
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportFileWriter
import com.pandulapeter.campfire.data.model.library.ExportSink
import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.library.PackedArchive
import com.pandulapeter.campfire.data.model.setlist.ParsedSetlist
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.repository.api.ArchiveRepository
import com.pandulapeter.campfire.data.repository.api.ExportFileRepository
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SharedLinksRepository
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * What every member of a stub does until a test overrides it. An [Error] rather than an exception, because the use
 * cases catch exceptions where a repository may fail, and a call no test expected must not pass for one of those.
 */
private fun unexpected(): Nothing = throw NotImplementedError("Not expected to be called by this test.")

/** A [SongRepository] whose every member fails the same way, for a test to override only what it expects to be called. */
internal open class SongRepositoryStub : SongRepository {
    override val songs: Flow<DataState<List<Song>>> get() = unexpected()
    override suspend fun loadSongsIfNeeded(): List<Song>? = unexpected()
    override suspend fun loadSongFileSizes(): Map<String, Long> = unexpected()
    override suspend fun rescan(): Unit = unexpected()
    override suspend fun refresh(fileNames: Set<String>): Unit = unexpected()
    override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean = unexpected()
    override suspend fun createSong(title: String, artist: String, text: String): Song = unexpected()
    override fun importFileName(fallbackTitle: String, text: String): String = unexpected()
    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean, takenNames: Set<String>?): Song = unexpected()
    override suspend fun adoptImported(songs: Collection<Song>): Unit = unexpected()
    override suspend fun copySongToItsName(song: Song): Song? = unexpected()
    override suspend fun finishSongRename(fileName: String, renamed: Song, shouldKeepOldFile: Boolean): Unit = unexpected()
    override suspend fun deleteSong(fileName: String): Unit = unexpected()
    override suspend fun deleteAllSongs(): Unit = unexpected()

    override suspend fun editSongs(fileNames: Collection<String>, edit: (text: String) -> String): EditedSongs =
        unexpected()
}

/** Keeps the text and the name of the last song it was asked to create, failing every other call as its stub does. */
internal class RecordingSongRepository : SongRepositoryStub() {
    var text = ""
    var name = "" to ""

    override suspend fun createSong(title: String, artist: String, text: String): Song {
        this.text = text
        name = title to artist
        return testSong(fileName = "song.cho", title = title, artist = artist)
    }
}

/** A [SetlistRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class SetlistRepositoryStub : SetlistRepository {
    override val setlists: Flow<DataState<List<Setlist>>> get() = unexpected()
    override suspend fun loadSetlistsIfNeeded(): List<Setlist>? = unexpected()
    override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> = unexpected()
    override suspend fun rescan(): Unit = unexpected()
    override suspend fun refresh(fileNames: Set<String>): Unit = unexpected()
    override suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist = unexpected()
    override suspend fun saveSetlist(setlist: Setlist): Unit = unexpected()
    override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? = unexpected()
    override suspend fun renameSetlist(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist? =
        unexpected()
    override suspend fun parseSetlist(document: String): ParsedSetlist? = unexpected()
    override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist = unexpected()
    override suspend fun adoptImported(setlists: Collection<Setlist>): Unit = unexpected()
    override suspend fun loadSetlistFileSizes(): Map<String, Long> = unexpected()
    override suspend fun loadSetlistDocument(fileName: String): String? = unexpected()
    override suspend fun deleteSetlist(fileName: String): Unit = unexpected()
    override suspend fun deleteAllSetlists(): Unit = unexpected()
}

/** A [SongContentRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class SongContentRepositoryStub : SongContentRepository {
    override val invalidations: Flow<Long> get() = unexpected()
    override suspend fun loadSongContent(fileName: String, useCache: Boolean): SongContent? = unexpected()
    override suspend fun invalidate(fileName: String?): Unit = unexpected()
    override suspend fun invalidate(fileNames: Set<String>): Unit = unexpected()
}

/** A [UserPreferencesRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class UserPreferencesRepositoryStub : UserPreferencesRepository {
    override val userPreferences: Flow<DataState<UserPreferences>> get() = unexpected()
    override suspend fun loadUserPreferencesIfNeeded(): UserPreferences? = unexpected()
    override suspend fun saveUserPreferences(userPreferences: UserPreferences): Unit = unexpected()
    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): Unit = unexpected()
    override suspend fun hasStoredUserPreferences(): Boolean = unexpected()
}

/** A [SyncRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class SyncRepositoryStub : SyncRepository {
    override val syncState: Flow<SyncState> get() = unexpected()
    override val availableProviders: List<SyncProviderId> get() = unexpected()
    override suspend fun restore(onReturnedFromAuthorization: () -> Unit): SyncRepository.RestoreResult = unexpected()
    override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage): Boolean = unexpected()
    override suspend fun cancelConnection(): Unit = unexpected()
    override suspend fun disconnect(): Unit = unexpected()
    override suspend fun forgetStoredConnection(): Unit = unexpected()
    override fun synchronize(deletionPolicy: SyncDeletionPolicy): Boolean = unexpected()
    override fun scheduleSynchronization(): Unit = unexpected()
    override fun synchronizeOnReturn(): Unit = unexpected()
    override suspend fun watchRemoteChanges(): Unit = unexpected()
    override fun startScheduledSynchronization(): SyncProgress? = unexpected()
    override fun cancelSynchronization(): Unit = unexpected()
}

/** A [SharedLinksRepository] whose every member fails the same way, see [SongRepositoryStub]. */
internal open class SharedLinksRepositoryStub : SharedLinksRepository {
    override val shares: Flow<List<Share>> get() = unexpected()
    override val hasPendingStops: Flow<Boolean> get() = unexpected()
    override suspend fun refresh(): SharingFailure? = unexpected()
    override suspend fun share(kind: SharedLinkKind, fileName: String, title: String, copy: ByteArray): CreateShareResult = unexpected()
    override suspend fun stopSharing(shareId: String): SharingFailure? = unexpected()
    override suspend fun followRename(kind: SharedLinkKind, fileName: String, newFileName: String): Unit = unexpected()
    override suspend fun stopSharingDeleted(kind: SharedLinkKind, fileName: String): Unit = unexpected()
    override suspend fun stopSharingEverything(): Unit = unexpected()
    override suspend fun stopSharingEverythingNow(): SharingFailure? = unexpected()
}

/** Records the renames and deletions the use cases tell the shares about, in order, as text. */
internal class RecordingSharedLinksRepository(
    private val events: MutableList<String> = mutableListOf(),
) : SharedLinksRepositoryStub() {

    val noted: List<String> get() = events

    override suspend fun followRename(kind: SharedLinkKind, fileName: String, newFileName: String) {
        events += "followRename(${kind.id}, $fileName, $newFileName)"
    }

    override suspend fun stopSharingDeleted(kind: SharedLinkKind, fileName: String) {
        events += "stopSharingDeleted(${kind.id}, $fileName)"
    }

    override suspend fun stopSharingEverything() {
        events += "stopSharingEverything"
    }
}

/**
 * Keeps the files of the last archive it packed, and unpacks every archive into what [unpack] answers - nothing,
 * unless a test says otherwise.
 */
internal class FakeArchiveRepository(
    private val songsIn: suspend (file: ImportedFile) -> List<ImportedFile>? = { null },
    private val unpack: suspend (archive: ByteArray, maxSize: Long) -> List<ImportedFile> = { _, _ -> emptyList() },
) : ArchiveRepository {

    var packed: Map<String, ByteArray> = emptyMap()

    /** Charges the picture budget with every distinct picture the files bring, as the real one charges each entry once. */
    override suspend fun unpack(archive: ByteArray, maxSize: Long, maxImageSize: Long) = unpack.invoke(archive, maxSize).let { files ->
        UnpackedArchive(files = files, imageSize = files.flatMap { it.images }.mapNotNull { it.bytes }.toSet().sumOf { it.size.toLong() })
    }

    override suspend fun songsIn(file: ImportedFile) = songsIn.invoke(file)

    override suspend fun pack(files: Map<String, ByteArray>, isDated: Boolean): ByteArray {
        packed = files
        return ByteArray(0)
    }

    /** Reads every entry in turn into [packed], and writes one byte per entry, so the archive has a size. */
    override suspend fun pack(entries: List<suspend () -> ArchiveEntry?>, isDated: Boolean, sink: ExportSink): PackedArchive {
        packed = buildMap { entries.forEach { read -> read()?.let { put(it.name, it.bytes) } } }
        packed.keys.forEach { _ -> sink.write(byteArrayOf(0), 0, 1) }
        return PackedArchive(entryCount = packed.size, unpackedSize = packed.values.sumOf { it.size.toLong() })
    }
}

/** Keeps the files in memory, by name: what was finished in [files], what was removed in [discarded]. */
internal class FakeExportFileRepository : ExportFileRepository {

    val files = mutableMapOf<String, ByteArray>()
    val discarded = mutableListOf<String>()

    override suspend fun clear() = files.clear()

    override suspend fun create(name: String): ExportFileWriter = object : ExportFileWriter {
        private var bytes = ByteArray(0)

        override val position get() = bytes.size.toLong()

        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
            this.bytes += bytes.copyOfRange(offset, offset + length)
        }

        override suspend fun finish(): ExportedContent.TemporaryFile {
            files[name] = bytes
            return ExportedContent.TemporaryFile(path = "/exports/$name", size = position)
        }

        override suspend fun abandon() {
            discarded += name
        }
    }

    override suspend fun read(file: ExportedFile) = when (val content = file.content) {
        is ExportedContent.Bytes -> content.bytes
        is ExportedContent.TemporaryFile -> files[file.name]
    }

    override suspend fun discard(file: ExportedFile) {
        files -= file.name
        discarded += file.name
    }
}

/**
 * The library's pictures in memory, named as the real repository names them in shape: [PNG] bytes are a picture, named by
 * a hash of their content and `.png`; [HEIC] bytes a picture of a format the library refuses; anything else no picture.
 * A test that puts other bytes under a name in [library] makes it taken, and the picture then takes one digit more, as a
 * SHA-256 prefix would.
 */
internal class FakeImageRepository(val library: MutableMap<String, ByteArray> = mutableMapOf()) : ImageRepository {

    /** Every picture [LibraryImageWriter.addImage] was handed, in order, and whether it ran inside [withImagesKept]. */
    val added = mutableListOf<String>()
    var isKeeping = false
        private set

    override val changes: StateFlow<Long> = MutableStateFlow(0L)

    override suspend fun loadImage(name: String) = library[name]

    override suspend fun loadImageSize(name: String): ImageSize? = unexpected()

    override suspend fun loadLibraryImageNames() = library.keys.sorted()

    override suspend fun libraryNameOf(bytes: ByteArray): ImageIntake = when {
        bytes.size > ImportLimits.MAX_IMAGE_FILE_SIZE -> ImageIntake.Refused(ImageRefusal.TOO_LARGE)
        bytes.startsWith(HEIC) -> ImageIntake.Refused(ImageRefusal.UNSUPPORTED_FORMAT)
        !bytes.startsWith(PNG) -> ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)
        else -> {
            val hex = bytes.contentHashCode().toUInt().toString(16).padStart(8, '0').repeat(8)
            (16..64).asSequence().map { hex.take(it) + ".png" }.first { name -> library[name]?.contentEquals(bytes) != false }
                .let { ImageIntake.Accepted(it) }
        }
    }

    /** Puts [bytes] into the library, as an earlier Save would have, and answers its name. */
    suspend fun store(bytes: ByteArray) = (libraryNameOf(bytes) as ImageIntake.Accepted).name.also { library[it] = bytes }

    override suspend fun addDraftImage(bytes: ByteArray): ImageIntake = unexpected()

    override fun holdEditorImages(names: Set<String>): Unit = unexpected()

    override suspend fun clearDraftImages(): Unit = unexpected()

    override suspend fun <T> withImagesKept(block: suspend (LibraryImageWriter) -> T): T {
        isKeeping = true
        return try {
            block(object : LibraryImageWriter {
                override suspend fun addImage(bytes: ByteArray) = libraryNameOf(bytes).also { intake ->
                    check(isKeeping)
                    if (intake is ImageIntake.Accepted) {
                        added += intake.name
                        library[intake.name] = bytes
                    }
                }

                override suspend fun moveDraftImages(names: Set<String>): Map<String, String> = unexpected()
            })
        } finally {
            isKeeping = false
        }
    }

    override fun onLibraryImagesChanged(): Unit = unexpected()

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    companion object {
        val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val HEIC = "HEIC".encodeToByteArray()

        /** A picture of the fake's, different for every [seed]. */
        fun picture(seed: Int) = PNG + byteArrayOf(seed.toByte(), (seed shr 8).toByte(), 1, 2, 3)
    }
}
