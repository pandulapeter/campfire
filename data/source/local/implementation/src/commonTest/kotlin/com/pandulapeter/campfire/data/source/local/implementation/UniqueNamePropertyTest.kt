/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [uniqueName] held against generated libraries: names of a few families written one after another in every case and
 * Unicode form, already numbered ones and names ending in a number included, on file systems that fold names and on
 * ones that do not. Each seed is fixed, so a failure names the names that broke the rule.
 */
internal class UniqueNamePropertyTest {

    @Test
    fun `a name given out never collides with one the directory holds and is still named what was asked`() = runTest {
        for (seed in SEEDS) {
            val random = Random(seed)
            val storage = InMemoryFileStorage(foldsNames = random.nextBoolean())
            val written = mutableListOf<String>()
            repeat(NAMES_PER_SEED) {
                val isArriving = random.nextInt(4) == 0
                val desired = random.nextName()
                val name = storage.uniqueName(
                    directory = StorageDirectory.SONGS,
                    desired = desired,
                    collisionSuffix = if (isArriving) ::arrivingCollisionSuffix else ::normalizedCollisionSuffix,
                )
                val context = "Seed $seed: $desired became $name after $written"
                assertTrue(written.none { LibraryFiles.isSameLibraryName(it, name) }, context)
                if (isArriving) {
                    assertTrue(name == desired || name.arrivingVariantNumberOf(desired) != null, context)
                } else {
                    assertTrue(name.isNamed(desired), context)
                }
                storage.writeText(StorageDirectory.SONGS, name, "")
                written += name
            }
            assertEquals(written.size, written.map(LibraryFiles::identityKey).toSet().size, "Seed $seed: $written")
        }
    }

    private fun Random.nextName(): String {
        val base = FAMILIES[nextInt(FAMILIES.size)] + if (nextInt(4) == 0) "_${nextInt(2, 5)}" else ""
        val spelled = when (nextInt(4)) {
            0 -> base.uppercase()
            1 -> base.replace("é", "é")
            else -> base
        }
        return spelled + EXTENSIONS[nextInt(EXTENSIONS.size)]
    }

    private companion object {
        val SEEDS = 1..40
        const val NAMES_PER_SEED = 40
        val FAMILIES = listOf("song", "song_2", "route_66", "café", "a", "a_b", "x (2)", "кино")
        val EXTENSIONS = listOf(".cho", ".cho", ".CHO", ".crd")
    }
}
