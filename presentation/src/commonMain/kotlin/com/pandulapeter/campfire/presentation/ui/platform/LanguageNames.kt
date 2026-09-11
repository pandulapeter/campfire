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

/**
 * What the language named by an ISO [code] is called in the language [inLocaleCode] names, or null where the platform
 * has nothing to call it. Campfire ships no list of language names: every platform carries the whole of CLDR for its
 * own formatting, so asking it is both free and more complete than anything that could be kept up to date here — a
 * library may well hold a song in a language the app itself will never be translated into.
 *
 * The answer is whatever CLDR says, which is not always capitalized (Hungarian writes `angol`, not `Angol`) and is
 * therefore a name to be shown rather than a label to be shown as it is, see `languageLabel`.
 */
internal expect fun languageDisplayName(code: String, inLocaleCode: String): String?
