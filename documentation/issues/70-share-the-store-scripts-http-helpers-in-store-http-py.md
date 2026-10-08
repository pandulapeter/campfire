# Give the store submission scripts one HTTP-with-retries module, store_http.py, instead of two copies and a cross-import

**Challenged:** amended — `request_json` takes a `label` for its log and `fail` lines: Apple's lines name the relative `path` (`GET /builds/… failed (503)`) while the request goes to `API + path`, so passing only the URL would change every Apple log line the plan promises to keep; Apple's strict `decode()` of an error body is kept inside its `error_details`.

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** medium  ·  **Platforms:** iOS, macOS, Windows (release pipeline)
**Files:** new `.github/scripts/store_http.py`; new `.github/scripts/test_store_http.py`; `.github/scripts/app_store_signing.py`;
`.github/scripts/app_store_submission.py`; `.github/scripts/microsoft_store_submission.py`;
`.github/scripts/test_app_store_signing.py`; `.github/scripts/test_microsoft_store_submission.py`; root `CLAUDE.md` /
`.github/CLAUDE.md` only if they name where `request` lives
**Depends on:** none (if plan 62 lands first, its `require_secrets.py` may reuse `fail` from here; either order works)

## Problem

`app_store_submission.py` borrows its HTTP layer from a sibling with a different job:

```python
from app_store_signing import awaiting_versions, fail, request
```

and `microsoft_store_submission.py` reimplements the same layer. Both files define, character for character,

```python
REQUEST_ATTEMPTS = 4
REQUEST_TIMEOUT_SECONDS = 60

def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)

def should_retry(method, error, attempt, attempts):
    """Whether a failed request is asked again: only a GET, only a failure the service or the network may not repeat."""
    if method != "GET" or attempt >= attempts:
        return False
    if isinstance(error, urllib.error.HTTPError):
        return error.code == 429 or error.code >= 500
    return isinstance(error, (OSError, http.client.HTTPException))
```

and a `request(method, path_or_url, body=None, …)` with the same loop: a fresh `Authorization: Bearer {token()}` per
attempt, `urlopen(..., timeout=REQUEST_TIMEOUT_SECONDS)`, JSON in and out, a `10 * attempt` second back-off printed
as "`{method} {path} failed (…), asking again in … s.`", and the same comment about `URLError` wrapping only send
errors. They differ only in the base URL handling (Microsoft accepts absolute `https://` URLs for the upload/commit
links), the token function, Apple's `errors[].detail` extraction and its `missing_ok` (404) / `conflict_ok` (409)
answers. `should_retry` is tested twice, identically (`ShouldRetryTest` in both test files). A retry-policy fix has
to be made in two places, and editing `app_store_signing.py` can break `app_store_submission.py`.

## Fix

1. Create `store_http.py` (MPL header, a docstring saying it is the HTTP layer of the three store scripts) holding
   `REQUEST_ATTEMPTS`, `REQUEST_TIMEOUT_SECONDS`, `fail`, `should_retry` and
   ```python
   def request_json(method, url, token, body=None, error_details=lambda error: error.read().decode(errors="replace"),
                    answers=None, label=None):
       """answers maps an HTTP status to the value to return instead of failing, e.g. {404: None}."""
   ```
   with the loop moved verbatim (comments included). `token` is a zero-argument callable, called once per attempt.
   `label` (default: `url`) is what the "asking again", "answered" and "failed" lines name: Microsoft's lines name the
   prefixed URL today (its `request` reassigns `url` before the loop), Apple's name the relative `path`, so Apple's
   wrapper passes `label=path` while the request itself goes to `API + path`. Apple's `error_details` keeps its strict
   `error.read().decode()` (no `errors="replace"`) inside the `errors[].detail` join, as today.
   For a status in `answers` the function returns `answers[code]`, after the existing stderr line for 409
   (`print(f"{method} {path} answered 409: {details}", file=sys.stderr)`) — keep that line by letting Apple's wrapper
   print it, or by printing for every answered status other than 404; the visible output must stay the same.
2. `app_store_signing.py`: `request(method, path, body=None, missing_ok=False, conflict_ok=False)` keeps its signature
   and becomes a thin wrapper: `request_json(method, API + path, token, body, error_details=<the errors[].detail
   join, moved verbatim>, answers={404: None} if missing_ok else … )`. Import `fail` from `store_http` (re-exported
   name unchanged for its own callers).
3. `app_store_submission.py`: `from store_http import fail` and keep `from app_store_signing import awaiting_versions,
   request` (the Apple API wrapper legitimately lives with the Apple token; only `fail` moves). Optional follow-up,
   not part of this plan: move the Apple API client (`token`, `request`, `awaiting_versions`, `find_app_id`) into
   `app_store_api.py`.
4. `microsoft_store_submission.py`: delete its `fail`, `should_retry`, constants and `request` body; `request(method,
   url, body=None)` keeps its signature (URL prefixing kept) and calls `request_json(..., token, body)`.
   `blob_request` (the upload PUT with its own 3-attempt, `< 500` policy) stays as it is — a different policy on
   purpose.
5. Tests: move `ShouldRetryTest` into `test_store_http.py` once; delete the two copies; update the imports in the two
   existing test files.

The workflows need no change: the scripts run from `.github/scripts`, Python puts the script's directory on
`sys.path`, and the "Take the store scripts from the workflow's commit" step checks out the whole directory.

## Tests

`test_store_http.py` (picked up by `python3 -m unittest discover -s .github/scripts -p 'test_*.py'` in `tests.yml`):
- `ShouldRetryTest` moved from the two existing files;
- `request_json` with `urllib.request.urlopen` patched (`unittest.mock.patch("store_http.urllib.request.urlopen")`)
  and `time.sleep` patched: a GET that fails with 503 twice then succeeds returns the JSON and calls `token` three
  times; a POST that fails with 503 calls `fail` (assert `SystemExit`) after one attempt; a 404 with
  `answers={404: None}` returns `None`; an empty body returns `None`; a dropped connection (`ConnectionResetError`) on a
  GET is retried.
Run `python3 -m unittest discover -s .github/scripts -p 'test_*.py'` before and after: all green.

## Manual check

At the next release, with every store's `submit: false` comment in its description (see plan 62's Manual check), the
iOS, macOS and Windows runs reach "prepared"/"draft" with the same log lines as before (`asking again in … s.` on a
retry, `answered 409` where Apple answers it). Then submit from the consoles.
