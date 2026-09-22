# 42 · A long tag in the song header hides its own remove button

**Severity:** minor (all platforms. Unlikely: needs a tag long enough to fill the header's width on its own, which
typed tags rarely are, but imported files can carry one; the tag can then only be removed by editing the file) ·
**Area:** `:presentation` (`ui/components/Tags.kt`)

## Symptom
A song carries `{tag: Songs of the British Invasion, 1964-1967 compilation}`. On a phone, or at a large system font,
its pill in the song details header fills the row with the ellipsized text and the "x" that removes it is gone. The
same happens to the language chip's icon side if its label ever gets that long.

## Cause
`TagPill`'s content (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Tags.kt:107-148`)
is a `Row` of an optional leading `Icon`, a `Text(maxLines = 1, overflow = Ellipsis)` with no weight, and the
trailing `Box(Modifier.size(TAG_TRAILING_ICON_SIZE))`. A `Row` measures its unweighted children in order with what is
left, so the text takes the whole width and the box is measured at zero. The pill is used at
`ui/screens/songDetails/SongLyrics.kt:300-305` inside a `FlowRow`, which bounds it at the row's width.
`SectionHeader` has the same shape and solves it with `.weight(1f, fill = false)`.

## Fix
`Tags.kt`, the pill's `Text` (`:122-128`):

```kotlin
Text(
    // Weighted so that the icons are measured first: a tag as long as the row is ellipsized rather than pushing
    // its own remove button out of the pill.
    modifier = Modifier.weight(1f, fill = false).padding(vertical = TAG_TEXT_PADDING),
    ...
)
```

`fill = false` keeps a short tag at its own width, so every pill that fits looks exactly as before. No width cap is
added: the flow row already wraps a long pill onto a line of its own, and the pill's text still shows as much of the
tag as that line has room for.

## Tests
None (UI).

## Verify
1. Add a 60-character tag to a song on a phone (the header's add-tag dialog, or the editor): its pill ellipsizes and
   the "x" is still there and removes it.
2. Short tags, the language chip and the "Add tag" pill look unchanged; the song lists' tags (no trailing icon) too.
3. Compile: `:presentation:compileKotlinDesktop`.

## Docs
None: nothing documented changes.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/Tags.kt`

## Depends on
Nothing.
