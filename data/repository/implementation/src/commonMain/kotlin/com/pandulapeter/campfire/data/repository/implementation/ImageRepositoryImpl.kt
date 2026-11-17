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

import com.pandulapeter.campfire.chordpro.edit.libraryFilesNamedBy
import com.pandulapeter.campfire.data.formats.image.ImageInfo
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.image.ImageSize
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagedFileMissingException
import com.pandulapeter.campfire.data.model.library.LibraryFileSizes
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.repository.api.EditorDraftRepository
import com.pandulapeter.campfire.data.repository.api.ImageRepository
import com.pandulapeter.campfire.data.repository.api.LibraryImageWriter
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.implementation.base.RepositoryEnvironment
import com.pandulapeter.campfire.data.source.local.api.ImageLocalSource
import com.pandulapeter.campfire.data.source.local.api.ImageShelf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * Pruning follows [CoverArtRepositoryImpl]'s: after each finished read of the library (never a scan's batches, which
 * would prune the pictures of songs not read yet). Unlike a cover, a picture exists nowhere else once deleted here and
 * synced, so a prune deletes only where it is sure: under [keeping], which every write of a picture and the songs naming
 * it holds, from the songs as the repository holds them at that moment, and only once every song file of the folder is
 * among them (one that could not be read may name the picture). A picture sync writes before the song naming it may still
 * be pruned in between; sync's own rules bring it back (`SyncPlanner`), so that costs a download, never the picture.
 */
