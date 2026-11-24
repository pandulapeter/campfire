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
// the extension, so a page shared from Safari arrives as the text the reader sees, with nothing loaded again. innerText
// is the page as it is drawn: what is hidden is left out, and a table's or a <pre>'s lines keep their breaks, which is
// how a chord sheet's columns survive. The same three fields as PAGE_SNAPSHOT_SCRIPT in :data:source:remote:implementation.
// Every element is drawn for the moment of the read, even one content-visibility: auto skips until it scrolls into view:
// innerText leaves such an element out, so a song set in blocks like that (Cifra Club's) would arrive only as far as the
// reader had scrolled.
var ExtensionPreprocessingJS = {
    run: function (parameters) {
        var shown = document.createElement("style");
        shown.textContent = "*, ::before, ::after { content-visibility: visible !important; }";
        (document.head || document.documentElement).appendChild(shown);
        var text;
        try {
            text = document.body ? document.body.innerText : "";
        } finally {
            shown.remove();
        }
        parameters.completionFunction({
            text: text,
            title: document.title || "",
            address: document.location.href,
        });
    },
    finalize: function () {
    },
};
