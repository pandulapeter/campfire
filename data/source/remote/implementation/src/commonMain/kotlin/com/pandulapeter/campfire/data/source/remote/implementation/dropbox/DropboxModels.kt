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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The parts of the Dropbox API's answers Campfire reads. Everything is defaulted and unknown keys are ignored, so
 * that a field added on the other side never turns into a parse failure the user sees as a broken sync.
 */
@Serializable
internal data class DropboxListFolderResponse(
    val entries: List<DropboxEntry> = emptyList(),
    val cursor: String = "",
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
internal data class DropboxEntry(
    @SerialName(".tag") val tag: String = "",
    val name: String = "",
    @SerialName("path_lower") val pathLower: String = "",
    val rev: String = "",
    val size: Long = 0,
    @SerialName("content_hash") val contentHash: String? = null
)

@Serializable
internal data class DropboxFileMetadata(
    val name: String = "",
    @SerialName("path_lower") val pathLower: String = "",
    val rev: String = "",
    val size: Long = 0,
    @SerialName("content_hash") val contentHash: String? = null
)

@Serializable
internal data class DropboxAccountResponse(
    @SerialName("account_id") val accountId: String = "",
    val name: DropboxName = DropboxName(),
    val email: String = ""
)

@Serializable
internal data class DropboxName(
    @SerialName("display_name") val displayName: String = ""
)

@Serializable
internal data class DropboxTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
    @SerialName("account_id") val accountId: String = ""
)

/**
 * Dropbox reports what went wrong in a `error_summary` string such as `path/conflict/file/...`. It is matched by
 * prefix rather than parsed: the tail changes between calls, the head is what says which kind of failure it is.
 */
@Serializable
internal data class DropboxErrorResponse(
    @SerialName("error_summary") val errorSummary: String = ""
)
