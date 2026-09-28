# Eighth review — what is left (2026-09-28)

Plans 01–11 of the eighth review (six from the pre-launch review at `cf588adf`, five from the continuation at
`b8cc0bc2`) have landed on `master`, one commit each, each commit removing its plan. The two plans still here were
**not carried out on purpose**, and wait for a decision:

| # | Plan | Why it was not carried out |
|---|---|---|
| [12](12-first-lyrics-preparation-blocks-navigation.md) | Move the first lyrics preparation out of composition | The first model is built during composition deliberately, so that a page never opens on an empty frame; building it in an effect brings a loading state back to every song opened and every pager page composed. The time it measured is not where a huge song's stall is: with 06 in place, opening a 3.3 MB song stalls the desktop UI for 0.4–1.3 s, and thread dumps of that stall are in Skia's picture recording and in text layout, never in the parse. Recommended: drop. |
| [13](13-library-export-has-unbounded-peak-memory.md) | Bound export memory before building the whole archive | The patch it proposes refuses every export above a fixed budget, so a library that backs up fine today (above all on the desktop) could no longer be backed up at all, which is worse than the risk it removes. A realistic library is a few megabytes of text: 3000 songs are about 9 MB. The real fix, a streaming zip writer behind a streamed export contract on all four platforms, is a project rather than a patch. Recommended: drop for this release. |

## Measured after 06 (desktop debug build, 3.3 MB song of 60 000 lines)

| | Longest frame gap | Resident memory peak |
|---|---|---|
| Before (no budget) | 11 991 ms | 3.5 GB, 0.9–1.2 GB still held 10 s later |
| After, a songbook (a blank line every 8 lines) | 760–1 345 ms | 590–710 MB, back to ~200 MB after leaving |
| After, one paragraph with no blank line | 383–568 ms | 560–710 MB |
| A normal song, for comparison | 210 ms | 230 MB |

What is left of the stall is the 3000 lines the budget allows being laid out and drawn at once. Lowering the budget
is the lever if that still matters; no real song comes near it.

## Manual checks owed

- 01 on a real slow link (Network Link Conditioner "Edge" or the emulator's GPRS profile) with about 2000 songs.
- 02 with `setState` made to throw: pick a color other than the app's own, leave the app, come back — no crash.
- 03 on Windows and Linux: `campfire.log` appears in the data directory and rotates past 1 MB (checked on macOS with
  the release build).
- 04 in Safari 18 (no `createWritable`): reload in the middle of saving a large song; it comes back whole. The journal
  itself is covered by `node --test app/web/tests/opfs-writer.test.cjs`.
- 05 with Android's "Force RTL layout direction": back, previous / next and undo / redo point the other way.
- 06 on the Android emulator with a 2 GB RAM profile and a 3 MB `.cho`: no crash, the notice and its "Edit song" at
  the end.
- The first-keystroke frame gap of the songs search, profiled on a low-end phone.
