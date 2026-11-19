/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.merge

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDocument
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.sync.implementation.SyncKey
import com.pandulapeter.campfire.data.sync.implementation.downloadFolderDocument
import com.pandulapeter.campfire.data.sync.implementation.index.SyncIndexDocument
import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/**
 * The last part of a run that completed: the app's one folder document ([codec]) settled between this device and the
 * cloud folder, three-way merged as JSON against the base the index keeps (`SyncIndexDocument.syncedPreferences`, with
 * the fields its writer owned and the folder's revision of it), written against the revision read, and merged again on a
 * conflict.
 *
 * It comes after the library files, so that the documents its entries are keyed by are the ones the run left on both
 * sides, and it is where the entries of a document deleted on either side are let go of: whatever the folder document
 * holds for a document no longer in the library is dropped on this device and in the folder alike - a file the run
 * could not move excepted, since it is still in the library somewhere, and so is an entry the folder's document has
 * gained since the last synced one, since its document may have reached the folder after the run listed it.
 *
 * The device's values are changed before the document is uploaded. The other way round, an upload followed by a write
 * that failed would leave this device's old values looking like changes made here since, and the next run would undo
 * the other devices' with them; this way round, a failed upload only leaves the next run a change to carry.
 *
 * One instance for the whole app: [localChanges] only recognizes the values that same instance's [synchronize] wrote,
 * and both the run and the scheduler of the next one reach it.
 */
