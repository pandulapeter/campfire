# Document the Setlists screen's minimum column width as the 416dp the code uses

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** sound
**Files:** `CLAUDE.md`, `presentation/CLAUDE.md`

## Problem

The code (`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListColumns.kt:55` at
1c52e5347):

```kotlin
internal val MIN_SETLIST_COLUMN_WIDTH = 416.dp
```

The docs say 440dp:

- root `CLAUDE.md` (Conventions, "A short window gives the keyboard everything it can"): "Song lists keep their 360dp
  minimum column (a setlist's 440dp, since its cards say more on a line)".
- `presentation/CLAUDE.md` (`ui/components/` entry): "The Setlists screen's columns are at least 440dp
  (`MIN_SETLIST_COLUMN_WIDTH`, `ListLayout.setlistColumnCount`)".

All three were written in the same commit (37d9be63c, "Improve Setlist card sizing."), and the constant has not changed
since, so 416 is what was tuned and shipped and the docs are the stale half.

## Fix

Change "440dp" to "416dp" in both sentences. Nothing else.

Decision the user can flip: if 440dp was the intended value, change the constant instead (and leave the docs) — a list
between 1248dp and 1320dp wide (a 1366dp laptop window less the navigation rail) then drops from three columns to two,
so check it on such a window before choosing that.

## Tests

None (documentation only).

## Manual check

None.
