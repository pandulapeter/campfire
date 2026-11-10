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
	campfireFretTablature(abc, campfireTablatureOf(source))
	abc.tosvg("tune", source)
	return (systems.length > 0 ? systems.join("\u0000") : "\u0000" + errors.join("\n")) + "\u0001" + records.join("\u0001")
}

// A position abc2svg passed, or nothing where it passed none (a message about the tune as a whole).
function campfireMessagePosition(value) {
	return typeof value == "number" && isFinite(value) ? String(value) : ""
}

// How the tune's tab voices are fretted for the reader (AbcTablature), from the header line AbcRendererImpl writes, or
// null, which leaves them to abc2svg.
function campfireTablatureOf(source) {
	var match = /^%campfire-tablature (.*)$/m.exec(source)
	if (!match) return null
	try {
		return JSON.parse(match[1])
	} catch (e) {
		return null
	}
}

// abc2svg's string tablature module works a tab voice's frets out from its notes' pitches in its set_stems hook, after
// calling the one it wraps. This is installed before the module's hooks are (abc2svg installs them as the tune is read),
// so it is the one wrapped: it runs once every note has its pitch and before any fret is worked out, and moves each tab
// voice to the reader's strings and capo there. The module adds the voice's capo to its strings on every call, so the
// strings are set again on every call, from the ones the voice first had.
function campfireFretTablature(abc, tablature) {
	var glyphs = abc.get_glyphs()
	// The TAB clefs are kept by abc2svg between tunes, and a tune of this rendering names its strings in them.
	delete glyphs.tab3
	delete glyphs.tab4
	delete glyphs.tab5
	delete glyphs.tab6
	var setStems = abc.set_stems
	abc.set_stems = function () {
		setStems.apply(this, arguments)
		var voices = abc.get_voice_tb(), tunings = {}, v, id
		for (v = 0; v < voices.length; v++) {
			if (!voices[v].tab) continue
			campfirePlaceTuplets(voices[v])
			if (!tablature) continue
			campfireRetune(voices[v], tablature)
			id = campfireClefOf(voices[v].tab.length)
			if (id) tunings[id] = tunings[id] === undefined || tunings[id] == String(voices[v].tab) ? String(voices[v].tab) : null
		}
		// One clef is drawn for every staff of as many strings, so the strings are named only where they agree.
		for (id in tunings) {
			if (tunings[id] != null) glyphs[id] = campfireStringNames(id, tunings[id].split(",").map(Number), tablature.noteNames)
		}
	}
}

// abc2svg sets a tab staff's tuplet number among its frets, on the strings, whatever its stems. Over a staff with no
// stems, which shows no rhythm, it says nothing and is left out; over one with stems, which hang below it, it goes above.
// The settings of a tuplet are shared with the voice's others and the tune's format, so each gets a copy of its own.
function campfirePlaceTuplets(voice) {
	var isStemless = voice.pos.stm == abc2svg.C.SL_HIDDEN
	for (var s = voice.sym; s; s = s.next) {
		if (!s.tp) continue
		s.tp.forEach(function (tuplet) {
			tuplet.f = tuplet.f.slice()
			if (isStemless) tuplet.f[0] = 1
			else tuplet.f[3] = abc2svg.C.SL_ABOVE
		})
	}
}

// The clef abc2svg draws in front of a tab staff of so many strings: a staff of more than six strings takes the six's,
// and one of three is a dulcimer's, whose diatonic frets are left to abc2svg.
function campfireClefOf(count) {
	return count >= 4 ? "tab" + Math.min(count, 6) : null
}

// Sets the open strings and the capo of a tab voice, and moves its notes by the score's transposition, which abc2svg
// leaves out of the pitches it frets (its %%transpose moves only what is drawn), and by whole octaves where that keeps
// them on the neck: onto the reader's strings where every note fits on them, else onto the voice's own. A staff's lines
// are drawn before this runs, so strings of another number than the voice's are never taken.
function campfireRetune(voice, tablature) {
	if (!voice.campfireStrings) {
		// abc2svg has already added the voice's own capo to the strings it names, and adds it again after this.
		voice.campfireCapo = voice.capo || 0
		voice.campfireStrings = voice.tab.map(function (string) { return string - voice.campfireCapo })
	}
	var notes = campfireNotesOf(voice), capo = voice.campfireCapo + tablature.capo
	var transposition = abc2svg.b40m((voice.tr_sco | 0) + 122) - 36
	notes.forEach(function (played) { played.note.midi += transposition })
	var strings = tablature.tuning && tablature.tuning.length == voice.campfireStrings.length ? tablature.tuning : null
	var shift = strings ? campfireOctaveShift(notes, strings, capo, tablature.maxFret) : null
	if (shift == null) {
		strings = voice.campfireStrings
		shift = campfireOctaveShift(notes, strings, capo, tablature.maxFret) || 0
	}
	notes.forEach(function (played) {
		played.note.midi += shift
		campfireKeepStringWhereFretted(played, strings, capo, tablature.maxFret)
	})
	voice.tab = strings.slice()
	voice.capo = capo
}

