/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextPayload
import kotlinx.coroutines.CompletableDeferred

/**
 * One share the share extension left in its inbox: either [files] or [texts], since files beat text in one share.
 *
 * @param taken Completed by the app once the share has settled (reviewed and written, abandoned, or its New song form
 *   gone), which is when the app module deletes it from the inbox, so that a process ended before then offers it again.
 */
class InboxShare(
    val files: List<ImportedFile>,
    val texts: SharedTextPayload?,
    val taken: CompletableDeferred<Unit>,
)
