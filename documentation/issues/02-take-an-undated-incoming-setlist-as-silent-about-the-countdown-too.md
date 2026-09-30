# Take an undated incoming setlist as silent about the countdown too, and keep the library's countdown when it replaces one

**Challenged:** amended — the premise "the two arrived in the file format together" is false (dates: `339f33303`, countdown: `a86b22881`, separate commits), so a *dated* file from the date-only build still differs by the flag and stays `CONFLICTING`; the fix is only for the undated file (pre-date exports and the bundled demo setlist, which carries no date), and the plan now says so. Its `ImportFilesUseCaseImplTest` replace case could not be reached as written (an undated copy differing only in the flag is now `IDENTICAL`, never offered for replacing) and is rewritten.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlanner.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/ImportPlannerTest.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`,
`domain/implementation/CLAUDE.md` where it describes the setlist comparison

## Problem

`ImportPlanner.holdsTheSameAs` (at 9ab7ca54e) tolerates a date the incoming setlist does not carry, because a file
written before there were dates, or the bundled demo setlist, cannot say one, and asking about it on every import
would be a question with no answer:

```kotlin
private fun Setlist.holdsTheSameAs(other: Setlist) =
    title == other.title && description == other.description && (other.date == null || date == other.date) &&
        isCountdownShown == other.isCountdownShown && isArchived == other.isArchived && entries == other.entries && unknownFields == other.unknownFields
```

The countdown flag, added since, gets no such tolerance, and a file that carries no date carries no countdown either
(`SetlistDocument.isCountdownShown` defaults to `false`, and nothing older than the date could have written it).
Scenario: the library holds `summer_set_2026.setlist.json`, dated, countdown on. The user imports the zip they
exported before this version, or adds the demo songs again after turning the demo setlist's countdown on. The
incoming copy decodes with `isCountdownShown = false`, `holdsTheSameAs` is false, the setlist is `CONFLICTING`, and the
import asks keep both / replace / skip about a file that is the same list. Choosing Replace writes the library setlist
with the countdown off: `ImportFilesUseCaseImpl` keeps the library's day for an undated replacement but nothing keeps
the flag:

```kotlin
val fallbackDate = if (shouldReplace) librarySetlists.firstOrNull { it.fileName == entry.fileName }?.date else null
importedSetlists += setlistRepository.importSetlist(
    setlist = entry.setlist.withSongFileNames(storedSongFileNames).let { it.copy(date = it.date ?: fallbackDate ?: today) },
    shouldReplace = shouldReplace,
)
```

## Fix

An undated incoming setlist says nothing about its day *or* its countdown: a file with no date is older than both, or
written by hand, or the bundled demo one. (Dates and the countdown arrived in separate builds, so a file that carries a
date but was written before the countdown still reads as countdown off and stays a conflict against a library copy
with it on; nothing in the file tells that apart from a deliberate off, so this plan leaves it.) In `holdsTheSameAs`, group the two behind the same tolerance:

```kotlin
title == other.title && description == other.description &&
    (other.date == null || (date == other.date && isCountdownShown == other.isCountdownShown)) &&
    isArchived == other.isArchived && entries == other.entries && unknownFields == other.unknownFields
```

and extend the KDoc: "except a date [other] does not carry, and with it the countdown, which no file older than the
date can say either". In `ImportFilesUseCaseImpl`, carry the flag the way the day is carried: look the library setlist
up once (`val replaced = if (shouldReplace) librarySetlists.firstOrNull { it.fileName == entry.fileName } else null`),
and when the incoming setlist is undated, take `date` from `replaced` (as now) and `isCountdownShown` from `replaced`
too (`isCountdownShown = if (it.date == null && replaced != null) replaced.isCountdownShown else it.isCountdownShown`; note this reads `it.date` before the `date =` argument of the same `copy` is applied, which is what is wanted: `copy` evaluates against the original `it`).
A dated incoming setlist keeps its own flag, since a file that carries a date was written by a build that knew the
flag. Update the comment above (`An undated file that replaces a library setlist says nothing about its day` → "about
its day or its countdown"). Check `domain/implementation/CLAUDE.md` for the sentence that describes the date tolerance
and extend it the same way.

## Tests

`ImportPlannerTest`: next to the existing `assertEquals(ImportPlan.Status.IDENTICAL, statusOf(library.copy(date = null)))`
(line 139), add a case where the library setlist has `isCountdownShown = true` and the incoming one is
`library.copy(date = null, isCountdownShown = false)`: `IDENTICAL`. And one where the incoming one is dated and differs
only in the flag: still `CONFLICTING`.

`ImportFilesUseCaseImplTest`: a replace of a library setlist (dated, countdown on) by an undated copy *that differs in
something else too, for instance its description* (one that differs only in the flag is now `IDENTICAL` and never
reaches Replace) writes the setlist with the library's date and `isCountdownShown = true`; a replace by a dated copy
with the flag off (differing only in the flag, so `CONFLICTING`) writes it off. The first fails with the bug in place
(the flag is written `false`). Check how the existing tests in that file drive a replace (`Resolution.REPLACE`) and
read the written setlist from the fake `SetlistRepository.importSetlist` before writing them.

## Manual check

On the desktop build, turn the demo setlist's countdown on, then Settings → "Add the demo songs": nothing is asked and
the countdown stays on.
