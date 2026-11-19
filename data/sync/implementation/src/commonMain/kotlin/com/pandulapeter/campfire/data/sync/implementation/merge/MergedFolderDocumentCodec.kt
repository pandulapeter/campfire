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

import com.pandulapeter.campfire.data.model.library.SyncCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * What an app's one folder document holds and where its values live on the device, for [MergedFolderDocument], which
 * knows only JSON. The document is a JSON object the app reads only in part: everything this version does not own
 * passes through untouched, so a newer version's additions survive an older one's writes.
 *
 * Its entries for single library documents are keyed by those documents' file names in [keyedBy], matched ignoring case
 * and Unicode form as the folder's names are; an entry follows its document's spelling and goes with the document.
 *
 * @param T The values this version reads from the document and keeps on the device; compared for equality.
 */
internal interface MergedFolderDocumentCodec<T> {

    /** Names the document in `SyncSummary.failedSteps` where a run could not settle it. */
    val id: String

    /** The document's name at the top of the cloud folder, where no collection lists it. */
    val fileName: String

    /** The fields this version owns ([localDocument] replaces them), recorded in the index beside the base. */
    val ownedFields: Set<String>

    /** The fields owned by the writer of a base whose index records none: the first version that synced the document. */
    val ownedFieldsWhenUnrecorded: Set<String>

    /** The collection whose documents the entries are keyed by. */
    val keyedBy: SyncCollection

    /** Whether [document] holds what every document this version writes does; one that does not is replaced like one that does not decode. */
    fun isReadable(document: JsonObject): Boolean

    /** Whether [document] declares a format this version must neither merge nor write over. */
    fun isNewerFormat(document: JsonObject): Boolean

    /** The names of the library documents [document] holds an entry for. */
    fun keysOf(document: JsonObject?): Set<String>

    /** [document] with every entry under the name [spelling] gives it, two entries of one document collapsed into one. */
    fun withKeysSpelled(document: JsonObject, spelling: (String) -> String): JsonObject

    /** [document] without the entries [isKept] says no to. */
    fun withKeysWhere(document: JsonObject, isKept: (String) -> Boolean): JsonObject

    /** [base] without the fields [ownedByWriter] does not name, which its writer passed through and has no opinion on. */
    fun withoutFieldsNotOwnedBy(base: JsonObject, ownedByWriter: Set<String>): JsonObject

    /** [local] and [remote] settled against [base] (null: no run has synced the document, so the merge only adds). */
    fun merge(base: JsonObject?, local: JsonObject, remote: JsonObject?): JsonObject

    /** What this device holds, as a document: [base] with this version's readable values replaced by [values]. */
    fun localDocument(base: JsonObject?, values: T): JsonObject

    /** The values of [document] this version understands; one it could not have written is left out. */
    fun valuesOf(document: JsonObject): T

    /** [values] with every entry under the name [spelling] gives it, an entry under a name [isPreferred] says yes to winning. */
    fun respelled(values: T, spelling: (String) -> String, isPreferred: (String) -> Boolean): T

    /** The device's values as they change, null while they cannot be read. */
    val values: Flow<T?>

    /** Reads the device's values if they have not been yet, and answers whether they could be. */
    suspend fun load(): Boolean

    /**
     * Writes into the device's values what changed from [since] to [values], keeping any value that is no longer what it
     * was in [since] (changed here during the merge). [onWritten] is told the values before and after the write from
     * inside it, before the [values] flow emits them, which is how [MergedFolderDocument.localChanges] knows the write for its own.
     */
    suspend fun apply(values: T, since: T, onWritten: (before: T, after: T) -> Unit)
}
