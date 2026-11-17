/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.run

import com.pandulapeter.campfire.data.sync.implementation.SyncOperation

/**
 * What [SyncPlanner] worked out for one pass.
 *
 * @param holdsDeletionsBack Whether pictures that no song names any more were kept on either side only because the
 *   pass changes which songs there are: a run that follows once the songs have settled deletes them.
 */
internal data class SyncPlan(
    val operations: List<SyncOperation>,
    val holdsDeletionsBack: Boolean,
)
