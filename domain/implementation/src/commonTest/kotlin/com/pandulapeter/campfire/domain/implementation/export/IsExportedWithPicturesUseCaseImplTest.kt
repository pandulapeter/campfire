/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.export

import com.pandulapeter.campfire.domain.implementation.FakeImageRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsExportedWithPicturesUseCaseImplTest {

    @Test
    fun `a song naming a picture the library holds is exported with it, one naming none or a missing one is not`() = runTest {
        val images = FakeImageRepository()
        val name = images.store(FakeImageRepository.picture(1))
        val useCase = IsExportedWithPicturesUseCaseImpl(images)

        assertTrue(useCase("{title: Chart}\n{image: src=$name}\n"))
        assertFalse(useCase("{title: Chart}\n{image: src=missing.png}\n"))
        assertFalse(useCase("{title: Song}\n[G]Hello\n"))
    }
}
