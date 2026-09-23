/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.EditorDraftRepository
import com.pandulapeter.campfire.domain.api.useCases.GetEditorDraftUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveEditorDraftUseCase
import org.koin.core.annotation.Factory

@Factory
class GetEditorDraftUseCaseImpl internal constructor(
    private val editorDraftRepository: EditorDraftRepository,
) : GetEditorDraftUseCase {

    override suspend operator fun invoke() = editorDraftRepository.loadEditorDraft()
}

@Factory
class SaveEditorDraftUseCaseImpl internal constructor(
    private val editorDraftRepository: EditorDraftRepository,
) : SaveEditorDraftUseCase {

    override suspend operator fun invoke(draft: SongContent?) = editorDraftRepository.saveEditorDraft(draft)
}
