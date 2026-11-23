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
var ExtensionPreprocessingJS = {
    run: function (parameters) {
        parameters.completionFunction({
            text: document.body ? document.body.innerText : "",
            title: document.title || "",
            address: document.location.href,
        });
    },
    finalize: function () {
    },
};
