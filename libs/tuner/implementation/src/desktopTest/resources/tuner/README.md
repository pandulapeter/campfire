<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Recorded strings

16-bit PCM WAV files (mono, or stereo of which the first channel is read), each named by the note it holds as the
first thing in its name — `E2.wav`, `A#1 bass.wav`, `Bb3 phone.wav` — with the octave in scientific numbering. Each one
dropped here is read by `RecordedStringsTest` the way the tuner reads the microphone, and every reading along the way
has to be that note. Record a string tuned against a hardware tuner, from a pluck until it has died away, on the device
whose microphone is in question.
