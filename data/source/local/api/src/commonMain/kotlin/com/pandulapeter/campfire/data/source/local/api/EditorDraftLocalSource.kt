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

import com.pandulapeter.campfire.data.model.domain.SongContent

/**
 * The editor's unsaved text, kept in a file of its own outside the library so that the process ending in the
 * background does not end the text with it. Never exported, synced or backed up.
 */
interface EditorDraftLocalSource {

    /** Null when there is none, and when the one there cannot be read: a draft is worth keeping, not failing over. */
    suspend fun loadEditorDraft(): SongContent?

    /** Replaces the stored draft; null deletes it. */
    suspend fun saveEditorDraft(draft: SongContent?)
}
