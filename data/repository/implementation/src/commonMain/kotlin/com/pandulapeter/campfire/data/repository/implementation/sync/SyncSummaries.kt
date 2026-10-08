/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.SyncSummary

internal operator fun SyncSummary.plus(other: SyncSummary) = SyncSummary(
    downloaded = downloaded + other.downloaded,
    uploaded = uploaded + other.uploaded,
    deletedLocally = deletedLocally + other.deletedLocally,
    deletedRemotely = deletedRemotely + other.deletedRemotely,
    conflicts = conflicts + other.conflicts,
    failed = (failed + other.failed).distinct(),
)
