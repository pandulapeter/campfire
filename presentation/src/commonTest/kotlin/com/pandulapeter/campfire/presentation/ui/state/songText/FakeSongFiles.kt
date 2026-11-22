/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.song.SongContent
import com.pandulapeter.campfire.domain.api.images.PictureDownload
import com.pandulapeter.campfire.domain.api.images.SaveSongImageResult
import com.pandulapeter.campfire.domain.api.images.SaveSongImageUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.songs.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.songs.SaveSongContentUseCase
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.pictures.PendingMissingImagePick
import com.pandulapeter.campfire.presentation.ui.pictures.PictureFitter
import com.pandulapeter.campfire.presentation.ui.pictures.PictureLinkDownloader
import com.pandulapeter.campfire.presentation.ui.pictures.PictureWork
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The song files a [SongTextStore] reads and writes, by name, refusing a write whose expected text the file no longer
 * holds as the repository does.
 *
 * @param changeBeforeEveryWrite Stands in for another writer that gets to the file between every read and write.
 *
 * Pictures are stored by [SaveSongImageUseCase] under [STORED_IMAGE_NAME], an empty file refused as no picture, and the
 * texts handed on for their pictures' sizes are kept in [textsReadForImages]. A picture of more than [MAX_IMAGE_BYTES] is
 * too large, and made to fit by [reencode], whose copy is the [STORED_IMAGE_NAME] picture's bytes. A picture link answers
 * from [downloads] by its address, and any other address is unreachable.
 */
internal class FakeSongFiles(
    initial: Map<String, String> = emptyMap(),
    private val changeBeforeEveryWrite: ((String) -> String)? = null,
) : GetSongContentUseCase,
    SaveSongContentUseCase,
    GetSongContentInvalidationsUseCase,
    SaveSongImageUseCase {

    val files = initial.toMutableMap()

    /** What downloading each picture link comes to. */
    val downloads = mutableMapOf<String, PictureDownload>()

    /** Every text that reached a file. */
    val writes = mutableListOf<String>()

    override suspend fun invoke(fileName: String) = files[fileName]?.let { SongContent(fileName = fileName, text = it) }

    override suspend fun invoke(content: SongContent, expectedText: String?): Boolean {
        changeBeforeEveryWrite?.let { change -> files[content.fileName]?.let { files[content.fileName] = change(it) } }
        if (expectedText != null && files[content.fileName] != expectedText) return false
        files[content.fileName] = content.text
        writes += content.text
        return true
    }

    override fun invoke() = MutableStateFlow(0L)

    val textsReadForImages = mutableListOf<String>()

    /** The platform's re-encoder, which shrinks a picture to its first byte unless a test says it fails. */
    var reencode: (ByteArray) -> ByteArray? = { bytes -> byteArrayOf(bytes.first()) }

    /** What storing a picture throws, where a test says it fails. */
    var imageSaveFailure: Exception? = null

    override suspend fun invoke(fileName: String, expectedText: String, missingName: String, bytes: ByteArray) = when {
        imageSaveFailure != null -> throw requireNotNull(imageSaveFailure)
        bytes.isEmpty() -> SaveSongImageResult.Refused(ImageRefusal.NOT_AN_IMAGE)
        else -> {
            val text = ChordProImages.withSourcesRewritten(expectedText) { source -> STORED_IMAGE_NAME.takeIf { source == missingName } }
            if (invoke(SongContent(fileName = fileName, text = text), expectedText)) SaveSongImageResult.Added(text) else SaveSongImageResult.SongChanged
        }
    }

    fun songTextStore(scope: CoroutineScope, messageSink: MessageSink, savedStateHandle: SavedStateHandle = SavedStateHandle()) = SongTextStore(
        scope = scope,
        backStack = emptyList(),
        editorDraftFileName = { null },
        messageSink = messageSink,
        getSongContent = this,
        saveSongContent = this,
        getSongContentInvalidations = this,
        inferredKeys = InferredSongKeys(inferKey = { null }, dispatcher = Dispatchers.Unconfined),
        readImageSizes = { textsReadForImages += it },
        imageAdding = SongImageAdding(
            fitter = PictureFitter(
                examine = { bytes ->
                    when {
                        bytes.isEmpty() -> ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE)
                        bytes.size > MAX_IMAGE_BYTES -> ImageIntake.Refused(ImageRefusal.TOO_LARGE)
                        else -> ImageIntake.Accepted(STORED_IMAGE_NAME)
                    }
                },
                reencode = { bytes, _, _ -> reencode(bytes) },
                work = PictureWork(),
            ),
            saveSongImage = this,
            pendingPick = PendingMissingImagePick(SavedStateStore(savedStateHandle, scope)),
            linkDownloader = PictureLinkDownloader(
                download = { url -> downloads[url] ?: PictureDownload.Unreachable },
                work = PictureWork(),
                messageSink = messageSink,
            ),
        ),
        addOnBackStackChanged = {},
        currentSongFileName = { null },
    )

    companion object {
        const val STORED_IMAGE_NAME = "3f9a0c1d2e4b5a69.jpg"
        const val MAX_IMAGE_BYTES = 3
    }
}
