/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportedFile

/** Stateless offline extraction. No document bytes or source format are retained after importing its text. */
interface DocumentRepository {
    /** Null for a malformed, unsupported, protected or textless document; cancellation propagates. */
    suspend fun extract(file: ImportedFile): ExtractedDocument?
}
