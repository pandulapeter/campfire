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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

internal actual fun createAbcScriptEngine(): AbcScriptEngine = QuickJsAbcScriptEngine(
    memoryLimitBytes = QuickJsAbcScriptEngine.MEMORY_LIMIT_BYTES,
    evaluationTimeoutMillis = QuickJsAbcScriptEngine.EVALUATION_TIMEOUT_MILLIS,
)

/**
 * abc2svg in QuickJS, through quickjs-kt's JNI library on the desktop and its native library on Android.
 *
 * One engine for the life of the process, started by the first render: loading the native library and abc2svg costs
 * far more than a render (~150 ms against ~5 ms on the desktop). It lives on a thread of its own, since a QuickJS
 * context may only be used by one thread at a time, and a render holds it for its whole length; renders queue there.
 * The thread is a daemon, so a window being closed is never held up by a tune still being rendered.
 *
 * Each evaluation runs under [memoryLimitBytes] and [evaluationTimeoutMillis], which sit above what the caps of
 * [AbcRendererImpl] let through, so a crafted tune or an abc2svg bug can cost neither unbounded memory nor the thread.
 */
internal class QuickJsAbcScriptEngine(
    private val memoryLimitBytes: Long,
    private val evaluationTimeoutMillis: Long,
) : AbcScriptEngine {

    private val dispatcher = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "abc2svg").apply { isDaemon = true } }
        .asCoroutineDispatcher()
    private var quickJs: QuickJs? = null

    /** The text of the render under way, read by the script through `hostRenderInput()` rather than escaped into code. */
    private var source = ""

    override suspend fun render(source: String) = withContext(dispatcher) {
        val engine = quickJs ?: start().also { quickJs = it }
        this@QuickJsAbcScriptEngine.source = source
        try {
            engine.evaluate<String?>("campfireRender()", "render.js") ?: error("abc2svg returned no text")
        } catch (exception: CancellationException) {
            // quickjs-kt interrupts a cancelled render as it does one past its time, and the context renders the next
            // tune after it. The editor's preview cancels a render on every keystroke, so the engine is kept here.
            throw exception
        } catch (throwable: Throwable) {
            // A context that ran out of memory fails every later evaluation, or answers null instead of throwing, and
            // keeps what the failed render built, so after any failure the next render starts a new one.
            engine.close()
            quickJs = null
            throw throwable
        }
    }

    private suspend fun start() = QuickJs.create(dispatcher).apply {
        this.memoryLimit = memoryLimitBytes
        this.evaluationTimeoutMillis = this@QuickJsAbcScriptEngine.evaluationTimeoutMillis
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

    internal companion object {
        /** Above the 96 MB the tune length cap of [AbcRendererImpl] was measured to let through in a realistic shape. */
        const val MEMORY_LIMIT_BYTES = 128L * 1024 * 1024

        /**
         * Above the ~2 s the slowest realistic tune within [AbcRendererImpl]'s caps takes on the desktop, Android being a
         * few times slower; a render that loops holds the queue this long once, and the engine after it is a new one.
         */
        const val EVALUATION_TIMEOUT_MILLIS = 10_000L
        private const val HOST_SCRIPT = "campfire-abc-host.js"
        private const val ABC2SVG_SCRIPT = "abc2svg/abc2svg-1.js"
        private val MODULE_NAME = Regex("""[a-z0-9]+-1\.js""")
    }
}
