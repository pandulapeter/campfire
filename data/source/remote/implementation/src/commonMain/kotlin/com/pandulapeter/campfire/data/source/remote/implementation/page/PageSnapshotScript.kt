/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.page

/**
 * The script both web views run to look at a page, answering [PageSnapshot] as a JSON text (a string rather than an
 * object, which each platform would hand over in a shape of its own). `innerText` is the text as it is drawn: what is
 * hidden is left out, and a table's or a `<pre>`'s lines keep their breaks, which is how a chord sheet's columns survive.
 * `app/ios/iosApp/ShareExtension/PageText.js` runs the same steps in Safari for a shared page; a change here is made there too.
 *
 * Only for the moment of the read, every element is drawn even where `content-visibility: auto` would skip it until it
 * scrolls into view: `innerText` leaves such an element out, and a page the web view never scrolls (Cifra Club sets its
 * song in blocks like that) would otherwise arrive without its song.
 *
 * A chord a page sets inline, in an element of its own drawn above the syllable it falls on (moved up by its style, as
 * Polish and Czech songbooks do), would reach `innerText` glued into the word (`potrzeDbuję`), losing the chord and the
 * word both. Such an element is told by what is drawn, never by a site's class name: it holds a chord's name alone,
 * and its middle sits above the top of the text on both sides of it, or of the text on one side that starts where it
 * does (or ends just before it), on the same line of the same block (a word ending a line the page wrapped is level with
 * the word before it, and not over the next line's first one; a `<br>` or a kept
 * line break between them is another line, which is how a chord row over its lyric in a `<pre>` or a block of its own
 * stays as it is). Where the page has two or more, each line holding them is written as a chord row, every chord at
 * the column of the character it was drawn over, above the line's words, which is the shape the import reads a
 * chord sheet in. A ruby's reading and a `<pre>`'s text are never taken for one.
 *
 * A page that sets each word or syllable as a block or a flex item of its own, a chord stacked over one in a box of its
 * own (lecarnetdenotes.com), reaches `innerText` a chunk a line. A chord's name whose middle sits above the top of the
 * first character drawn after it, in another block, which starts where it does (a space a chunk holds alone counts), is
 * such a chord. The run it stands in, its highest ancestor at most half as tall again as the chord and its chunk, or a
 * row of flex items each that short (which may wrap), is written again from its boxes: pieces drawn beside one another
 * joined in their order, a space where a gap is drawn between two, a piece drawn under the last starting another line,
 * and each chord before the piece under it, which the chord rows above then set over it. A run with a piece drawn
 * neither beside the last one nor under it, or none of whose lines joins two blocks, is left as `innerText` reads it.
 * The two or more chords the rewriting needs are counted over both kinds.
 *
 * Beside the text, `marks` holds one object per line of it (split at `\n`), what the page model can learn of the line's
 * structure that `innerText` drops (`tools/page-model`, `lines.py`'s `features`): `{}` for a line with nothing
 * printed, `{unaligned: 1, covered}` for one under 90% of whose printed characters were found, else `link` (every
 * character inside a link, a button, or a form control), `newBlock` (its card differs from the previous printed line's),
 * `landmark` (inside `nav`, `header`, `footer`, `aside`, or `form`), `size` (its font size over the body's), `bold`,
 * `italic`, `monospace`, and `heading`. They are found by aligning, never by rebuilding the text: the visible text nodes
 * are walked in order and each one's words found, one after another, in `innerText` near where the last node ended,
 * and a line takes the marks of most of its characters. They are worked out on the text as read, a raised chord's
 * sentinels in it and counted as nothing, and a line written as a chord row over its words gives both its marks.
 */
