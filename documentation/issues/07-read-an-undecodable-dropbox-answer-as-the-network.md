<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Decode every Dropbox answer inside the transport, so an answer that is not Dropbox's reads as a network failure

**Challenged:** amended — corrected the `delete` claim: today an undecodable `delete_batch` answer does not end the run (`SyncEngine.deleteRemotely` catches it and fails every deletion as one file each); after the fix it ends the run as NETWORK, which is the intended change. A refused refresh whose body does not decode is unaffected (`oAuthError` swallows it); fix otherwise unchanged.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** T
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTransport.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxSyncProvider.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokens.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxDeletionTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`DropboxTransport.rpc` returns the body as text, and its callers decode it outside `transport`
(`dropbox/DropboxSyncProvider.kt`):

```kotlin
var response = json.decodeFromString<DropboxListFolderResponse>(            // :214
    requests.rpc(LIST_FOLDER_URL, """{"path":"","recursive":true,"include_deleted":false}""")
)
…
response = json.decodeFromString(requests.rpc(LIST_FOLDER_CONTINUE_URL, …))  // :219
…
var response = json.decodeFromString<DropboxDeleteBatchResponse>(requests.rpc(DELETE_BATCH_URL, body))  // :362
…
response = json.decodeFromString<DropboxDeleteBatchResponse>(requests.rpc(DELETE_BATCH_CHECK_URL, …))   // :366
…
val account = json.decodeFromString<DropboxAccountResponse>(requests.rpc(ACCOUNT_URL, body = null))      // :164
```

A `200` whose body is not JSON (a TLS-intercepting proxy's block page, a captive portal that answers every request with
its login page, or a body cut mid-JSON) throws `JsonDecodingException`. `list()` is the first call of a run, so the run ends
with `SyncFailureReason.UNKNOWN` ("something went wrong") rather than NETWORK ("could not reach Dropbox"). In
`delete` it escapes `delete` as a whole, and `SyncEngine.deleteRemotely` (not a `SyncRunEndingException`) fails every
deletion of the pass as one file's failure each, with a JSON parser message, and the run goes on; after this fix the
run ends as the network instead, like a listing does.

The token answer in `DropboxTokens.exchange` (`DropboxTokens.kt:81`) has the same gap:

```kotlin
return json.decodeFromString(response.bodyAsText())
```

During a renewal this is inside `request`'s `transport { }` and so already a `SyncNetworkException`, but in
`completeAuthorization` (the code exchange) it is not: a portal page there ends the connection attempt as
`ConnectionFailed(UNKNOWN)`.

The upload receipt already does it right (`:297-303`): an unreadable receipt is a `SyncNetworkException`, because the
contract files it under "cannot tell".

## Fix

- In `DropboxTransport`, make `rpc` decode: `suspend fun <T> rpc(url: String, body: String?, deserializer: DeserializationStrategy<T>): T`,
  reading the text inside `transport { }` as now and then
  `try { json.decodeFromString(deserializer, text) } catch (exception: SerializationException) { throw SyncNetworkException("Dropbox's answer could not be read.", exception) }`.
  A `DeserializationStrategy` parameter keeps `rpc` non-inline (an inline reified version could not reach the private
  `httpClientHolder`). Callers pass `DropboxListFolderResponse.serializer()` and so on.
  Alternative: keep `rpc` returning text and add a private `decode` helper in the provider; equivalent, but every future
  call has to remember it. Recommended: decode in `rpc`.
- In `DropboxTokens.exchange`, decode the success body the same way (`SerializationException` →
  `SyncNetworkException`), and read `bodyAsText()` inside `transport { }`.
- `loadAccount` (`:161-190`) catches `SyncAuthorizationException` first and every other `Exception` after, falling back
  to `storedAccountOf(credentials)`. `SyncNetworkException` is a sibling of `SyncAuthorizationException` under the sealed
  `SyncRunEndingException`, so the fallback still applies; keep the order.
- Update the KDoc of `transport` (`DropboxTransport.kt:147-157`): "Only the call itself is wrapped, so that a body
  Campfire cannot make sense of stays the programming error it is" is no longer true for answers. Say instead that an
  answer which is not one of Dropbox's documented shapes is taken for something in between (a proxy, a portal, or a cut
  body) and is the network's failure, while `transport` itself still wraps only the call.
- `data/source/remote/implementation/CLAUDE.md`, Dropbox section: add "An answer that cannot be decoded is
  `SyncNetworkException`: a 200 HTML page comes from something in between, not from Dropbox."

Plan 08 builds on this (a missing field becomes a `SerializationException` that this mapping turns into the network).

## Tests

- `DropboxRequestTest`: `a listing answered with a page that is not Dropbox's is the network failing`: the handler
  answers `respond("<html>portal</html>", OK, headersOf(ContentType, "text/html"))`;
  `assertFailsWith<SyncNetworkException> { provider.list() }`.
- `DropboxDeletionTest`: the same body for `delete_batch` → `assertFailsWith<SyncNetworkException> { provider.delete(...) }`.
- `DropboxRequestTest`: `an account read answered with a portal page is the account stored last time` (the
  `STORED_NAME` credentials, HTML body) → the stored `SyncAccount`.
- `DropboxRequestTest`: `completeAuthorization` whose token answer is a 200 HTML page →
  `assertFailsWith<SyncNetworkException>` and nothing stored.
