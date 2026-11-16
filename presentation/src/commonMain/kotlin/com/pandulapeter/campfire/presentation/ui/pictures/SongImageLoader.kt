/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.edit.ChordProImages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where the pictures a song names (`{image: src=…}`) come from for whoever draws the song: the library's for a song of
 * the library ([LibrarySongImages]), which is what [LocalSongImageLoader] holds unless a screen showing pictures from
 * elsewhere (an editor's unsaved ones, an archive's under review) provides its own.
 *
 * **Sizes come before the picture**: [sizes] holds what each picture read so far is, from its header alone, and a song is
 * laid out from it, so that a picture decoded later moves nothing. Whoever hands a text to the song view first awaits
 * [loadSizes] for the names it holds ([loadSizesOf]); a name [sizes] has no entry for yet is drawn at a placeholder's room.
 */
@Stable
internal interface SongImageLoader {

    /**
     * Tells this loader's pictures apart from another's in the decoded pictures kept in memory: names are pictures'
     * contents in the library, but not in an archive under review.
     */
    val cacheKey: String

    /** What every picture asked about so far is, by the name a song names it by. */
    val sizes: StateFlow<Map<String, SongImageSize>>

    /** Reads the size of every one of [names] [sizes] does not hold yet, returning once [sizes] holds them all. */
    suspend fun loadSizes(names: Set<String>)

    /** The bytes of the picture called [name], null where there is none or it cannot be read. */
    suspend fun loadImage(name: String): ByteArray?
}

/** [SongImageLoader.loadSizes] for every picture [texts] name. */
internal suspend fun SongImageLoader.loadSizesOf(texts: Collection<String>) {
    val names = texts.flatMapTo(mutableSetOf(), ::imageNamesOf)
    if (names.isNotEmpty()) loadSizes(names)
}

/** The pictures [text] names, read only where it could hold an image directive at all: most songs hold none. */
internal fun imageNamesOf(text: String): Set<String> =
    if (text.contains(IMAGE_DIRECTIVE, ignoreCase = true)) ChordProImages.referencedSources(text) else emptySet()

/** Of [sizes], those of the pictures [text] names: what its layout depends on, so a picture read for another song changes nothing. */
internal fun imageSizesOf(text: String, sizes: Map<String, SongImageSize>): Map<String, SongImageSize> {
    val names = imageNamesOf(text)
    return if (names.isEmpty()) emptyMap() else sizes.filterKeys { it in names }
}

private const val IMAGE_DIRECTIVE = "image"

/**
 * What the pictures [text] names were read as ([imageSizesOf]), for a song view's model: already there for a text
 * `SongTextStore` held (it reads them first), read here too for a text that came another way, whose pictures then arrive
 * as data.
 */
@Composable
internal fun rememberSongImageSizes(text: String): Map<String, SongImageSize> {
    val loader = LocalSongImageLoader.current
    val sizes by loader.sizes.collectAsStateWithLifecycle()
    LaunchedEffect(loader, text) { loader.loadSizesOf(listOf(text)) }
    return remember(text, sizes) { imageSizesOf(text, sizes) }
}

/**
 * The pictures of the song being drawn. The app's root provides the library's; this default, where none was provided,
 * answers every picture as missing.
 */
internal val LocalSongImageLoader = staticCompositionLocalOf<SongImageLoader> { NoSongImages }

/**
 * Picks a file for the missing picture a song names by the name it is handed, and stores it under that song: only where
 * the song may be written (never in read only mode or a preview), null elsewhere, which hides the placeholder's button.
 */
internal val LocalSongImageAdder = compositionLocalOf<((name: String) -> Unit)?> { null }

private object NoSongImages : SongImageLoader {
    private val _sizes = MutableStateFlow(emptyMap<String, SongImageSize>())
    override val cacheKey = "none"
    override val sizes = _sizes.asStateFlow()
    override suspend fun loadSizes(names: Set<String>) = _sizes.update { sizes -> sizes + (names - sizes.keys).associateWith { SongImageSize.Missing } }
    override suspend fun loadImage(name: String): ByteArray? = null
}
