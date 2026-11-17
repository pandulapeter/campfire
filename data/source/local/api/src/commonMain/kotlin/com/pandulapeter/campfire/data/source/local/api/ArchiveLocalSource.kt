/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.UnpackedArchive
import com.pandulapeter.campfire.data.model.library.ArchiveEntry
import com.pandulapeter.campfire.data.model.library.ExportSink
import com.pandulapeter.campfire.data.model.library.PackedArchive

interface ArchiveLocalSource {

    /**
     * Every file in a zip archive, with the paths of the entries stripped and archives inside it unpacked too (up to
     * a few levels deep, so that a zip of zips of zips cannot be used to make the app work forever). Throws when the
     * bytes are not a readable archive.
     *
     * An archive that is another app's library backup (SongbookPro's) comes back as the songs and setlists it holds,
     * written as the ChordPro and `*.setlist.json` files an archive of Campfire's own would carry, with the setlists
     * pointing at the songs by the names they are returned under.
     *
     * Hidden files are not among them: an AppleDouble "._name.cho" or a ".DS_Store" is the archiving tool's own
     * bookkeeping rather than anything the user put in, so the caller never learns of them and never has to account
     * for them.
     *
     * [maxSize] is how much the files may add up to, nested archives included; the caller owns it because one import
     * may unpack several archives. An entry that was not read - not something an import looks inside, too large, or
     * unreadable - is still returned, as `ImportedFile.unread`, so that it is reported.
     *
     * A ChordPro song comes back with the pictures its image directives name (`ImportedFile.images`), each found in the
     * same archive (or the same nested one) beside the song, in an `images/` folder, or by its file name, read within
     * [maxImageSize] all together, which the answer says how much of was taken. A picture no song names is never read,
     * and is reported as skipped like any other entry; one a song names is reported with the song, never as skipped.
     * The files its attachment lines name come back the same way (`ImportedImage.isAttachment`), found where its pictures
     * would be, each within `ImportLimits.MAX_ATTACHMENT_FILE_SIZE` and all together within [maxAttachmentSize]; a file a
     * song both shows and attaches is read once, as a picture.
     */
    suspend fun unpack(archive: ByteArray, maxSize: Long, maxImageSize: Long, maxAttachmentSize: Long): UnpackedArchive

    /**
     * [unpack] of an archive in the staging folder, read from there and never held whole: the same files, setlists,
     * reports, and pictures and attachments, decided by the same rules, but a picture's or attachment's content is
     * staged (`IncomingContent.Staged`) in the request that staged [archive], and so is each nested archive (above the
     * last level unpacked), deleted once it is unpacked. Song texts are read into memory within [maxSize], as there.
     *
     * Pictures and attachments are held to their own limits each, and all of them, with the nested archives, to the
     * disk the staging may use rather than to the in-memory budgets, reserved from the sizes the archive declares before
     * any is read, so an archive that passed those budgets brings more of its files. Across [archive] and the archives
     * nested in it, at most `ImportLimits.MAX_ARCHIVE_ENTRIES` central directory records are read and at most
     * `ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE` of directories held at once; a nested archive past either is reported as
     * one that cannot be read.
     *
     * [archive] itself is left in place for its owner. Throws when it is not a readable archive, when the staging
     * refuses the room (`StagingRefusedException`), and when the storage fails; whatever ends it so, a cancellation
     * included, first deletes every file the unpack staged.
     */
    suspend fun unpack(archive: StagedFile, maxSize: Long): UnpackedArchive

    /**
     * The songs a foreign document holds - another app's export that is not an archive, such as a JSON document of
     * songs - written as the ChordPro files an import of Campfire's own would carry, or null where [file] is not one.
     * What [unpack] already does to such a document inside an archive, for one that arrived on its own.
     */
    suspend fun songsIn(file: ImportedFile): List<ImportedFile>?

    /**
     * Packs the given entries (name to content, the names may contain directories) into a zip archive, in the map's
     * order.
     *
     * With [isDated], every entry is stamped with the current local time, as an archive the user saves expects. Without
     * it, every entry carries the earliest moment a zip can hold, so the same files always pack to the same bytes,
     * whenever and in whichever time zone: a copy kept current by several devices is compared by its bytes, and a
     * date in it would have each device replace the others' copy on every run.
     */
    suspend fun pack(files: Map<String, ByteArray>, isDated: Boolean): ByteArray

    /**
     * [pack], streamed into [sink] one entry at a time, each read only when it is its turn and let go of before the
     * next, so that the archive is never in memory whole. An entry read as nothing is left out; the answer says how
     * many were written; one past 4 GiB or of 65,535 entries or more is ZIP64. Throws what [sink] throws, and a
     * `ZipException` for an entry name longer than a zip holds, leaving [sink] to be abandoned by its owner.
     */
    suspend fun pack(entries: List<suspend () -> ArchiveEntry?>, isDated: Boolean, sink: ExportSink): PackedArchive
}
