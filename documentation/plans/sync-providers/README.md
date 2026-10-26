<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Google Drive as a second sync provider: implementation plans

First draft, 2026-10-26, from a read of the code and of Google's current documentation, plus live probes of its
endpoints. Nothing is implemented. Claims marked as spikes still need a hands-on test.

| Plan | What | Ships |
|---|---|---|
| [01-groundwork.md](01-groundwork.md) | Credentials that know their provider, OAuth and HTTP plumbing out of `dropbox/`, case-insensitive names as a contract, a provider-neutral link result, and UI code that stops naming Dropbox | The next release, on its own, invisible to users |
| [02-google-drive.md](02-google-drive.md) | The Drive provider, then the picker, switching, v2 share links, the website, build keys, and docs | A later release, after its spikes |

Considered and dropped:

- **Nextcloud:** an app password sees the whole account; there is no CORS, so no web build; and shared links depend on
  the user's own server.
- **OneDrive:** shared links work only through an undocumented download host, and the web build must sign in again
  every 24 hours.

## Decisions taken in these plans

- **One provider at a time per device.** Switching is Disconnect, then Connect the other; nothing is merged across
  services. The Disconnect confirmation offers to stop sharing first and says other devices stay where they are.
- **Dropbox links stay v1**, so released apps keep opening them. Drive writes v2 links that name the provider.
- **A provider that can't work on a platform isn't shown there**, the same way a build without its key shows none.
- **Polling, not push**, for Drive: `awaitChanges` polls inside the provider every ~30 s while the app is in front,
  with no engine change.
