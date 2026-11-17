/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

/** What [ZipDirectory.walk] did: the names of the entries whose sinks were closed, the ones left out, and the cost. */
class ZipWalkResult(
    val read: List<String>,
    val unread: List<UnreadZipEntry>,
    /** What the walk's reads cost its budget, damaged entries included. */
    val chargedSize: Long,
)
