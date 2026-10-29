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

import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.data.model.preferences.AppLanguage

/**
 * The direction the interface is laid out in while [this] string table is the app's, which is its language's
 * [AppLanguage.layoutDirection]: the app's language decides it, never the system's, so a switch in Settings turns the
 * layout with the texts.
 */
internal val AppLocale.layoutDirection
    get() = when (appLanguage.layoutDirection) {
        AppLanguage.Direction.LEFT_TO_RIGHT -> LayoutDirection.Ltr
        AppLanguage.Direction.RIGHT_TO_LEFT -> LayoutDirection.Rtl
    }

/** The app language [this] string table holds the texts of; the generated default table is English's. */
internal val AppLocale.appLanguage get() = AppLanguage.entries.firstOrNull { it.id == code } ?: AppLanguage.ENGLISH
