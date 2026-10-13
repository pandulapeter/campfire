# Correct the onboarding's reload sentence, the README's feature paragraph, and the settings doc's list of items that scroll to the top

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/onboarding/CLAUDE.md`,
`README.md` (the feature paragraphs only, lines 18-22 at 72d48deb7),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/CLAUDE.md`

## Problem

1. `ui/screens/onboarding/CLAUDE.md` ("One screen, one pager, one footer") says: "On the web the screen is one history
   entry at the address of the screen under it (`BrowserRoutes`), so a reload opens the app rather than a question, and
   neither does a Forward once it is finished, skipped, or closed." A reload while the onboarding is still owed *does*
   open it again: `FirstRunController.plantDemoLibraryOnFirstRun` opens it on every start that finds
   `isOnboardingPending` (`else if (… isOnboardingPending == true) { if (backStack.toList() == listOf(CampfireDestination.Songs)) openOnboardingOnStartUp() }`),
   as the next paragraph of the same file says ("It is opened on every start up until it has been finished or skipped").
   The reload starts it on its first page (the web keeps no saved state), every answer kept. What the address does decide
   is that a reload of a page *under* an owed onboarding opens the onboarding, not that page: `navigateOnLaunch` runs
   only after `isDemoLibraryPending` is cleared, which happens after the onboarding was put on the stack, and it then
   finds the stack is no longer `[Songs]` and leaves the address alone. (That order is fixed, not a race: both run on the
   main thread and the flag is cleared in the `finally` after `openOnboardingOnStartUp`.)
2. `README.md` describes the chord diagrams and the tuner as they were before the Chords tab, the mandolin, the banjo, the
   12-string guitar, and the alternate tunings:
   - "chord diagrams for guitar, ukulele, and keyboard (with every other way to play each chord one tap away, and the
     shapes a song defines for itself)" — the app draws them for the six- and twelve-string guitar, ukulele, mandolin,
     banjo, and keyboard (`Instrument.hasDiagrams`), in alternate tunings such as drop D and DADGAD, and browses them in
     a Chords tab;
   - "against the strings of a guitar, bass, ukulele, violin, mandolin or banjo" — the tuner's instruments now include
     the seven-, eight-, and twelve-string guitars and the five-string bass, and the list lacks its serial comma;
   - "with a page preview, song selection, and saved paper, sizing and content options" lacks a serial comma.
3. `ui/screens/settings/CLAUDE.md` says pressing a top level item on its own screen scrolls it to the top for "the Songs,
   Setlists or Metronome item". The Tuner and Chords screens collect `scrollToTopRequests` too (`TunerScreen.kt`,
   `ChordsScreen.kt`), and the list lacks its serial comma.

## Fix

1. Onboarding `CLAUDE.md`: replace the sentence with "On the web the screen is one history entry at the address of the
   screen under it (`BrowserRoutes`), so it has no address of its own: a reload while it is still owed opens it again on
   its first page, over the songs rather than the address the page was at (the start up puts it on the stack before
   `navigateOnLaunch` looks), and neither a reload nor a Forward opens it once it is finished, skipped, or closed."
2. README, in the paragraph beginning "Songs automatically adapt…": replace "chord diagrams for guitar, ukulele, and keyboard
   (with every other way to play each chord one tap away, and the shapes a song defines for itself)" with "chord diagrams
   for the six- and twelve-string guitar, ukulele, mandolin, banjo, and keyboard, in standard and alternate tunings such as
   drop D and DADGAD (with every other way to play each chord one tap away, the shapes a song defines for itself, and a
   Chords tab to browse them by key and name)". In the tuner sentence replace "against the strings of a guitar, bass,
   ukulele, violin, mandolin or banjo" with "against the strings of a six-, seven-, eight-, or twelve-string guitar, a
   four- or five-string bass, a ukulele, a violin, a mandolin, or a banjo, in their alternate tunings too". In the export
   sentence write "saved paper, sizing, and content options". Do not touch the Third-party software table, which plans 48
   and 49 edit.
3. Settings `CLAUDE.md`: "as does pressing the Songs, Setlists or Metronome item on its screen" becomes "as does pressing the
   Songs, Setlists, Metronome, Tuner, or Chords item on its screen (or a tool's tab where the tools are grouped)".

Check each tuner instrument and tuning claim against `UserPreferences.Instrument` and `ui/tuner/CLAUDE.md` before writing
it, in case a later plan of this review changes them.

## Tests

None: documentation.

## Manual check

None.
