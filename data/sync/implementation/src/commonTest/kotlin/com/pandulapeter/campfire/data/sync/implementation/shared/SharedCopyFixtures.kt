/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * The library's formats as the shared-copy step's tests write them: a setlist is its title on the first line and the
 * song file names it holds on the following ones, a song's title is its first line, and a pack is every file's name and
 * bytes one after the other, which is as deterministic as the real zip. [packCount] is how many copies were built, and
 * [isOversized] makes every copy one byte more than a share may hold.
 */
internal class FakeSharedCopyContents : SharedCopyContents {

    var packCount = 0

    var isOversized = false

    override suspend fun setlistSongFileNames(bytes: ByteArray) = bytes.decodeToString().lines().drop(1).filter { it.isNotBlank() }

    override suspend fun titleOf(kind: SharedLinkKind, fileName: String, bytes: ByteArray) = bytes.decodeToString().lines().first()

    override suspend fun pack(files: Map<String, ByteArray>): ByteArray {
        packCount++
        if (isOversized) return ByteArray((ImportLimits.MAX_IMPORT_SIZE + 1).toInt())
        return files.entries.joinToString(separator = "\n") { (name, bytes) -> "[$name]\n${bytes.decodeToString()}" }.encodeToByteArray()
    }
}

/** A setlist document in [FakeSharedCopyContents]'s format. */
internal fun setlistDocument(title: String, vararg songFileNames: String) = (listOf(title) + songFileNames).joinToString("\n").encodeToByteArray()

/** One device's shared-copy step, with its own mirror and journal in [localSource], as [buildNumber] would build. */
internal fun sharedCopySync(
    library: LibraryFileLocalSource,
    environment: SyncEnvironment,
    localSource: FakeSharedLinksLocalSource = FakeSharedLinksLocalSource(),
    contents: SharedCopyContents = FakeSharedCopyContents(),
    buildNumber: Int = 1,
): SharedCopySync {
    val mirror = SharedLinksMirror(localSource, environment)
    return SharedCopySync(
        libraryFileLocalSource = library,
        contents = contents,
        registry = SharedLinksRegistry(mirror, environment),
        mirror = mirror,
        journal = SharedLinksJournal(localSource, environment),
        logger = Logger.Standard,
        buildNumber = buildNumber,
    )
}

/** The registry as it is in [cloud]'s folder, or an empty one where there is none. */
internal fun FakeSyncProvider.registry() = documents[SharedLinksFormat.NAME]
    ?.let { (bytes, _) -> (SharedLinksFormat.decode(bytes.decodeToString()) as SharedLinksFormat.Decoded.Readable).document }
    ?: SharedLinksDocument()

/** Puts [entries] in [cloud]'s registry and uploads a copy for each of them with [copy] bytes, as sharing them would. */
internal fun FakeSyncProvider.share(vararg entries: SharedLinksEntry, copy: ByteArray = "copy".encodeToByteArray()) {
    entries.forEach { sharedFiles[it.shareId] = copy to "s-${it.shareId}" }
    writeDocumentElsewhere(SharedLinksFormat.NAME, SharedLinksFormat.encode(registry().let { it.copy(links = it.links + entries) }).encodeToByteArray())
}

internal fun FakeSyncProvider.entryOf(shareId: String) = registry().links.single { it.shareId == shareId }

/** The shared copy [shareId] in the folder, as text. */
internal fun FakeSyncProvider.copyOf(shareId: String) = sharedFiles.getValue(shareId).first.decodeToString()

/** The registry [localSource]'s mirror holds, as last read or written there. */
internal fun mirrorOf(localSource: FakeSharedLinksLocalSource) = localSource.mirror
    ?.let { Json.parseToJsonElement(it).jsonObject.getValue("registry").toString() }
    ?.let { (SharedLinksFormat.decode(it) as SharedLinksFormat.Decoded.Readable).document }
    ?: SharedLinksDocument()

internal fun sharedEntry(shareId: String, kind: SharedLinkKind, fileName: String, title: String = fileName) = SharedLinksEntry(
    shareId = shareId,
    kind = kind.id,
    fileName = fileName,
    title = title,
    fileId = "id-$shareId",
    rlkey = "key",
    sharedAt = "2026-10-21",
)
