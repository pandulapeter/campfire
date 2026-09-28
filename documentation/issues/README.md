<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Ninth review: the code since the eighth

**Reviewed commit:** `42c970dd` on `master`, clean tree. **Angle:** bugs in the code the eighth review never saw —
`3ea540f9` (the list rows and their actions, Settings' back preview), `339f3330` (dates for setlists) and `42c970dd`
(the song details rework). **Budget:** lean — three Sonnet reviewers (setlist dates: data and domain; lists and dialogs;
song details). There was no live run.

## Headlines

Nothing high. The one that every upgrading user would notice:

- On the first launch of this version, every existing setlist has no date and the list becomes **alphabetical**. The
  user chose to accept this (D1), so the release notes should say it. The plan that would have kept the order is
  rejected.
- **01** — a setlist file whose `date` is a number or an object cannot be read at all, although the field promises
  that a bad date loses only itself.
- **04** — in landscape on a phone, the first section of a song sits alone in a one-column row under the taller
  header.

## Index

| # | Plan | Severity | Lane |
|---|------|----------|------|
| 01 | Read a setlist whose date is not text, losing only the date | low | A |
| 02 | Keep the date of a library setlist that an undated incoming one replaces | low | A |
| 04 | Let the first row of sections use the whole screen when the header leaves too little of it | low | B |
| 05 | Show the date picker's calendar in the language the app is set to | low | B |
| 06 | Keep the song's title readable beside the text-size stepper in performance mode (option A, **D2**) | low | B |
| 07 | Test the transposition stepper's label again | low | B |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| A | setlist data and domain | 01, 02 | `data/model` `Setlist.kt`; `data/source/local/implementation` (`SetlistDocument`, `SetlistMappers`, their tests, its `CLAUDE.md`); `domain/implementation` (`ImportFilesUseCaseImpl`, `GetScreenDataUseCaseImpl`, their tests, its `CLAUDE.md`); root `CLAUDE.md` |
| B | presentation | 07, 04, 05, 06 | `presentation/**` (`songDetails/*`, `dialogs/Dialogs.kt`, `ui/platform/CalendarLocale*.kt`, tests, `presentation/CLAUDE.md`) |

**Merge order: A, then B.** The two lanes share no file. Lane B touches no root `CLAUDE.md` paragraph.

## Decisions

- **D1 (rejected plan 03): accept the alphabetical order.** Decided by the user on 2026-09-28. Existing setlists
  are undated after the update and fall back to title order, and the release notes say so. The plan to restore the
  order from the old `priority` field was deleted.
- **D2 (plan 06): option A.** Decided by the user on 2026-09-28. In performance mode, drop the cover from the app
  bar when the title would get less than 160 dp.

## Challenge

Two fresh reviewers tried to break every fix, one per lane. All seven plans were amended and none was dropped (03 was rejected later, by decision D1):

- **02:** the fallback date now follows whether that write actually replaces the library file (`shouldReplace`, computed
  once), not the user's answer. A setlist the import numbers is new, and is dated today.
- **05:** Material's headline formats with the system locale, so the plan now builds its own headline with the app's
  locale. The picked day is carried only while the calendar is open.
- **01:** the serializer needs an opt-in and an import, both now in the plan.
- **04:** the helper lives in `SectionGrid.kt`, and a test was added for exactly half.
- **06:** one `showsCoverInBar` flag gates both the drawing and the width, and the stepper width is a shared constant.
- **07:** `KEY_SEPARATOR` becomes internal.

## Checked and found solid

- Setlist dates:
  - "Today" is the device's local day, and the web bundles its own tz database.
  - An absent date stays absent through a round trip, and no read path writes a date back.
  - An undated incoming setlist is still IDENTICAL to a library copy that was dated at import, the demo setlist included.
  - Versions from 4.2.3 on keep an unknown `date` key when they save (`unknownFields`).
  - Sorting puts the latest day first, sorts a day's setlists by title, and puts undated after dated and archived last.
  - The stored `newest_first` preference maps to `BY_DATE`.
- Date picker:
  - UTC conversion both ways, with no off-by-one-day in any zone.
  - Cancel keeps the old date, and Done is disabled with nothing selected.
  - The date survives rotation and process death as ISO text.
  - Confirm goes through `confirmOnce`.
- Strings: every new key is in both files, and the formatted date string gets all three arguments.
- Lists and actions: overflow menus are dropdowns, menu keys are unique, and no lambda captures a stale item. The slot
  arithmetic is sound, and the pushed-header copy takes no touches.
- `SectionHeader` refactor: `sectionHeaderEndInsets` gives the same numbers, and the header stays unfocusable.
- Settings back-gesture preview: cancellation and re-entrancy are sound.
- Song details:
  - The long-song layout budget (`LayoutBudget.fit`) still runs before any layout on every path.
  - A header-height change only reruns the grid DP, not the section measurements.
  - The first frame knows the header height, with no wait gate.
  - The steppers act on their own page's song, and the transposition and font bounds hold.
  - Fold keys are unaffected, and RTL is handled.
- `CoverArt.kt`, `FilePicker.android.kt` and `AndroidManifest.xml` (comment-only changes), and the `SyncRepositoryImpl`
  smart cast (same behaviour).

## Dropped after verification

- **The date picker crashes on a setlist dated outside 1900–2100.** It does not. In Material 3 1.9.0 and in
  1.12.0-alpha03, `DatePickerStateImpl` turns an out-of-range initial date into "nothing selected" and shows today's
  month, so Done is simply disabled.

## Manual checks owed

Listed in each plan. In short:
- 04: a phone in landscape.
- 05: the calendar with the app and the system in different languages, on each platform.
- 06: a 360 dp phone in performance mode.
