/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.implementation

/**
 * The host functions `campfire-abc-host.js` calls, for an engine that hands the script values rather than functions:
 * JavaScriptCore and the browser are given the tune to render as [RENDER_INPUT] and the modules by name as [MODULES],
 * which keeps every Kotlin object out of the engine's heap. Evaluated first, before the host script.
 */
internal object AbcScriptGlobals {

    const val RENDER_INPUT = "campfireRenderInput"
    const val MODULES = "campfireModules"

    const val PRELUDE = """
        var $RENDER_INPUT = "", $MODULES = {};
        function hostRenderInput() { return $RENDER_INPUT; }
        function hostLoadModule(name) { return Object.prototype.hasOwnProperty.call($MODULES, name) ? $MODULES[name] : null; }
    """

    /** The scripts abc2svg may load by name (`chordnames-1.js`), from the generated [abcScripts]. */
    fun modules(scripts: Map<String, Lazy<String>>) = scripts.filterKeys { it.startsWith(MODULE_FOLDER) && it != CORE }.mapKeys { it.key.removePrefix(MODULE_FOLDER) }

    const val HOST = "campfire-abc-host.js"
    const val CORE = "abc2svg/abc2svg-1.js"
    private const val MODULE_FOLDER = "abc2svg/"
}
