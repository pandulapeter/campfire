/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProSplitter
import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.convert.PageTitle
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.domain.api.importing.PageCropper
import kotlinx.coroutines.CancellationException

/** A page's text cropped to its song by a [PageCropper], before the chord sheet converter reads it. */
internal object PageTextCrop {

    /**
     * A page's song as the crop found it: [text], the kept lines with the lines naming the title and artist, and [body],
     * the same without the ones heading the song, which name its [title] and [artist]. Null [body] where no line heads it.
     */
    data class CroppedPage(
        val text: String,
        val body: String?,
        val title: String?,
        val artist: String?,
    )

    /** What a cropped page converts to: its songs, and the title and artist its header lines name where the songs declare none. */
    data class ConvertedPage(
        val songs: List<String>,
        val title: String?,
        val artist: String?,
    )

    /**
     * [text] with only the lines [cropper] keeps, or null where it is to be converted whole: the cropper keeps every line,
     * finds no song, answers nothing usable, or fails, which [logger] hears. A blank line between two kept lines stays
     * even where the model did not keep it, since the converter tells a song's verses apart by the blank lines between
     * them, and a run of clutter taken out from between two parts still leaves the parts apart.
     *
     * The title and artist lines are kept with the song, except that a body is also made without the ones that head it
     * ([com.pandulapeter.campfire.domain.api.importing.PageSongLines.headerLines]): any other stays in both, since it may
     * be a line of the song the model misjudged, and a misjudged song line must never be lost.
     */
    suspend fun cropped(text: String, cropper: PageCropper, logger: Logger): CroppedPage? {
        val lines = text.lines()
        val found = try {
            cropper.songLinesOf(lines)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not crop the page: ${exception.message}")
            null
        } ?: return null
        val headerLines = listOfNotNull(found.titleLine, found.artistLine).filter { it in lines.indices }
        val songLines = (found.keptLines.filter { it in lines.indices } + headerLines).toSet()
        val heading = found.headerLines.filter { it in headerLines }.toSet()
        val withHeader = keptText(lines, songLines) ?: return null
        if (withHeader.size == lines.size) return null
        val title = found.titleLine?.takeIf { it in heading }?.let { PageTitle.of(lines[it]) }
        return CroppedPage(
            text = withHeader.joinToString("\n") { lines[it] },
            body = if (heading.isEmpty()) null else keptText(lines, songLines - heading)?.joinToString("\n") { lines[it] },
            title = title?.title,
            artist = title?.artist ?: found.artistLine?.takeIf { it in heading }?.let { PageTitle.artistOf(lines[it]) },
        )
    }

    /**
     * [page] converted as the import converts a chord sheet. Its header lines are left in where the converter reads the
     * song's title from them (or from any other line) itself, since the text's own header wins: only a page converted
     * into one song with no title is converted again without them, its [CroppedPage.title] and [CroppedPage.artist]
     * then suggested in their place. That second conversion guesses no title, which with the header gone could only be
     * the song's first lyric. Null where the crop holds nothing the converter finds a song in.
     */
    fun converted(page: CroppedPage): ConvertedPage? {
        val songs = convert(page.text, guessesTitle = true) ?: return null
        val asItIs = ConvertedPage(songs = songs, title = null, artist = null)
        val body = page.body ?: return asItIs
        val song = songs.flatMap(ChordProSplitter::split).singleOrNull() ?: return asItIs
        if (!ChordProParser.parseMetadata(song).title.isNullOrBlank()) return asItIs
        val bodySongs = convert(body, guessesTitle = false) ?: return asItIs
        return ConvertedPage(songs = bodySongs, title = page.title, artist = page.artist)
    }

    /** The indices of [lines] in [kept], with the blank lines between the first and the last; null for none. */
    private fun keptText(lines: List<String>, kept: Set<Int>): List<Int>? {
        if (kept.isEmpty()) return null
        val first = kept.min()
        val last = kept.max()
        return lines.indices.filter { index -> index in kept || (lines[index].isBlank() && index in first..last) }
    }

    private fun convert(text: String, guessesTitle: Boolean) =
        ChordSheetConverter.convert(ChordSheet.ofPlainText(text), String::normalizedToNfc, guessesTitle = guessesTitle).takeIf { it.any(String::isNotBlank) }
}
