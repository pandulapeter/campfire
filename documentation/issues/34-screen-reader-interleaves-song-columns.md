# 34 · On wide windows a screen reader reads a song's lines out of order, mixing the columns together

**Severity:** wrong behaviour, accessibility (Android and iOS, whenever a song is laid out in more than one column:
tablets in landscape, foldables, large phones in landscape at small text sizes. Certain for a screen reader user in
that layout; desktop's bridge was not traced) · **Area:** `:presentation` (`ui/screens/songDetails/SongLyrics.kt`)

## Symptom
Open a song on a tablet in landscape (two or more columns) with TalkBack or VoiceOver and swipe through it. Line 1 of
the first verse is followed by line 1 of the section at the top of the second column, then line 2 of the first verse,
and so on: the columns are read interleaved, line by line. A chorus card in column 2 is read as one block in the middle
of column 1's lines, wherever its top happens to fall. The editor's split preview does the same. Only a one column
layout reads correctly.

## Cause
The sections are emitted straight into `SongSectionsLayout` (`SongLyrics.kt:201-248`). A non-carded section is a bare
`Column` (`SongSectionContent`, `:410-412`) with no semantics, so it is not a traversal group, while a carded one is a
Material 3 `Surface`, which is.

Compose's traversal order (Compose UI 1.12.0 `SemanticsSort.kt`, the same code on Android and, through
`sortFlattenChildren` in `SemanticsNodeUtils.ios.kt:420`, on iOS) collects every focusable node under the nearest
traversal group into one list (`geometryDepthFirstSearch`), groups that list into rows by overlapping vertical bounds
(`placedEntryRowOverlaps`, which narrows a row's band as nodes join it) and reads each row left to right. Every lyric
`Text` of every non-carded section therefore competes in one list, sorted by y first: two lines at the same height in
two columns land in one row and are read one after the other. The only thing sorted after the geometry is
`traversalIndex` (`UnmergedConfigComparator`, a stable sort), which nothing here sets.

## Fix
Make every section a traversal group placed by its index in the song, and the section layout a group of its own.

`SongLyrics.kt`, in `SongLyrics`, where `sectionModifier` is built (`:203-208`), wrap the result:

```kotlin
// Each section is read as a whole and in the order the song declares, whatever column it was put in: the reading
// order is otherwise worked out from the geometry, line by line across the page, which with two columns reads the
// first line of each, then the second line of each.
val sectionModifier = if (extraWidth > 0.dp || bounds.isTooTallToAnimate) {
    Modifier
} else {
    Modifier.animateBounds(this@LookaheadScope).layoutId(AnimatedSectionLayoutId)
}.semantics {
    isTraversalGroup = true
    traversalIndex = index.toFloat()
}
```

It then reaches the comment, the card `Surface` and the bare `SongSectionContent` alike, since all three take
`sectionModifier`. Keep the existing comment about `maxAnimatedSectionHeight` above it.

And give `SongSectionsLayout` itself `isTraversalGroup = true`, so that the indices only compete with each other and
not with the metadata header above: at its call site (`:192`) pass
`modifier = Modifier.semantics { isTraversalGroup = true },` as the first argument (it already takes a `modifier`,
`:722`, applied to its `Layout`).

Imports: `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.semantics.isTraversalGroup`,
`androidx.compose.ui.semantics.traversalIndex`.

The dividers of the horizontal flow have no semantics and are unaffected. The horizontal flow reads correctly too,
since the index is the song's order in both modes.

## Tests
None (UI).

## Verify
1. Android tablet emulator in landscape, TalkBack on, "House of the Rising Sun" laid out in two columns: swiping
   reads the whole of each section before the next, in the song's order, with the chorus card where the song has it.
   The metadata header (tags, capo line) comes first.
2. The same with horizontal section flow on.
3. Phone portrait: unchanged order.
4. iPad simulator with VoiceOver: the same as 1.
5. The column animations on resize and on a text size change still run (the semantics modifier must not replace the
   `animateBounds` / `layoutId` chain; it is appended to it).
6. Compile: `:presentation:compileKotlinDesktop`, `:app:android:assembleDebug`,
   `:app:ios:linkDebugFrameworkIosSimulatorArm64`.

## Docs
`presentation/CLAUDE.md`, in the song details paragraph that describes `SongSectionsLayout` (after "…the block is
centered."), add: "Each section is a traversal group with its index as the `traversalIndex`, so a screen reader reads
it whole and in the song's order whatever column it is in; the geometry alone read the columns across, line by line."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 33 and 37 also edit `SongLyrics.kt` (other functions); run them one after another.
