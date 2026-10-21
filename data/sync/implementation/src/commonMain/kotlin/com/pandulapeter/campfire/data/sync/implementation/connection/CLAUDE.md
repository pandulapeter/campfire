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
- `connect` from a stored connection (`Connected` or `ConnectionFailed`) first stops the running run, a waiting one,
  and the account refresh (after remembering the state, so backing out shows what the user saw); backing out asks for
  a run again. A reconnect whose token exchange fails (network or authorization) backs out too, keeping the working
  tokens, since a provider stores nothing before the exchange. A reconnect finished at a later start up (web, Android
  after process death) remembers nothing, so it forgets them as a first connection does.
- `connect` never throws but cancellation; every exit not ending connected forgets the credentials the authorization
  stored, so an abandoned authorization is never restored next launch.
- An authorization that ends with another account resets the index and the shared links under the run lock, as
  `disconnect` does, and shows the new account inside it. Not covered: the journal applier and sharing actions never
  take that lock, so one started for the old account a moment before the new tokens were stored could write into the
  new account's list.
- A run refused by the service (after renewal fails) sets `ConnectionFailed`, not a failed outcome: Disconnect would
  delete the index, Connect keeps it.
- Disconnecting cancels and awaits a running run, then deletes the index under the run lock and shows Disconnected,
  completing regardless of caller cancellation; the service is told afterwards, in the background, with the token
  captured before the credentials were forgotten. A new authorization cancels a revocation still going. A run writes
  its outcome only into a state still `Connected`.
