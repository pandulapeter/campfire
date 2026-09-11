/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

interface NormalizeLanguageCodeUseCase {

    /**
     * The language a piece of text names, under the one code the library files it by, or null where it names none.
     * `HUN`, `hun`, `hu` and `hu-HU` all answer `hu`; `rom` answers itself, having no shorter form; `und` and a
     * blank line answer null.
     *
     * It is the same normalization [SetChordProLanguagesUseCase] puts a code through on its way into a file, so a
     * caller can look a language up by whatever a user typed and find it under the code everything else knows.
     */
    operator fun invoke(value: String): String?
}
