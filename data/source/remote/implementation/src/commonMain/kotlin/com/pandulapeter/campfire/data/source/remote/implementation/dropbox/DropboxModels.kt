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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The parts of the Dropbox API's answers Campfire reads. Everything is defaulted and unknown keys are ignored, so
 * that a field added on the other side never turns into a parse failure the user sees as a broken sync.
 */
@Serializable
internal data class DropboxListFolderResponse(
    val entries: List<DropboxEntry> = emptyList(),
    val cursor: String = "",
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
internal data class DropboxEntry(
    @SerialName(".tag") val tag: String = "",
    val name: String = "",
    @SerialName("path_lower") val pathLower: String = "",
    val rev: String = "",
    val size: Long = 0,
    @SerialName("content_hash") val contentHash: String? = null,
)

@Serializable
internal data class DropboxFileMetadata(
    val name: String = "",
    @SerialName("path_lower") val pathLower: String = "",
    val rev: String = "",
    val size: Long = 0,
    @SerialName("content_hash") val contentHash: String? = null,
)

@Serializable
internal data class DropboxAccountResponse(
    @SerialName("account_id") val accountId: String = "",
    val name: DropboxName = DropboxName(),
    val email: String = "",
)

@Serializable
internal data class DropboxName(
    @SerialName("display_name") val displayName: String = "",
)

@Serializable
internal data class DropboxTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
    @SerialName("account_id") val accountId: String = "",
)

/** What the token endpoint answers a refusal with: standard OAuth, such as `invalid_grant` for a spent code. */
@Serializable
internal data class DropboxOAuthErrorResponse(
    val error: String = "",
    @SerialName("error_description") val errorDescription: String = "",
)

/**
 * Dropbox reports what went wrong in a `error_summary` string such as `path/conflict/file/...`. It is matched by
 * prefix rather than parsed: the tail changes between calls, the head is what says which kind of failure it is.
 */
@Serializable
internal data class DropboxErrorResponse(
    @SerialName("error_summary") val errorSummary: String = "",
)

/**
 * What `delete_batch` and each `delete_batch/check` answer: a job still running (`async_job_id`, `in_progress`), its
 * outcome (`complete`, with one entry per file in the order they were sent), or the job refused as a whole (`failed`).
 */
@Serializable
internal data class DropboxDeleteBatchResponse(
    @SerialName(".tag") val tag: String = "",
    @SerialName("async_job_id") val asyncJobId: String = "",
    val entries: List<DropboxDeleteBatchEntry> = emptyList(),
    val failed: JsonObject? = null,
)

/** `success`, or `failure` with the reason as a tagged union in [failure]. */
@Serializable
internal data class DropboxDeleteBatchEntry(
    @SerialName(".tag") val tag: String = "",
    val failure: JsonObject? = null,
)

/**
 * A tagged union written out the way an `error_summary` starts, so that both are matched by the same prefixes:
 * `{".tag": "path_lookup", "path_lookup": {".tag": "not_found"}}` is `path_lookup/not_found`.
 */
internal fun JsonObject.tagPath(): String {
    val tag = (this[".tag"] as? JsonPrimitive)?.contentOrNull ?: return ""
    val inner = (this[tag] as? JsonObject)?.tagPath().orEmpty()
    return if (inner.isEmpty()) tag else "$tag/$inner"
}

/**
 * The body of an answer that asks for patience: `{"error": {"reason": {...}, "retry_after": 3}}`. Only the number of
 * seconds is read, since the reason makes no difference to how long to wait.
 */
@Serializable
internal data class DropboxRateLimitResponse(
    val error: DropboxRateLimitError = DropboxRateLimitError(),
)

@Serializable
internal data class DropboxRateLimitError(
    @SerialName("retry_after") val retryAfter: Long? = null,
)
