# Let a tool's instrument pill share its line only where it fits whole, so that the Tuner on a phone names its tuning

**Kind:** bug (layout)  ·  **Severity:** medium  ·  **Platforms:** all (any window where a tool's line is narrow: a 360 dp phone at the default text size)
**Challenged:** amended — measured that the rule also sends the Chords and Scales tabs' capo under the pill on every 360 dp phone, English included (its pill's room is exactly 161 px and its names already overflow it), so the decision, the Problem, and the docs now say so, a Chords-tab UI test proves it, and the touch tests get a note.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/ToolInstrumentLines.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/ToolInstrumentSetting.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/InstrumentPillWidths.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/tuner/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/TunerScreen.kt` (KDoc only, if it still says "where the line is too narrow for both"),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/instrument/ToolInstrumentLinesTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/tuner/TunerInstrumentPillTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/chords/ToolCapoLineTest.kt` (new)

**Decision (user):** this costs the Tuner on a 360 dp phone at the default text size one more pinned line (the reference
pitch moves under the pill, as it already does at 130% text), and moves the Chords and the Scales tabs' capo under
their pill on a 360 dp phone in **every** language, English included (measured below; that line scrolls away with the
grid, so it costs no pinned height there, but both tabs look different on every phone). Recommended: accept both
(option A), one rule for every tool's line. The user's earlier ruling
("the Tuner's pill wraps at 200%", 40th review) chose moving the line over cutting the pill; this extends it to where the
cut leaves nothing readable.

## Problem

On the Tuner tab of a 360 dp phone at text scale 1.0, in **every** language (English included), the instrument pill
reads `Gui…` and its tuning half is an **empty** grey box where "Standard" should be (live run,
`sheets/tuner-100.png`: `Gui… |       ` / `Chi… |` / `Gita… |` / `Gui… |`). The probe's text layouts confirm it:

```
tuner	MAXLINES	[32,78 40x20]	"Guitar"
tuner	MAXLINES	[97,78 17x20]	"Standard"
```

(the same 40 px / 17 px for ro, de, es). At 130% the pill moves to a line of its own and is whole
(`sheets/tuner-130.png`). Not a render artifact: the UI tests' `TestWindow.PHONE` is the smallest supported phone at a
density of one, drawn in the app's bundled fonts; the store screenshots never show this line (their tuner shot is the
tuner *sheet* over the metronome, `tools/screenshots/.../Shot.kt` `03-metronome-tuner`).

Cause, in `ToolInstrumentLines.kt`:

```kotlin
internal fun pillSharesLine(
    lineWidth: Int,
    trailingWidth: Int,
    gap: Int,
    pillWidth: Int,
    hasTuning: Boolean,
    nameRoom: Int,
    minNameWidth: Int,
): Boolean {
    val room = lineWidth - trailingWidth - gap
    if (room >= pillWidth) return true
    val shownName = if (hasTuning) (room * TUNING_SHARE).roundToInt() - nameRoom else room - nameRoom
    return shownName >= minNameWidth
}
```

with `minNameWidth = fontSize * MIN_SHOWN_NAME_EMS` (`MIN_SHOWN_NAME_EMS = 1f`, 14 px at `titleSmall`). Its KDoc says
the 360 dp Tuner leaves the pill 114 dp (the reference pitch takes 214 dp of the 336 dp line), "the tuning's share of it
about 17 dp of name", and that one em (14 dp) "keeps that line whole with a little margin". The premise is wrong: 17 dp
is less than one glyph plus the ellipsis (`S…` needs ~20 dp at 14 sp), and a single-line `Text` with
`TextOverflow.Ellipsis` that cannot fit one character and the ellipsis draws **nothing**. The instrument half fares little
better: `tuningHalfMaxWidth(114, ~74)` keeps 46 dp for the tuning, leaving the instrument 68 dp, 40 dp of name: `Gui…`.
`tuner/CLAUDE.md` repeats the false premise ("the default size on a 360 dp phone keeps about 17 dp").

The existing tests codify it: `ToolInstrumentLinesTest` `a cut pill whose share still shows the tuning shares the line`
(`shares(pillWidth = 161)` with `minNameWidth = 14`), and `TunerInstrumentPillTest` only asserts the tuning node is
`> 0` px wide, which a node with an empty ellipsized line is.