// Every note of a voice's symbols, grace notes included, with the symbol whose decorations may name its string.
function campfireNotesOf(voice) {
	var C = abc2svg.C, notes = [], s, g
	function add(symbol) {
		for (var m = 0; m <= symbol.nhd; m++) notes.push({ symbol: symbol, note: symbol.notes[m] })
	}
	for (s = voice.sym; s; s = s.next) {
		if (s.type == C.NOTE) add(s)
		else if (s.type == C.GRACE) for (g = s.extra; g; g = g.next) add(g)
	}
	return notes
}

// The fewest semitones, in whole octaves, that put every note on some string between the capo and the last fret, or
// null where none does.
function campfireOctaveShift(notes, strings, capo, maxFret) {
	var shifts = [0, 12, -12, 24, -24, 36, -36]
	for (var i = 0; i < shifts.length; i++) {
		if (notes.every(function (played) { return campfireFitsOn(played.note.midi + shifts[i], strings, capo, maxFret) })) return shifts[i]
	}
	return null
}

function campfireFitsOn(midi, strings, capo, maxFret) {
	return strings.some(function (string) { return campfireIsFret(midi - string - capo, maxFret) })
}

function campfireIsFret(fret, maxFret) {
	return fret >= 0 && fret <= maxFret
}

// A note written on a string (!3s!, or !3! in a tab voice) stays on it where it still fits there; elsewhere abc2svg picks
// the string, as it does for any other note.
function campfireKeepStringWhereFretted(played, strings, capo, maxFret) {
	function keeps(decoration) {
		var number = /^([1-9])s?$/.exec(decoration.name)
		if (!number) return true
		var string = strings.length - Number(number[1])
		return string >= 0 && campfireIsFret(played.note.midi - strings[string] - capo, maxFret)
	}
	if (played.note.a_dd) played.note.a_dd = played.note.a_dd.filter(keeps)
	if (!played.symbol.nhd && played.symbol.a_dd) played.symbol.a_dd = played.symbol.a_dd.filter(keeps)
}

// Where abc2svg puts the origin of a tab staff's clef, in the staff's units (six a string): how far above its lowest
// string, and how far right of where its strings start.
var CAMPFIRE_CLEF_ORIGIN = 6
var CAMPFIRE_CLEF_INDENT = 7.5

// The clef naming a staff's open strings, each at the start of its string, as a tab written as text names them, and
// centred on it as the frets are, in the frets' size and the regular weight, over a box of the page's colour that cuts
// the string as a fret does.
function campfireStringNames(id, strings, noteNames) {
	var names = strings.map(function (string) { return noteNames[((string % 12) + 12) % 12] })
	// The highest of the strings of one name is named in lowercase, as e B G D A E tells its two Es apart.
	names.forEach(function (name, index) {
		var highest = -1
		names.forEach(function (other, j) {
			if (other.toLowerCase() == name.toLowerCase() && (highest < 0 || strings[j] > strings[highest])) highest = j
		})
		if (highest == index && names.filter(function (other) { return other.toLowerCase() == name.toLowerCase() }).length > 1) names[index] = name.toLowerCase()
	})
	var texts = names.map(function (name, index) {
		var y = -6 * index + CAMPFIRE_CLEF_ORIGIN + 2.5
		return '<text class="bg1" x="' + -CAMPFIRE_CLEF_INDENT + '" y="' + y + '" style="font:7px sans-serif">' + campfireEscapeXml(name) + '</text>'
	})
	return '<g id="' + id + '">\n' + texts.join("\n") + '\n</g>'
}

function campfireEscapeXml(text) {
	return String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
}
