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

interface GetSharedLinkImportUseCase {

    /**
     * The library paths (`songs/<file name>`, `setlists/<file name>`) that earlier imports of [linkFileId]'s link wrote
     * on this device, see [RememberSharedLinkImportUseCase]; empty where there are none or the preferences cannot be read.
     * Names are as they were written, so they are compared with `LibraryFiles.isSameLibraryName`.
     */
    suspend operator fun invoke(linkFileId: String): Set<String>
}
