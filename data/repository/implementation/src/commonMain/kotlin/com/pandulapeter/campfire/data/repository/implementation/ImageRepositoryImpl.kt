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

import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.image.ImageSize
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

    /** What the editor's open text names, or until it says, what the draft it left behind names. Guarded by [keeping]. */
    private var editorImages = emptySet<String>()
    private var hasEditorReported = false

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
            val draftNames = environment.logger.recovering(describe = { "Could not read the editor's draft: ${it::class.simpleName}" }, fallback = { null }) {
                editorDraftRepository.loadEditorDraft()?.text?.let(ChordProImages::referencedSources)
            }.orEmpty()
            keeping.withLock {
                // An editor that has already said what it holds knows better than the draft it was restored from.
                if (!hasEditorReported) editorImages = draftNames
                tidyDraftShelf(keep = draftNames + editorImages)
            }
            launch { songRepository.songs.filterIsInstance<DataState.Idle<*>>().collect { pruneRequests.send(Unit) } }
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

    override suspend fun loadLibraryImageNames() = imageLocalSource.listImageNames(ImageShelf.LIBRARY).sorted()

    override suspend fun libraryNameOf(bytes: ByteArray): ImageIntake {
        val examined = withContext(environment.computation) { LibraryImages.examine(bytes) }
        examined.refusal?.let { return ImageIntake.Refused(it) }
        return LibraryImages.contentNames(bytes, examined.extension).firstOrNull { name -> !otherBytesUnder(name, bytes, ImageShelf.LIBRARY) }
            ?.let { ImageIntake.Accepted(it) }
            ?: ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)
    }

    override suspend fun addDraftImage(bytes: ByteArray): ImageIntake = withContext(NonCancellable) {
        keeping.withLock {
            store(bytes, shelf = ImageShelf.DRAFT, isTaken = { name -> otherBytesUnder(name, bytes, ImageShelf.LIBRARY, ImageShelf.DRAFT) })
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

    override suspend fun clearDraftImages() = withContext(NonCancellable) {
        keeping.withLock {
            draftImagesWrittenHere.clear()
            tidyDraftShelf(keep = emptySet())
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

        override suspend fun addImage(bytes: ByteArray): ImageIntake = withContext(NonCancellable) {
            store(bytes, shelf = ImageShelf.LIBRARY, isTaken = { name -> otherBytesUnder(name, bytes, ImageShelf.LIBRARY) })
        }

        override suspend fun moveDraftImages(names: Set<String>): Map<String, String> = withContext(NonCancellable) {
            val renamed = mutableMapOf<String, String>()
            names.forEach { name ->
                val bytes = imageLocalSource.readImage(ImageShelf.DRAFT, name) ?: return@forEach
                when (val intake = addImage(bytes)) {
                    is ImageIntake.Accepted -> if (intake.name != name) renamed[name] = intake.name
                    // Taken in as a draft, so it passes again; were it ever refused, the song shows it as missing.
                    is ImageIntake.Refused -> environment.logger.log("Could not keep the picture \"$name\": ${intake.reason}")
                }
            }
            renamed
        }
    }

    /** Must be called under [keeping]. */
    private suspend fun store(bytes: ByteArray, shelf: ImageShelf, isTaken: suspend (String) -> Boolean): ImageIntake {
        val examined = withContext(environment.computation) { LibraryImages.examine(bytes) }
        val refusal = examined.refusal
        if (refusal != null) return ImageIntake.Refused(refusal)
        // Every name taken by another picture would take 49 pictures sharing a SHA-256 prefix of 64 digits.
        val name = LibraryImages.contentNames(bytes, examined.extension).firstOrNull { !isTaken(it) } ?: return ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)
        if (!imageLocalSource.hasImage(shelf, name)) {
            imageLocalSource.writeImage(shelf, name, bytes)
            if (shelf == ImageShelf.LIBRARY) mayHaveStrays = true
            _changes.update { it + 1 }
        }
        examined.size?.let { size -> sizesMutex.withLock { sizes[name] = size } }
        return ImageIntake.Accepted(name)
    }

    private suspend fun Sequence<String>.firstOrNull(predicate: suspend (String) -> Boolean): String? {
        for (name in this) if (predicate(name)) return name
        return null
    }

    /** Whether a shelf holds different bytes under [name], which makes the name another picture's. */
    private suspend fun otherBytesUnder(name: String, bytes: ByteArray, vararg shelves: ImageShelf) = shelves.any { shelf ->
        imageLocalSource.readImage(shelf, name)?.contentEquals(bytes) == false
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
            val named = songs.flatMapTo(mutableSetOf()) { it.imageNames }
            if (named == prunedFor && !mayHaveStrays) return@withLock
            val unnamed = imageLocalSource.listImageNames(ImageShelf.LIBRARY).filter { it !in named && it !in editorImages }
            if (unnamed.isNotEmpty()) {
                val read = songs.mapTo(mutableSetOf()) { it.fileName }
                val unread = songRepository.loadSongFileSizes().keys.filter { it !in read }
                if (unread.isNotEmpty()) {
                    environment.logger.log("Kept ${unnamed.size} pictures no song names: ${unread.size} song files could not be read.")
                    return@withLock
                }
                unnamed.forEach { imageLocalSource.deleteImage(ImageShelf.LIBRARY, it) }
                environment.logger.log("Deleted ${unnamed.size} pictures no song names.")
                _changes.update { it + 1 }
            }
            prunedFor = named
            mayHaveStrays = false
        }
    }
}
