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
 * [a] plus [b], two sizes or offsets out of an archive, refused as a damaged archive where either is negative or the sum
 * does not fit a [Long]. Every sum of numbers an archive declares goes through here: a 64-bit field is the archive's to
 * choose, and an offset plus a size that wraps around would point a check at the wrong end of the file.
 */
internal fun checkedSum(a: Long, b: Long): Long {
    if (a < 0 || b < 0 || a > Long.MAX_VALUE - b) {
        throw ZipException("A size or offset in the archive is out of range ($a + $b).")
    }
    return a + b
}
