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

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

internal actual fun createAbcScriptEngine(): AbcScriptEngine = QuickJsAbcScriptEngine()

/**
 * abc2svg in QuickJS, through quickjs-kt's JNI library on the desktop and its native library on Android.
 *
 * One engine for the life of the process, started by the first render: loading the native library and abc2svg costs
 * far more than a render (~150 ms against ~5 ms on the desktop). It lives on a thread of its own, since a QuickJS
 * context may only be used by one thread at a time, and a render holds it for its whole length; renders queue there.
 * The thread is a daemon, so a window being closed is never held up by a tune still being rendered.
 */
private class QuickJsAbcScriptEngine : AbcScriptEngine {

    private val dispatcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "abc2svg").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private var quickJs: QuickJs? = null

    /** The text of the render under way, read by the script through `hostRenderInput()` rather than escaped into code. */
    private var source = ""

    override suspend fun render(source: String) = withContext(dispatcher) {
        val engine = quickJs ?: start().also { quickJs = it }
        this@QuickJsAbcScriptEngine.source = source
        engine.evaluate<String>("campfireRender()", "render.js")
    }

    private suspend fun start() = QuickJs.create(dispatcher).apply {
        try {
            function("hostRenderInput") { source }
            // Only abc2svg's own module names ("chordnames-1.js") are looked up, so a tune cannot name another file.
            function("hostLoadModule") { arguments ->
                (arguments.firstOrNull() as? String)?.takeIf { MODULE_NAME.matches(it) }?.let { readResource("abc2svg/$it") }
            }
            evaluate<Any?>(checkNotNull(readResource(HOST_SCRIPT)), HOST_SCRIPT)
            evaluate<Any?>(checkNotNull(readResource(ABC2SVG_SCRIPT)), ABC2SVG_SCRIPT)
        } catch (exception: Exception) {
            close()
            throw exception
        }
    }

    private fun readResource(path: String) = QuickJsAbcScriptEngine::class.java.classLoader
        ?.getResourceAsStream(path)
        ?.use { it.readBytes().decodeToString() }

    private companion object {
        const val HOST_SCRIPT = "campfire-abc-host.js"
        const val ABC2SVG_SCRIPT = "abc2svg/abc2svg-1.js"
        val MODULE_NAME = Regex("""[a-z0-9]+-1\.js""")
    }
}
