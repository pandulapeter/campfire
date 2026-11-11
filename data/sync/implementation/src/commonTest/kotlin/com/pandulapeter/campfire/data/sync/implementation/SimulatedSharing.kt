/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.sync.implementation.shared.SharedLinksEntry
import com.pandulapeter.campfire.data.sync.implementation.shared.registry

/**
 * The shared links of [SyncSimulation]'s library: items shared from any device, and every rename and deletion a user
 * makes noted the way the app's use cases note them ([Device.sharing]). What it checks:
 * - a share stops (its copy or its entry gone) only where a user deleted the item under a name the share had;
 * - settled, every share whose copy is there names a file of the folder and holds that file's bytes, or is orphaned
 *   where the folder has no such file.
 */
internal class SimulatedSharing(private val cloud: FakeSyncProvider) {

    /** The items a user deleted, by kind and folded name, which is what may stop a share of them. */
    private val deletedNames = mutableSetOf<String>()

    /** Every name each share was seen under, by its id. */
    private val namesOf = mutableMapOf<String, MutableSet<String>>()

    suspend fun share(device: Device, key: SyncKey): String {
        val bytes = device.library.files.getValue(key)
        val result = device.sharing(cloud).share(
            kind = key.kind.shareKind,
            fileName = key.name,
            title = bytes.decodeToString().lines().first(),
            copy = device.sharedContents.pack(mapOf(key.name to bytes)),
        )
        observe()
        return if (result is CreateShareResult.Shared) "shared as ${result.share.shareId}" else "not shared: $result"
    }

    suspend fun noteDeletion(device: Device, key: SyncKey) {
        deletedNames += key.folded()
        device.sharing(cloud).stopSharingDeleted(key.kind.shareKind, key.name)
    }

    suspend fun noteRename(device: Device, from: SyncKey, to: SyncKey) = device.sharing(cloud).followRename(from.kind.shareKind, from.name, to.name)

    /** Records the names the list gives its shares now, and checks that none stopped without a user deleting it. */
    fun observe() {
        val links = cloud.registry().links
        links.forEach { namesOf.getOrPut(it.shareId) { mutableSetOf() } += it.folded() }
        namesOf.forEach { (shareId, names) ->
            val isListed = links.any { it.shareId == shareId }
            val isCopied = shareId in cloud.sharedFiles
            check((isListed && isCopied) || names.any { it in deletedNames }) {
                "Share $shareId of ${names.joinToString()} stopped (listed: $isListed, copy: $isCopied) though nobody deleted it"
            }
        }
    }

    /** Every share follows the library the folder settled on. */
    fun checkSettled() {
        val folder = cloud.files.entries.associate { (key, file) -> key.folded() to file.first.decodeToString() }
        cloud.registry().links.filterNot { it.copyMissing }.forEach { entry ->
            val file = folder[entry.folded()]
            if (file == null) {
                check(entry.state == SharedLinksEntry.STATE_ORPHANED) { "Share ${entry.shareId} of the missing ${entry.fileName} is ${entry.state}" }
            } else {
                check(entry.state == SharedLinksEntry.STATE_OK) { "Share ${entry.shareId} of ${entry.fileName} is ${entry.state}" }
                val copy = cloud.sharedFiles.getValue(entry.shareId).first.decodeToString()
                check(copy.substringAfter('\n') == file) { "Share ${entry.shareId} of ${entry.fileName} holds $copy rather than $file" }
            }
        }
    }

    /** The list and the copies, for telling whether a round of runs moved anything. */
    fun state() = cloud.registry().links.map { "${it.shareId}=${it.fileName}/${it.state}/${it.copyMissing}" } +
        cloud.sharedFiles.entries.map { (shareId, file) -> "$shareId@${file.second}" }

    fun describe() = "Shares: " + cloud.registry().links.joinToString { entry ->
        "${entry.shareId}=${entry.fileName} (${entry.state}${if (entry.copyMissing) ", copy missing" else ""}): " +
            cloud.sharedFiles[entry.shareId]?.first?.decodeToString()?.substringAfter('\n')
    }

    private val LibraryFileKind.shareKind get() = if (this == LibraryFileKind.SONG) SharedLinkKind.SONG else SharedLinkKind.SETLIST

    private fun SyncKey.folded() = "${kind.shareKind.id}/${LibraryFiles.identityKey(name)}"

    private fun SharedLinksEntry.folded() = "$kind/${LibraryFiles.identityKey(fileName)}"
}
