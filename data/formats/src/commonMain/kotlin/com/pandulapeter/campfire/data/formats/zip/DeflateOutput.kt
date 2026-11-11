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

/** Where [Deflater] writes its stream, one byte at a time: the zip writer's buffer, or the PDF writer's. */
fun interface DeflateOutput {
    /** Appends the low eight bits of [value]. */
    fun byte(value: Int)
}
