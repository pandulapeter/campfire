# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Submits a new .msix to the Microsoft Store for certification, which is everything an update used to need Partner Center
open for.

    microsoft_store_submission.py <package identity name> <package version> <.msix file> <release notes file>

The app is found by its package identity name (campfire.windows.identityName), so no Store ID has to be kept anywhere.
A new submission is a copy of the last published one: this replaces its package with the new one, writes the release
notes as its "What's new in this version", sets it to be published as soon as it passes certification, uploads the
package and commits it, then waits until Partner Center has accepted the commit (which is where a package that does
not match the product's identity is refused). A green run means submitted, not certified: certification answers by
email, usually within a few days.

A submission that is already in progress with this very package version is left as it is, so a run that is repeated does
not fail on its own success. One in progress with anything else - a draft started in Partner Center, the previous
release still in certification, a commit that failed - stops the run instead of being deleted: it may be somebody's
work, and the product can only have one submission in progress at a time.

Uses the Microsoft Store submission API as a Microsoft Entra application that has the Manager role in Partner Center,
named by MICROSOFT_STORE_TENANT_ID and MICROSOFT_STORE_CLIENT_ID. It proves who it is with no secret at all: the
application trusts the OIDC token GitHub Actions hands a job in this repository's microsoft-store environment (a
federated credential on the application in Entra), so the job needs the id-token: write permission and nothing kept
anywhere expires. Only needs the Python standard library.
"""

import base64
import json
import os
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

API = "https://manage.devcenter.microsoft.com/v1.0/my"
# The most Partner Center takes for "What's new in this version".
RELEASE_NOTES_LIMIT = 1500
# The largest block every version of the blob service accepts, whichever one the upload URL was signed for.
UPLOAD_BLOCK_BYTES = 4 * 1024 * 1024
COMMIT_TIMEOUT_SECONDS = 30 * 60
POLL_INTERVAL_SECONDS = 30
# The states of a submission that is past its commit and on its way to the Store.
SUBMITTED_STATES = {"PreProcessing", "Certification", "Release", "PendingPublication", "Publishing", "Published"}

_token = {"value": None, "expires": 0}


def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def github_token():
    """The job's OIDC token from GitHub, which is what the federated credential on the Entra application trusts."""
    url = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL")
    if not url:
        fail("GitHub hands out no OIDC token to this job: it needs the id-token: write permission, in the calling "
             "workflow too.")
    url += "&audience=" + urllib.parse.quote("api://AzureADTokenExchange")
    headers = {"Authorization": f"Bearer {os.environ['ACTIONS_ID_TOKEN_REQUEST_TOKEN']}"}
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers)) as response:
        return json.loads(response.read())["value"]


def token():
    """An access token for the API, fetched again a few minutes before it runs out, since one lasts an hour."""
    if time.time() < _token["expires"] - 300:
        return _token["value"]
    tenant = os.environ["MICROSOFT_STORE_TENANT_ID"]
    # GitHub's token is asked for again every time, since it lasts minutes rather than the hour Entra's does.
    body = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "client_id": os.environ["MICROSOFT_STORE_CLIENT_ID"],
        "client_assertion_type": "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        "client_assertion": github_token(),
        "scope": "https://manage.devcenter.microsoft.com/.default",
    }).encode()
    try:
        url = f"https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token"
        with urllib.request.urlopen(url, data=body) as response:
            answer = json.loads(response.read())
    except urllib.error.HTTPError as error:
        # AADSTS70021 is a subject nobody trusts: the job is not in the environment the credential names, or the
        # credential's subject is not the one GitHub writes (repo:<owner>/<repository>:environment:<name>).
        fail(f"Microsoft Entra ID refused the GitHub token ({error.code}): {error.read().decode(errors='replace')}\n"
             "The application needs a federated credential with the issuer "
             f"https://token.actions.githubusercontent.com, the subject "
             f"repo:{os.environ.get('GITHUB_REPOSITORY')}:environment:microsoft-store and the audience "
             "api://AzureADTokenExchange.")
    _token["value"] = answer["access_token"]
    _token["expires"] = time.time() + int(answer.get("expires_in", 3600))
    return _token["value"]


def request(method, url, body=None):
    url = url if url.startswith("https://") else API + url
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Authorization": f"Bearer {token()}", "Content-Type": "application/json"}
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method)) as response:
            content = response.read()
            return json.loads(content) if content else None
    except urllib.error.HTTPError as error:
        fail(f"{method} {url} answered {error.code}: {error.read().decode(errors='replace')}")


def find_app(identity_name):
    url = "/applications?top=100"
    while url:
        page = request("GET", url)
        app = next((item for item in page.get("value", []) if item.get("packageIdentityName") == identity_name), None)
        if app is not None:
            return app
        next_link = page.get("@nextLink")
        url = f"/{next_link}" if next_link else None
    fail(f"There is no app with the package identity name {identity_name} in Partner Center, or the Entra application "
         "cannot see it: it needs the Manager role on the account.")


