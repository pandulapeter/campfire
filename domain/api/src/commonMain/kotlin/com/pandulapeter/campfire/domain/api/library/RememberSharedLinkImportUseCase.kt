/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.library

/**
 * Records on this device which library files an import of a shared link wrote under the names it asked for, so that a
 * later import of the same link starts them on Replace, see [GetSharedLinkImportUseCase].
 */
interface RememberSharedLinkImportUseCase {

    /**
     * Adds these names to what [linkFileId]'s link is remembered for, never forgetting any recorded before: a file the
     * link brought back unchanged is not written again, and is still the link's. Only the names the link asked for
     * belong here, never a numbered copy kept beside one of the library's. Never throws: a record that could not be
     * written costs only the next update's preselection.
     *
     * @param linkFileId The link's Dropbox file id, the one part of a link another link cannot borrow: it decides which
     *   bytes the link serves.
     */
    suspend operator fun invoke(linkFileId: String, songFileNames: Collection<String>, setlistFileNames: Collection<String>)
}
