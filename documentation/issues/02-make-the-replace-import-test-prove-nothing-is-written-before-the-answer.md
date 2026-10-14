# Make the import review test prove nothing is written before the Replace question is answered

**Kind:** test  ·  **Severity:** medium  ·  **Platforms:** desktop (UI tests)
**Files:** `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReviewTest.kt`

## Problem

`a confirmed replace overwrites the colliding file and nothing else` checks the "nothing before confirmation" rule
(root `CLAUDE.md`: imports are reviewed before writing; collisions replaced after confirmation) with

```kotlin
onNodeWithContentDescription(pluralString(Res.plurals.import_review_confirm, 2)).performClick()
onNodeWithText(pluralString(Res.plurals.import_replace_title, 1)).assertExists()
assertEquals(before, libraryContents())
```

Library writes run on IO threads (`launchLibraryChange`), not on the composition, so a regression that writes the new
song or the replacement on Confirm, before the question is answered, lands a few milliseconds after this assertion
and the test stays green. The final assertion cannot catch it either: the end state is the same.

## Fix

Give the negative step a positive barrier through the same write path. Recommended: at the Replace question, answer
**Cancel** (the dialog's dismiss, not Replace); the review stays up. Then untick nothing, switch the collision row back
to Keep both, press Confirm again, `waitForLibrary` for the two new files, and assert the library is exactly `before`
plus the new song plus the numbered copy — i.e. the house song was never replaced. A write on the first Confirm would
have replaced `HOUSE_OF_THE_RISING_SUN` (or written `NEW_SONG` twice → a `_2` file), which this end state rules out.
Keep the existing replace test as is, minus the racing `assertEquals(before, libraryContents())`, or turn it into this
one; the existing "numbers the colliding one" test already covers Keep both alone.

Drop this plan if Cancel on the Replace question leaves the review screen (then use: Cancel, start the import again
from the menu with Keep both, and assert the same end state).

## Tests

This is the test. Prove it can fail: temporarily make `ConfirmImportReplace`'s caller start the import before the
answer (in a scratch edit, not committed) and see it fail.
