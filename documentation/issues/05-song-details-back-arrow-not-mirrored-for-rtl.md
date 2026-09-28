# Mirror the song details back arrow under a right-to-left layout

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (a system locale that reads right to left)
**Files:** presentation/src/commonMain/composeResources/drawable/ic_back.xml, presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt

## Problem
The song details app bar draws its back arrow with `painterResource(Res.drawable.ic_back)` in a plain `Icon`
(`SongDetailsScreen.kt:281`). `ic_back.xml` is a left-pointing arrow without `autoMirrored`, and nothing flips it for
`LocalLayoutDirection.Rtl`. The rest of the chrome mirrors (the paddings, the rows, the Material components), so on a
device whose system locale is Arabic, Hebrew, Persian or Urdu the whole bar is mirrored except the one arrow, which
then points forward. The in-app language only offers English and Hungarian, but the layout direction follows the
system, and these users are among the first a store launch brings. It is the only directional glyph in the icon set;
`ic_clear`, `ic_expand` and `ic_drag_handle` are symmetric.

## Fix
Add `android:autoMirrored="true"` to `ic_back.xml` and check that Compose Multiplatform's resource pipeline honours
it on desktop and wasmJs as well as Android (it reads the attribute into `ImageVector.autoMirror`). If it does not on
some target, wrap the one usage in `Modifier.graphicsLayer { scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f }`
reading `LocalLayoutDirection.current`.

## Verification
Manual: Android developer options, "Force RTL layout direction", open a song: the arrow points right. The desktop
app with `-Duser.language=ar` for the same.

## Conflicts
None.