The same rule serves the Chords and Scales tabs' capo after the pill (`isTrailingAfterPill`). A probe at HEAD (English,
`TestWindow.PHONE`, text scale 1, both tabs alike): the pill spans 16–177 and the "Capo" label starts at 185, so the
pill's room is 161 px, exactly what it is given, and both names report `didOverflowWidth` (the pill's uncut width is a
pixel or two over its room; "Standard" is drawn cut). Option A therefore moves the capo under the pill on any 360 dp
phone in English, and the wider labels (German "Kapodaster", Romanian "Capodastru") have less room still. Wider
windows (a phone on its side, 640 dp) keep it beside.

## Fix

Any threshold that really shows a tuning name needs about an ellipsis plus two or three letters (≥ 35 dp) past the 29 dp
of padding and divider, which the 40% share only reaches at a room of ~160 dp, about the whole pill's width. So the
cut-but-shared state buys nothing on a real phone; drop it.

**Option A (recommended): the pill shares its line only where it fits whole.**

1. `ToolInstrumentLines.kt`: reduce `pillSharesLine` to

   ```kotlin
   internal fun pillSharesLine(lineWidth: Int, trailingWidth: Int, gap: Int, pillWidth: Int) =
       lineWidth - trailingWidth - gap >= pillWidth
   ```

   KDoc: where the pill, [pillWidth] wide uncut, fits beside its trailing controls; cut, it would show too little of
   either name to read (one em of room past the padding is not one letter and an ellipsis), so it takes a line of its
   own and the controls slide under it. Delete `MIN_SHOWN_NAME_EMS` and its KDoc. Drop the now unused
   `kotlin.math.roundToInt` import if nothing else in the file uses it.
2. `ToolInstrumentSetting.kt`: call `pillSharesLine(lineWidth, trailing.width, gap, measurables[0].maxIntrinsicWidth(lineHeight))`.
   Remove what only fed the old rule: `ToolLineMeasurePolicy`'s `hasTuning` and `fontSize` parameters (and from the
   `remember(...)` keys), `ToolInstrumentLine`'s `hasTuning` parameter, the `hasTuning = shownTuning(instrument, tuning) != null`
   argument, `textStyle` if only `pillHeight` still needs it (keep `songControlHeight(MaterialTheme.typography.titleSmall)`),
   and the imports of `TextUnit`, `roundToInt`, `INSTRUMENT_PILL_TUNING_ROOM`, `INSTRUMENT_PILL_LONE_ROOM`,
   `MIN_SHOWN_NAME_EMS`. In `ToolInstrumentSetting`'s KDoc replace "The pill gives up width before it reaches
   [trailing], cutting its tuning short, and where it could no longer show the tuning beside them it keeps the line to
   itself" with "Where the pill does not fit whole beside [trailing], it keeps the line to itself".
3. `InstrumentPillWidths.kt`: delete `INSTRUMENT_PILL_TUNING_ROOM` and `INSTRUMENT_PILL_LONE_ROOM` (their only reader
   was the policy; grep `presentation/src` and `tools/screenshots/src` to confirm), and the `DividerDefaults` import if
   unused. Keep `tuningHalfMaxWidth` / `TUNING_SHARE`: the pill on a song's line and on a line of its own still cuts the
   tuning first.
4. `ui/instrument/CLAUDE.md`: in the pill bullet replace "the pill takes the line to itself where its share could not
   show an em of the tuning: `pillSharesLine`" with "the pill takes the line to itself wherever it does not fit whole
   beside them: `pillSharesLine`"; in "The tools' capo" replace "where the line is too narrow" with "where the pill
   does not fit whole beside it (any 360 dp phone)". `ui/screens/chords/CLAUDE.md`'s "the capo right after it" stays
   true of a wide window; add nothing there (lane I owns only `ui/instrument` and `ui/tuner` docs, and the instrument
   doc is where the rule lives).
5. `ui/tuner/CLAUDE.md` `## Layout`: replace "the instrument pill gives up width (its tuning cut short) before it reaches
   them. Where it could then show less than an em of its tuning (large text on a phone; the default size on a 360 dp
   phone keeps about 17 dp, as the controls take 214 dp), the pill keeps the line and they slide under it" with "where
   the pill does not fit whole beside them (any 360 dp phone, the controls taking 214 dp; large text), the pill keeps
   the line and they slide under it".
