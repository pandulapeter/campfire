# Remember the selection mode's semantics modifier so that song cards can skip

**Challenged:** sound

**Kind:** performance  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songs/SongList.kt`

## Problem

While the songs screen is in the selection mode, every visible song card is composed again on every tick, and on every
scroll start and end, because its modifier is a new, unequal value each pass (`SongList.kt` at ab97d3105):

```kotlin
SongListItem(
    modifier = Modifier.fadingUnderListTop(topFade).selectionSemantics(isSelecting = isSelecting, isTicked = isTicked),
    ...
```

```kotlin
/** A card of the selection mode is one checkbox to a screen reader, the card being what ticks it. */
private fun Modifier.selectionSemantics(isSelecting: Boolean, isTicked: Boolean) = if (isSelecting) {
    semantics(mergeDescendants = true) {
        role = Role.Checkbox
        toggleableState = ToggleableState(isTicked)
    }
} else {
    this
}
```

`selectionSemantics` is not a composable, so the Compose compiler does not memoize the lambda handed to `semantics`; a
new lambda makes a semantics element that never equals the previous one, the modifier chain differs, and
`SongListItem` cannot skip. Every tick changes `selection`, which reruns the grid's item DSL and re-invokes each visible
card's content; every other argument of the card is equal for an untouched card (its lambdas are memoized in the
composable item content, `key` is remembered), so this modifier alone has every visible card — cover, title, labels,
key, and tempo — composed again for one tick. Outside the mode the function returns `this` and cards skip, so only the
selection mode pays.

The section headers' `text = header.displayText(selectionCount = selection.size)` was looked at as a second cause and
is not one: `displayText` ignores the count for a `Section` header, so the string handed to `SectionHeader` is equal and
it skips; the header's item content itself reruns on every tick whatever it reads, since the grid's DSL builds new item
lambdas each pass. Leave it as it is.

## Fix

Make the modifier an equal value across passes for an unchanged card. Options:

1. **Recommended:** remember it at the call site, keyed by what it reads:

   ```kotlin
   val selectionSemantics = remember(isSelecting, isTicked) { Modifier.selectionSemantics(isSelecting = isSelecting, isTicked = isTicked) }
   SongListItem(
       modifier = Modifier.fadingUnderListTop(topFade).then(selectionSemantics),
   ```

   (`Modifier.then(Modifier)` returns the receiver for the empty modifier, so outside the mode the chain is unchanged.)
2. Make `selectionSemantics` a `@Composable` function that remembers its own result. It works the same but hides a
   `remember` in a modifier factory, which the code style avoids where a call site can hold it.
3. A custom `ModifierNodeElement` data class carrying the two booleans. Equal by value without `remember`, but more
   code than the problem needs.

The semantics a screen reader hears stay exactly the same: one checkbox per card, merged, ticked or not.

## Tests

None can be written: whether a composable skips is a property of the composition, which the project does not test by
code (the root `CLAUDE.md`: only pure logic is tested), and the modifier's contents do not change.

## Manual check

On a phone with a long library, start the selection mode and tick and untick cards, then fling and stop the list a few
times. With Android Studio's Layout Inspector showing recomposition counts, a tick recomposes the tapped card (and the
copy that joins the Selection section), not every visible card, and a scroll's start and end recompose no card. With
TalkBack (or VoiceOver) on, a card in the mode is still read as one checkbox with its ticked state, and toggles with a
double tap.
