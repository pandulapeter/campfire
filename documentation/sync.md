<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Sync

Campfire can keep the library the same on all of your devices, and it does that without a service of its own. You
connect **your** Dropbox in Settings, and from then on the songs and setlists go straight between your devices and
your own storage. Until you do, nothing on the network is touched at all.

### What it sees

- Campfire asks Dropbox for the **app folder** permission, so it only ever sees `Apps/Campfire` and the rest of your
  Dropbox stays invisible to it. Inside that folder the files sit in `songs/` and `setlists/`, exactly as they do in
  an exported zip — plain ChordPro text you can open, edit or back up with anything else.
- Only the library is synced. Your settings, your text size and your transpositions stay on the device they were made
  on.

### How a run decides

- A run compares **content**, never modification times: the four platforms disagree about those and the web build has
  none.
- An edit always wins over a deletion.
- A song that changed on two devices at once is never merged. The local one keeps its name and the incoming one lands
  next to it as ` (2)`, a copy for you to look at and delete.
- Renaming a file reaches sync as a deletion and a new file, since a run is keyed by name and knows nothing of moves.
  The rule above then applies: a device that edited the file under its old name since the last run puts that file
  back, leaving both.

### When it runs

- Sync runs when the app starts and whenever you press **Sync now**.
- A run belongs to the app rather than to the screen that started it, so it carries on while you use the rest of the
  app, or leave it. Android keeps the process alive with a foreground service and iOS with a background task.
- It can be stopped at any time, and a run that is interrupted — killed, swiped away, suspended — leaves the library
  usable and says so the next time.
- A first sync of a whole library is expected to be rate limited rather than to fail; a run asks for a few files at a
  time and slows down when the service tells it to.

### Signing in

Authorization is OAuth 2.0 with PKCE and no client secret, which is what makes a backend unnecessary. The page you
type your password on is Dropbox's own, opened in your browser. Disconnecting revokes the token and deletes it from
the device; the files stay where they are on both sides.

Dropbox is the first provider rather than the only possible one: the engine sees one flat remote folder addressed by
kind and name, and treats a service's revisions as opaque strings it never parses.

### Building with sync

Sync needs a Dropbox app key at build time. A build made without one simply does not offer it, and Settings says so —
see the Build section of [CLAUDE.md](../CLAUDE.md).
