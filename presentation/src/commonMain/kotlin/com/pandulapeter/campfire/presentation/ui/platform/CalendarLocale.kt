/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.material3.CalendarLocale

/**
 * The locale Material's date picker draws its calendar in for the app language [languageCode] (`currentLanguage`'s
 * code). The picker would otherwise take the system's, and the language is chosen in the app rather than by the system,
 * so the month and weekday names would be in a different language from the dialog around them.
 */
internal expect fun calendarLocale(languageCode: String): CalendarLocale