@Single
internal class ImageRepositoryImpl(
    private val imageLocalSource: ImageLocalSource,
    private val songRepository: SongRepository,
    private val editorDraftRepository: EditorDraftRepository,
    private val environment: RepositoryEnvironment,
) : ImageRepository {

    private val scope = environment.scopeFor("images")
    private val keeping = Mutex()
    private val sizes = mutableMapOf<String, ImageSize>()
    private val sizesMutex = Mutex()
    private val _changes = MutableStateFlow(0L)
    override val changes = _changes.asStateFlow()
    private val _librarySizes = MutableStateFlow<LibraryFileSizes?>(null)
    override val librarySizes = _librarySizes.asStateFlow()

    /** What the editor's open text names, or until it says, what the draft it left behind names. Guarded by [keeping]. */
    private var editorImages = emptySet<String>()
    private var hasEditorReported = false

    /** What an open Attachments sheet picked and has not saved, kept from the draft shelf's tidying. Guarded by [keeping]. */
    private var pickedAttachments = emptySet<String>()

    /** Draft pictures written by this process, which the launch's tidying of the draft shelf must not take. */
    private val draftImagesWrittenHere = mutableSetOf<String>()

    /**
     * The names the songs held at the last prune, null before the first. A prune with the same set is skipped unless a
     * picture may have been left unnamed since: one written to the library, or one the editor let go of.
     */
    private var prunedFor: Set<String>? = null
    private var mayHaveStrays = false
    private val pruneRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            // Null where the draft could not be read: it may name any of the draft's pictures, so they are all kept, for the
            // editor that reads it after all and for the next launch's tidying.
            val draftNames = environment.logger.recovering(describe = { "Could not read the editor's draft: ${it::class.simpleName}" }, fallback = { null }) {
                editorDraftRepository.loadEditorDraft()?.text?.let(::libraryFilesNamedBy).orEmpty()
            }
            keeping.withLock {
                // An editor that has already said what it holds knows better than the draft it was restored from.
                if (!hasEditorReported) editorImages = draftNames.orEmpty()
                if (draftNames != null) tidyDraftShelf(keep = draftNames + editorImages + pickedAttachments)
            }
            launch { songRepository.songs.filterIsInstance<DataState.Idle<*>>().collect { pruneRequests.send(Unit) } }
            launch {
                changes.collectLatest {
                    // An import stores thousands of files inside one kept block, each a change, and a listing stats every
                    // file of the folder (opens it, on the web) without stopping for a newer change: the sizes are listed
                    // once the block lets go, not over and over behind the import.
                    keeping.withLock { }
                    _librarySizes.value = listLibrarySizes()
                }
            }
            for (request in pruneRequests) prune()
        }
    }

    override suspend fun loadImage(name: String): ByteArray? = environment.logger.recovering(
        describe = { "Could not read the picture \"$name\": ${it::class.simpleName}" },
        fallback = { null },
    ) {
        imageLocalSource.readImage(ImageShelf.LIBRARY, name) ?: imageLocalSource.readImage(ImageShelf.DRAFT, name)
    }

    /** Cached by name for good: a name is its content, so what it says about a picture never changes. */
    override suspend fun loadImageSize(name: String): ImageSize? {
        sizesMutex.withLock { sizes[name] }?.let { return it }
        val size = loadImage(name)?.let { bytes -> withContext(environment.computation) { LibraryImages.examine(bytes) } }?.size ?: return null
        sizesMutex.withLock { sizes[name] = size }
        return size
    }

    override suspend fun readImageSize(bytes: ByteArray) = withContext(environment.computation) { LibraryImages.examine(bytes) }.size

    override suspend fun loadStagedImage(file: StagedFile): ByteArray? = if (file.size > ImportLimits.MAX_IMAGE_FILE_SIZE) {
        null
    } else {
        environment.logger.recovering(
            describe = { "Could not read the staged picture \"${file.name}\": ${it::class.simpleName}" },
            fallback = { null },
        ) {
            imageLocalSource.readStagedHead(file, file.size.toInt())
        }
    }

    override suspend fun loadLibraryImageNames() = imageLocalSource.listImageNames(ImageShelf.LIBRARY).sorted()

    override suspend fun loadLibraryFileSizes() = imageLocalSource.listImageSizes(ImageShelf.LIBRARY)

    override suspend fun libraryNameOf(content: IncomingContent) = nameFor(content) { examinePicture(content) }

    override suspend fun libraryNameOfAttachment(content: IncomingContent, extension: String) =
        nameFor(content) { LibraryImages.examineAttachment(content.size, extension) }

    private suspend fun nameFor(content: IncomingContent, examine: suspend () -> LibraryImages.Examined): ImageIntake = withContext(environment.computation) {
        val examined = examine()
        examined.refusal?.let { return@withContext ImageIntake.Refused(it) }
        contentNamesOf(content, examined.extension)?.firstOrNull { name -> !otherBytesUnder(name, content, ImageShelf.LIBRARY) }
            ?.let { ImageIntake.Accepted(it) }
            ?: ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)
    }

    /** [LibraryImages.examine] of [content], a staged picture from its head and size alone, never read whole. */
    private suspend fun examinePicture(content: IncomingContent) = when (content) {
        is IncomingContent.Bytes -> LibraryImages.examine(content.bytes)
        is IncomingContent.Staged -> if (content.size > ImportLimits.MAX_IMAGE_FILE_SIZE) {
            LibraryImages.examine(head = ByteArray(0), size = content.size)
        } else {
            val head = imageLocalSource.readStagedHead(content.file, ImageInfo.MAX_HEADER_BYTES) ?: throw StagedFileMissingException(content.file.name)
            LibraryImages.examine(head = head, size = content.size)
        }
    }

    /**
     * The names [content] may take ([LibraryImages.contentNames]): a staged file's from the SHA-256 its staging took over
     * exactly the bytes it wrote, so it is never read whole for it. Null for a staged file with no hash (an archive), which
     * is nothing a song shows or attaches.
     */
    private suspend fun contentNamesOf(content: IncomingContent, extension: String) = when (content) {
        is IncomingContent.Bytes -> LibraryImages.contentNames(content.bytes, extension)
        is IncomingContent.Staged -> content.file.sha256Hex?.let { LibraryImages.contentNamesOf(it, extension) }
    }

    override suspend fun addDraftImage(bytes: ByteArray): ImageIntake = addDraft(bytes) { LibraryImages.examine(bytes) }

    override suspend fun addDraftAttachment(bytes: ByteArray, extension: String) = addDraft(bytes) { LibraryImages.examineAttachment(bytes, extension) }

    private suspend fun addDraft(bytes: ByteArray, examine: suspend () -> LibraryImages.Examined): ImageIntake = withContext(NonCancellable) {
        val content = IncomingContent.Bytes(bytes)
        keeping.withLock {
            store(content, shelf = ImageShelf.DRAFT, examine = examine, isTaken = { name -> otherBytesUnder(name, content, ImageShelf.LIBRARY, ImageShelf.DRAFT) })
                .also { intake -> if (intake is ImageIntake.Accepted) draftImagesWrittenHere += intake.name }
        }
    }

    override fun holdEditorImages(names: Set<String>) {
        scope.launch {
            val hasReleased = keeping.withLock {
                hasEditorReported = true
                (editorImages - names).isNotEmpty().also { hasReleased ->
                    editorImages = names
                    if (hasReleased) mayHaveStrays = true
                }
            }
            if (hasReleased) pruneRequests.send(Unit)
        }
    }

    override suspend fun holdPickedAttachments(names: Set<String>) = withContext(NonCancellable) {
        keeping.withLock { pickedAttachments = names }
    }

    override suspend fun clearDraftImages() = withContext(NonCancellable) {
        keeping.withLock {
            draftImagesWrittenHere.clear()
            tidyDraftShelf(keep = pickedAttachments)
        }
    }

    override suspend fun <T> withImagesKept(block: suspend (LibraryImageWriter) -> T): T = keeping.withLock {
        try {
            block(writer)
        } finally {
            // The songs the block wrote are in the repository by now, so a prune that finds a picture of it unnamed is
            // looking at a song that was not saved after all.
            if (mayHaveStrays) pruneRequests.trySend(Unit)
        }
    }

    override fun onLibraryImagesChanged() = _changes.update { it + 1 }

    private val writer: LibraryImageWriter = object : LibraryImageWriter {

        /** Read only inside [withImagesKept], under [keeping], which guards [editorImages]. */
        override val heldNames get() = editorImages

        override suspend fun addImage(content: IncomingContent): ImageIntake = addToLibrary(content) { examinePicture(content) }

        override suspend fun addAttachment(content: IncomingContent, extension: String) =
            addToLibrary(content) { LibraryImages.examineAttachment(content.size, extension) }

        private suspend fun addToLibrary(content: IncomingContent, examine: suspend () -> LibraryImages.Examined) = withContext(NonCancellable) {
            store(content, shelf = ImageShelf.LIBRARY, examine = examine, isTaken = { name -> otherBytesUnder(name, content, ImageShelf.LIBRARY) })
        }

        override suspend fun moveDraftImages(names: Set<String>): Map<String, String> = withContext(NonCancellable) {
            val renamed = mutableMapOf<String, String>()
            names.forEach { name ->
                when (val intake = withContext(environment.computation) { copyDraftFile(name) }) {
                    null -> Unit
                    is ImageIntake.Accepted -> if (intake.name != name) renamed[name] = intake.name
                    // Taken in as a draft, so it passes again; were it ever refused, the song shows it as missing.
                    is ImageIntake.Refused -> environment.logger.log("Could not keep the file \"$name\": ${intake.reason}")
                }
            }
            renamed
        }

        /**
         * A draft file put into the library under the content name it was given at intake, checked by hashing it where it
         * lies: a name the library holds with the same bytes needs nothing written, one it holds with other bytes passes to
         * the next content name, as [store] does. Copied rather than moved, never read whole: the editor may still show it
         * from the draft shelf, and its Undo may bring back a line naming it after a later Save let the library prune it.
         * Null where the draft holds no such file.
         */
        private suspend fun copyDraftFile(name: String): ImageIntake? {
            val size = imageLocalSource.imageSize(ImageShelf.DRAFT, name) ?: return null
            val hex = imageLocalSource.hashImage(ImageShelf.DRAFT, name) ?: return null
            if (!LibraryFiles.isLibraryFileNamedFor(name, hex)) {
                // Not what its name says (damaged, or put there by hand): taken in from its bytes, as any file is.
                return imageLocalSource.readImage(ImageShelf.DRAFT, name)?.let { addDraftFile(name, it) }
            }
            for (candidate in LibraryImages.contentNamesOf(hex, "." + name.substringAfterLast('.'))) {
                when (imageLocalSource.imageSize(ImageShelf.LIBRARY, candidate)) {
                    null -> {
                        imageLocalSource.copyImage(from = ImageShelf.DRAFT, name = name, to = ImageShelf.LIBRARY, toName = candidate)
                        mayHaveStrays = true
                        _changes.update { it + 1 }
                        return ImageIntake.Accepted(candidate)
                    }
                    size -> if (imageLocalSource.hashImage(ImageShelf.LIBRARY, candidate) == hex) return ImageIntake.Accepted(candidate)
                }
            }
            return ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)
        }

        /**
         * A draft file stored as what its name says: an attachment keeps the extension it was taken in with, and a name
         * that is a picture's is stored as a picture, or as an attachment where it is none (a picture attached rather than
         * shown may be larger than a picture can be).
         */
        private suspend fun addDraftFile(name: String, bytes: ByteArray): ImageIntake {
            val asPicture = if (LibraryFiles.isLibraryImageName(name)) addImage(bytes) else null
            return if (asPicture is ImageIntake.Accepted) asPicture else addAttachment(bytes, name.substringAfterLast('.'))
        }
    }

    /**
     * Must be called under [keeping]. Examining, hashing, and comparing a file are one hop to [RepositoryEnvironment.computation]:
     * the editor and the Attachments sheet call this from the main thread, which a 50 MB attachment would hold for
     * seconds, long enough that the work bar waiting to appear could not either.
     */
    private suspend fun store(
        content: IncomingContent,
        shelf: ImageShelf,
        examine: suspend () -> LibraryImages.Examined,
        isTaken: suspend (String) -> Boolean,
    ): ImageIntake {
        val (examined, name) = withContext(environment.computation) {
            val examined = examine()
            if (examined.refusal != null) return@withContext examined to null
            // Every name taken by another picture would take 49 pictures sharing a SHA-256 prefix of 64 digits.
            examined to contentNamesOf(content, examined.extension)?.firstOrNull { !isTaken(it) }
        }
        examined.refusal?.let { return ImageIntake.Refused(it) }
        if (name == null) return ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)
        when (content) {
            is IncomingContent.Bytes -> if (!imageLocalSource.hasImage(shelf, name)) {
                imageLocalSource.writeImage(shelf, name, content.bytes)
                onStored(shelf)
            }
            // Never over a file of the name, which holds the same bytes (isTaken said so): its staged copy goes instead.
            is IncomingContent.Staged -> {
                imageLocalSource.moveStagedFile(content.file, shelf, name)
                onStored(shelf)
            }
        }
        examined.size?.let { size -> sizesMutex.withLock { sizes[name] = size } }
        return ImageIntake.Accepted(name)
    }

    private fun onStored(shelf: ImageShelf) {
        if (shelf == ImageShelf.LIBRARY) mayHaveStrays = true
        _changes.update { it + 1 }
    }

    private suspend fun Sequence<String>.firstOrNull(predicate: suspend (String) -> Boolean): String? {
        for (name in this) if (predicate(name)) return name
        return null
    }

    /**
     * Whether a shelf holds different bytes under [name], which makes the name another picture's. A file of another size is
     * that without being read; one of the same size is compared with [content] a slice at a time, neither read whole.
     */
    private suspend fun otherBytesUnder(name: String, content: IncomingContent, vararg shelves: ImageShelf) = shelves.any { shelf ->
        when (imageLocalSource.imageSize(shelf, name)) {
            null -> false
            content.size -> !imageLocalSource.holdsContent(shelf, name, content)
            else -> true
        }
    }

    /** Zero where the folder cannot be listed, so that a failure leaves Settings showing the songs' size rather than none. */
    private suspend fun listLibrarySizes(): LibraryFileSizes {
        val sizes = environment.logger.recovering(describe = { "Could not list the library's files: ${it::class.simpleName}" }, fallback = { emptyMap() }) {
            imageLocalSource.listImageSizes(ImageShelf.LIBRARY)
        }
        val draftSizes = environment.logger.recovering(describe = { "Could not list the draft's files: ${it::class.simpleName}" }, fallback = { emptyMap() }) {
            imageLocalSource.listImageSizes(ImageShelf.DRAFT)
        }
        val (pictures, attachments) = sizes.entries.partition { LibraryFiles.isLibraryImageName(it.key) }
        return LibraryFileSizes(
            picturesSize = pictures.sumOf { it.value },
            attachmentsSize = attachments.sumOf { it.value },
            fileSizes = draftSizes + sizes,
        )
    }

    /** Must be called under [keeping]. */
    private suspend fun tidyDraftShelf(keep: Set<String>) {
        environment.logger.recovering(describe = { "Could not tidy the draft's pictures: ${it::class.simpleName}" }, fallback = {}) {
            imageLocalSource.listImageNames(ImageShelf.DRAFT)
                .filter { it !in keep && it !in draftImagesWrittenHere }
                .forEach { imageLocalSource.deleteImage(ImageShelf.DRAFT, it) }
        }
    }

    private suspend fun prune() = environment.logger.recovering(
        describe = { "Could not prune the library's pictures: ${it::class.simpleName}" },
        fallback = {},
    ) {
        keeping.withLock {
            // The repository's list as it is now, rather than the state that asked for the prune, which may be from before
            // the songs a kept block just wrote.
            val songs = songRepository.loadSongsIfNeeded() ?: return@withLock
            val named = songs.flatMapTo(mutableSetOf()) { it.libraryFileNames }
            if (named == prunedFor && !mayHaveStrays) return@withLock
            val unnamed = imageLocalSource.listImageNames(ImageShelf.LIBRARY).filter { it !in named && it !in editorImages }
            if (unnamed.isNotEmpty()) {
                val read = songs.mapTo(mutableSetOf()) { it.fileName }
                val unread = songRepository.loadSongFileSizes().keys.filter { it !in read }
                if (unread.isNotEmpty()) {
                    environment.logger.log("Kept ${unnamed.size} files no song names: ${unread.size} song files could not be read.")
                    return@withLock
                }
                unnamed.forEach { imageLocalSource.deleteImage(ImageShelf.LIBRARY, it) }
                environment.logger.log("Deleted ${unnamed.size} files no song names.")
                _changes.update { it + 1 }
            }
            prunedFor = named
            mayHaveStrays = false
        }
    }
}
