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

import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.source.local.api.SharedLinksLocalSource
import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.koin.core.annotation.Single

/**
 * `preferences/shared-links.json`: the account's list of shared links as this device last read or wrote it, with the
 * revision it had then, so that the screens know what is shared without a request and a run can tell from its listing
 * whether the list changed since. Replaced on every read and every successful edit, and deleted with the sync index
 * when the account goes, since it describes that account's folder.
 */
@Single
internal class SharedLinksMirror(
    private val localSource: SharedLinksLocalSource,
    private val environment: SyncEnvironment,
) {

    /** The list as last read, and the revision it was read at; null for one that is not known. */
    data class Mirrored(
        val revision: String?,
        val document: SharedLinksDocument,
    )

    /** Null until read from the disk. */
    private val state = MutableStateFlow<Mirrored?>(null)

    /** Keeps a read from the disk from replacing a fresher list saved while it was under way. */
    private val mutex = Mutex()

    val shares = flow {
        load()
        emitAll(state.filterNotNull())
    }.map { it.document.toShares() }.distinctUntilChanged()

    /** The mirror, read from the disk the first time; an empty list where there is none, or none that can be read. */
    suspend fun load(): Mirrored = state.value ?: mutex.withLock {
        state.value ?: read().also { state.value = it }
    }

    suspend fun save(revision: String?, document: SharedLinksDocument) = mutex.withLock {
        val mirrored = Mirrored(revision = revision, document = document)
        state.value = mirrored
        environment.logger.recovering(
            describe = { "Could not write the shared links mirror: ${it::class.simpleName}" },
            // The list on screen is right for this process; the next read of the account's list writes it again.
            fallback = {},
        ) {
            localSource.saveSharedLinksMirror(
                json.encodeToString(
                    JsonElement.serializer(),
                    JsonObject(
                        mapOf(
                            REVISION_KEY to (revision?.let(::JsonPrimitive) ?: JsonNull),
                            DOCUMENT_KEY to SharedLinksFormat.encodeToElement(document),
                        ),
                    ),
                ),
            )
        }
    }

    /** Throws where the file cannot be deleted, for the caller to decide what that is worth. */
    suspend fun clear() = mutex.withLock {
        state.value = Mirrored(revision = null, document = SharedLinksDocument())
        localSource.saveSharedLinksMirror(null)
    }

    private suspend fun read() = environment.logger.recovering(
        describe = { "Could not read the shared links mirror: ${it::class.simpleName}" },
        fallback = { null },
    ) { localSource.loadSharedLinksMirror()?.let(::decode) } ?: Mirrored(revision = null, document = SharedLinksDocument())

    private fun decode(text: String): Mirrored? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return null
        val document = root[DOCUMENT_KEY]?.toString()?.let(SharedLinksFormat::decode) as? SharedLinksFormat.Decoded.Readable ?: return null
        return Mirrored(
            revision = (root[REVISION_KEY] as? JsonPrimitive)?.contentOrNull,
            document = document.document,
        )
    }

    private companion object {
        const val REVISION_KEY = "revision"
        const val DOCUMENT_KEY = "registry"
        val json = Json { prettyPrint = true }
    }
}
