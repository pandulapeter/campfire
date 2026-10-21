/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.remote.api.sync.model.SharedLinkResult
import com.pandulapeter.campfire.data.source.remote.implementation.network.toAsciiJsonString
import kotlinx.serialization.json.JsonObject

/**
 * The links to shared copies and their deletion, for [DropboxSyncProvider.createSharedLink] and
 * [DropboxSyncProvider.deleteSharedFile]. The copies themselves are uploaded like any other file.
 */
internal class DropboxSharing(
    private val requests: DropboxTransport,
    private val logger: Logger,
) {

    /**
     * Asks for a public, view-only link to the file at [path]. A file that has one already is answered with
     * `shared_link_already_exists` and, because settings are sent, without the link in it, so that one is read with
     * `list_shared_links` instead: the file keeps the one link it has. Any other refusal of the request (a team that
     * forbids sharing, an address not verified, the file gone) is [SharedLinkResult.Refused].
     */
    suspend fun createLink(path: String): SharedLinkResult {
        val link = try {
            requests.rpc(
                url = CREATE_SHARED_LINK_URL,
                body = """{"path":${path.toAsciiJsonString()},"settings":$PUBLIC_VIEWER_SETTINGS}""",
                deserializer = DropboxSharedLinkMetadata.serializer(),
            )
        } catch (exception: DropboxApiException) {
            if (exception.statusCode != CONFLICT) throw exception
            if (exception.errorSummary.startsWith("shared_link_already_exists")) {
                existingLink(path)
            } else {
                logger.log("Dropbox made no link: ${exception.errorSummary}")
                null
            }
        }
        return link?.toResult() ?: SharedLinkResult.Refused
    }

    private suspend fun existingLink(path: String) = requests.rpc(
        url = LIST_SHARED_LINKS_URL,
        body = """{"path":${path.toAsciiJsonString()},"direct_only":true}""",
        deserializer = DropboxListSharedLinksResponse.serializer(),
    ).links.firstOrNull()

    /**
     * A link a team's rules made narrower than asked is [SharedLinkResult.NotPublic] whatever its form, since nobody
     * outside the team could open it; a public one of a form [parseDropboxSharedLink] does not read is refused.
     */
    private fun DropboxSharedLinkMetadata.toResult(): SharedLinkResult {
        val audience = (linkPermissions.resolvedVisibility ?: linkPermissions.effectiveAudience)?.tag
        if (audience != PUBLIC) return SharedLinkResult.NotPublic
        return parseDropboxSharedLink(url) ?: SharedLinkResult.Refused.also { logger.log("Dropbox answered a link of an unknown form.") }
    }

    /** Deletes the file at [path], which ends its link; a file already gone is the outcome that was asked for. */
    suspend fun delete(path: String) {
        try {
            requests.rpc(url = DELETE_URL, body = """{"path":${path.toAsciiJsonString()}}""", deserializer = JsonObject.serializer())
        } catch (exception: DropboxApiException) {
            if (exception.statusCode != CONFLICT || !exception.errorSummary.startsWith("path_lookup/not_found")) throw exception
        }
    }

    private companion object {
        const val CREATE_SHARED_LINK_URL = "https://api.dropboxapi.com/2/sharing/create_shared_link_with_settings"
        const val LIST_SHARED_LINKS_URL = "https://api.dropboxapi.com/2/sharing/list_shared_links"
        const val DELETE_URL = "https://api.dropboxapi.com/2/files/delete_v2"
        const val PUBLIC_VIEWER_SETTINGS = """{"requested_visibility":"public","audience":"public","access":"viewer"}"""
        const val PUBLIC = "public"
        const val CONFLICT = 409
    }
}
