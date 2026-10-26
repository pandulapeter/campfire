/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.chordpro.scales.SongKeys
import com.pandulapeter.campfire.data.model.importing.ImportChoices
import com.pandulapeter.campfire.data.model.importing.ImportConflictResolution
import com.pandulapeter.campfire.data.model.importing.ImportEntryKey
import com.pandulapeter.campfire.data.model.importing.ImportPlan
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.normalizedTags
import com.pandulapeter.campfire.presentation.ui.navigation.ImportPreview
import com.pandulapeter.campfire.presentation.ui.screens.importReport.ImportReport
import com.pandulapeter.campfire.presentation.ui.screens.importReport.review.hasEntriesToChoose

/**
 * Whether the batch [plan] is shown as the preview of its one song rather than reviewed as a list: one song the library
 * does not hold as it is, and nothing else, a name it finds taken included.
 */
internal val ImportPlan.isPreviewed get() = newSongOf() != null

/** The one song [plan] is where the library already holds it as it is, which is opened rather than previewed. */
internal val ImportPlan.librarySong get() = loneSong?.takeIf { it.status == ImportPlan.Status.IDENTICAL }

/** The entry the preview of a batch of one song is about, see [ImportPreview.SingleSong]. */
internal val PREVIEWED_SONG_KEY = ImportEntryKey(ImportEntryKey.Kind.SONG, 0)

/**
 * One page of a preview: the entry at [index] of the plan's songs, as the song details screen draws it.
 *
 * @param song What the entry's text says about itself, under [pageFileName], see [importPreviewSongOf].
 */
internal data class ImportPreviewPage(
    val index: Int,
    val entry: ImportPlan.SongEntry,
    val song: Song,
) {
    val key get() = ImportEntryKey(ImportEntryKey.Kind.SONG, index)

    val pageFileName get() = song.fileName
}

/**
 * The places in [plan]'s songs that [preview] pages through: the ones it was opened on, and after them whatever a batch
 * joining the review since then added, a repeat of an earlier entry aside, as the review leaves it unlisted.
 */
internal fun importPreviewSongIndices(preview: ImportPreview, plan: ImportPlan) =
    preview.songIndices.filter { it in plan.songs.indices } +
        (preview.planSongCount until plan.songs.size).filter { plan.songs[it].repeatedEntryIndex == null }

/**
 * The name a preview's page goes by where the song details screen keys a song by its file name: never one of the
 * library's, since it has no song extension, and one per entry, since names are not unique within a batch.
 */
internal fun importPreviewFileName(index: Int) = "$IMPORT_PREVIEW_FILE_NAME_PREFIX$index"

/**
 * What [entry]'s own text says about the song, as the library would read it once written, under [fileName]: the title
 * the header names, or the one the entry arrived under. Neither its cover nor anything about the file the library
 * might write it as: nothing is fetched for a song that is not in the library, and the name it ends up with is only
 * known once it is written.
 */
internal fun importPreviewSongOf(entry: ImportPlan.SongEntry, fileName: String): Song {
    val summary = ChordProParser.summarize(entry.text)
    val metadata = summary.metadata
    val key = metadata.key?.takeIf { it.isNotBlank() }
    return Song(
        fileName = fileName,
        title = metadata.displayTitle(fallback = entry.title.ifBlank { entry.fileName.removeSuffix(LibraryFiles.SONG_EXTENSION) }),
        artist = metadata.artist?.takeIf { it.isNotBlank() }.orEmpty(),
        key = key,
        transpose = metadata.transpose,
        tags = normalizedTags(metadata.tags),
        languages = metadata.languages,
        coverArtUrl = null,
        hasChords = summary.hasChords,
        canUpdateFileName = false,
        lastModified = 0,
        size = entry.text.length.toLong(),
        duration = ChordProDuration.parse(metadata.duration),
        tempo = ChordProTempo.parse(metadata.tempo),
        time = metadata.time?.takeIf { it.isNotBlank() },
        capo = metadata.capo,
        capoChartKey = if (key == null || (metadata.capo ?: 0) == 0) null else SongKeys.capoChartKeyOf(ChordProParser.parse(entry.text)),
    )
}

/** What one page of a preview over the review offers about its own entry, bound to the choices the review's row uses. */
internal sealed interface ImportPreviewPageChoice {

    /** The library holds the song as it is, so the import leaves it alone and there is nothing to choose. */
    data object AlreadyInLibrary : ImportPreviewPageChoice

    /** A song the import writes under a name of its own, ticked or left out. */
    data class New(val isChosen: Boolean) : ImportPreviewPageChoice

    /** A song whose name the library has given to another one: ticked or left out, and kept beside it or written over it. */
    data class NameTaken(val isChosen: Boolean, val resolution: ImportConflictResolution) : ImportPreviewPageChoice
}

internal fun importPreviewPageChoiceOf(entry: ImportPlan.SongEntry, key: ImportEntryKey, choices: ImportChoices) = when (entry.status) {
    ImportPlan.Status.IDENTICAL -> ImportPreviewPageChoice.AlreadyInLibrary
    ImportPlan.Status.NEW -> ImportPreviewPageChoice.New(isChosen = choices.isChosen(key))
    ImportPlan.Status.CONFLICTING -> ImportPreviewPageChoice.NameTaken(isChosen = choices.isChosen(key), resolution = choices.resolutionOf(key))
}

/** What a preview's floating buttons do. */
internal enum class ImportPreviewAction {

    /** Writes the one song of the batch, whose name is free. */
    IMPORT,

    /** Writes the one song of the batch beside the library's song of the same name, numbered. */
    IMPORT_AS_COPY,

    /** Writes the one song of the batch over the library's song of the same name, once that is confirmed. */
    REPLACE,

    /** Does what the review's own Import does, for the whole batch it lists. */
    IMPORT_REVIEW,
}

/**
 * The floating buttons of a preview: one for a free name, and for a taken one both ways at once, never one hidden behind
 * the other, the copy first since it loses nothing, but Replace where the name is the one an earlier import of the same
 * shared link wrote here ([ImportReport.Review.sharedLinkUpdateCount]), since opening a link again is how its recipient
 * takes the sharer's changes. A preview over the review has the review's Import, and nothing where the review has
 * nothing to tick. Null where there is no button at all.
 */
internal fun importPreviewActionsOf(review: ImportReport.Review, isReviewPager: Boolean): ImportPreviewActions? = when {
    isReviewPager -> ImportPreviewActions(ImportPreviewAction.IMPORT_REVIEW).takeIf { review.plan.hasEntriesToChoose }
    review.plan.songs.singleOrNull()?.status != ImportPlan.Status.CONFLICTING -> ImportPreviewActions(ImportPreviewAction.IMPORT)
    review.sharedLinkUpdateCount > 0 -> ImportPreviewActions(ImportPreviewAction.REPLACE, ImportPreviewAction.IMPORT_AS_COPY)
    else -> ImportPreviewActions(ImportPreviewAction.IMPORT_AS_COPY, ImportPreviewAction.REPLACE)
}

/**
 * @param secondary The other way, drawn beside or under [primary] as a button of less weight.
 */
internal data class ImportPreviewActions(
    val primary: ImportPreviewAction,
    val secondary: ImportPreviewAction? = null,
) {

    /** What Ctrl / Cmd + S presses: the primary button, unless that writes over a library file, which only a press may. */
    val saveShortcutAction get() = primary.takeIf { it != ImportPreviewAction.REPLACE }
}

private const val IMPORT_PREVIEW_FILE_NAME_PREFIX = ":import-preview:"
