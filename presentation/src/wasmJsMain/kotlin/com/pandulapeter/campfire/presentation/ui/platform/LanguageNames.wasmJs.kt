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
 * `Intl.DisplayNames` is the only one of the four platforms' answers that can be missing at runtime, and it throws
 * on an argument it does not like rather than returning nothing, so both are answered with null — the code itself is
 * shown then, which is worse than a name and better than a crash.
 */
internal actual fun languageDisplayName(code: String, inLocaleCode: String): String? = displayLanguageName(code, inLocaleCode)

/**
 * The `Intl.DisplayNames` of a language is kept rather than built per call: building one is the expensive half of
 * this, and the picker asks for the name of every language there is (twice, where the app's own language has none)
 * every time it is opened.
 */
private fun displayLanguageName(code: String, inLocaleCode: String): String? = js(
    """(function () {
        try {
            if (typeof Intl === 'undefined' || typeof Intl.DisplayNames !== 'function') return null;
            var cache = globalThis.__campfireLanguageNames || (globalThis.__campfireLanguageNames = {});
            var names = cache[inLocaleCode] || (cache[inLocaleCode] = new Intl.DisplayNames([inLocaleCode], { type: 'language', fallback: 'none' }));
            return names.of(code) || null;
        } catch (error) {
            return null;
        }
    })()"""
)
