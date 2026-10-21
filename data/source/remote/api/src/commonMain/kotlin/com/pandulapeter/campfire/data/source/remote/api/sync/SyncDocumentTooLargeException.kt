/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.sync

/**
 * A folder document too large to download, refused before its body was read. It carries the [revision] the document is
 * at, so that a write over it names that revision and is not a conflict.
 *
 * Deliberately not a [SyncRunEndingException]: whether such a document is unreadable, and written over, is the sync
 * module's decision, and nothing about the connection or the rest of the folder is wrong.
 */
class SyncDocumentTooLargeException(
    val revision: String,
) : Exception("The document is too large to download.")
