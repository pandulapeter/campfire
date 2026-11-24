/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.reflect.Method

/** What one frame of a [PhasedScene] cost the main thread, by phase, and what it allocated and paused for collections. */
internal class FrameTiming(
    val composeNanos: Long,
    val layoutNanos: Long,
    val drawNanos: Long,
    val allocatedBytes: Long,
    val gcPauseMillis: Long,
) {
    val mainNanos get() = composeNanos + layoutNanos + drawNanos
}

/**
 * One [ImageComposeScene] of [widthPx] by [heightPx] on [Dispatchers.Main], whose frames are timed phase by phase: the
 * frame recomposer's `performFrame` (the clock, recomposition, applying changes, effects), the scene's
 * `measureAndLayout` (lazy lists' subcomposition included), and its `draw` into a Skia picture recorder, which records
 * the commands as a GPU window would issue them and rasterizes nothing. The scene's own `render` runs the same three
 * steps into a raster surface, but only as one call; the phases are reached by reflection on its private fields, named
 * as Compose Multiplatform 1.13 has them. Where a lookup fails (a later Compose renamed them), a frame is `render`
 * timed whole and reported as compose alone, and [isSplit] says so.
 */
internal class PhasedScene(
    private val widthPx: Int,
    private val heightPx: Int,
    density: Float,
    content: @Composable () -> Unit,
) : AutoCloseable {
    private val scene = ImageComposeScene(
        width = widthPx,
        height = heightPx,
        density = Density(density),
        coroutineContext = Dispatchers.Main,
        content = content,
    )
    private val phases = runCatching { ScenePhases(scene) }.getOrNull()
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val pauseBeans = gcPauseBeans()

    val isSplit get() = phases != null

    /** Draws the frame at [nanoTime] of the scene's clock. */
    fun frame(nanoTime: Long): FrameTiming {
        val allocatedBefore = threads.currentThreadAllocatedBytes
        val pausesBefore = pauseBeans.sumOf { it.collectionTime.coerceAtLeast(0) }
        val started = System.nanoTime()
        val composed: Long
        val laidOut: Long
        if (phases == null) {
            scene.render(nanoTime).close()
            composed = System.nanoTime()
            laidOut = composed
        } else {
            phases.performFrame.invoke(phases.recomposer, nanoTime)
            composed = System.nanoTime()
            phases.measureAndLayout.invoke(phases.composeScene)
            laidOut = System.nanoTime()
            PictureRecorder().use { recorder ->
                val canvas = recorder.beginRecording(Rect.makeWH(widthPx.toFloat(), heightPx.toFloat()))
                phases.draw.invoke(phases.composeScene, canvas.asComposeCanvas())
                recorder.finishRecordingAsPicture().close()
            }
        }
        val drawn = System.nanoTime()
        return FrameTiming(
            composeNanos = composed - started,
            layoutNanos = laidOut - composed,
            drawNanos = drawn - laidOut,
            allocatedBytes = threads.currentThreadAllocatedBytes - allocatedBefore,
            gcPauseMillis = pauseBeans.sumOf { it.collectionTime.coerceAtLeast(0) } - pausesBefore,
        )
    }

    override fun close() = scene.close()
}

/** The private members of an [ImageComposeScene] that its `render` calls one after the other. */
private class ScenePhases(scene: ImageComposeScene) {
    val recomposer: Any = scene.field("frameRecomposer")
    val performFrame: Method = recomposer.javaClass.getMethod("performFrame", Long::class.javaPrimitiveType)
    val composeScene: Any = scene.field("scene")
    private val sceneInterface = Class.forName("androidx.compose.ui.scene.ComposeScene")
    val measureAndLayout: Method = sceneInterface.getMethod("measureAndLayout")
    val draw: Method = sceneInterface.getMethod("draw", androidx.compose.ui.graphics.Canvas::class.java)

    private fun Any.field(name: String): Any = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)
}

/**
 * The collectors whose time stops the application's threads: G1's young and old generations, ZGC's and Shenandoah's
 * pauses. Their concurrent beans (`G1 Concurrent GC`, `ZGC … Cycles`, `Shenandoah Cycles`) count work done beside the
 * main thread, and counting it would make a concurrent collector look worse for frames than it is.
 */
internal fun gcPauseBeans(): List<GarbageCollectorMXBean> = ManagementFactory.getGarbageCollectorMXBeans().filter { bean ->
    "Cycles" !in bean.name && "Concurrent" !in bean.name
}
