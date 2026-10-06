# Read the day of a setlist date written with a time (`2026-10-06T20:00:00`) rather than replacing it with today

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SetlistMappers.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SetlistMappersTest.kt`
- `data/source/local/implementation/CLAUDE.md` (one clause)

## Problem

A setlist's `date` is read with:

```kotlin
private fun String.toLocalDate() = try {
    LocalDate.parse(trim())
} catch (_: IllegalArgumentException) {
    null
}
```

and `isDated` / `toModel` use it (`date?.toLocalDate() ?: undatedDay`). A date written with a time of day — by hand, by a
script, or by another tool that writes ISO date-times, e.g. `"date": "2026-10-06T20:00:00"` or `"2026-10-06T20:00Z"` —
does not parse as a `LocalDate`, so the file counts as **undated**: it is given today and, since 46746e2b1, saved with
today at once (`SetlistLocalSourceImpl.toDatedModel`), and an import of it is dated with the import day and compared as
undated. The day the file plainly names is lost, and written over in the library file. Verified live on the desktop build
(reviewer L2 #3).

## Fix

Read the day part of an ISO date-time:

```kotlin
/** An ISO date, or the day of an ISO date-time (`2026-10-06T20:00:00`), which names the day just as well. */
private fun String.toLocalDate() = try {
    LocalDate.parse(trim().substringBefore('T'))
} catch (_: IllegalArgumentException) {
    null
}
```

`isDated` uses the same function, so such a file counts as dated and is **not** rewritten on read (its text keeps the
time until the user's next change to the setlist writes `date.toString()`). Anything else that does not read as a date
still costs only the date, as documented. In `data/source/local/implementation/CLAUDE.md`, the sentence "A setlist's
`date` is kept as text in the document and read as a `LocalDate`" gains "(the day of an ISO date-time too)".

## Tests

`SetlistMappersTest`: a document with `date = "2026-10-06T20:00:00"` → `isDated` true and `toModel(..., undatedDay = X).date
== LocalDate(2026, 10, 6)`; `" 2026-10-06T20:00Z "` likewise; `"2026-13-01T10:00"` and `"T"` → not dated, `undatedDay` used.

## Manual check

On the desktop, put a setlist file with `"date": "2026-12-24T19:30:00"` into the library folder and launch: the setlist
details sheet shows 24 December 2026, and the file on disk still holds the original text.
