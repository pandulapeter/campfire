<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# data/sync/connection

- `restore` is called once per view model; finding `Connected` it reads nothing (re-reading would make a running run
  look interrupted). It shows the stored account before asking the service, so a slow network never shows
  disconnected. Refused credentials are a `ConnectionFailed` (authorization), nothing deleted and no launch run;
  credentials unreadable right now are a connection failure with the file left alone.
- A previous installation's credentials that could not be forgotten are noted and retried by every `restore` first;
  until then it answers disconnected. A start-up carrying the answer to a waiting authorization forgets nothing, even if
  it looks like a first launch.
- `connect` never throws but cancellation; every exit not ending connected forgets the stored credentials, so an
  abandoned authorization is never restored next launch.
- A run refused by the service (after renewal fails) sets `ConnectionFailed`, not a failed outcome: Disconnect would
  delete the index, Connect keeps it.
- Disconnecting cancels and awaits a running run, then deletes the index under the run lock, completing regardless of
  caller cancellation. A run writes its outcome only into a state still `Connected`.
