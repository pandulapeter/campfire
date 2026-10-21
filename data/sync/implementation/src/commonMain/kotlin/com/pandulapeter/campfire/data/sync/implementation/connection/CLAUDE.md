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
  credentials unreadable right now are a connection failure with the file left alone. A start up carrying a consent
  page's answer shows `Connecting` (and tells the UI, which puts the user back where they tapped Connect) before the
  token exchange; Cancel cancels that exchange and forgets what it stored.
- A previous installation's credentials that could not be forgotten are noted and retried by every `restore` first;
  until then it answers disconnected. A start-up carrying the answer to a waiting authorization forgets nothing, even if
  it looks like a first launch.
- Backing out of `connect` (Cancel, a closed consent page) returns to the state it started from while that one's
  credentials are still stored: a `ConnectionFailed` stays failed, and a `Connected` account (connected again for the
  permission to make links) stays connected, its index kept (`ConnectionBeforeConnecting`).
- `connect` never throws but cancellation; every exit not ending connected forgets the stored credentials, so an
  abandoned authorization is never restored next launch.
- A run refused by the service (after renewal fails) sets `ConnectionFailed`, not a failed outcome: Disconnect would
  delete the index, Connect keeps it.
- Disconnecting cancels and awaits a running run, then deletes the index under the run lock and shows Disconnected,
  completing regardless of caller cancellation; the service is told afterwards, in the background, with the token
  captured before the credentials were forgotten. A new authorization cancels a revocation still going. A run writes
  its outcome only into a state still `Connected`.
