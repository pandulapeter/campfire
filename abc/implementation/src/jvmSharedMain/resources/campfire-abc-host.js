/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// The host abc2svg's core runs in, written against its documented API (new abc2svg.Abc(user), tosvg, and the user
// object's img_out, errmsg, and read_file), not taken from its command line helpers. It is evaluated before
// abc2svg-1.js, which keeps the loadjs it finds here: the engine has no DOM to load a script with, so a module a tune
// asks for (%%chordnames, %%roman) is read through the host's hostLoadModule(name), which returns its text or null.
var abc2svg = {
	loadjs: function (name, relay, onerror) {
		var source = hostLoadModule(name)
		if (source == null) {
			if (onerror) onerror()
			return
		}
		(0, eval)(source)
		if (relay) relay()
	}
}

// Renders the ABC text hostRenderInput() returns, header lines included, and returns every staff system's <svg> joined
// by NUL characters, which no SVG contains. Nothing rendered returns the messages abc2svg reported instead, prefixed
// by a NUL so that the host tells them apart from a single system.
function campfireRender() {
	var source = hostRenderInput(), systems = [], errors = []
	var user = {
		read_file: function () { return "" },
		errmsg: function (message) { errors.push(message) },
		img_out: function (svg) { systems.push(svg) }
	}
	// loadjs is synchronous here, so every module the text asks for has been evaluated once this returns.
	abc2svg.modules.load(source, null, function (message) { errors.push(message) })
	new abc2svg.Abc(user).tosvg("tune", source)
	return systems.length > 0 ? systems.join("\u0000") : "\u0000" + errors.join("\n")
}
