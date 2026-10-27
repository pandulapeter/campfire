/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.api

/** What [AbcRenderer.render] made of a tune. */
public sealed interface AbcRendering {

    /**
     * The tune as images, top to bottom: a header (titles, composer, tempo) where the tune has one, then one image per
     * staff system, so that a layout can keep each system whole.
     *
     * **The systems of one rendering are read together, in order**: the renderer writes its styles (`<style>`) and shared
     * shapes (`<defs>`) once, into the first image that needs them, and the images after it refer to them. Their class
     * names (`f0`, `f1`, …) are numbered by the renderer across calls, so the same tune rendered twice may name its
     * classes differently: they mean something only within one rendering, and are never part of a cache key.
     */
    public data class Systems(val systems: List<AbcSystem>) : AbcRendering

    /** Nothing was rendered; [message] is the renderer's own account of why, for the log. */
    public data class Failed(val message: String) : AbcRendering
}
