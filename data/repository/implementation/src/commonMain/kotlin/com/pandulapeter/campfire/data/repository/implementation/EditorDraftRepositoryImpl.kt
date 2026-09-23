/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.EditorDraftRepository
import com.pandulapeter.campfire.data.source.local.api.EditorDraftLocalSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

/**
 * Holds no cache: the draft is read once per start and written on every pause. The lock is what keeps a pause's write
 * and the deletion that follows a save in the order they were asked for.
 */
@Single
internal class EditorDraftRepositoryImpl(
    private val editorDraftLocalSource: EditorDraftLocalSource,
) : EditorDraftRepository {

    private val mutex = Mutex()

    override suspend fun loadEditorDraft() = mutex.withLock { editorDraftLocalSource.loadEditorDraft() }

    override suspend fun saveEditorDraft(draft: SongContent?) = mutex.withLock { editorDraftLocalSource.saveEditorDraft(draft) }
}
