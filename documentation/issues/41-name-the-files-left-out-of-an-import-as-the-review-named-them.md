# Name the files left out of an import as the review named them, not by the library name they never got

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — `leftOut.distinct()` goes too, since repeats no longer reach the list and two different files of a batch can share a name (`a/x.cho`, `b/x.cho`), each a row of the review; and the `domain/implementation/CLAUDE.md` sentence "The result's duplicate and left-out lists name each library file once" is corrected, which the source names make wrong.
**Files:** `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImpl.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/ImportFilesUseCaseImplTest.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportResult.kt` (KDoc),
`domain/implementation/CLAUDE.md`

## Problem

The review lists a song by the file it arrived in (`ImportReviewSections.kt`, `fileName = entry.sourceFileName ?: entry.fileName`),
and a setlist the same way (`fileName = entry.sourceFileName`). Once the import is written, the result's **Left out by your
choice** group lists what the use case put into `leftOutFileNames`, which is the name the entry *would* have had in the
library (`ImportFilesUseCaseImpl.kt`, at 72d48deb7):

```kotlin
// The library's spelling of the name where it holds the file under another one (case, Unicode form),
// since that is the name the song list holds and the batch's setlists are pointed at.
Action.LEAVE_ALONE -> (entry.replacesFileName ?: entry.fileName).also {
    leftAloneIndices += index
    leftOut += it
}
```

and for setlists `Action.LEAVE_ALONE -> leftOut += entry.fileName`. For a new song unticked in the review, `entry.fileName`
is the normalized name derived from its header, a file that exists nowhere: `test_two.cho` was reviewed and unticked, and
the result says `live_run-test_song_two.cho` was left out (live run: `104-report.png`). For an unticked taken name it is the
library's file, which was *not* the one left out. The stored name is still right for its other use, the name the batch's
setlists are pointed at (`storedNames[index]`); only the report is wrong.

A repeat of a left-out entry adds the stored name of the entry it repeats (`Action.DISREGARD` with `repeatedEntryIndex in
leftAloneIndices`), which today coincides with the first one's and is removed by `leftOut.distinct()`.

## Fix

In `ImportFilesUseCaseImpl`, report the name the review showed, and leave every stored name as it is:

- Songs, `Action.LEAVE_ALONE`: keep the `storedName` expression, but add `entry.sourceFileName ?: entry.fileName` to
  `leftOut` instead of the stored name (the fallback is a song out of a multi-song document, which the review names the
  same way).
- Songs, `Action.DISREGARD` of a repeat whose original was left alone: still add the index to `leftAloneIndices`, but add
  nothing to `leftOut` — the review lists only the first of the repeats, "its entry saying how often it appears", and that
  one is already there. (With source names a repeat can come from a differently named file, which `distinct()` would no
  longer fold away.)
- Setlists, `Action.LEAVE_ALONE`: `leftOut += planned.sourceFileName` (the `plan.setlists` entry the loop zips in), not the
  replanned `entry.fileName`.
- `result()`: `leftOutFileNames = leftOut` rather than `leftOut.distinct()`. Only an unticked row of the review reaches
  `LEAVE_ALONE` (a repeat is not a row, and now adds nothing), so the list is the review's unticked rows one for one;
  two of them with the same source name (from two folders of an archive) are two rows there and two here, which the
  result's list keys apart already (`toRows` numbers a repeated name). `duplicateFileNames` keeps its `distinct()`; the
  comment above the two ("Both lists name library files…") becomes one about the duplicate list alone.

Update the `leftOutFileNames` KDoc in `ImportResult.kt` to "Entries the user unticked in the review, named by the file each
arrived in, as the review listed them", and in `domain/implementation/CLAUDE.md` after "What the review left out has its
own result list (`leftOutFileNames`)" add ", named by the file each arrived in, as the review listed it".

## Tests

`ImportFilesUseCaseImplTest` asserts today's names in five places (`skippedResult.leftOutFileNames == listOf("x.cho")`,
`listOf("Beatles-Yesterday.cho")`, `listOf("conflict.cho")`, `listOf("a.cho")`, `listOf("set.setlist.json")`). Update each to
the entry's `sourceFileName` (or its `fileName` where that is null, as in the `conflict.cho` case): the
`Beatles-Yesterday.cho` case becomes `beatles-yesterday.cho`, while its assertions on the setlist pointing at
`Beatles-Yesterday.cho` and on the library keeping that file stay as they are; the
`a repeat of a left-out conflicting song is left out with it…` case (`first.cho`, repeated as `second.cho`) becomes
`listOf("first.cho")`, which is also what checks that the repeat is not reported a second time. Add:

- `a new song left out is reported by the file it arrived in`: a plan with one `NEW` song whose `fileName` is
  `artist-title.cho` and `sourceFileName` `title.cho`, left out → `leftOutFileNames == listOf("title.cho")`, and a setlist of
  the batch naming `title.cho` still points at `title.cho` (the missing-song rule).

## Manual check

Import an archive holding `test_two.cho` (a song whose header gives it another name, such as "Live Run - Test Song Two").
On the review, untick it and import the rest: the result's **Left out by your choice** lists `test_two.cho`, the name the
review showed. Untick a taken name instead: the result lists the incoming file's name, not the library's.
