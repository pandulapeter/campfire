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

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import kotlin.test.Test
import kotlin.test.assertEquals

class InflaterImportLimitTest {

    @Test
    fun `the inflater allows what an import does`() {
        assertEquals(ImportLimits.MAX_IMPORT_SIZE, Inflater.MAX_ENTRY_SIZE.toLong())
    }
}
