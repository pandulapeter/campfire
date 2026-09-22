# 37 · Tapping a section's header scrolls it to somewhere below the top of the screen, by the height of the song's tag and metadata header

**Severity:** minor (all platforms; every tap on a section header of a song that has anything above its first
section — which on the details screen is always, since the language and "Add tag" chips are always there outside
performance mode; the stop is 50–60dp short at the default text size and a few hundred dp short at the largest one
with two metadata lines) · **Area:** `:presentation` (`SongLyrics.kt`: `SongLyrics`)

## Symptom
1. Open a song with several sections (the demo songs will do) on a phone.
2. Scroll until a later section's header pill has gone past the top, scroll back a little so the pill is visible, tap
   it.

The section is meant to go "back to the top of the screen" (the code's own comment), and it stops with its header
well below the top: the gap is exactly the height of the header block above the first section (tag row, capo /
tempo line, composer line) plus the top padding. With the text size at 250% and a song declaring capo, tempo,
composer and album, the section stops a third of a phone screen down, with the end of the previous section still
showing above it. The first section's header "works" only because its target is 0.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt:248`:

```kotlin
onHeaderClick = { coroutineScope.launch { scrollState.animateScrollTo(bounds.top) } },
```

`bounds.top` is published by `SongSectionsLayout` (`:836`, `sectionBounds[index].top = arrangement.tops[index]`) in
the coordinates of that layout, whose top is not the top of the scrolled content: `SongLyrics` is one `Column`
(`:174`) holding `SongMetadataHeader` first and the `LookaheadScope { SongSectionsLayout }` under it, so the sections
start `headerHeight` further down — the very height `SongLyrics` already measures for the column search
(`:172-183`, the `.layout { … headerHeight = placeable.height … }` around the header, which includes the header's
bottom `SECTION_GAP` padding). On top of that the caller's top padding (8dp, inside the `verticalScroll`, see
`SongDetailsScreen.kt:441-447`) sits above the header. Scrolling to `bounds.top` therefore leaves the section at
`8dp + headerHeight` from the top of the viewport.

## Fix
Scroll to the section's position in the scrolled content, which is the header's height plus its position in the
layout. `headerHeight` is already state in `SongLyrics`, read at the time of the tap:

```kotlin
// Scrolls the section back to the top of the screen. The sections start under the song's header, so its height is
// part of where they are; the top padding the caller puts above everything is left showing, as it is at the start
// of the song.
onHeaderClick = { coroutineScope.launch { scrollState.animateScrollTo(headerHeight + bounds.top) } },
```

That lands the header pill 8dp below the top edge, the same distance the song's own first line starts at.
`animateScrollTo` clamps to `maxValue`, so a section near the end of a short song still stops where the song does.

Do **not** use `bringIntoView` / `BringIntoViewRequester` (that scrolls as little as needed, so a section that is
partly on screen would not move), and do not compute the offset from `positionInRoot` of the section (it would also
count the app bar and change during the `animateBounds` spring).

## Tests
None (UI is untested).

## Verify
1. `./gradlew :app:desktop:run`, text size 100%: open a demo song, scroll a later section's pill near the bottom,
   click it — the pill ends up 8dp under the app bar, like the first line of the song at the top.
2. Text size 250% (Ctrl + scroll), a song with capo, tempo and composer: same result.
3. Performance mode on (no chips): same result.
4. The editor's preview: click a header there — same behaviour.
5. `./gradlew :app:android:assembleDebug`, repeat 1 on a phone.

## Docs
None — `presentation/CLAUDE.md` already says a header click scrolls back to the start of the section.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`

## Depends on
Nothing.
