# 42 · The demo library can be planted on a used installation

**Severity:** low · **Area:** `:presentation` (`CampfireViewModel.plantDemoLibraryOnFirstRun`) · **Decision:** write the preferences on every first launch

## Cause

`IsFirstRunUseCaseImpl` is "no preferences file". `plantDemoLibraryOnFirstRun` (`CampfireViewModel.kt:905–931`)
writes the preferences only after planting. A user who imported their own songs on day one (nothing planted, nothing
written), never changed a setting, and later deleted every song and setlist gets the demo library on the next launch.

## Fix

Move `saveUserPreferences(userPreferences.filterNotNull().first())` out of the inner `let`, so it runs whenever
`isFirstRun()` was true — planted or not:

```kotlin
if (isFirstRun()) {
    val library = screenData.first { it !is DataState.Loading }.data
    if (library != null && library.unfilteredSongs.isEmpty() && library.setlists.isEmpty()) {
        readDemoLibrary()?.let { import(it, shouldAnnounceResult = false) }
    }
    // Written on every first run, planted or not: this is what makes the next launch not a first run.
    saveUserPreferences(userPreferences.filterNotNull().first())
}
```

Keep it inside the `try` so the `finally` still clears `isDemoLibraryPending`. Update the KDoc's last paragraph and
the root `CLAUDE.md` demo paragraph ("They are planted once, on a run that finds no preferences document *and* an
empty library …" — add that the first run writes the preferences either way).