internal class MergedFolderDocument<T>(
    private val codec: MergedFolderDocumentCodec<T>,
    private val libraryFileLocalSource: LibraryFileLocalSource,
    private val logger: Logger,
) {

    val id get() = codec.id

    val fileName get() = codec.fileName

    /** Recorded in the index beside the base, so that a later version knows which fields this one had an opinion on. */
    val ownedFields get() = codec.ownedFields

    /**
     * What [synchronize] last wrote into the device's values, which is not a change for [localChanges] to report - until
     * the next emission has been seen, whichever it is, so that a later return to the same values is reported again.
     */
    @Volatile
    private var writtenBySync: T? = null

    /**
     * Emits whenever the device's values are changed by anything but a run, which is what schedules the next one. The
     * first value is the values being read, which is not a change.
     */
    val localChanges: Flow<Unit> = codec.values
        .filterNotNull()
        .distinctUntilChanged()
        .drop(1)
        .filter { values ->
            // Cleared by any emission, not only by its own: where the state conflated the run's write away under a
            // change the user made right after it, the user's value arrives instead and must not leave the marker set.
            val isOwnWrite = values == writtenBySync
            writtenBySync = null
            !isOwnWrite
        }
        .map { }

    /** The base [index] keeps, as this version can merge against it: what its writer did not own taken out. */
    fun baseIn(index: SyncIndexDocument) = index.syncedPreferences?.let {
        codec.withoutFieldsNotOwnedBy(it, index.syncedPreferencesFields ?: codec.ownedFieldsWhenUnrecorded)
    }

    /**
     * The document as the folder still holds it, when [listedRevisions] (a listing's documentRevisions) show the revision
     * [index] pairs with its base for [accountId]: what downloading it would give, without the download. Null otherwise.
     * The raw document, not [baseIn], since it stands for the folder's bytes.
     */
    fun unchangedIn(index: SyncIndexDocument, accountId: String, listedRevisions: Map<String, String>): RemoteDocument? {
        val revision = index.syncedPreferencesRevision?.takeIf { it.isNotEmpty() } ?: return null
        val document = index.syncedPreferences ?: return null
        if (index.accountId != accountId || listedRevisions[codec.fileName] != revision) return null
        return RemoteDocument(bytes = FolderDocumentJson.encode(document), revision = revision)
    }

    /**
     * [base] without the entries of [forgotten] files: with the base still naming them, [synchronize] would read the
     * folder's document lacking them as removing their entries.
     */
    fun withoutEntriesOf(base: JsonObject, forgotten: Collection<SyncKey>): JsonObject {
        val names = forgotten.filter { it.kind == codec.keyedBy }.mapTo(hashSetOf()) { it.name.folded() }
        return codec.withKeysWhere(base) { it.folded() !in names }
    }

    /** Whether the folder's document, [bytes] or null where there is none, is not [document], the one a run left there. */
    fun isOtherThan(bytes: ByteArray?, document: JsonObject?) = bytes?.let(FolderDocumentJson::decode) != document

    /**
     * Settles the document and answers the one to remember as the last synced, with the folder's revision of it, or null
     * where it could not be settled - the device's values unreadable, or another device writing the document under every
     * attempt - which leaves the previous one in place for the next run.
     *
     * @param base The document the last run left behind, or null where no run has synced it with this account yet.
     * @param unchanged The folder's document as the last settle left it, when the run's listing shows it unchanged since,
     *   used in place of the first download; a retry after a conflict always downloads.
     * @param keptFileNames The files this run could not move, whose entries are kept although they are not in the
     *   library on this device.
     * @param onLocalSettled Told the base a run stopped from here on has to remember, once the device's values hold what
     *   the folder's document says: that document, not the merged one, since a value only this device changed is not in
     *   the folder until the upload lands, and against a merged base the next run would read the folder's older value as
     *   a change made elsewhere and revert the user's edit everywhere.
     */
    suspend fun synchronize(
        provider: SyncFolder,
        base: JsonObject?,
        unchanged: RemoteDocument?,
        keptFileNames: Collection<String>,
        onLocalSettled: (JsonObject?) -> Unit,
    ): SettledFolderDocument? {
        if (!codec.load()) return null
        // Folded name to this device's spelling, which is the one its screens read the values under. Two names of one
        // document only exist on a file system that tells case apart, and the service refuses one of them anyway; the
        // first in sort order is taken, which the descending order leaves last for associateBy to keep.
        val localNames = libraryFileLocalSource.loadLibraryFiles()
            .filter { it.kind == codec.keyedBy }
            .map { it.name }
            .plus(keptFileNames)
            .sortedDescending()
            .associateBy { it.folded() }
        // The documents the last synced document named, folded. An entry the folder's document holds for any other was
        // written by another device since then, possibly for a document that reached the folder after this run listed it:
        // it is kept until a run that has seen the document decides about it, or the next run, which has it in its base,
        // prunes it. The base rather than previous, which a conflict moves onto the remote document, so that the retry
        // does not prune the very entry the first attempt kept.
        val baseNames = codec.keysOf(base).mapTo(hashSetOf()) { it.folded() }
        var previous = base
        repeat(MAXIMUM_ATTEMPTS) { attempt ->
            val remote = unchanged.takeIf { attempt == 0 } ?: provider.downloadFolderDocument(codec.fileName)
            val remoteDocument = remote?.bytes?.let(FolderDocumentJson::decode)
            if (remoteDocument != null && codec.isNewerFormat(remoteDocument)) {
                // The version that bumped the format is the one that has to keep this one's values, so the document is
                // neither applied nor written over. Null would report a failure no retry can mend until the app is
                // updated, and an empty base names no entry, so a later run with it only ever adds, as with none. The
                // folder holds another document than that base, so the two are never paired with its revision, or the
                // next run would merge against the base without downloading and write over the newer format.
                logger.log("${codec.fileName} was written by a newer version of the app; it is left alone.")
                return SettledFolderDocument(document = previous ?: JsonObject(emptyMap()), revision = null)
            }
            // A document that is missing, or is not one this version can read, is taken as unchanged since the last run
            // rather than as one that removed everything: this device's values stay, and are uploaded in its place.
            val effectiveRemote = remoteDocument?.takeIf(codec::isReadable) ?: previous
            val arrivingNames = codec.keysOf(effectiveRemote)
                .map { it.folded() }
                .filterTo(hashSetOf()) { it !in baseNames }
            val snapshot = codec.values.first() ?: return null
            // Each device keeps its own spelling of a file that differs only by case or Unicode form, so all three sides
            // are put on one spelling per document before they are merged, or the merge would read one entry as two. The
            // folder's spelling comes first, since every device reads the same folder and so settles on the same one.
            val spellings = mutableMapOf<String, String>().apply {
                codec.keysOf(effectiveRemote).sorted().forEach { getOrPut(it.folded()) { it } }
                codec.keysOf(previous).sorted().forEach { getOrPut(it.folded()) { it } }
                localNames.forEach { (folded, name) -> getOrPut(folded) { name } }
            }
            val spelling = { name: String -> spellings[name.folded()] ?: name }
            val canonicalBase = previous?.let { codec.withKeysSpelled(it, spelling) }
            val merged = codec.withKeysWhere(
                document = codec.merge(
                    base = canonicalBase,
                    local = codec.localDocument(
                        base = canonicalBase,
                        // A value left under the folder's spelling beside the one under this device's is a leftover
                        // no screen here reads, so the one the user set wins.
                        values = codec.respelled(snapshot, spelling, isPreferred = { localNames[it.folded()] == it }),
                    ),
                    remote = effectiveRemote?.let { codec.withKeysSpelled(it, spelling) },
                ),
                isKept = { it.folded().let { folded -> folded in localNames || folded in arrivingNames } },
            )
            // Applied over the snapshot as it was read, so that a value under any other spelling of a document is
            // removed here: compared with a respelled one, such a leftover would look like the merged value and stay.
            val mergedValues = codec.respelled(codec.valuesOf(merged), { localNames[it.folded()] ?: it }, isPreferred = { false })
            if (mergedValues != snapshot) {
                logger.log("Sync: ${codec.fileName} changed in the folder is applied here")
                codec.apply(mergedValues, since = snapshot) { before, after ->
                    // Marked only when the write is the run's alone. One that changes nothing synced emits nothing,
                    // and its marker would swallow the next emission, which can be a change of the user's the
                    // collector has not seen yet; one that kept a value changed here during the merge is what
                    // carries that change, which a conflating state may deliver in place of the user's own emission.
                    writtenBySync = after.takeIf { it != before && it == mergedValues }
                }
            }
            onLocalSettled(effectiveRemote)
            if (merged == remoteDocument) return SettledFolderDocument(document = merged, revision = remote.revision)
            val result = provider.uploadDocument(
                name = codec.fileName,
                bytes = FolderDocumentJson.encode(merged),
                expectedRevision = remote?.revision,
            )
            when (result) {
                is RemoteWriteResult.Written -> {
                    logger.log("Sync: ${codec.fileName} uploaded")
                    return SettledFolderDocument(document = merged, revision = result.revision)
                }
                // Written elsewhere since it was read. This device's values already hold what the document said then,
                // so that is the base the next attempt merges what was written since against.
                RemoteWriteResult.Conflict -> {
                    logger.log("Sync: ${codec.fileName} was written elsewhere meanwhile, merging again")
                    previous = effectiveRemote
                }
            }
        }
        logger.log("Another device kept writing ${codec.fileName}; the next run settles it.")
        return null
    }

    /** See `foldRemoteNamesOntoLocal`: an entry is the same document's under any spelling of its file. */
    private fun String.folded() = LibraryFiles.identityKey(this)

    private companion object {
        const val MAXIMUM_ATTEMPTS = 2
    }
}
