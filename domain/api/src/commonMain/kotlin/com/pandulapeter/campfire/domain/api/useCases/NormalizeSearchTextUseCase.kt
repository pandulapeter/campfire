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

interface NormalizeSearchTextUseCase {

    /**
     * What a search compares: the text as [NormalizeTextUseCase] folds it, with everything that is neither a letter,
     * a digit nor a mark taken out - the spaces, the punctuation and the symbols. Both the query and the text it is
     * looked for in are put through it, so a search is answered whatever the two of them were punctuated with:
     * `ymca` finds `Y.M.C.A.`, `acdc` and `ac dc` find `AC/DC`, `dont` finds `Don't`.
     *
     * A query that has nothing left after it is empty, and so matches everything, the way a blank one does.
     */
    operator fun invoke(text: String): String
}
