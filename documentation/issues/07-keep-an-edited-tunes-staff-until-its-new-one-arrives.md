<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Keep an edited tune drawn as its previous staff until its new rendering arrives, then cross-fade to it

**Challenged:** amended — only step 3's capture of the stand-ins: a plain local read at the effect's start, without
`rememberUpdatedState`, which bought nothing. The rule, the lookup, and the cross-fade stand as written.

**Kind:** bug (flicker) · **Severity:** medium · **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/notation/SongNotations.kt`, new
`.../ui/notation/NotationStandIns.kt`, `.../ui/notation/CLAUDE.md`, `.../ui/screens/songEditor/CLAUDE.md` (Preview),
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/notation/NotationStandInsTest.kt`

(`...` is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation`.) Written against the code as plans
05 and 06 leave it: renderings hold their painted systems, `rememberSongNotations` consults `NotationCache`, and its
state starts from the cache.

## Problem

In the editor's Split view (and Preview), typing inside an `abc` block makes the tune flip from staff notation to
monospace text and back on every pause in the typing, and with it everything below jumps twice.

`SongPreview` (`ui/screens/songEditor/SongPreview.kt`) rebuilds the model 150 ms after the typing stops
(`.debounce(PREVIEW_DELAY_MILLIS)`, `PREVIEW_DELAY_MILLIS = 150L`). The tune's text is then new, so
`abcTuneOffsets(blocks)` gives a new `tunes` map and a new `NotationRequest`, but until the engine answers, `rendered`
still holds the renderings of the old texts, and a run is looked up by its exact text:

```kotlin
// ui/notation/SongNotations.kt, SongNotations.of
val tune = run.abcTune() ?: return null
val rendering = rendered.renderings[tune] ?: return null
```

So the edited run is drawn as its text (`SectionRun` falls through to `SongTabBlock`) for the frames the render takes,
then as notation again. The preview lays out with `sectionMotion = SectionMotion.NONE`, so nothing glides: the sections
under the tune jump to the text's height and back. The effect also deliberately treats the edit as new
(`// A tune whose text alone changed (an edit) is new rather than re-rendered, and arrives the way the edit does.`), so
the new staff snaps in instead of cross-fading. The same happens on the details screen when the song is saved from the
editor beneath it.

## Fix

While a new tune text waits for its rendering, draw it as the rendering of the old text it replaced; when its own
arrives, cross-fade from that stand-in.

1. **Which old tune a new one stands in for** — a pure function in new `ui/notation/NotationStandIns.kt`:

   ```kotlin
   /**
    * The tune of [previous] each tune of [current] is drawn as until its own rendering arrives: the tunes that left
    * [previous], paired in order with those that came into [current], where as many left as came. An edit of one tune
    * is one leaving and one coming; a tune added or removed beside the others pairs with nothing, so no tune is ever
    * drawn as its neighbour's staff.
    */
   internal fun notationStandInsOf(previous: List<String>, current: List<String>): Map<String, String> {
       val currentSet = current.toSet()
       val previousSet = previous.toSet()
       val left = previous.filter { it !in currentSet }
       val came = current.filter { it !in previousSet }
       return if (left.size == came.size) came.zip(left).toMap() else emptyMap()
   }
   ```

   The brief proposed matching by index in `abcTuneOffsets`; pairing only the tunes that changed is that same position
   rule restricted to where it is safe: pure index matching would, for a tune inserted above another, draw every tune
   below as the staff of the one above it until the render lands. Editing several tunes at once (a find-and-replace)
   still pairs them in order.

2. **`SongNotations` takes the stand-ins.** In `rememberSongNotations`:

   ```kotlin
   val standIns = remember(tunes, rendered) {
       notationStandInsOf(previous = rendered.request?.tunes?.keys?.toList().orEmpty(), current = tunes.keys.toList())
           .filterValues { it in rendered.renderings }
   }
   ```

   pass it into `SongNotations(rendered, standIns, unitSize)`, and in `of`:

   ```kotlin
   val rendering = rendered.renderings[tune] ?: standIns[tune]?.let { rendered.renderings[it] } ?: return null
   ```

   with `isRerendered = tune in rendered.rerenderedTunes` as before (false for a stand-in: it is what was already on
   screen). Add `standIns` to the `remember` that builds `SongNotations`. A stand-in has the old tune's height, so the
   sections hold still while the engine works, and the run's slots are the new text's (`runSlotCount` of the new run):
   the slots a stand-in has no system for draw nothing, as for any rendering.

3. **The new rendering cross-fades from its stand-in.** In the effect, capture the stand-ins the screen shows when the
   request starts (`val shownStandIns = standIns` as the effect's first line: the effect is launched by the
   composition whose request changed, which computed `standIns` for that request, and nothing but this effect changes
   `rendered` meanwhile, so no `rememberUpdatedState` is needed) and widen `rerenderedTunes`:

   ```kotlin
   rerenderedTunes = (if (isRerendering) renderings.keys.intersect(rendered.renderings.keys) else emptySet()) +
       renderings.keys.intersect(shownStandIns.keys)
   ```

   and replace the "is new rather than re-rendered" comment: an edited tune replaces the staff it was drawn as, which
   it cross-fades with (`rememberNotationCrossfade` sees new systems, `isRerendered` true, and `lastRun` the stand-in's).
   A tune with no stand-in (a new block, the first one typed) still arrives as data, replacing its text at once.

4. **Plan 06's cache**: a hit makes no stand-in necessary (the tune is in `rendered` at once), and undoing an edit back
   to a text seen a moment ago is a hit. Nothing else changes. If plan 08 lands with renderings keyed by tune and
   offset, `notationStandInsOf` takes and returns those keys instead of strings; the rule is the same.

5. **Docs.** `ui/notation/CLAUDE.md`: a tune whose text changed is drawn as the staff it replaced (the tunes that left
   paired in order with those that came, where as many left as came) until its own rendering arrives, and cross-fades
   from it. `ui/screens/songEditor/CLAUDE.md`, Preview: typing in a tune keeps its staff until the new one is engraved.

## Tests

`NotationStandInsTest` (commonTest):

- `` `an edited tune stands in as the staff it replaced` ``: `previous = [a, b, c]`, `current = [a, b2, c]` gives
  `{b2: b}`.
- `` `a tune added beside the others stands in for none` ``: `[a, b]` → `[a, x, b]` gives `{}`.
- `` `a removed tune leaves the others their own` ``: `[a, b, c]` → `[a, c]` gives `{}`.
- `` `several tunes edited at once pair in order` ``: `[a, b]` → `[a2, b2]` gives `{a2: a, b2: b}`.
- `` `an unchanged song needs no stand-in` ``.

The flicker itself is a matter of a few frames during typing in a live editor; the pure rule above is what decides it,
and a UI test timing the debounce against the engine would test the clock, not the behaviour, so none is added.
