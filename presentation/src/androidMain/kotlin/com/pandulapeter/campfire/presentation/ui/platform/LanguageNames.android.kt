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

import java.util.Locale

/**
 * The JVM answers an unknown code with the code itself rather than with nothing, so that is what "has nothing to
 * call it" looks like here.
 */
internal actual fun languageDisplayName(code: String, inLocaleCode: String): String? = Locale.forLanguageTag(code)
    .getDisplayLanguage(Locale.forLanguageTag(inLocaleCode))
    .takeIf { it.isNotEmpty() && !it.equals(code, ignoreCase = true) }
