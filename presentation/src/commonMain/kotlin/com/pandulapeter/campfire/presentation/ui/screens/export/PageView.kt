/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/** What a page fits into at a zoom of 1: the [viewport] less [margin] at its top and sides and [bottom] under it. */
internal fun fitArea(viewport: Size, margin: Float, bottom: Float) = Rect(
    left = margin,
    top = margin,
    right = maxOf(margin, viewport.width - margin),
    bottom = maxOf(margin, viewport.height - bottom),
)

/** The largest page of [aspectRatio] that fits in [area]. */
internal fun fittedPageSize(area: Size, aspectRatio: Float): Size {
    val width = minOf(area.width, area.height * aspectRatio)
    return Size(width, width / aspectRatio)
}

/** Where a page as large as [pageSize] starts when it is centered in [area]. */
internal fun centeredTopLeft(pageSize: Size, area: Rect) =
    Offset(area.center.x - pageSize.width / 2f, area.center.y - pageSize.height / 2f)

/**
 * How a page of the preview is zoomed, and the point of it, as a fraction of its width and its height, that is in the
 * middle of what it fits into: a pan in pixels would be another part of the page in a pane of another size.
 */
@Immutable
internal data class PageView(
    val zoom: Float = 1f,
    val focus: Offset = Offset(0.5f, 0.5f),
) {
    /** How far a page as large as [pageSize] is moved from the middle by looking at [focus]. */
    fun panOf(pageSize: Size) = Offset(pageSize.width * (0.5f - focus.x), pageSize.height * (0.5f - focus.y))
}

/** The [PageView] of a page as large as [pageSize] at [zoom], moved by [pan] from the middle of [area] as far as it may be. */
internal fun pageViewOf(zoom: Float, pan: Offset, area: Rect, pageSize: Size, viewport: Size): PageView {
    val clamped = clampPan(pan, area, pageSize, viewport)
    return PageView(
        zoom = zoom,
        focus = Offset(0.5f - clamped.x / pageSize.width, 0.5f - clamped.y / pageSize.height),
    )
}

/** Where in [viewport] a page of [aspectRatio] lies, zoomed and moved by [view] from the middle of [area]. */
internal fun pageBounds(viewport: Size, area: Rect, aspectRatio: Float, view: PageView): Rect {
    val pageSize = fittedPageSize(area.size, aspectRatio) * view.zoom
    return Rect(centeredTopLeft(pageSize, area) + clampPan(view.panOf(pageSize), area, pageSize, viewport), pageSize)
}

/**
 * Keeps a page as large as [pageSize], moved by [pan] from the middle of [area], where nothing is lost from sight along
 * each axis: centered in [area] while it fits it, covering [area] and inside [viewport] while it is between the two, and
 * covering [viewport] once it is larger, so that no pan shows anything beyond its edges. Each range runs into the next,
 * so a page zoomed out drifts back to the middle as it shrinks rather than jumping there at the end.
 */
internal fun clampPan(pan: Offset, area: Rect, pageSize: Size, viewport: Size): Offset {
    val centered = centeredTopLeft(pageSize, area)
    fun clamp(value: Float, size: Float, areaStart: Float, areaEnd: Float, room: Float, start: Float): Float {
        val range = when {
            size <= areaEnd - areaStart -> start..start
            size <= room -> maxOf(areaEnd - size, 0f)..minOf(areaStart, room - size)
            else -> (room - size)..0f
        }
        return (start + value).coerceIn(range) - start
    }
    return Offset(
        x = clamp(pan.x, pageSize.width, area.left, area.right, viewport.width, centered.x),
        y = clamp(pan.y, pageSize.height, area.top, area.bottom, viewport.height, centered.y),
    )
}

internal val PAGE_VIEW_SAVER = Saver<PageView, List<Float>>(
    save = { listOf(it.zoom, it.focus.x, it.focus.y) },
    restore = { PageView(zoom = it[0], focus = Offset(it[1], it[2])) },
)
