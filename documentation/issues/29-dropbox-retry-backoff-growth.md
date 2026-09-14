# 29 · The retry delay has no growth and a short ceiling

**Severity:** low · **Area:** `:data:source:remote:implementation`

`request` (:295–307) tries five times with a fixed `DEFAULT_RETRY_SECONDS = 2` (+ jitter) when no `Retry-After` is
given, so a Dropbox 5xx blip longer than ~12 s aborts the run with `SyncNetworkException`.

## Fix

When the service gives no `Retry-After`, double the delay per attempt from 2 s, capped at 32 s
(`delay = min(DEFAULT_RETRY_SECONDS shl attempt, MAX_RETRY_SECONDS)`), and raise `MAXIMUM_RETRIES` to 6 (≈ 94 s of
patience). A `Retry-After` header or a `retry_after` body field (issue 16) is honoured as given. Update the KDoc.
