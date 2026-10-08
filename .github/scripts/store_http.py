# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The HTTP layer of the three store scripts (app_store_signing.py, app_store_submission.py and
microsoft_store_submission.py): one JSON call of a store's API, asked again where the service or the network failed a
GET, and a run's failure reported the way GitHub Actions shows it.
"""

import http.client
import json
import sys
import time
import urllib.error
import urllib.request

# How often a GET is asked before its failure is final, and how long any request may take: a connection that hangs
# would otherwise hold the job until GitHub's six-hour limit.
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


def request_json(method, url, token, body=None, error_details=lambda error: error.read().decode(errors="replace"),
                 answers=None, label=None):
    """
    One call of a store's API, its JSON body in and out. A GET is asked again where the service or the network failed
    it, since one bad answer among the polls that follow an upload or a commit would otherwise fail a job that cannot
    simply be run again. token is a function returning the bearer token. answers maps an HTTP status to the value to
    return instead of failing, e.g. {404: None}; an answered status other than 404 is still named on stderr. label is
    what the lines about the call name, the url by default.
    """
    label = url if label is None else label
    answers = answers or {}
    data = json.dumps(body).encode() if body is not None else None
    for attempt in range(1, REQUEST_ATTEMPTS + 1):
        # Asked for on every attempt, since a token lasts minutes and the attempts wait between them.
        headers = {"Authorization": f"Bearer {token()}", "Content-Type": "application/json"}
        try:
            call = urllib.request.Request(url, data=data, headers=headers, method=method)
            with urllib.request.urlopen(call, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            if should_retry(method, error, attempt, REQUEST_ATTEMPTS):
                print(f"{method} {label} failed ({error.code}), asking again in {10 * attempt} s.")
                time.sleep(10 * attempt)
                continue
            details = error_details(error)
            if error.code in answers:
                if error.code != 404:
                    print(f"{method} {label} answered {error.code}: {details}", file=sys.stderr)
                return answers[error.code]
            fail(f"{method} {label} answered {error.code}: {details}")
        # urllib wraps only the errors of sending a request in URLError: a connection dropped while the answer is
        # awaited or read arrives as it was raised.
        except (OSError, http.client.HTTPException) as error:
            if should_retry(method, error, attempt, REQUEST_ATTEMPTS):
                print(f"{method} {label} failed ({error}), asking again in {10 * attempt} s.")
                time.sleep(10 * attempt)
                continue
            fail(f"{method} {label} failed: {error}")
