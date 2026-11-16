<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: asking regular users to spread the word

**Status:** not started. Written against the code on 2026-11-16 (380f66a02). `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

Campfire grows by word of mouth or not at all: about 300 downloads and 33 sync sign-ins so far, no rating prompt, and
the Share and Rate rows in Settings → About, which nobody happens upon. Once someone has made Campfire part of how
they play, the app asks them once, in the developer's own words, to tell a musician friend about it and, where the
platform has a store, to rate it. Sharing needs no store, so the web gets the dialog too.

## Decisions

- **Who is asked: someone who plays from Campfire, not someone trying it.** All three must hold:
  - **at least 5 songs of their own**: the library's songs, not counting a demo song still as it was planted
    (`UserPreferences.demoLibraryContentHashes` names those);
  - **the song details screen opened at least 10 times**, all songs together: each push of a
    `CampfireDestination.SongDetails`, not counting an import preview (`importPreview != null`), nor paging between
    the songs of a setlist inside one push;
  - **songs opened on at least 3 different days**, by the device's local date. This replaces "installed at least 3
    days ago": it asks the same of a new user (the third day is two days after the first at the earliest) and more of
    everyone else, since it counts days the app was used rather than days it sat installed. It also needs no install
    date, which the app does not keep: an existing user starts counting from this version, like a new one.
- **When: once, on the way back from song details.** The moment a pop leaves a `Songs` or `Setlists` screen on top
  of a stack that held a `SongDetails`, once the pop's transition has settled, so the dialog never fights it. Not
  when song details hands over to the editor, or to another song details. The gate is `canShowWhatsNew`'s, plus no
  onboarding and no What's new owed this session, so it never stacks on another question: no dialog or sheet open, no
  import running or reviewed or queued, no sync run waiting for an answer about deletions. Conditions met while the
  gate is shut wait for the next return.
- **Once per device, recorded when it appears.** A device-local flag, never exported or synced like
  `isOnboardingPending`: most users have one device, and sync is off until they connect it. An app ended while the
  dialog is up does not ask again; asking twice would cost more than asking once too few. Counting stops once the
  thresholds are met or the dialog has been shown, so after that a song open writes nothing.
- **What it says: a short personal note, signed.** Not a sentiment question ("Enjoying Campfire?"): Play's policy
  forbids gating a rating on one, and the note asks the same of everyone. The author's photo (the website's
  `assets/peter.webp`, bundled as a small PNG) beside the title. Draft copy, for the owner to rewrite in their voice:

  > **A word from Péter**
  >
  > Hi! I'm the guy behind Campfire, which is and will remain completely free, with no ads and no account. This app
  > reaches new musicians almost only when someone who uses it mentions it.
  >
  > If Campfire has been useful to you, telling a musician friend about it, or leaving a rating, would help it more
  > than anything I could do myself. Thank you for using it!

  Where the dialog offers no Rate button (below), the note leaves out ", or leaving a rating,": a second string, not
  a sentence asking for something the dialog cannot do.

- **The buttons: Share Campfire (the main action), Rate on <store> (Play and the Microsoft Store only), and Close.**
  Close because the dialog never comes back; Settings → About keeps both rows for later.
  - **Share** always sends the website's address (`WEBSITE_URL`) after one translated sentence, never a store
    listing: the friend may be on another platform, and the website offers every one. Through the share sheet where `FilePicker.canShare` (Android, iOS,
    and a browser with the Web Share API); elsewhere (the desktop, and desktop browsers without it) it copies the same
    text and says so in a snackbar. Nothing is offered for a share, which both stores forbid.
  - **Rate** opens the store's own review page rather than an in-app review API: Play's and Apple's are throttled,
    may show nothing at all, and both say not to call them from a button. Play opens the listing
    (`Distribution.PLAY_STORE.listingUrl`); the Microsoft Store opens
    `ms-windows-store://review/?ProductId=9MSR62F288M1`, falling back to the https listing where that scheme does not
    open. The store is the machine's, as in Settings (`platformStore`), so a Windows copy downloaded from the website
    rates in the Microsoft Store too. No new dependency.
  - **No Rate button on Apple's platforms** (iOS, iPadOS, and macOS, whichever way the Mac copy was downloaded):
    guideline 5.6.1 allows only Apple's own review prompt, a popup the app asks for and the system may not show, so a
    dialog of the app's own inviting a rating could cost a rejection for little gain. There the dialog asks to share
    only; the Rate row in Settings → About stays as it is. The web and Linux have no store, so no Rate button either.