def pending_submission(app, version):
    """False where there is nothing in progress, True where this version already is; stops on anything else."""
    pending = app.get("pendingApplicationSubmission")
    if not pending:
        return False
    submission = request("GET", f"/applications/{app['id']}/submissions/{pending['id']}")
    status = submission.get("status")
    versions = {package.get("version") for package in submission.get("applicationPackages", [])
                if package.get("fileStatus") != "PendingDelete"}
    if status in SUBMITTED_STATES and version in versions:
        print(f"Version {version} is already submitted ({status}); nothing to do.")
        return True
    fail(f"The app already has a submission in progress ({status}, with {', '.join(sorted(v for v in versions if v)) or 'no package'}). "
         "The Store takes one at a time: wait for it to be published, or delete it in Partner Center, and run this again.")


def prepare(submission, package_name, notes):
    """Swaps the package of the copied submission for the new one and writes the notes into every listing."""
    packages = submission.get("applicationPackages", [])
    for package in packages:
        package["fileStatus"] = "PendingDelete"
    packages.append({
        "fileName": package_name,
        "fileStatus": "PendingUpload",
        # Required by the API and ignored for anything newer than Windows 8.
        "minimumDirectXVersion": "None",
        "minimumSystemRam": "None",
    })
    submission["applicationPackages"] = packages
    listings = submission.get("listings", {})
    if not listings:
        fail("The copied submission has no Store listing to write the release notes into.")
    # The listing is in English only; any other listing it gains gets the same text rather than the last release's.
    for language, listing in listings.items():
        listing.setdefault("baseListing", {})["releaseNotes"] = notes
        print(f"Wrote the release notes for {language}.")
    submission["targetPublishMode"] = "Immediate"


def upload(upload_url, package):
    """Puts the package into a zip at the top level, the way the submission names it, and uploads it block by block."""
    with tempfile.TemporaryDirectory() as directory:
        archive = os.path.join(directory, "submission.zip")
        # Stored rather than compressed: an .msix already is a zip, and deflating it again only costs time.
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_STORED, allowZip64=True) as zip_file:
            zip_file.write(package, os.path.basename(package))
        block_ids = []
        with open(archive, "rb") as file:
            while chunk := file.read(UPLOAD_BLOCK_BYTES):
                block_id = base64.b64encode(f"{len(block_ids):08d}".encode()).decode()
                blob_request(f"{upload_url}&comp=block&blockid={urllib.parse.quote(block_id)}", chunk)
                block_ids.append(block_id)
        block_list = "".join(f"<Latest>{block_id}</Latest>" for block_id in block_ids)
        blob_request(f"{upload_url}&comp=blocklist", f'<?xml version="1.0" encoding="utf-8"?><BlockList>{block_list}</BlockList>'.encode())
        print(f"Uploaded {os.path.basename(package)} in {len(block_ids)} blocks.")


def blob_request(url, data, attempts=3):
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, method="PUT")):
                return
        except urllib.error.HTTPError as error:
            if attempt == attempts or error.code < 500:
                fail(f"The upload was refused ({error.code}): {error.read().decode(errors='replace')}")
        except urllib.error.URLError as error:
            if attempt == attempts:
                fail(f"The upload failed: {error.reason}")
        time.sleep(5 * attempt)


def wait_for_commit(app_id, submission_id):
    deadline = time.time() + COMMIT_TIMEOUT_SECONDS
    while True:
        answer = request("GET", f"/applications/{app_id}/submissions/{submission_id}/status")
        status = answer.get("status")
        details = answer.get("statusDetails") or {}
        for warning in details.get("warnings", []):
            print(f"::warning::{warning.get('code')}: {warning.get('details')}")
        if status in SUBMITTED_STATES:
            return status
        if status != "CommitStarted":
            errors = "\n".join(f"{error.get('code')}: {error.get('details')}" for error in details.get("errors", []))
            fail(f"Partner Center did not accept the submission ({status}):\n{errors or 'no details given'}\n"
                 "Delete the submission in Partner Center before running this again.")
        if time.time() > deadline:
            fail(f"The commit was still in progress after {COMMIT_TIMEOUT_SECONDS // 60} minutes; see Partner Center.")
        print("The commit is still in progress, asking again in half a minute.")
        sys.stdout.flush()
        time.sleep(POLL_INTERVAL_SECONDS)


def main(identity_name, version, package, notes_file):
    with open(notes_file, encoding="utf-8") as file:
        notes = file.read().strip()
    if len(notes) > RELEASE_NOTES_LIMIT:
        fail(f"The release notes are {len(notes)} characters long, and Partner Center takes {RELEASE_NOTES_LIMIT}.")
    if not notes:
        fail("An update needs release notes for its \"What's new in this version\", and there are none.")
    if not os.path.isfile(package):
        fail(f"There is no package at {package}.")
    app = find_app(identity_name)
    if pending_submission(app, version):
        return
    submission = request("POST", f"/applications/{app['id']}/submissions")
    submission_id = submission["id"]
    upload_url = submission.pop("fileUploadUrl")
    print(f"Created submission {submission_id} for {app.get('primaryName', identity_name)}.")
    prepare(submission, os.path.basename(package), notes)
    request("PUT", f"/applications/{app['id']}/submissions/{submission_id}", submission)
    upload(upload_url, package)
    request("POST", f"/applications/{app['id']}/submissions/{submission_id}/commit")
    status = wait_for_commit(app["id"], submission_id)
    print(f"Submitted version {version} for certification ({status}). It is published as soon as it passes.")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        fail(__doc__.strip().split("\n\n")[1].strip())
    main(*sys.argv[1:])
