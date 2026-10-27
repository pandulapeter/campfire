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
 * The JavaScript engine abc2svg runs in, one per platform: QuickJS on Android and the desktop, JavaScriptCore on iOS,
 * the browser's own on the web. It is started once, with `campfire-abc-host.js` and then `abc2svg-1.js` evaluated in
 * it, and kept for every later call.
 */
internal interface AbcScriptEngine {

    /**
     * Runs the host's `campfireRender()` over [source] (the ABC text with the header lines [AbcRendererImpl] puts in
     * front of it) and returns what it returned. May throw, when the engine cannot start or the script fails; the
     * renderer turns that into a failed rendering.
     */
    suspend fun render(source: String): String
}

/** The engine of the platform, not yet started: nothing is loaded before the first tune is rendered. */
internal expect fun createAbcScriptEngine(): AbcScriptEngine
