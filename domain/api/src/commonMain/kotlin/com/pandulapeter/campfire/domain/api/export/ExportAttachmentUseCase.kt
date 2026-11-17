/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.export

import com.pandulapeter.campfire.data.model.library.ExportedFile

interface ExportAttachmentUseCase {

    /**
     * A copy of the file a song attaches as [source], from the library or else the editor's draft shelf, written into the
     * exports folder under [name] (the name the song shows it by, made safe for every file system and given [source]'s
     * extension where it lacks it), for the platform to open: the app that opens it shows that name rather than a hash,
     * and can never write back into the library. A copy of the same name made before is replaced. Null where neither
     * shelf holds the file; throws where the copy cannot be written. The caller owns the file as it owns any export (see
     * [CreateExportFileUseCase]).
     */
    suspend operator fun invoke(source: String, name: String): ExportedFile?
}
