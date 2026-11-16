/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

/**
 * A temporary file an export is being written into. Exactly one of [finish] and [abandon] ends it: [finish] once the
 * export is whole, [abandon] where it failed or was cancelled, which removes the file. Whoever opened one must end it.
 */
interface ExportFileWriter : ExportSink {

    /** Closes the file and answers it, to be handed to an [ExportedFile]. Throws where it cannot be completed. */
    suspend fun finish(): ExportedContent.TemporaryFile

    /** Closes and removes the file, quietly and even while cancelled. */
    suspend fun abandon()
}