- **No donation request.** The note makes one ask, to bring Campfire to more musicians; money beside it would read as
  a fundraising pitch and weaken that ask. The Buy me a coffee row stays in Settings → About where it is allowed
  (`canAskForDonations`: never in Apple's builds).
- **A tap on Share or Rate closes the dialog**: the ask is done whichever way it was answered.

## Where it hooks in today

- Ratings: `Distribution` and `platformStore` (`ui/platform/Platform.kt`, one actual per platform); the Rate row in
  `ui/screens/settings/AboutSections.kt` already opens `listingUrl`.
- Sharing: the Share Campfire row in `AboutSections` (`onShareLink(filePicker, WEBSITE_URL, "Campfire")`), only
  where `filePicker.canShare`; `plainTextClipEntry` (`ui/platform/Clipboard.kt`) for the copy.
- One-time dialogs: `DialogType.WhatsNew`, gated by `canShowWhatsNew` (`ui/firstRun/WhatsNewGate.kt`) and recorded
  by `FirstRunController`, is the pattern to follow.
- The back stack: `CampfireDestination`, `Navigator`, and `BackStackQueries` (`ui/navigation`).
- Preferences: `UserPreferences` (`:data:model`), `UserPreferencesDocument` and its mappers
  (`:data:source:local:implementation`), the `upgrade-5.2.0` fixture beside `UserPreferencesLocalSourceTest`.

## Steps

### 1. What is counted (`:data:model`, `:data:source:local:implementation`)

`UserPreferences` gains `songOpenCount: Int`, `songOpenDayCount: Int`, `lastSongOpenEpochDay: Long?`, and
`isSpreadTheWordShown: Boolean`, all defaulted, never exported or synced, and the same fields in
`UserPreferencesDocument`. New keys only, so no `version` bump. An `upgrade-5.4.0` fixture of `preferences.json` as
5.4.0 (the last tag) writes it, from a worktree at the tag, read by a test that finds all four at their defaults.

### 2. The rules (`ui/spreadTheWord/`, pure)

- `SongOpens.counted(today)`: one more open, and one more day when `today` differs from the last; unchanged once
  every threshold is met.
- `isSpreadTheWordDue(ownSongCount, songOpenCount, songOpenDayCount, isShown)` and `canShowSpreadTheWord(…)`, the
  gate.
- Tests for each: a same-day open, a new day, a clock moved backwards (counts as a new day, never fewer), demo songs
  not counted, and every gate condition closing it.

### 3. Counting and showing (`ui/navigation`, `CampfireViewModel`)

Count on each qualifying `SongDetails` push, with today's epoch day from kotlinx-datetime (already shipped). On a
pop back to a list screen, show `DialogType.SpreadTheWord` when it is due and the gate is open, and set
`isSpreadTheWordShown` in the same preferences write.

### 4. The dialog (`ui/dialogs/spreadTheWord/`)

The photo, the note, and the buttons; the copy-and-snackbar fallback for sharing; the Microsoft Store's review
address (a `reviewUrl` beside `listingUrl`, so the Settings row on Windows can use it too). Strings in every
language of the app; each translation keeps the personal voice rather than translating word for word.

### 5. A UI test (`tools/screenshots`)

A library of 5 songs with the counters one open short: opening a song and going back shows the dialog; going back
again after closing it shows nothing.

### 6. Documentation

A `CLAUDE.md` for `ui/spreadTheWord` (who is asked, when, once per device), and one line under Product rules in the
root `CLAUDE.md`.

## Size and risk

Small: a few preferences fields, two pure functions, one dialog, and the strings. The one risk is outside the code:
there is no way to measure it in the app, which sends nothing. Judge it by the stores' rating counts and installs in
the weeks after the release.
