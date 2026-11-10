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
// by a NUL so that the host tells them apart from a single system. Either way, what follows the first U+0001 are the
// messages again, one record each, separated by U+0001: the line and the column abc2svg found it at (empty where it
// gave none) and its text, separated by U+0002. The host blanks those three characters out of the tune, so only these
// separators carry them.
function campfireRender() {
	var source = hostRenderInput(), systems = [], errors = [], records = []
	var user = {
		read_file: function () { return "" },
		errmsg: function (message, line, column) {
			errors.push(message)
			records.push(campfireMessagePosition(line) + "\u0002" + campfireMessagePosition(column) + "\u0002" + String(message).replace(/[\u0000-\u0002]/g, " "))
		},
		img_out: function (svg) { systems.push(svg) }
	}
	// loadjs is synchronous here, so every module the text asks for has been evaluated once this returns.
	abc2svg.modules.load(source, null, function (message) { errors.push(message) })
	// A tune is untrusted text (a shared link, an import, a paste), and abc2svg evaluates a %%beginjs block's script as
	// is. Its hook is replaced per instance, since the constructor sets the prototype's methods again on every call.
	var abc = new abc2svg.Abc(user), doBeginEnd = abc.do_begin_end
	abc.do_begin_end = function (type) {
		if (type == "js") {
			errors.push("%%beginjs is ignored")
			return
		}
		return doBeginEnd.apply(this, arguments)
	}
	abc.tosvg("tune", source)
	return (systems.length > 0 ? systems.join("\u0000") : "\u0000" + errors.join("\n")) + "\u0001" + records.join("\u0001")
}

// A position abc2svg passed, or nothing where it passed none (a message about the tune as a whole).
function campfireMessagePosition(value) {
	return typeof value == "number" && isFinite(value) ? String(value) : ""
}
