# Offer the copied share link once more when the first run's onboarding is skipped before its last page

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** iOS
**Challenged:** amended — "the card was shown" is read from the page the onboarding left from (`Navigator.onboardingStep` is still that page when the back stack listener runs), not from a `LaunchedEffect` in the card, which the pager composes ahead of being seen; the snackbar's offer is the reader alone, with `isClipboardLinkOffered` cleared as today, so an onboarding opened from Settings while the snackbar stands shows no card and offers nothing again; `OnboardingClipboardLinkCard.kt` leaves the file list.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/FirstRunController.kt`,
`…/ui/CampfireViewModel.kt` (one lambda), `…/ui/navigation/Navigator.kt`, `…/ui/messages/Message.kt`, `…/ui/messages/Messages.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`, `…/values-hu/strings.xml`, `…/ui/firstRun/CLAUDE.md`,
`…/ui/screens/onboarding/CLAUDE.md`, a new
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/ClipboardLinkOfferTest.kt`

(`…/ui/` = `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.)

## Problem

The iOS path for "a friend sent me a song, I don't have Campfire yet": the landing page's **Get Campfire** copies the
link and opens the App Store; on the first launch `FirstRunController.offerClipboardLink` checks, without reading, that
the clipboard seems to hold an address, and the onboarding's **last** page shows "Did someone send you a song or
setlist? **Open it**" (`OnboardingClipboardLinkCard`, given to whichever page is last through
`LocalOnboardingPageFooter`). The offer is dropped as soon as the onboarding leaves the back stack
(`FirstRunController.onBackStackChanged`):

```kotlin
if (wasOnboardingOpen && !isOnboardingOpen) {
    // The offer belongs to the first run's onboarding, not to one opened again from Settings.
    clipboardLinkReader = null
    _isClipboardLinkOffered.update { false }
```

But the onboarding's way out is **Skip**, on its first page only (`screens/onboarding/CLAUDE.md`: "The way out (Skip on a
first run, Close from Settings) is only in the first page's footer"). Someone who installed Campfire to open one song is
exactly the person likely to skip the questions, and they never see the card: the app opens on the demo library with
no sign of the song they came for. Paste still works, if they think of it. The plan (§9.2) chose "dropped when the
onboarding leaves" to keep it off an onboarding reopened from Settings, not with Skip in mind.

## Fix

**Decided (2026-10-21):** a one-time snackbar as the first run's onboarding closes without the card having been shown,
"Did someone send you a song or setlist?" with an **Open it** action that calls `openClipboardLink` (the clipboard
read only on that tap). The card is not added to the first page, and the behaviour is not left as is.

1. Whether the card was seen is whether the onboarding left from its last page: Skip and a back from page one leave
   from the first page, Start playing (or Next while an import waits) from the last, where the card sits. Read it as
   the onboarding leaves: `closeOnboarding` only edits the back stack, so `Navigator.onboardingStep` still holds the
   page left from when `onBackStackChanged` runs. `Navigator` gains `isOnboardingOnLastStep`
   (`nextOnboardingStep(onboardingStep, onboardingSteps()) == null`, beside `isOnboardingBackToPreviousStep`, since
   `onboardingSteps` is private to it), and `FirstRunController` takes it as a lambda from the view model
   (`isOnboardingOnLastStep = { navigator.isOnboardingOnLastStep }`).
   Not a `LaunchedEffect` in `OnboardingClipboardLinkCard`: the pager composes the next page ahead of it being
   swiped to, so the effect would run on the page before the last. Someone who visited the last page, went back to
   the first, and skipped gets the snackbar once more, which is harmless.
2. In `onBackStackChanged`, when the first run's onboarding leaves with the offer standing and not from its last page,
   keep the reader and send `Message.ClipboardLinkOffered`; otherwise drop the reader as today. In both cases clear
   `_isClipboardLinkOffered` as today, so no later onboarding (one opened from Settings while the snackbar stands)
   shows the card. Put the choice in a pure function (`isClipboardLinkOfferedAgain(isOffered, isLeftFromLastPage)`)
   for the test. `openClipboardLink` then takes the reader once whichever asked, the card or the snackbar
   (`val reader = clipboardLinkReader ?: return; clipboardLinkReader = null; _isClipboardLinkOffered.update { false }`),
   which still guards a double tap, everything running on the main thread. An offer that arrives after the onboarding
   has already left (a very fast Skip) stays unshown, as today.
3. `Messages.kt` shows it with `actionLabel = onboarding_clipboard_link_open` and `SnackbarDuration.Long`; the action
   calls `firstRunController.openClipboardLink()` (which already takes the offer once and answers
   `ClipboardNotCampfireLink` for anything else). When the snackbar is dismissed without the action
   (`SnackbarResult.Dismissed`, a branch beside today's `ActionPerformed` one), drop the offer
   (`firstRunController.dropClipboardLinkOffer()`, clearing the reader), so nothing reads the clipboard later. Reuse `onboarding_clipboard_link_question` for the text
   (English and Hungarian exist).
4. The import that follows waits for the onboarding as today (`ImportReportState.awaitNothingInTheWay`); the onboarding
   is already gone here, so it opens at once.
5. Privacy is unchanged: `detectPatterns` only on the first launch (`offerClipboardLink`), the clipboard read only on the
   user's tap. Say so in `ui/firstRun/CLAUDE.md` (the iOS clipboard bullet: "until taken or until the onboarding leaves"
   becomes "… leaves; skipped before its last page, a snackbar offers it once") and in
   `ui/screens/onboarding/CLAUDE.md`'s footer line.

## Tests

`ClipboardLinkOfferTest` in commonTest: the pure decision offers the snackbar only when the offer stands and the onboarding
left from a page before its last, and never when it left from the last page or nothing was offered. The snackbar and its tap are iOS-only (the
desktop shell never calls `offerClipboardLink`), so the UI tests in `tools/screenshots` cannot reach them without a
clipboard fake the desktop app has no seam for; the decision is the part worth pinning.
