/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import java.lang.reflect.Proxy
import javax.swing.JComponent
import javax.swing.RootPaneContainer

/**
 * Hears a pinch on a Mac's trackpad, which AWT turns into neither a mouse event nor a touch, so Compose never learns of
 * it: the JDK hands the gesture only to a listener of `com.apple.eawt.event`, registered on a Swing component the
 * pointer is over or inside of. It is registered on the root pane, which everything in the window is inside of.
 *
 * The package is reached through reflection, since it exists only in a JDK built for macOS and the build that uses it
 * is also made on Linux and Windows, and it is not exported by `java.desktop`: the macOS build starts with the
 * `--add-exports` that opens it (see `build.gradle.kts`). A runtime without it, or without the package, answers with
 * an exception, and the window is then simply one that does not hear the gesture.
 *
 * The events arrive on the event dispatch thread, since the JDK posts them there itself.
 *
 * @param onMagnified Called with how much farther apart the fingers are than at the previous event - a ratio, the
 *   way macOS applies a magnification to a scale.
 * @return What unregisters the listener, or null where none was registered.
 */
internal fun RootPaneContainer.listenForTouchpadMagnification(onMagnified: (Float) -> Unit): (() -> Unit)? {
    if (!isMacOs) return null
    return try {
        val utilities = Class.forName("$GESTURE_PACKAGE.GestureUtilities")
        val gestureListenerClass = Class.forName("$GESTURE_PACKAGE.GestureListener")
        val magnificationListenerClass = Class.forName("$GESTURE_PACKAGE.MagnificationListener")
        val getMagnification = Class.forName("$GESTURE_PACKAGE.MagnificationEvent").getMethod("getMagnification")
        val listener = Proxy.newProxyInstance(magnificationListenerClass.classLoader, arrayOf(magnificationListenerClass)) { proxy, method, arguments ->
            when (method.name) {
                "magnify" -> onMagnified(1f + (getMagnification.invoke(arguments[0]) as Double).toFloat())
                // The JDK keeps its listeners in a list, which finds the one to remove by equals.
                "equals" -> return@newProxyInstance proxy === arguments[0]
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "toString" -> return@newProxyInstance "TouchpadMagnificationListener"
            }
            null
        }
        val addListener = utilities.getMethod("addGestureListenerTo", JComponent::class.java, gestureListenerClass)
        val removeListener = utilities.getMethod("removeGestureListenerFrom", JComponent::class.java, gestureListenerClass)
        val component = rootPane
        addListener.invoke(null, component, listener)
        return { removeListener.invoke(null, component, listener) }
    } catch (exception: Exception) {
        println("Trackpad pinches are not available: $exception")
        null
    }
}

private const val GESTURE_PACKAGE = "com.apple.eawt.event"