internal const val PAGE_SNAPSHOT_SCRIPT = """
(() => {
    const shown = document.createElement('style');
    shown.textContent = '*, ::before, ::after { content-visibility: visible !important; }';
    (document.head || document.documentElement).appendChild(shown);
    const sentinels = [];
    try {
        const chord = /^(?:[A-Ha-h]|Do|Re|Mi|Fa|Sol|La|Si)(?:#|b|\u266F|\u266D|is|es|s)?(?:m|min|maj|dim|aug|sus|add|M|\+|\u00B0|\u00F8|\d|\(|\)|\/|[A-H]|#|b|\u266F|\u266D)*/;
        const isInline = (element) => { const display = getComputedStyle(element).display; return display.startsWith('inline') || display === 'contents'; };
        const block = (element) => { let at = element; while (at.parentElement && isInline(at)) at = at.parentElement; return at; };
        const beside = (element, back) => {
            const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
            walker.currentNode = back ? element : (element.lastChild || element);
            for (let node = back ? walker.previousNode() : walker.nextNode(); node; node = back ? walker.previousNode() : walker.nextNode()) {
                if (element.contains(node) || node.contains(element)) continue;
                if (node.nodeType !== 3) { if (node.nodeName === 'BR' || !isInline(node)) return null; continue; }
                const text = node.textContent;
                const at = back ? text.trimEnd().length - 1 : text.length - text.trimStart().length;
                if (at < 0 || at >= text.length) { if (text.includes('\n') && !/^(normal|nowrap)/.test(getComputedStyle(node.parentElement).whiteSpace)) return null; continue; }
                const between = back ? text.slice(at + 1) : text.slice(0, at);
                if (block(node.parentElement) !== block(element) || (between.includes('\n') && !/^(normal|nowrap)/.test(getComputedStyle(node.parentElement).whiteSpace))) return null;
                const range = document.createRange();
                range.setStart(node, at);
                range.setEnd(node, at + 1);
                const box = range.getBoundingClientRect();
                return box.height ? box : null;
            }
            return null;
        };
        // The first character drawn after an element, a space a chunk holds alone included, wherever it stands.
        const below = (element) => {
            const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
            walker.currentNode = element.lastChild || element;
            for (let node = walker.nextNode(); node; node = walker.nextNode()) {
                const text = node.textContent;
                if (element.contains(node) || !text.length) continue;
                const range = document.createRange();
                const at = text.trim() ? text.length - text.trimStart().length : 0;
                range.setStart(node, at);
                range.setEnd(node, at + 1);
                const box = range.getBoundingClientRect();
                if (box.height && (box.width || text.trim())) return {node: node, box: box};
            }
            return null;
        };
        // The run a stacked chord stands in: its highest ancestor no taller than the chord and its chunk (with room to
        // spare), or a row of flex items each that short, which may wrap into several visual lines.
        const rowOf = (element, height) => {
            const fits = (at) => at.getBoundingClientRect().height <= height * 1.5;
            const isRun = (at) => {
                const style = getComputedStyle(at);
                if (!/flex/.test(style.display) || style.flexDirection.slice(0, 3) !== 'row') return false;
                for (let i = 0; i < at.children.length; i++) if (!fits(at.children[i])) return false;
                return true;
            };
            let row = element;
            for (let at = element.parentElement; at && at !== document.body && (fits(at) || isRun(at)); at = at.parentElement) row = at;
            return row;
        };
        // A run's text, its visual lines one under the other, or null where it is not one: a piece drawn neither beside
        // the last one nor under it, or no visual line made of more than one block (innerText already reads those whole).
        const rowText = (row) => {
            const walker = document.createTreeWalker(row, NodeFilter.SHOW_TEXT);
            let text = '';
            let pending = '';
            let line = null;
            let blocks = [];
            let joined = false;
            for (let node = walker.nextNode(); node; node = walker.nextNode()) {
                const parent = node.parentElement;
                if (!parent || parent.closest('script, style, noscript, template')) continue;
                if (stacked.has(parent)) { pending += '\uE000' + parent.textContent.trim() + '\uE001'; continue; }
                const range = document.createRange();
                range.selectNodeContents(node);
                const box = range.getBoundingClientRect();
                if (!box.height || !box.width) continue;
                const style = getComputedStyle(parent);
                let piece = node.textContent.replace(/\s+/g, ' ');
                if (style.textTransform === 'uppercase') piece = piece.toUpperCase();
                if (style.textTransform === 'lowercase') piece = piece.toLowerCase();
                const blank = !piece.trim();
                if (!blank && range.getClientRects().length !== 1) return null;
                let gap = false;
                if (line && Math.min(line.bottom, box.bottom) - Math.max(line.top, box.top) >= Math.min(line.bottom - line.top, box.height) / 2 && box.left >= line.right - 2) {
                    gap = box.left - line.right > (parseFloat(style.fontSize) || 16) * 0.2;
                    line.right = box.right;
                } else if (!line || box.top >= line.bottom - 2) {
                    if (text.length && text.slice(-1) !== '\n') text += '\n';
                    line = {top: box.top, bottom: box.bottom, right: box.right};
                    blocks = [];
                } else if (blank) continue; else return null;
                // Inside a visual line, a space a gap or the piece's own whitespace draws, never two, and the chords standing
                // over the piece just before its first character.
                const open = text.length && text.slice(-1) !== '\n' && text.slice(-1) !== ' ';
                if (blank) { text += (open ? ' ' : '') + (pending ? pending + ' ' : ''); pending = ''; continue; }
                text += (open && (gap || piece[0] === ' ') ? ' ' : '') + pending + piece.trimStart();
                pending = '';
                const owner = block(parent);
                if (blocks.indexOf(owner) < 0) blocks.push(owner);
                if (blocks.length > 1) joined = true;
            }
            return joined ? (text + pending).replace(/ +(?=\n|$)/g, '') : null;
        };
        const raised = [];
        const stacked = new Map();
        for (const element of document.body ? document.body.querySelectorAll('*') : []) {
            if (element.childElementCount || element.closest('pre, ruby, script, style, noscript, textarea')) continue;
            const name = element.textContent.trim();
            const match = name.length <= 12 && chord.exec(name);
            if (!match || match[0] !== name) continue;
            const glyphs = document.createRange();
            glyphs.selectNodeContents(element);
            const box = glyphs.getBoundingClientRect();
            if (!box.height) continue;
            const isOver = (line) => line !== null && (box.top + box.bottom) / 2 <= line.top && box.bottom >= line.top - line.height;
            const after = beside(element, false);
            const before = beside(element, true);
            const isAt = (line, back) => Math.abs((back ? line.right : line.left) - box.left) <= box.height;
            if ((isOver(after) && isOver(before)) || (isOver(after) && isAt(after, false)) || (isOver(before) && isAt(before, true))) { raised.push(element); continue; }
            const under = below(element);
            if (under && block(under.node.parentElement) !== block(element) && isOver(under.box) && isAt(under.box, false)) stacked.set(element, under.box.bottom - box.top);
        }
        // A chord stacked over a chunk that is a block or a flex item of its own reaches innerText a chunk a line. The run of
        // such chunks it stands in is written again from what is drawn: the chunks of one visual line joined in their order,
        // a space where a gap is drawn between two, each chord before the chunk under it.
        const rows = [];
        stacked.forEach((height, element) => {
            const row = rowOf(element, height);
            for (let i = 0; i < rows.length; i++) if (rows[i].element.contains(row) || row.contains(rows[i].element)) return;
            const text = rowText(row);
            if (text !== null) rows.push({element: row, text: text});
        });
        const inRow = (element) => { for (let i = 0; i < rows.length; i++) if (rows[i].element.contains(element)) return true; return false; };
        const inline = [];
        for (const element of raised) if (!inRow(element)) inline.push(element);
        let chords = inline.length;
        stacked.forEach((height, element) => { if (inRow(element)) chords++; });
        if (chords >= 2) {
            for (const element of inline) {
                const open = document.createTextNode('\uE000');
                const close = document.createTextNode('\uE001');
                element.insertBefore(open, element.firstChild);
                element.appendChild(close);
                sentinels.push(open, close);
            }
            for (let i = 0; i < rows.length; i++) {
                const open = document.createTextNode('\uE002' + i + '\uE002');
                const close = document.createTextNode('\uE003');
                rows[i].element.insertBefore(open, rows[i].element.firstChild);
                rows[i].element.appendChild(close);
                sentinels.push(open, close);
            }
        }
        // Only what the run's own markers enclose is written again: a page may draw its own private-use characters (a
        // music font's glyphs).
        const read = (document.body ? document.body.innerText : '')
            .replace(/\uE002(\d+)\uE002[^\uE003]*\uE003/g, (whole, index) => rows[+index].text);
        for (const sentinel of sentinels) if (sentinel.data[0] === '\uE002' || sentinel.data === '\uE003') sentinel.remove();
        const lines = read.split('\n');
        const lineMarks = document.body ? marksOf(document.body, read) : [{}];
        if (!sentinels.length) return JSON.stringify({text: read, title: document.title || '', address: location.href, marks: lineMarks});
        // A line split into a chord row and its words gives both its marks; the words follow their own chord row in the
        // same card, so they start no new block.
        const written = [];
        const marks = [];
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            const mark = lineMarks[i];
            if (!line.includes('\uE000')) { written.push(line); marks.push(mark); continue; }
            let words = '';
            let chords = '';
            line.split(/\uE000([^\uE001]*)\uE001/).forEach((part, index) => {
                if (index % 2 === 0) { words += part; return; }
                const name = part.trim();
                if (!name) return;
                chords = chords.padEnd(chords.length && chords.length >= words.length ? chords.length + 1 : words.length) + name;
            });
            if (!words.trim()) { written.push(chords); marks.push(mark); continue; }
            written.push(chords, words.trimEnd());
            if (!chords) { marks.push({}, mark); continue; }
            const below = {};
            for (const key in mark) if (Object.prototype.hasOwnProperty.call(mark, key)) below[key] = mark[key];
            if (below.newBlock) below.newBlock = 0;
            marks.push(mark, below);
        }
        const text = written.join('\n').replace(/[\uE000\uE001]/g, '');
        return JSON.stringify({text: text, title: document.title || '', address: location.href, marks: marks});
    } finally {
        for (const sentinel of sentinels) sentinel.remove();
        shown.remove();
    }

    function marksOf(body, text) {
        const CARD_TAGS = new Set(['ARTICLE', 'SECTION', 'ASIDE', 'NAV', 'HEADER', 'FOOTER', 'FORM', 'LI', 'MAIN', 'TABLE', 'DIALOG']);
        const LANDMARK = 'nav,header,footer,aside,form,[role=navigation],[role=banner],[role=contentinfo],[role=complementary],[role=search]';
        const LINK = 'a[href],button,[role=button],[role=link],summary,label';
        const MONO = 'pre,code,kbd,samp,tt';
        // A raised chord's sentinels are no character the reader sees: they neither count toward a line nor stand between
        // two words.
        const isGap = (character) => /[\s\uE000\uE001]/.test(character);
        const styles = new Map();
        const style = (element) => {
            let found = styles.get(element);
            if (!found) { found = getComputedStyle(element); styles.set(element, found); }
            return found;
        };
        const cards = new Map();
        let cardCount = 0;
        const isCard = (element) => {
            if (CARD_TAGS.has(element.tagName)) return true;
            const s = style(element);
            if (s.display.slice(0, 6) === 'inline' || s.display === 'contents') return false;
            const border = ['Top', 'Right', 'Bottom', 'Left'].some((side) => parseFloat(s['border' + side + 'Width']) > 0 && s['border' + side + 'Style'] !== 'none');
            const background = s.backgroundImage !== 'none' || !/rgba\(.*,\s*0\)$|transparent/.test(s.backgroundColor);
            return border || background || s.boxShadow !== 'none';
        };
        const cardOf = (element) => {
            element = element.closest('select') || element;
            const path = [];
            let card = 0;
            for (let at = element; at && at !== body && at !== document.documentElement; at = at.parentElement) {
                if (cards.has(at)) { card = cards.get(at); break; }
                path.push(at);
                if (isCard(at)) { card = ++cardCount; cards.set(at, card); path.pop(); break; }
            }
            path.forEach((at) => cards.set(at, card));
            return card;
        };
        const baseSize = parseFloat(style(body).fontSize) || 16;
        // Lowercased one character at a time, a character whose lowercase is longer (Turkish İ) kept as it is, so every
        // position in it is the same position in the text. Plain loops and slice() only: pages still load libraries that
        // replace builtins (Prototype.js's Array.from drops its map function, its startsWith ignores the position).
        const fold = (value) => {
            let folded = '';
            for (let i = 0; i < value.length; i++) {
                const l = value[i].toLowerCase();
                folded += l.length === 1 ? l : value[i];
            }
            return folded;
        };
        const lower = fold(text);
        const n = text.length;
        // How many printed characters come before each position, so the search window ignores whitespace.
        const printed = new Int32Array(n + 1);
        for (let i = 0; i < n; i++) printed[i + 1] = printed[i] + (isGap(text[i]) ? 0 : 1);
        // Per character of the text: -1 where no text node was aligned, else the index of the run of marks covering it.
        const owner = new Int32Array(n).fill(-1);
        const runs = [];
        const walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
        let cursor = 0;
        const WINDOW = 400;
        const BACK = 2000;
        for (let node = walker.nextNode(); node; node = walker.nextNode()) {
            const element = node.parentElement;
            const words = fold(node.data).split(/[\s\uE000\uE001]+/).filter((word) => word);
            if (!element || !words.length) continue;
            if (element.closest('script,style,noscript,template,head')) continue;
            // A select's options print in innerText although the browser calls them invisible.
            const control = element.closest('select,datalist');
            const drawn = control || element;
            if (drawn.checkVisibility && !drawn.checkVisibility({checkVisibilityCSS: true, contentVisibilityAuto: true})) continue;
            const s = style(element);
            const run = runs.length;
            runs.push({
                link: control || element.closest(LINK) ? 1 : 0,
                landmark: element.closest(LANDMARK) ? 1 : 0,
                card: cardOf(element),
                size: (parseFloat(s.fontSize) || baseSize) / baseSize,
                bold: (parseInt(s.fontWeight, 10) || 400) >= 600 || element.closest('b,strong,th') ? 1 : 0,
                italic: s.fontStyle !== 'normal' || element.closest('i,em') ? 1 : 0,
                monospace: element.closest(MONO) || /mono|courier|consol|menlo/i.test(s.fontFamily) ? 1 : 0,
                heading: element.closest('h1,h2,h3,h4,h5,h6,[role=heading]') ? 1 : 0,
            });
            // The node's words found one after another, only whitespace between them, from where the last node ended (or a
            // little before it, so one node matched too far ahead cannot throw the rest of the page off). A node not found
            // that way is left unmarked and does not move the cursor.
            const place = (from) => {
                for (let at = lower.indexOf(words[0], from); at >= 0 && printed[at] - printed[from] <= WINDOW; at = lower.indexOf(words[0], at + 1)) {
                    const spans = [[at, at + words[0].length]];
                    let end = at + words[0].length;
                    for (let w = 1; w < words.length; w++) {
                        let next = end;
                        while (next < n && isGap(lower[next])) next++;
                        if (lower.slice(next, next + words[w].length) !== words[w]) break;
                        spans.push([next, next + words[w].length]);
                        end = next + words[w].length;
                    }
                    if (spans.length === words.length) return spans;
                }
                return null;
            };
            const spans = place(cursor) || place(Math.max(0, cursor - BACK));
            if (!spans) continue;
            for (const [from, to] of spans) owner.fill(run, from, to);
            cursor = spans[spans.length - 1][1];
        }
        const marks = [];
        let start = 0;
        let previousCard = -1;
        for (const line of text.split('\n')) {
            const end = start + line.length;
            let visible = 0, covered = 0, link = 0, landmark = 0, bold = 0, italic = 0, monospace = 0, heading = 0, size = 0;
            let card = -1;
            for (let i = start; i < end; i++) {
                if (isGap(text[i])) continue;
                visible++;
                const run = owner[i];
                if (run < 0) continue;
                const r = runs[run];
                covered++;
                if (card < 0) card = r.card;
                link += r.link; landmark += r.landmark; bold += r.bold; italic += r.italic;
                monospace += r.monospace; heading += r.heading; size += r.size;
            }
            const most = (count) => (covered && count * 2 > covered ? 1 : 0);
            const aligned = visible > 0 && covered * 10 >= visible * 9;
            marks.push(visible === 0 ? {} : aligned ? {
                link: covered === visible && link === covered ? 1 : 0,
                newBlock: card !== previousCard ? 1 : 0,
                landmark: most(landmark),
                size: Math.round((size / covered) * 100) / 100,
                bold: most(bold),
                italic: most(italic),
                monospace: most(monospace),
                heading: most(heading),
            } : {unaligned: 1, covered: Math.round((covered / visible) * 100) / 100});
            if (aligned) previousCard = card;
            start = end + 1;
        }
        return marks;
    }
})()
"""
