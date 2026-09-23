/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.source.local.implementation.model.EditorDraftDocument

internal fun EditorDraftDocument.toModel() = SongContent(
    fileName = fileName,
    text = text,
)

internal fun SongContent.toEditorDraftDocument() = EditorDraftDocument(
    fileName = fileName,
    text = text,
)
