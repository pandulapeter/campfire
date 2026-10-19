<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Treat only a refusal by Dropbox's token endpoint as an authorization failure, and anything else it answers as the network

**Challenged:** sound — checked: Ktor 3.6.0's `HttpRedirect` follows only GET and HEAD, so a portal's 3xx to the token `POST` does arrive as the status; after plan 05 the newly network-classified answers (3xx, 403, 407, 408) are retried three times by `request` before the run ends, which the tests below still see as `SyncNetworkException`.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Lane:** T
**Decision:** taken by the user (2026-10-19): option B, as recommended.
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokens.kt`,
`data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTransport.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxTokensTest.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/dropbox/DropboxRequestTest.kt`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

`DropboxTokens.exchange` (`dropbox/DropboxTokens.kt:73-80`):

```kotlin
if (!response.status.isSuccess()) {
    // Being told to wait, or the service having trouble of its own, says nothing about the credentials, and
    // reporting it as a refusal would send the user off to connect an account that is perfectly fine.
    if (response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500) {
        throw SyncNetworkException("Dropbox is busy: ${response.status.value}")
    }
    throw SyncAuthorizationException("Dropbox refused the authorization: ${response.status.value} ${response.oAuthError()}")
}
```

Every answer that is not 429 or 5xx is read as Dropbox refusing the credentials. Probes: a corporate proxy's 403 block
page, a 407 (proxy authentication required), a 408, and any 3xx (Ktor does not follow redirects of a `POST`, so a
captive portal's 302 arrives as the status) all end in `SyncAuthorizationException`. `SyncRunner` turns that into
`SyncState.ConnectionFailed(AUTHORIZATION)`: Settings tells the user to reconnect an account that is fine, on a network
that merely blocks the token endpoint for a while. The comment above the branch states exactly the principle the
fall-through breaks.

`exchange` serves two flows:

- the renewal in `accessToken` (`grant_type=refresh_token`), where Dropbox answers `400 invalid_grant` for a revoked or
  expired refresh token: the one answer that really means "connect again";
- the code exchange in `DropboxSyncProvider.completeAuthorization` (`grant_type=authorization_code`), where
  `invalid_grant` means the code was spent or expired (a reload, a slow consent page). Reported as an authorization
  failure it says "could not connect", which is right.

Separately, `DropboxTransport.retryAfterMillis` (`DropboxTransport.kt:121-129`) waits out 429, 5xx, and
`too_many_write_operations`, but not `408 Request Timeout`, which a proxy or load balancer sends for a request it gave
up on; `ensureSuccessful` then makes a 408 one file's `DropboxApiException` instead of a network failure.

## Fix

Options for what counts as a refusal (all keep 429/5xx as `SyncNetworkException` as today):

- **A. By the OAuth error code.** `SyncAuthorizationException` only for a 400 or 401 whose JSON body has an `error` of
  `invalid_grant`, `invalid_client`, or `unauthorized_client` (the RFC 6749 §5.2 codes that say the grant or the app is
  refused); everything else, including a 400/401 without a readable OAuth body, `SyncNetworkException`. Most precise.
  Risk: a refusal Dropbox phrases otherwise (`invalid_request` for a malformed refresh token, `invalid_scope`, a
  plain-text 400 like its RPC endpoints send) is retried as "network" on every run forever, and the user never sees
  "reconnect".
- **B. By the status (recommended).** `SyncAuthorizationException` for 400 and 401 whatever the body;
  `SyncNetworkException` for everything else (3xx, 403, 407, 408, other 4xx, 429, 5xx). Proxies, portals, and
  firewalls answer 302/303/307, 403, 407, 408, or 511, almost never 400/401, so this removes every probed false
  disconnect; and any refusal Dropbox can send (all standard OAuth errors are 400 or 401) still reads as a refusal.
  Residual risk: a middlebox answering 400/401 still disconnects, as today.
- **C. A then B.** 400/401 with a readable OAuth `error` → refusal; 400/401 with an HTML or empty body → network.
  Catches a portal that answers 401 with its login page, at the cost of A's risk for a plain-text Dropbox 400.

Recommended: **B**. It fixes the probed cases with no new way to strand a user who really must reconnect. Before
landing, check Dropbox's token endpoint documentation
(<https://www.dropbox.com/developers/documentation/http/documentation#oauth2-token>) that every refusal it lists is a
400 or 401; if it lists a refusal under another status, add that status to the refusal set.

In the code: replace the fall-through with a `when` on the status; keep `oAuthError()` in the message of both
exceptions (it is how a log tells the cases apart). Keep the existing comment's reasoning and extend it with one
sentence: an answer from something between the device and Dropbox is never about the credentials either.

Also: add `HttpStatusCode.RequestTimeout` (408) to the waited statuses in `retryAfterMillis`, and to the
`SyncNetworkException` branch of `ensureSuccessful` (`DropboxTransport.kt:180`), so a 408 that outlasts the retries
ends the run as the network rather than failing one file.

Update `data/source/remote/implementation/CLAUDE.md`: the Dropbox "Retries on 429, 5xx, …" bullet gains 408; the OAuth
section gains "Only a 400 or 401 from the token endpoint is a refusal; any other answer (a proxy's 403 or 407, a portal's
redirect) is the network."

## Tests

`DropboxTokensTest`, with an expired stored token so `list()` renews first (as the existing renewal tests do):

- the token endpoint answers `403` with an HTML body → `assertFailsWith<SyncNetworkException> { provider.list() }`;
  same for `407`, `408`, and `302` (with a `Location` header).
- the existing `reports why the token endpoint refused a refresh` (400 `invalid_grant` → `SyncAuthorizationException`)
  keeps passing; add a 401 `invalid_client` case.
- `completeAuthorization` whose exchange is answered `400 invalid_grant` → `SyncAuthorizationException` and nothing is
  stored; answered `403` HTML → `SyncNetworkException`.

`DropboxRequestTest`: an RPC answered `408` once and then successfully succeeds; one answered `408` every time fails with
`SyncNetworkException`.
