/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.localization

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.list_of_two
import com.pandulapeter.campfire.presentation.resources.list_with_last

/**
 * [items] as a sentence lists them, in the app's language: "A and B", "A, B, and C". The comma between the others is
 * the same in every language the app speaks; where the last one is joined with a comma too is the language's
 * ([Res.string.list_with_last]).
 */
@Composable
internal fun listText(items: List<String>) = when (items.size) {
    0 -> ""
    1 -> items.single()
    2 -> textResource(Res.string.list_of_two, items[0], items[1])
    else -> textResource(Res.string.list_with_last, items.dropLast(1).joinIsolated(", "), items.last())
}
