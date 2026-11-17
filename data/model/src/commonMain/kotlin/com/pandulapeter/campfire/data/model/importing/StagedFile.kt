/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/**
 * A file an import wrote whole into the staging folder (`imports/`): an archive it received, or a picture or attachment
 * it unpacked. Only ever made once the file was closed cleanly, so a staged file is never a partial one.
 *
 * @param name The file's name in the staging folder, made by the staging from the session, the request, and a number or
 *   the content's hash, never from anything that arrived with the file.
 * @param size How many bytes were written.
 * @param sha256Hex The SHA-256 of exactly the bytes written, in lowercase hex, for an unpacked file, which is named by it;
 *   null for an archive, which nothing names by its content and which would cost a hash of its whole size.
 */
data class StagedFile(
    val name: String,
    val size: Long,
    val sha256Hex: String?,
)
