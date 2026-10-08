/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The JVM's `equalsIgnoreCase`, which the desktop and Android compare names with, folds cased letters above U+FFFF by
 * code point. [LibraryFiles.identityKey] has to agree with it there, which only this platform can check.
 */
class LibraryNameIdentityJvmTest {

    @Test
    fun `every supplementary capital is folded as the JVM folds it`() {
        (0x10000..0x1FFFF).forEach { codePoint ->
            val letter = String(Character.toChars(codePoint))
            val lowercase = String(Character.toChars(Character.toLowerCase(codePoint)))
            assertEquals(
                letter.equals(lowercase, ignoreCase = true),
                LibraryFiles.isSameLibraryName(letter, lowercase),
                "U+${codePoint.toString(16)}",
            )
            assertEquals(
                letter.equals(lowercase, ignoreCase = true),
                LibraryFiles.identityKey(letter) == LibraryFiles.identityKey(lowercase),
            )
        }
    }
}
