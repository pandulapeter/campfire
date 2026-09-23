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

- Campfire asks Dropbox for the **app folder** permission, so it only ever sees `Apps/Campfire Sync` and the rest of
  your Dropbox stays invisible to it. Inside that folder the files sit in `songs/` and `setlists/`, exactly as they do in
  an exported zip — plain ChordPro text you can open, edit or back up with anything else.
- Only song and setlist files are synced. Anything else you keep in those two folders is left exactly where it is,
  and so is a file too large to be a song (over 8 MB). On Windows, a file whose name has a character Windows does not
  allow (`? : * " < > |`) is left where it is too, and named in the run's summary; renaming it on another device lets
  it through. A file with a `\` in its name is left where it is on every device, the same way.
- Only the library is synced. Your settings, your text size and your transpositions stay on the device they were made
  on (a phone's own backup does carry them to its replacement).

### How a run decides

- A run compares **content**, never modification times: the four platforms disagree about those and the web build has
  none.
- An edit always wins over a deletion.
- A run that would delete most of your library on this device — more than half of the files it has synced before and
  at least five of them, or all of them — stops before anything moves and asks. That is what a cloud folder that was emptied, renamed or replaced looks like,
  and following it would leave every device with only the songs edited since the last sync. **Delete them here too**
  goes ahead; **Keep them and upload** puts the files back into the cloud folder instead. Until you answer, every run
  asks again.
- The same holds the other way round: a run that would delete most of the cloud folder because the files are gone
  from this device stops and asks too — and it always asks when this device's library is empty, however few songs it
  held, since a library folder that was moved or deleted looks exactly like that. **Delete them from the cloud too**
  goes ahead; **Keep them and download** brings the files back onto this device instead. An answer covers one
  direction only, so a run that would empty both sides asks about each in turn.
- A song that changed on two devices at once is never merged. The local one keeps its name and the incoming one lands
  next to it as ` (2)` (or the next number that is free everywhere), a copy for you to look at and delete.
- Renaming a file reaches sync as a deletion and a new file, since a run is keyed by name and knows nothing of moves.
  The rule above then applies: a device that edited the file under its old name since the last run puts that file
  back, leaving both.

### When it runs

- Sync runs when the app starts and whenever you press **Sync now**.
- A run belongs to the app rather than to the screen that started it, so it carries on while you use the rest of the
  app, or leave it. Android keeps the process alive with a foreground service and iOS with a background task.
- It can be stopped at any time, and a run that is interrupted — killed, swiped away, suspended — leaves the library
  usable and says so the next time, when the app waits for **Sync now** instead of starting a run on its own.
- A first sync of a whole library is expected to be rate limited rather than to fail; a run asks for a few files at a
  time and slows down when the service tells it to.
- A file that cannot be moved does not hold up the others: Settings names it, and the next run tries again. The same
  goes for a file another device keeps changing while the run is trying to send it up. A cloud folder that is full
  does stop the run, and Settings says so. A song on this device that cannot be read — a file another app is
  holding, one whose permissions were taken away — is named the same way and left alone on both sides until it can be.

### Signing in

Authorization is OAuth 2.0 with PKCE and no client secret, which is what makes a backend unnecessary. The page you
type your password on is Dropbox's own, opened in your browser. On Android the token is encrypted with a key the
system keystore holds, and on iOS it is kept in the Keychain; on desktop and the web it is a file in the app's own
data. On a phone neither the token nor the record of the last run is part of the device's backup, so a phone restored
from one, or a new phone the library was moved to, starts disconnected: you sign in again, and the first run compares
the two sides by content and duplicates nothing. Reinstalling the app disconnects it too, even where the system kept
the token; connecting again is a tap in Settings. Disconnecting revokes the token and deletes it from the device; the files stay where they are on both sides.

Dropbox is the first provider rather than the only possible one: the engine sees one flat remote folder addressed by
kind and name, and treats a service's revisions as opaque strings it never parses.

### Building with sync

Sync needs a Dropbox app key at build time. A build made without one simply does not offer it, and Settings says so —
see the Build section of [CLAUDE.md](../CLAUDE.md).
