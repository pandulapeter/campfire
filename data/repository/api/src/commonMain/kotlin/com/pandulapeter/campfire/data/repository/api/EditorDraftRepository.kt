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

import com.pandulapeter.campfire.data.model.domain.SongContent

/**
 * The editor's unsaved text as the app last left the front, kept outside the library so that the system ending the
 * process in the background does not end the text too. A copy against the process ending rather than a write of the
 * song: nothing here makes the text saved.
 */
interface EditorDraftRepository {

    /** Null when there is none, and when the one there cannot be read. */
    suspend fun loadEditorDraft(): SongContent?

    /** Replaces the stored draft, null deleting it. Writes land in the order they were asked for. */
    suspend fun saveEditorDraft(draft: SongContent?)
}
