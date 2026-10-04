# Search the import report's rows with the library's search folding, so `ymca` finds `Y.M.C.A.` there too

**Challenged:** sound

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportSections.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ImportReportScreen.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/ImportReportSectionsTest.kt`,
`presentation/CLAUDE.md` (the import screen's search sentence under `ui/screens/importReport/`)

## Problem

Every other search in the app (the Songs and Setlists screens, Song assignments, Setlist assignments) folds both sides
through `NormalizeSearchTextUseCase` (`CampfireViewModel.normalizeForSearch`), "ignoring case, accents, spaces and
punctuation alike (`ymca` finds `Y.M.C.A.`)" (root `CLAUDE.md`). The import report's search only ignores case:

```kotlin
// ImportReportSections.kt
internal fun List<ImportReportSection>.matching(query: String): List<ImportReportSection> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return mapNotNull { section ->
        section.copy(
            rows = section.rows.filter { row ->
                listOfNotNull(row.fileName, row.title, row.subtitle).any { it.contains(trimmed, ignoreCase = true) }
            },
        ).takeIf { it.rows.isNotEmpty() }
    }
}
```

called from `ImportReportScreen` as `remember(sections, query) { sections.matching(query) }`. So after importing a
songbook, typing `ymca` finds nothing for a song titled `Y.M.C.A.`, `tukorfurogep` finds nothing for `Tükörfúrógép`,
and `acdc` finds nothing for `AC/DC` — while the same words find those songs on the Songs screen a tap away. The rows'
file names are already folded (`village_people-ymca.cho`), so the mismatch shows most on titles and artists, which are
what the rows display.

## Fix

1. Give `matching` the folding as a parameter, keeping it pure and testable:

   ```kotlin
   internal fun List<ImportReportSection>.matching(query: String, normalize: (String) -> String): List<ImportReportSection> {
       val normalizedQuery = normalize(query)
       // A query that folds to nothing (punctuation, symbols or emoji alone) is no search, as on the library's screens.
       if (normalizedQuery.isEmpty()) return this
       return mapNotNull { section ->
           section.copy(
               rows = section.rows.filter { row ->
                   listOfNotNull(row.fileName, row.title, row.subtitle).any { normalizedQuery in normalize(it) }
               },
           ).takeIf { it.rows.isNotEmpty() }
       }
   }
   ```

   Update its KDoc ("ignoring case" → "folded the way the library's searches fold, so that case, accents, spaces and
   punctuation do not count; a query that folds to nothing is no filter").

2. `ImportReportScreen`: `remember(sections, query) { sections.matching(query, viewModel::normalizeForSearch) }`.

   Optional, if a large import (thousands of rows) makes typing feel slow: fold each row's three texts once per
   `sections` (e.g. a `remember(sections) { … }` map from row key to the folded strings) and only fold the query per
   keystroke. Not required for correctness; the folding is cheap.

3. In `presentation/CLAUDE.md`'s `ui/screens/importReport/` entry, where the search field is described ("The search is
   a field that is always there …"), add that it matches the file name, title and artist folded like the library's
   searches.

## Tests

In `ImportReportSectionsTest`, update `aSearchKeepsTheRowsWhoseNameTitleOrArtistHoldsItAndDropsEmptiedSections` to
pass a folding function (presentation's tests cannot reach the domain implementation, so use a stand-in such as
`{ it.lowercase().filter(Char::isLetterOrDigit) }`), and add a case: a song titled `Y.M.C.A.` is found by `ymca` and by
`y m c a`, and a query of `"..."` returns the sections unchanged.

## Manual check

Import a ChordPro file whose `{title}` is `Y.M.C.A.` together with any second file (so the result has a Details
screen; or import one that conflicts so the report screen opens). On the import screen type `ymca` into its search
field: the song's row stays. Type `...`: every row is shown.
