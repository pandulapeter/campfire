<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Frame the Hebrew store images with the bundled Hebrew font and from the right

**Kind:** bug (store images) · **Severity:** low · **Platforms:** the screenshot tool (`tools/screenshots`)
**Files:** `tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/ListingFraming.kt`,
`ListingImage.kt`, `ListingChip.kt`, `CompositionFraming.kt`, `CompositionImage.kt` (same folder), `tools/screenshots/CLAUDE.md`
**Challenged:** amended — `ProvideScriptFaces` is reachable (the tool compiles with `-Xfriend-paths` to `:presentation`,
`build.gradle.kts`), so it is used directly; the direction goes on the text styles only, never as an Rtl
`LocalLayoutDirection` over a scene: `CompositionImage` places its elements with `Modifier.offset(x, y)`, which an Rtl
direction negates, and `DeviceFrame` is offset the same way; the language has to be threaded into `frame` and `draw`,
which do not receive it today.

## Problem

The `--frame store` path composes the headlines, chips, feature graphic, and Apple's header and search results images in
bare `ImageComposeScene`s with `fontFamily = interFontFamily()` (`ListingImage.kt`, `ListingChip.kt`,
`CompositionImage.kt`). It never runs `ProvideScriptFaces` (`presentation/…/ui/theme/ProvideScriptFaces.kt`), which is what
registers Noto Sans Hebrew with the font resolver, and Inter has no Hebrew. The Hebrew headlines
(`screenshot-texts/he.md`) and chips fall back to whatever system font Skia finds: a system Hebrew face on a Mac, empty
boxes on a Linux machine without one. The scenes also provide no `LocalLayoutDirection`, so a Hebrew chip that starts
with a Latin word ("Campfire …") gets a left-to-right base direction and its words out of order.

## Fix

The app's own renders get the Hebrew face through `CampfireTheme`, which wraps everything in
`ProvideScriptFaces(neededScriptFaces) { … }`; on desktop that resolves every `scriptFaces` row through the scene's
`LocalFontFamilyResolver` while composing, which registers them as the Skia fallbacks. Do the same for the framing:

- In `ListingFraming.frame` and `CompositionFraming.draw`, wrap the scene's content in
  `ProvideScriptFaces(neededScriptFaces = emptyFlow()) { ListingImage(…) }` (and `CompositionImage(…)`); the desktop
  actual ignores the flow and registers every row. The existing `LOADING_FRAMES` covers the fonts arriving.
- Thread the listing language (the `language` string both loops already have, `AppLanguage.entries.first { it.id ==
  language }`, `:data:model`) into `frame` and `draw`, and from there an `isRightToLeft` into `ListingImage`,
  `ListingChipPill`, and `CompositionImage`. Set the text's own direction from it, `TextStyle(textDirection =
  if (isRightToLeft) TextDirection.Rtl else TextDirection.Ltr)`, on the headline (`ListingImage`), the chip text
  (`ListingChip`), and the composition's texts (`CompositionImage`), so a Hebrew chip that starts with "Campfire" keeps
  its words in Hebrew order.
- Do not provide an Rtl `LocalLayoutDirection` over a scene: `CompositionImage`'s `Modifier.offset(element.x, …)` and
  `DeviceFrame`'s offset are direction-aware and would move. Inside `ListingChipPill` alone, an Rtl
  `LocalLayoutDirection` around its `Row` puts the icon on the right, ahead of Hebrew text; take it if the image reads
  better so. The chips' corners stay absolute (`ListingChips` places with `place`, not `placeRelative`); whether a
  Hebrew chip should sit at the mirrored corner, since the screen under it is mirrored, is the owner's call, not part
  of this fix.
- Add a line to `tools/screenshots/CLAUDE.md` saying the framing scenes register the script faces and set a
  right-to-left language's text direction.

## Tests

Render `--frame store --only he` (see `tools/screenshots/CLAUDE.md` for the flags) and look at the Hebrew headline and a
chip: Hebrew letters in Noto Sans Hebrew, read from the right. The tool's own tests (`:tools:screenshots:test`) still pass.
