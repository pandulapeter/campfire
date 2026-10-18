<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Review plans: the last 20 commits, low-end performance, and the v4.9.0 release

Reviewed at `6e9e8e6`; the plans landed on 2026-10-18 as `0fa252e49..10e7f8e76` (14 plans, one commit each, plus a fix of
plan 13's test in `5ddd42c1c`). One plan is left.

| # | Plan | Severity | Why it is left |
|---|---|---|---|
| 10 | Let the editor's preview give the web page's thread back | medium (web) | Its measurement gate held: on the web build a warm `renderSong` and `prepareSongLyrics` of a 49,852-character song took about 5.5 ms together (4.3 and 1.2), under the plan's 16 ms, on a fast Mac. Revisit only if a low-end device measures it over a frame. |
