/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.abc.api.AbcSystem

/** Systems shaped as abc2svg writes them: the styles and shared shapes in [HEADER], used by the systems after it. */
internal object NotationFixtures {
    val HEADER = AbcSystem(
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg" version="1.1"
             xmlns:xlink="http://www.w3.org/1999/xlink"
             fill="currentColor" stroke-width=".7" class="f0 tune0"
             width="360px" height="28.00px" viewBox="0 0 360 28.00">
            <style>
            .f0{font:24.0px music}
            @font-face{
             font-family:music;
             src:url("data:application/octet-stream;base64,AAEAAAAOAIAAAwBg") format("truetype")}
            .f0 text,tspan{white-space:pre}
            .bthW{stroke:currentColor;fill:none;stroke-width:3}
            .slW{stroke:currentColor;fill:none;stroke-width:.7}
            </style>
            </svg>
        """.trimIndent(),
        height = 28f,
    )

    val SYSTEM = system(
        """
        <style>
        .f3{font:700 16.0px text,sans-serif}
        .f5{font:bold 13.0px text,serif}
        </style>
        <defs>
        <path id="stdef" class="slW" d="m0 0h358.0m-358.0 -6.00h358.0"/>
        </defs>
        <use x="0.0" y="61.6" xlink:href="#stdef"/>
        <path class="bthW" d="M63.9 61.6v-24.0"/>
        <path d="m134.3 59.4l37.3 4.3v-3.2l-37.3 -4.3z"/>
        <text x="10.0,20.0"
        y="61.6,55.6"
        >&#xE050;&#xE0A4;</text>
        <g transform="translate(0,61.6)">
        <text class="f3 gch" x="208.0" y="-39.1">Em</text>
        <text class="f5 lyr" x="81.0" y="25.9" text-anchor="middle">la</text>
        </g>
        """.trimIndent(),
    )

    fun system(content: String) = AbcSystem(
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg" version="1.1"
             xmlns:xlink="http://www.w3.org/1999/xlink"
             fill="currentColor" stroke-width=".7" class="f0 tune0"
             width="360px" height="92.40px" viewBox="0 0 360 92.40">
        """.trimIndent() + "\n" + content + "\n</svg>",
        height = 92.4f,
    )
}
