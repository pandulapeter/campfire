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

/**
 * One entry of an archive written by the streaming [ZipWriter.write], whose bytes [read] gives only when it is the
 * entry's turn: the writer holds one entry at a time, never the whole archive's. [read] is called exactly once.
 */
class ZipEntrySource(
    val name: String,
    val read: suspend () -> ByteArray,
)
