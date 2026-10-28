/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.abc.implementation

internal actual fun createAbcScriptEngine(): AbcScriptEngine = WebAbcScriptEngine()

/**
 * abc2svg in the browser's own engine, in the page's global scope: started by the first render, kept for the life of
 * the page. A render runs on the main thread, as everything of the app does in the browser, and takes a few
 * milliseconds; starting it reads abc2svg once, which is the one longer pause, on the first tune shown.
 *
 * The tune and the modules are handed over as values ([AbcScriptGlobals]), since a Kotlin function cannot be passed to
 * the page's scripts, and every error is caught on the script's side and handed back as text: an exception thrown in a
 * script would reach Kotlin as something no `catch` of an `Exception` catches.
 */
private class WebAbcScriptEngine : AbcScriptEngine {

    private var isStarted = false

    override suspend fun render(source: String): String {
        if (!isStarted) {
            evaluate(AbcScriptGlobals.PRELUDE)
            AbcScriptGlobals.modules(abcScripts).forEach { (name, script) -> putModule(name, script.value) }
            evaluate(abcScripts.getValue(AbcScriptGlobals.HOST).value)
            evaluate(abcScripts.getValue(AbcScriptGlobals.CORE).value)
            isStarted = true
        }
        return renderInScript(source)
    }

    private fun evaluate(script: String) = evaluateGlobally(script)?.let { error -> throw IllegalStateException(error) }
}

/** Evaluates [code] in the page's global scope, as a script would be, returning what it threw or null. */
@JsFun("(code) => { try { (0, eval)(code); return null; } catch (error) { return String(error); } }")
private external fun evaluateGlobally(code: String): String?

@JsFun("(name, source) => { globalThis.campfireModules[name] = source; }")
private external fun putModule(name: String, source: String)

/** `campfireRender()` over [input]; what it throws comes back as the host's failure, its message after a NUL. */
@JsFun("(input) => { try { globalThis.campfireRenderInput = input; return globalThis.campfireRender(); } catch (error) { return '\\u0000' + String(error); } }")
private external fun renderInScript(input: String): String
