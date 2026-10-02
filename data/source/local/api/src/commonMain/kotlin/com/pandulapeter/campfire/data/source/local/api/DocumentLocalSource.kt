/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.ExtractedDocument
import com.pandulapeter.campfire.data.model.domain.ImportedFile

/** Extracts positioned text on the computation dispatcher, with the same bounds and result on every platform. */
interface DocumentLocalSource {
    /** Null for unsupported, protected, malformed or textless documents. Cancellation still propagates. */
    suspend fun extract(file: ImportedFile): ExtractedDocument?
}
