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

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageCode

/**
 * The language code key rather than the identifier one: asked about `en-US` the latter answers "English (United
 * States)", which is a name for a dialect the library does not file songs under, see `ChordProSyntax.languageCode`.
 */
internal actual fun languageDisplayName(code: String, inLocaleCode: String): String? =
    NSLocale(localeIdentifier = inLocaleCode).displayNameForKey(key = NSLocaleLanguageCode, value = code)?.takeIf { it.isNotEmpty() }
