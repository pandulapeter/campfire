<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Ask the change feed again at once after a long poll the service held and then lost, backing off only a poll that failed soon after it was sent

**Kind:** bug (latency)  ·  **Severity:** low–medium  ·  **Platforms:** all (measured on desktop)
**Challenged:** amended — the claim that a poll that could not connect never counts as held holds only on the desktop and Android (the browser's `fetch` has no connect timeout, and the iOS engine is not relied on for one); a late 408 / 429 / 5xx counts as held too, now said and bounded rather than left implicit; the nested catch's comment no longer says such a failure "says nothing against the link"; a test pins that a feed listing failing after a held poll still backs off (the caller's "loops at once forever" case), and the two `DropboxChangeFeedTest` cases are named as contract pins that pass before the change. The rule, the code, and the other tests stand: only `awaitChanges` is measured, `changesSince` and the preferences download keep the outer back-off, `environment.timeSource` is the test scheduler's in tests, and the cursor, not the clock, still decides what changed.
**Files:**
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRemoteWatcher.kt`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRemoteWatcherTest.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/CLAUDE.md`,
`data/source/remote/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/api/sync/SyncFolder.kt` (KDoc),
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxChangeFeed.kt` (KDoc only),
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxChangeFeedTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

Lane S, after 01 and 02 (01 also edits the `SyncRemoteWatcher` bullet of `scheduling/CLAUDE.md`, and `SyncRemoteWatcherTest`'s
`world(...)`; keep both sides).

## Problem

`SyncRemoteWatcher.watchFrom` (`SyncRemoteWatcher.kt:42-78`) treats every `SyncNetworkException` alike, whether the poll
never reached Dropbox or Dropbox held it for minutes and the connection then ended without an answer:

```kotlin
} catch (exception: SyncNetworkException) {
    // Offline, or Dropbox busy: the same cursor is asked about again once the wait is over, or as soon as the
    // device is back online, and nothing that changed meanwhile is lost by waiting.
    connectivityMonitor.awaitReconnection(NETWORK_RETRY_DELAYS[minOf(failures, NETWORK_RETRY_DELAYS.lastIndex)])
    failures++
}
...
val NETWORK_RETRY_DELAYS: List<Duration> = listOf(30.seconds, 1.minutes, 2.minutes, 5.minutes)
```

A long poll that ends without an answer is routine on a healthy link. In a live run of the desktop build against the
real Dropbox through a local logging proxy (logs in the review's scratchpad, `live/B.log`, `live/proxyB.log`,
`live/A.log`, `live/s4a.out`):

- **Undegraded link, device B**: the poll sent at `1792489167.424` was closed by the far end after 313.9 s
  (`ClosedReadChannelException: Failed to parse HTTP response: the server prematurely closed the connection`; the proxy
  logs `CLOSE notify.dropboxapi.com 313.8s`). The watcher waited 30 s; the next poll was answered in 0.6 s with a change
  that had been waiting. The poll sent at `1792490010.913` ran into the client's own bound after 360.5 s; again 30 s of
  waiting, then a change answered in 0.7 s. The two changes uploaded during those silent polls arrived 96 s and 54 s
  late, most of it the 30 s wait plus the poll that hid them.
- **A connection that went silent** (device A, the proxy stopped forwarding, as a Wi-Fi switch does): the poll sent at
  `1792489208.650` hung until the call's bound, 360.5 s, then waited 60 s (its second failure in a row, the first
  being a premature close 48 s into the previous poll), so a change uploaded elsewhere at `1792489241.895` started a
  run 389.6 s later.
- **A proxy restart** cut a poll 10.7 s after it was sent; it was asked again only after 30 s (and failed then, the
  proxy still down, which is right).

Each such failure also grows the next wait, so two silent polls in a row cost a minute, then two.

The transport does not cover for it either. `DropboxChangeFeed.awaitChanges` (`DropboxChangeFeed.kt:37-51`) sends the
poll through a bare `transport { }`, not through `DropboxTransport.request`, so it is never retried:

```kotlin
val response = transport {
    httpClientHolder.client().post(LONGPOLL_URL) {
        attributes.put(BrowserCallTimeout, LONGPOLL_CALL_TIMEOUT_MILLIS)
        timeout { socketTimeoutMillis = LONGPOLL_CALL_TIMEOUT_MILLIS }
        ...
```

That is the right place for it not to be retried (the transport's budget of six timeouts would hold a dead poll for
7 × 360 s, and the longpoll takes no token, which `request` always fetches), but
`data/source/remote/implementation/CLAUDE.md` says of every call "Transport failures are retried too, counted apart
from the waits: timeouts six times, anything else ... three, since every call here is safe to resend", which is false
for the longpoll.

The same notes and the feed's KDoc say Dropbox adds "up to 90 s" to the 240 s asked for; the run measured a quiet
answer after 344.5 s (B, `1792489666.418` → `1792490010.899`), 104 s over.

## Fix

**The rule:** a poll that failed after it had been open for at least `HELD_POLL` (one minute) was held by the service,
so the link worked; it is asked again at once with the same cursor, and the waits start over (as after any wait that
reached the service). A poll that failed sooner backs off exactly as now. Why a duration rather than the exception's
type: the measured failures cannot be told apart by type (a far-end close, the client's own bound, and a proxy's
refused `CONNECT` all came out as `ClosedReadChannelException` on the desktop's CIO engine, and the browser's failed
`fetch` is a `kotlin.Error`), while the time a poll was open separates them cleanly. A minute is past the 20 s connect
timeout (`network/HttpClientConfiguration.kt`) that the desktop's CIO and Android's OkHttp apply, so there a poll that
could not connect never counts as held, and well short of the 240 s Dropbox holds a quiet folder's poll. In the
browser (`fetch` has only the call's 360 s total) and on iOS (do not rely on the Darwin engine applying
`connectTimeoutMillis`), a connection that hangs without being refused lasts the call's bound and then counts as held:
harmless, since that bound already paces it to one request every six minutes, against six minutes plus 30 s and more
today; an offline device fails at once there, which backs off as now. It is also the bound on the cost: a held poll
that is asked again at once can only repeat once a minute at most, however the link misbehaves, so no count of
immediate retries is needed.

A late answer of 408, 429, or 5xx (`ensureSuccessful` makes each a `SyncNetworkException`) after a minute counts as
held too, deliberately: it cannot be told from a cut connection without reading the exception's cause, Dropbox answers
its own rate limit at once rather than after holding a poll (and asks a poll to slow down through the answer's
`backoff`, which `awaitChanges` already waits), and what answers late is a middlebox at its idle limit, the case this
plan is about. The worst case is such a middlebox cutting every silent poll just past a minute: about 60 polls an hour
while the app is in front instead of about 12, each change still arriving at once, since Dropbox answers a poll as
soon as something changes. That stays within the bound above, so nothing is added for it. (The alternative of "one immediate retry, then the back-off" was weighed and not taken: two silent
polls in a row, which a healthy link produced within an hour here, would then back off again, and a dead socket's
360 s already paces the loop.)

This is about when to ask, not about what changed: the cursor still decides what the next poll reports, so nothing is
judged by a clock. The run retries of `SyncRunScheduler` (`NETWORK_RETRY_DELAYS` there) are untouched.

In `SyncRemoteWatcher.watchFrom`, measure only the wait itself (a failure of `changesSince` or of the preferences
download after a long wait that did answer is not a held poll):

```kotlin
while (true) {
    try {
        val askedAt = environment.timeSource.markNow()
        val hasChanges = try {
            point.folder.awaitChanges(cursor)
        } catch (exception: SyncNetworkException) {
            // Held for a minute and then lost - the far end closing a quiet poll, the call's own bound, a connection that
            // went silent, a middlebox at its idle limit - so the link carried it, and the next poll, asked at once with
            // the same cursor, reports whatever came meanwhile. A poll can be held this long at most once a minute.
            if (askedAt.elapsedNow() < HELD_POLL) throw exception
            failures = 0
            continue
        }
        if (!hasChanges) {
            failures = 0
            continue
        }
        val changes = point.folder.changesSince(cursor)
        ... (unchanged)
    } catch (exception: CancellationException) {
        ... (unchanged)
```

The rethrown exception reaches the existing outer `catch (exception: SyncNetworkException)`, which keeps its back-off;
reword its comment to "Offline, refused, or Dropbox busy: ...". Add to the companion object:

```kotlin
/**
 * How long a poll has to have been open for its failure to count as the service holding it and then losing it rather
 * than as the link failing: past the 20 s connect timeout of the engines that have one, well short of the minutes a
 * quiet folder's poll is held. Such a poll is asked again at once, which can repeat at most this often.
 */
val HELD_POLL: Duration = 1.minutes
```

`environment.timeSource` is `TimeSource.Monotonic` in the app and the test scheduler's in tests (`SyncEnvironment`), so
virtual time drives it. `continue` inside the nested `catch` is fine in Kotlin.

Expected effect on the measured cases: B's two silent polls are asked again within milliseconds (changes 30 s sooner,
and no growing waits); A's silent connection still hangs for the call's 360 s bound, but the change then arrives about
330 s after its upload instead of 389.6 s; the proxy restart's 10.7 s cut still waits 30 s (indistinguishable from a
link that cannot hold a connection).

**Dropbox's `timeout` stays 240 s.** A shorter one would bound a silently dead connection sooner (the call's bound is
the timeout + 90 + 30 s: 360 s now, 240 s at a 120 s timeout, 180 s at 60 s), at the price of more polls for the
whole time the app is in front. From the quiet answers measured (250, 279, and 344 s for a 240 s timeout) a poll
lasts about 290 s, about 12 polls an hour; at 120 s about 165 s (about 22 an hour), at 60 s about 105 s (about 34 an
hour), each a TLS request that wakes a phone's radio. A silent death needs a network change the system does not
report on the old connection, which on the measured desktop is a Wi-Fi switch, and a return to the app starts a run
anyway; and a live poll that the bound cuts short delays nothing, since Dropbox answers a poll at once when something
changes. So the cost is not worth paying for the rare case; no change, and no decision left open.

**No retry inside the feed.** `awaitChanges` keeps its bare `transport { }`; only the KDoc and the notes change:

- `DropboxChangeFeed.awaitChanges`'s KDoc: replace "plus up to a minute and a half of its own" with "plus the minute
  and a half of its own that Dropbox documents (104 s was measured)", and add that it is sent once and never retried
  here: what a poll that ends without an answer means is the watcher's call (`SyncRemoteWatcher`), which asks again at
  once after one the service held. Do the same in `LONGPOLL_CALL_TIMEOUT_MILLIS`'s KDoc ("the minute and a half it
  documents adding, with half a minute for the network, which also covers the 104 s once measured").
- `data/source/remote/implementation/CLAUDE.md`, the Retries bullet: "since every call here is safe to resend" becomes
  "since every call here is safe to resend; the longpoll alone is sent once, and the watcher decides what its failure
  means". In the Watching bullet, "plus Dropbox's up to 90 s" becomes "plus Dropbox's documented 90 s (104 s measured)".
- `SyncFolder.awaitChanges`'s KDoc (the `api` contract the watcher relies on): "Throws [SyncNetworkException] for a
  service that cannot be reached" becomes "... for a service that cannot be reached, or a wait that ended without an
  answer (a connection closed or timed out while the service held it)".
- `scheduling/CLAUDE.md`, the `SyncRemoteWatcher` bullet: "A feed that cannot be reached is asked again later" becomes
  "A poll the service held for `HELD_POLL` and then lost is asked again at once; one that failed sooner is asked again
  after `NETWORK_RETRY_DELAYS`". Keep the paragraph under ~1,500 characters together with plan 01's addition (trim a
  phrase if needed).

Kept as decided by the 36th review: only a 400 or 401 from the token endpoint disconnects; nothing here touches
renewals (the longpoll carries no token).

## Tests

In `SyncRemoteWatcherTest` (virtual time; `FakeSyncProvider.awaitAnswers` answers the next waits in place of the feed,
and a lambda there may `delay` before it throws; import `kotlinx.coroutines.delay`):

- `` `a poll the service held and then lost is asked again at once` ``: `world(localFiles = mapOf(song(1) to ONE))`;
  `world.provider.awaitAnswers.add { delay(5.minutes); throw SyncNetworkException("The server closed the connection.") }`;
  `world.synchronizeAndWatch()`; `advanceTimeBy(1.minutes)`; `world.provider.writeElsewhere(song(2), TWO)`;
  `advanceTimeBy(4.minutes + AUTOMATIC_RUN_DELAY + MARGIN)`; assert `world.runs.count == 2` and
  `world.local.files[song(2)]` is `TWO`. Today it fails: the watcher waits 30 s after the failure at 5 min.
- `` `a poll lost soon after it was sent still waits before it is asked again` ``: the same world, the answer
  `{ delay(10.seconds); throw SyncNetworkException("Connection refused.") }`; `synchronizeAndWatch()`;
  `writeElsewhere(song(2), TWO)`; `advanceTimeBy(10.seconds + AUTOMATIC_RUN_DELAY + MARGIN)`; assert one run and
  `world.provider.awaitCount == 1`; then `advanceTimeBy(30.seconds)` and assert two runs. This keeps the back-off for a
  link that fails at once (it passes before and after the change; it fails if the threshold is dropped).
- `` `a feed listing that keeps failing after a held poll still waits longer and longer` ``: the same world;
  `world.provider.awaitAnswers.add { delay(5.minutes); throw SyncNetworkException("The server closed the connection.") }`;
  `synchronizeAndWatch()` (the poll is asked at 1 s); `world.provider.writeElsewhere(song(2), TWO)`; count the calls
  in `world.provider.onChangesSince = { listings++; throw SyncNetworkException("Offline") }`;
  `advanceTimeBy(30.minutes)`; assert `listings == 8` (at 301 s, then after 30 s, 1, 2, and every 5 minutes: 301, 331,
  391, 511, 811, 1111, 1411, 1711 s; 7 before the change, which waits 30 s first; a loop at once would give
  thousands). This pins that only the wait is measured: the next poll answers at once, since the change is pending, so
  it is never held, and the listing's failure keeps the outer back-off.
- The existing `` `a wait that reached the service starts the retries over` `` and
  `` `the feed is asked again as soon as the device is back online` `` use failures that take no time and must pass
  unchanged.

In `DropboxChangeFeedTest` (contract pins for the KDoc this plan rewrites: the feed's code does not change, so both
pass before and after; they fail if the longpoll is ever routed through the retrying `request`, or its failure mapped
to anything but `SyncNetworkException`. MockEngine; per the 36th review's measured fact, a test that relies on `HttpTimeout` passes
`engineDispatcher = StandardTestDispatcher(testScheduler)` to `dropboxProvider`, as `DropboxRequestTest` does):

- `` `a wait cut off without an answer is the network failing, and is sent once` ``: a handler that counts requests and
  throws `io.ktor.utils.io.ClosedReadChannelException()` (the measured failure; a `kotlinx.io.IOException` does as
  well if that class's constructor is not public in the Ktor version in use); `assertFailsWith<SyncNetworkException>
  { provider.awaitChanges("cursor 1") }` and one request. This pins the contract the watcher relies on: anything else
  (a `DropboxApiException`) would make it stop watching and schedule a run.
- `` `a stalled wait fails within the call's own bound` ``: modelled on `DropboxRequestTest`'s
  `` `a stalled call on the browser fails within its own bound` `` (`configure = { configureClient(sendsUserAgent =
  false, hasSocketTimeout = false) }`, the handler `awaitCancellation()`, the engine dispatcher above); assert it throws
  `SyncNetworkException`, sends one request, and `currentTime <= DropboxChangeFeed.LONGPOLL_CALL_TIMEOUT_MILLIS + 1_000`.
  MockEngine does not enforce `socketTimeoutMillis`, so only the browser's bound can be driven this way; drop the test
  if `BrowserCallTimeout` does not reach this call under that configuration, and say so in the commit.

Run `./gradlew :data:sync:implementation:desktopTest :data:source:remote:implementation:desktopTest`.
