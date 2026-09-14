# 62 · `listItemAnimation` swaps `Modifier.animateItem()` in and out with `isScrollInProgress`

**Severity:** low · **Area:** `:presentation` (`ListItemAnimation.kt`)

`ListItemAnimation.kt:66, 82`: every visible item recomposes at scroll start and end, and a placement animation in
flight is cut when a scroll begins. The reasoning in the KDoc (a fling's last frames mis-place a row that animates)
is sound; the cost is the swap.

## Fix

Keep the modifier and vary its specs instead, which does not change the modifier chain's shape:
`Modifier.animateItem(placementSpec = if (shouldAnimate) spring(...) else null, fadeInSpec = …, fadeOutSpec = …)`.
`animateItem` accepts null specs to disable each animation. The items still recompose when `isScrollInProgress`
flips (it is snapshot state read in composition); if that matters, read it through `derivedStateOf` at the screen
level and pass a `State<Boolean>` down. Verify the fling-to-top case from the KDoc still looks right afterwards.
