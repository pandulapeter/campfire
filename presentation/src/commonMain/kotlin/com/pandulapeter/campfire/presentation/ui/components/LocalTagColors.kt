/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * `UserPreferences.tagColors`, provided once by `CampfireApp`: a tag is drawn in a dozen places, most of them shared
 * components several screens deep, and handing the map down to every one of them would make each a parameter for a
 * detail of its icon. Not static, so that a color being changed recomposes the pills that read it rather than the app.
 */
internal val LocalTagColors = compositionLocalOf<Map<String, String>> { emptyMap() }

/** The color [tag] was given, or null for none. */
@Composable
@ReadOnlyComposable
internal fun tagColor(tag: String): TagColor? = tagColorOf(LocalTagColors.current, tag)
