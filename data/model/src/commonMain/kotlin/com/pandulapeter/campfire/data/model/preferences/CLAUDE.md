<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/model/preferences

- Enum `id`s are persisted; never change them. `areChordsEnabled` is stored as the inverse `isLyricsOnlyModeEnabled`.
- **Per-song maps keyed by file name** (`transpositions`, `tempos`, `capos`, `beatLevels`, `foldedSections`) must be
  listed in `withSongRenamed` and `withoutSongOverrides` and nowhere else (`UserPreferencesSongMapsTest`).
- What travels where: `chordVoicings`, `tagColors`, and the per-song transpositions, tempos, capos, and `beatLevels`
  sync through `preferences.json` but are never exported. `tagColors` are never pruned when a tag's last song goes.
  Local only: `instrument`/`tunings`, `foldedSections` (one reader's choice), `tunerSettings`, `seenWhatsNewVersions`,
  `isOnboardingPending` (default false so old installations are never sent through onboarding), and
  `demoLibraryContentHashes` (sync uses it to prefer the cloud's copy of an untouched demo file). Song filters are never
  stored (`SongFilter` in `:domain:api`).
- `instrument` ids are the tuner's standard tuning ids and, for the five with shapes, `:chordpro`'s; `ofTuningId`
  matches by longest prefix; the twelve-string guitar's id names no tuning. `tunings` has no entry for standard, and
  unknown ids (here and in `chordVoicings`) are kept as they are.
- `MetronomeSettings` stores the metronome's own ids, so this module depends on nothing; volume zero is the mute.
- `MIN_FONT_SCALE` bounds the whole song details screen, not only the lyrics.
