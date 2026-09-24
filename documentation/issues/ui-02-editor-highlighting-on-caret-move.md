<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Reduce full-document work when the editor caret moves

Priority: high for long songs. Affects all platforms, especially the single-threaded web UI.

## Evidence

`ChordProOutputTransformation.transformOutput` converts `originalText` to a new `String` and calls `addStyle` for every cached token (`ChordProOutputTransformation.kt`, around lines 37-50). Its cache intentionally avoids tokenizing again when only the caret or selection changes, but the field still invokes the transformation on those changes (comment around lines 72-88). The copy, content comparison, and style loop therefore still scale with the full document during arrow-key repeats and selection drags.

## Implementation plan

1. In `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProOutputTransformation.kt`, first separate **change detection** from **token generation**. Compare `originalText` with the cached text by characters (for example, length followed by `contentEquals`), not by `CharSequence` reference equality, before calling `toString()`. Convert to a `String` only when the content differs. Keep offsets and token order unchanged.
2. Check the Compose `OutputTransformation` API available in this repository for a reliable way to skip reapplying styles on selection-only changes or to style only the visible range. If it does not offer that, retain the full style loop; do not introduce a stale-color or stale-offset bug to avoid it. Record that limit in the code comment.
3. Add a focused cache test proving that identical content supplied as a different `CharSequence` does not retokenize or allocate a replacement cache string. Cover an edit at the beginning and one at the end, where token offsets change. Keep the existing highlighter tests passing.
4. Compare a long ChordPro file on web before and after: hold an arrow key, drag a selection, type near the start and end, then change theme while the editor is open. Use a browser Performance trace to check long tasks and allocation; preserve correct highlighting throughout.

## Done when

- Caret-only changes no longer create a full-document `String` for the token cache.
- Edited text and theme changes retain the same syntax colors and offsets.
- The trace shows whether `addStyle` remains the dominant cost; if it does, document a separate API-level follow-up instead of claiming the whole issue is solved.