6. `TunerScreen.kt`'s KDoc ("or under the pill, at the same end, where the line is too narrow for both") stays true;
   leave it.

The slide itself (`toolLinePlaces`, `toolLineTrailingX`, the `Animatable`, lookahead) is unchanged, so a window resized
across the threshold still travels, never jumps.

**Option B (not recommended):** keep the shared line and raise `MIN_SHOWN_NAME_EMS` to ~2.5 em. On a 360 dp phone it
moves the pill exactly as A does (the room would have to reach ~160 dp), keeps three parameters and two constants for a
band of widths a few dp wide, and still shows `Gita…` there.

## Tests

- `ToolInstrumentLinesTest` (`desktopTest`): rewrite `shares` without `hasTuning` / `nameRoom` / `minNameWidth`.
  Keep `a pill that fits whole shares the line` (`pillWidth = 100`) and `an unbounded line is shared`. Replace
  `a cut pill whose share still shows the tuning shares the line`, `at the largest text size …`,
  `exactly the least of a name …`, and `without a tuning half …` with
  `` `a pill exactly as wide as its room shares the line and a pixel wider does not` `` (room = 336 − 214 − 8 = 114:
  `shares(pillWidth = 114)` true, `shares(pillWidth = 115)` false). The `places` / `toolLineTrailingX` tests stay.
- `TunerInstrumentPillTest` (`tools/screenshots`):
  - Add a private helper reading a text node's layout as `OnboardingFooterTest` does
    (`config[SemanticsActions.GetTextLayoutResult]`), and assert a name is whole:
    `!multiParagraph.didExceedMaxLines && !didOverflowWidth`. **Not** `isLineEllipsized`: Skiko's paragraph
    (`SkiaParagraph.skiko.kt`) always answers false, so on the desktop JVM it can never fail; the cut "Standard"
    reports `didExceedMaxLines`. Find the name's own `Text` with `useUnmergedTree = true`; the pill's `AnimatedContent`
    composes one `Text` at rest. (If plan 10's `TextFit.kt` has landed when this runs, use its `textLayout` / `isWhole`
    instead of a private copy.)
  - New: `` `on the smallest phone the instrument and its tuning are named in full in every language` ``: for each
    `AppLanguage` but `SYSTEM_DEFAULT`, `runAppTest(appearance = TestAppearance(language = it))`, `openTuner()`, assert
    `instrument_guitar` and `instrument_tuning_standard` whole. Fails on HEAD (17 px, ellipsized to nothing).
  - Change `a long instrument name beside the reference pitch still leaves its tuning in sight` to assert the twelve-string's
    tuning whole rather than `> 0` px wide.
  - `at the default text size the reference pitch keeps the instrument's line` no longer holds on `TestWindow.PHONE`.
    Run it in `TestWindow(width = 600.dp, height = 640.dp)` and rename it
    `` `where the whole pill fits beside it the reference pitch keeps the instrument's line` ``; add the PHONE case to
    the 200% test's sibling: `` `on the smallest phone the reference pitch stands under the instrument` `` (the
    `increase.top >= instrument.bottom` assertion of the 200% test, at text scale 1).
  - The two touch tests (`a touch just above/below …`) still find the halves by text; keep them, and run them: with
    the fix the reference pitch's stepper stands under the pill on `TestWindow.PHONE`, its decrease button starting
    ~138 dp across and 8 dp under the pill, so the touch 6 dp under the tuning half (centred ~130 dp across) lands a
    few dp from that button's 48 dp touch target. If it now opens nothing or steps the pitch, move that test to the
    600 dp window rather than changing the touch.
- New `ToolCapoLineTest` (`tools/screenshots`, package `…ui.screens.chords`), `` `on the smallest phone the capo
  stands under a whole instrument pill` ``: English, `TestWindow.PHONE` (note `ToolCapoPresenceTest` uses a taller
  window; this one needs only the line), `openChords()`; assert `instrument_guitar` and `instrument_tuning_standard`
  whole with the same helper, and the `song_editor_insert_capo` label's top (unmerged node) at or below the guitar
  half's bottom. Fails on HEAD (the names overflow and the label is level with them). The Scales tab draws the same
  `ToolInstrumentSetting`; one tab is enough.
