/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

// Safari runs this in the page being shared (NSExtensionJavaScriptPreprocessingFile) and hands what it completes with to
// the extension, so a page shared from Safari arrives as the text the reader sees, with nothing loaded again. The same
// steps as PAGE_SNAPSHOT_SCRIPT in :data:source:remote:implementation (PageSnapshotScript.kt, which says why each is
// there), answering the same three fields: innerText, read with every element drawn even where content-visibility: auto
// would skip it, and with a chord drawn above its syllable inline (Polish and Czech songbooks) written as a chord row
// over its line instead of glued into the word.
function readPage() {
    const shown = document.createElement('style');
    shown.textContent = '*, ::before, ::after { content-visibility: visible !important; }';
    (document.head || document.documentElement).appendChild(shown);
    const marks = [];
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
        const raised = [];
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
            if ((isOver(after) && isOver(before)) || (isOver(after) && isAt(after, false)) || (isOver(before) && isAt(before, true))) raised.push(element);
        }
        if (raised.length >= 2) for (const element of raised) {
            const open = document.createTextNode('\uE000');
            const close = document.createTextNode('\uE001');
            element.insertBefore(open, element.firstChild);
            element.appendChild(close);
            marks.push(open, close);
        }
        let text = document.body ? document.body.innerText : '';
        if (marks.length) text = text.split('\n').map((line) => {
            if (!line.includes('\uE000')) return line;
            let words = '';
            let chords = '';
            line.split(/\uE000([^\uE001]*)\uE001/).forEach((part, index) => {
                if (index % 2 === 0) { words += part; return; }
                const name = part.trim();
                if (!name) return;
                chords = chords.padEnd(chords.length && chords.length >= words.length ? chords.length + 1 : words.length) + name;
            });
            return words.trim() ? chords + '\n' + words.trimEnd() : chords;
        }).join('\n').replace(/[\uE000\uE001]/g, '');
        return {text: text, title: document.title || '', address: document.location.href};
    } finally {
        for (const mark of marks) mark.remove();
        shown.remove();
    }
}

var ExtensionPreprocessingJS = {
    run: function (parameters) {
        parameters.completionFunction(readPage());
    },
    finalize: function () {
    },
};
