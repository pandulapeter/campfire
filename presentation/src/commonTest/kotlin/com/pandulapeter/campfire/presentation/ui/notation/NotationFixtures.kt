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

    /**
     * A tune of one voice in the tab clef rendered under `%strtab` (`CDEF GABc|cBAG FEDC|` on a guitar's six strings),
     * verbatim but for the embedded font: its header, then its system, which holds the filter behind each fret.
     */
    val TAB_HEADER = AbcSystem(
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg" version="1.1"
             xmlns:xlink="http://www.w3.org/1999/xlink"
             fill="currentColor" stroke-width=".7" class="f1 tune0"
             width="800px" height="54.00px" viewBox="0 0 800 54.00">
            <style>
            .f0{font:20.0px text,serif}
            .f1{font:24.0px music}
            @font-face{
             font-family:music;
             src:url("data:application/octet-stream;base64,AAEAAAAOAIAAAwBg") format("truetype")}
            .f1 text,tspan{white-space:pre}
            .stroke{stroke:currentColor;fill:none}
            .bW{stroke:currentColor;fill:none;stroke-width:1}
            .bthW{stroke:currentColor;fill:none;stroke-width:3}
            .slW{stroke:currentColor;fill:none;stroke-width:.7}
            .slthW{stroke:currentColor;fill:none;stroke-width:1.5}
            .sltnW{stroke:currentColor;fill:none;stroke-width:.25}
            .sldW{stroke:currentColor;fill:none;stroke-width:.7;stroke-dasharray:5,10}
            .sW{stroke:currentColor;fill:none;stroke-width:.7}
            .box{outline:1px solid black;outline-offset:1px}
            </style>
            <text class="f0" x="399.0" y="43.6" text-anchor="middle">Tab</text>
            </svg>
        """.trimIndent(),
        height = 54f,
    )

    val TAB_SYSTEM = AbcSystem(
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg" version="1.1"
             xmlns:xlink="http://www.w3.org/1999/xlink"
             fill="currentColor" stroke-width=".7" class="f1 tune0"
             width="800px" height="94.00px" viewBox="0 0 800 94.00">
            <filter x="-0.1" y="0.1" width="1.2" height=".8" id="bg1">
            <feFlood flood-color="white"/>
            <feComposite in="SourceGraphic" operator="over"/>
            </filter>
            <style>
            .f2{font:bold 13.0px text,serif}
            .f3{font:700 7.0px sans-serif}
            .bg1{filter:url(#bg1)}
            </style>
            <defs>
            <text id="tab6" x="-4,-4,-4" y="-14.5,-4,5.5" style="font:bold 13px sans-serif">TAB</text>
            </defs>
            <g transform="translate(48.5, 71.0)">
            <path class="slW" d="m0 0h332.9m-332.9 -9.60h332.9m-332.9 -9.60h332.9m-332.9 -9.60h332.9m-332.9 -9.60h332.9m-332.9 -9.60h332.9"/>
            </g>
            <g transform="translate(0,71.0) scale(1.6)">
            </g>
            <path class="bW" d="M234.8 71.0v-48.0M381.4 71.0v-48.0"/>
            <text class="f2" x="24.3" y="50.6" text-anchor="middle">Guitar</text>
            <g transform="translate(0,71.0) scale(1.6)">
            <g class="f3">
            <text class="bg1" x="59.5" y="-21.5">1</text>
            <text class="bg1" x="70.2" y="-21.5">3</text>
            <text class="bg1" x="80.9" y="-27.5">0</text>
            <text class="bg1" x="91.6" y="-27.5">1</text>
            <text class="bg1" x="103.4" y="-27.5">3</text>
            <text class="bg1" x="114.1" y="-27.5">5</text>
            <text class="bg1" x="124.8" y="-27.5">7</text>
            <text class="bg1" x="135.4" y="-27.5">8</text>
            <text class="bg1" x="152.2" y="-27.5">8</text>
            <text class="bg1" x="162.9" y="-27.5">7</text>
            <text class="bg1" x="173.6" y="-27.5">5</text>
            <text class="bg1" x="184.2" y="-27.5">3</text>
            <text class="bg1" x="196.1" y="-27.5">1</text>
            <text class="bg1" x="206.8" y="-27.5">0</text>
            <text class="bg1" x="217.5" y="-21.5">3</text>
            <text class="bg1" x="228.1" y="-21.5">1</text>
            </g>
            <use x="37.8" y="-6.0" xlink:href="#tab6"/>
            </g>
            </svg>
        """.trimIndent(),
        height = 94f,
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
