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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import platform.Foundation.NSString
import platform.Foundation.create
import platform.JavaScriptCore.JSContext
import platform.JavaScriptCore.setObject

internal actual fun createAbcScriptEngine(): AbcScriptEngine = JavaScriptCoreAbcScriptEngine()

/**
 * abc2svg in the system's JavaScriptCore, which adds nothing to the app's size. One context for the life of the process,
 * started by the first render, on a thread of its own: a context's virtual machine locks itself to one thread at a time,
 * and a render holds it for its whole length.
 *
 * The tune and the modules are handed over as values ([AbcScriptGlobals]), never as Kotlin functions, so no Kotlin
 * object lives on in the engine's heap; the system runs scripts there with the JIT off, as on every iPhone app, which
 * still renders a tune in a few milliseconds.
 */
@OptIn(BetaInteropApi::class, DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
private class JavaScriptCoreAbcScriptEngine : AbcScriptEngine {

    private val dispatcher = newSingleThreadContext("abc2svg")
    private var context: JSContext? = null

    override suspend fun render(source: String) = withContext(dispatcher) {
        val context = context ?: start().also { context = it }
        context.setObject(source, forKeyedSubscript = NSString.create(string = AbcScriptGlobals.RENDER_INPUT))
        val result = context.evaluateChecked("campfireRender()")
        checkNotNull(result?.toString_()) { "abc2svg returned nothing" }
    }

    private fun start() = JSContext().apply {
        evaluateChecked(AbcScriptGlobals.PRELUDE)
        setObject(AbcScriptGlobals.modules(abcScripts).mapValues { it.value.value }, forKeyedSubscript = NSString.create(string = AbcScriptGlobals.MODULES))
        evaluateChecked(abcScripts.getValue(AbcScriptGlobals.HOST).value)
        evaluateChecked(abcScripts.getValue(AbcScriptGlobals.CORE).value)
    }

    /** Evaluates [script], throwing what it threw, which JavaScriptCore otherwise only leaves on the context. */
    private fun JSContext.evaluateChecked(script: String) = evaluateScript(script).also {
        exception?.let { thrown ->
            exception = null
            throw IllegalStateException(thrown.toString_())
        }
    }
}
