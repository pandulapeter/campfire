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

/**
 * An image laid out by hand rather than one shot to a canvas: the stores' feature graphic, header, search results, icon,
 * poster and box art, the README's banners, and the website's link preview. Its [elements] are drawn back to front on
 * a canvas of [widthPx] by [heightPx], in its pixels, over [background], and it is written to [outputs]: one file, or a
 * panorama cut into as many equal parts side by side, each scaled by [scale] (the README's banners are drawn at their
 * stores' size and written at half of it).
 *
 * @property banner The folder of the [Banner] whose renders its screens show, by their slot.
 */
internal class Composition(
    val widthPx: Int,
    val heightPx: Int,
    val background: Background,
    val banner: String? = null,
    val elements: List<Element>,
    val outputs: List<Output>,
    val scale: Float = 1f,
) {
    enum class Background {
        /** The listing's: the app's purple at the top start corner running into the orange at the bottom end. */
        LINEAR,

        /** Purple in the middle, running out into orange at the edges. */
        RADIAL_PURPLE,

        /** Orange in the middle, running out into purple at the edges. */
        RADIAL_ORANGE,
    }

    /** Something drawn on the canvas, in the box from [x], [y], [width] by [height] pixels. */
    sealed interface Element {
        val x: Int
        val y: Int
        val width: Int
        val height: Int
    }

    /**
     * The render of the banner's slot [slot] in a frame of [kind], as large as fits the box and centered in it, turned
     * by [rotation] degrees clockwise.
     */
    class Screen(
        val slot: Int,
        val kind: FrameKind,
        override val x: Int,
        override val y: Int,
        override val width: Int,
        override val height: Int,
        val rotation: Float = 0f,
    ) : Element

    /** The app's campfire mark in white, as large as fits the box. */
    class Logo(
        override val x: Int,
        override val y: Int,
        override val width: Int,
        override val height: Int,
    ) : Element

    /** [text] set as the listing's headlines are, [size] pixels high, centered in the box. */
    class Caption(
        val text: String,
        val size: Int,
        override val x: Int,
        override val y: Int,
        override val width: Int,
        override val height: Int,
    ) : Element

    /** Where a file of the composition goes: [path] under the Desktop folder of the run, or under the README's own. */
    class Output(val path: String, val isReadme: Boolean = false)
}
