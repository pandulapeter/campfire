/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.setlists

import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SharedLinksRepository
import com.pandulapeter.campfire.domain.api.setlists.EditSetlistUseCase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Factory

@Factory
internal class EditSetlistUseCaseImpl(
    private val setlistRepository: SetlistRepository,
    private val sharedLinksRepository: SharedLinksRepository,
) : EditSetlistUseCase {

    /**
     * A new title renames the file, and a link to the setlist follows it, which only this device can tell from a
     * deletion. Not cancellable once written, so that a screen going away cannot leave the link on the old name.
     */
    override suspend operator fun invoke(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist? {
        val setlist = setlistRepository.renameSetlist(fileName = fileName, title = title.trim(), description = description.trim(), date = date, isCountdownShown = isCountdownShown)
        if (setlist != null && setlist.fileName != fileName) {
            withContext(NonCancellable) { sharedLinksRepository.followRename(SharedLinkKind.SETLIST, fileName, setlist.fileName) }
        }
        return setlist
    }
}
