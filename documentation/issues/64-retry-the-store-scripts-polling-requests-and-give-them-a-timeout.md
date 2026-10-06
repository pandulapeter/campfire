# Retry the store scripts' GET requests on a transient failure and give every request a timeout

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** CI (iOS, macOS, Windows)
**Files:** `.github/scripts/app_store_signing.py`, `.github/scripts/microsoft_store_submission.py`,
`.github/scripts/test_app_store_signing.py`, a new `.github/scripts/test_microsoft_store_submission.py`
**Challenged:** amended — urllib wraps only the errors of *sending* a request in `URLError`; a connection dropped while the answer is awaited or read (`http.client.RemoteDisconnected`, `ConnectionResetError`, `http.client.IncompleteRead`) reaches the caller unwrapped, so the retry now covers `OSError` and `http.client.HTTPException` too, and the `except` clause catches them; `github_token()`'s request gets the timeout as well.

## Problem

After `altool` has uploaded a build, `app_store_submission.py` `wait_for_build` polls App Store Connect once a minute
for up to 90 minutes, and after the commit `microsoft_store_submission.py` `wait_for_commit` polls Partner Center every
30 s for up to 30 minutes. Every poll goes through a `request` with no timeout and no retry, which treats any
`HTTPError` as final and does not catch a `URLError` at all (8ee010b36):

```python
# app_store_signing.py:108-126 (imported by app_store_submission.py)
def request(method, path, body=None, missing_ok=False, conflict_ok=False):
    ...
    try:
        with urllib.request.urlopen(urllib.request.Request(API + path, data=data, headers=headers, method=method)) as response:
            ...
    except urllib.error.HTTPError as error:
        ...
        fail(f"{method} {path} answered {error.code}: {details}")
```

```python
# microsoft_store_submission.py:121-131
def request(method, url, body=None):
    ...
    except urllib.error.HTTPError as error:
        fail(f"{method} {url} answered {error.code}: {error.read().decode(errors='replace')}")
```

One 429/500/502/503 or one reset connection among up to 90 polls ends the job red after the upload. "Re-run failed
jobs" then dies at `altool` on the duplicate build number, so the submission is finished by hand or re-dispatched with
`build_number`. A connection that hangs holds the job until GitHub's six-hour limit, since `urlopen` has no `timeout`.
Only `blob_request` (the package upload) retries today.

## Fix

In both `request` functions:

1. Pass `timeout=60` to `urlopen` (the Microsoft token request in `token()` and the GitHub OIDC request in
   `github_token()` too; not `blob_request`, whose 4 MiB blocks keep their own handling).
2. Retry **GET** requests only (POST, PATCH and DELETE stay single-shot, since a repeated create is not idempotent),
   up to 4 attempts with `time.sleep(10 * attempt)` between them, when the failure is an `HTTPError` with code 429 or
   ≥ 500, or any other `OSError` (`urllib.error.URLError`, `TimeoutError`, `ConnectionResetError` and the rest of
   `ConnectionError`, an `ssl.SSLError`) or `http.client.HTTPException` (`RemoteDisconnected`, `IncompleteRead`).
   `urllib.request`'s `do_open` wraps only what `h.request` raises in `URLError`; what `h.getresponse()` and
   `response.read()` raise — the usual shape of a dropped keep-alive connection, "Remote end closed connection without
   response" — arrives unwrapped, so the `except` clause after the `HTTPError` one must be
   `except (OSError, http.client.HTTPException) as error:` (with `import http.client`), and an unretried one of those
   ends in `fail(f"{method} {path} failed: {error}")` rather than a traceback. Every other `HTTPError` keeps today's
   handling (`missing_ok`, `conflict_ok`, `fail`). After the last attempt, `fail` with the last error, as today.
   `token()` is called inside the loop, so each attempt carries a fresh token (App Store Connect's last ten minutes).

Put the decision in a small pure function so it can be tested, in each file (the two scripts do not import each
other, and `microsoft_store_submission.py` should not start importing the Apple one):

```python
def should_retry(method, error, attempt, attempts):
    """Whether a failed request is asked again: only a GET, only a failure the service or the network may not repeat."""
    if method != "GET" or attempt >= attempts:
        return False
    if isinstance(error, urllib.error.HTTPError):
        return error.code == 429 or error.code >= 500
    return isinstance(error, (OSError, http.client.HTTPException))
```

(`HTTPError` is a subclass of `URLError` and so of `OSError`, so the `HTTPError` check must come first, as above.) Print a line on each
retry (`print(f"{method} {path} failed ({reason}), asking again in {seconds} s.")`) so the log shows it.

## Tests

`should_retry` in both files, with `urllib.error.HTTPError(url, code, msg, hdrs, fp)` built by hand: a GET with 503,
502, 429, a `URLError("reset")`, a `TimeoutError()`, a `ConnectionResetError()` and an
`http.client.RemoteDisconnected("Remote end closed connection without response")` retries before the last attempt and not at it; a GET with
404, 401 or 409 does not; a POST/PATCH with 503 does not. Put the Apple one in `test_app_store_signing.py` and the
Microsoft one in a new `test_microsoft_store_submission.py` (with the MPL header the other test files carry); `tests.yml`
already runs every `test_*.py` in `.github/scripts`.

## Manual check

None realistic on demand; the next releases' iOS, macOS and Windows logs show a "asking again" line where a poll
failed and the job still green.
