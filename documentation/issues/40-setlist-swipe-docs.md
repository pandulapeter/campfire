# 40 — The docs describe a swipe-to-remove on setlist rows that no longer exists

**Severity:** documentation (all platforms) · **Area:** `presentation/CLAUDE.md`, one code comment in
`screens/setlists/SetlistsScreen.kt`, the testing scripts

**Read, not run.** This was found by reading the code and the history at HEAD (2065e47f). Nothing to reproduce: the
change is to documentation only.

**Decided by the user on 2026-09-23 (decision D-C): the swipe is not restored.** A song leaves a setlist through the
row's overflow menu (Setlist assignments), and the documentation is brought in line with that. No behaviour changes.

## What the user sees

Nothing in the app. A reader of `presentation/CLAUDE.md` or of the testing scripts is told that a setlist row can be
swiped away, looks for it, and a tester marks CORE-102 and AND-051 as failed for a gesture that was removed on purpose.

## Cause

There is no `SwipeToDismissBox` (or any swipe handling on setlist rows) anywhere in `presentation/src`; it was removed
in 80f047d9 ("Setlist handling improvements."), the same commit that wrote the text below.

`presentation/CLAUDE.md:52` (Performance mode):

> … the two "New" buttons, the per-song menu (and the long press that opens it on touch), the actions menu of a
> setlist header, the drag handle, the overflow menu and the swipe a setlist row reorders and removes itself by, the
> "Add to setlist" action, …

`presentation/CLAUDE.md:58`:

> - **A setlist row is reordered and removed in more than one way on purpose**, since neither gesture announces itself:
>   the row drags from its handle (…) and also from a long press anywhere on it (…) — both of them absent from a
>   setlist of one song, which has no order to change — and a song leaves the setlist either by being swiped away or
>   through the row's overflow menu. … It offers no "remove" of its own: **one `Setlist assignments` entry covers both
>   directions**, …, and unticking the box of the setlist the row is in is the swipe written as a list.

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt:525-526`:

```kotlin
    // Nothing is locked: the sheet's box for this very setlist is what unticks the song out of it, which is the
    // swipe written as a list rather than as a gesture.
```

Testing scripts:

- `documentation/testing/00-core-functional.md:298` (performance mode expectation): "… the setlist header menu, the
  drag handles, row swipes, "Add to setlist", …"
- `documentation/testing/00-core-functional.md:587` (CORE-102 step 5): "Swipe two songs out quickly." and `:591`
  "Both swiped songs are gone from the file."
- `documentation/testing/01-android.md:246` (AND-051): "… long press a row and drag; swipe a row away; use ⋮ → Move up
  / Move down."

`documentation/testing/02-ios.md:209` (IOS-044, "Setlist row: swipe up/down → Move up / Move down") is VoiceOver's
custom actions rotor gesture, not a swipe on the row, and stays.

## The change

Documentation only (plus one code comment). Invoke the **`code-style`** skill before editing the `.kt` comment.

1. `presentation/CLAUDE.md:52`: "the drag handle, the overflow menu and the swipe a setlist row reorders and removes
   itself by" → "the drag handle and the overflow menu a setlist row is reordered and removed by".
2. `presentation/CLAUDE.md:58`, rewrite the start and the last clause:
   - "**A setlist row is reordered and removed in more than one way on purpose**, since neither gesture announces
     itself:" → "**A setlist row is reordered in more than one way on purpose**, since neither gesture announces
     itself:"
   - "— and a song leaves the setlist either by being swiped away or through the row's overflow menu." → "— and a
     song leaves the setlist through the row's overflow menu."
   - "and unticking the box of the setlist the row is in is the swipe written as a list." → "and unticking the box of
     the setlist the row is in is how the song is taken out of it."
3. `SetlistsScreen.kt:525-526`:

   ```kotlin
       // Nothing is locked: the sheet's box for this very setlist is what unticks the song out of it, which is the one
       // way a song is taken out of a setlist.
   ```

4. `documentation/testing/00-core-functional.md:298`: delete "row swipes, ".
5. `documentation/testing/00-core-functional.md` CORE-102: delete step 5 ("Swipe two songs out quickly.") and the
   sentence "Both swiped songs are gone from the file." from its Expected.
6. `documentation/testing/01-android.md:246` (AND-051): delete "swipe a row away; ".

## Tests

- None: no behaviour changes. `./gradlew :presentation:compileKotlinDesktop` after the comment edit, for form's sake.

## Verification

1. `grep -rn -i "swip" presentation/CLAUDE.md documentation/testing/00-core-functional.md documentation/testing/01-android.md`
   finds nothing about setlist rows (the remaining hits are back gestures, pagers, sheets and the app switcher).
2. On any build, a setlist row still does not react to a sideways swipe (the pager-free Setlists screen scrolls
   vertically only), and ⋮ → Setlist assignments → untick removes the song — which is what the docs now say.

## Docs

This plan is the docs change.

## Files touched

- `presentation/CLAUDE.md`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/setlists/SetlistsScreen.kt`
  (comment only)
- `documentation/testing/00-core-functional.md`
- `documentation/testing/01-android.md`

## Depends on

Nothing. Plan 38 edits `SetlistsScreen.kt:259-264`, and plans 35, 36 and 43 edit other entries of `01-android.md`;
either order. Plan 47 (lane E) edits `04-windows.md` and `05-linux.md` only.
