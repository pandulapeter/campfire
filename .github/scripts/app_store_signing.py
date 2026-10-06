# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
The signing identities of the App Store builds: one certificate of each type for the team's signing key, which every run
finds again, and renews before it expires, through the App Store Connect API.

Apple's distribution certificates expire after a year, and a certificate kept in a repository secret is a release that
fails on the day it does. The private key it was made for does not expire, and the App Store Connect API key may create
certificates and provisioning profiles - so the APPLE_SIGNING_KEY secret holds the key alone, and a run asks the API for
the certificates of the type it needs, takes the one made for that key that expires last (`certificate`), and creates a
new one for the same key where there is none, or where that one has less than RENEWAL_DAYS left. A certificate is never
revoked: a build has to stay signed by a valid certificate until App Review has approved it, and one whose certificate
is revoked in the meantime is refused as an invalid binary (ITMS-90238, CSSMERR_TP_CERT_REVOKED), even after it was
attached and submitted. The old one is left to expire instead, months after the last build it signed was decided. The
renewal is early enough that no build is ever signed with a certificate that could expire while it is in review.

Kubriko's pipeline signs with the same key and so with the same certificates, which matters because Apple allows a team
only two Apple Distribution certificates: the two pipelines hold one between them, and the other place is the renewal's.
A certificate somebody made by hand in Xcode takes a place too, which is what a creation refused with 409 means.

The Mac App Store profiles (`profile`) are found the same way: the active one of the type for the bundle ID that names
the certificate is used again, and one is created where there is none. A profile expires with its certificate, so
nothing ever deletes one either.

Identities a run made for a key of its own, named by the state files in the app-store-signing-* artifacts of earlier
runs, are revoked by `cleanup-earlier` - unless App Store Connect says the build they signed is attached to a version
that is still waiting for review, in review or not on the store yet, in which case they are left alone and the artifact
kept, to be asked about again by the run after. A state file that names no build is kept for as long as any version of
the platform is waiting for Apple. Revoking those does not touch what Apple has already released, which it signs again.

Never use this for a Developer ID certificate: an app signed outside the store is checked against it on every Mac that
opens it. It only needs the Python standard library and the system's openssl.

    app_store_signing.py certificate <certificateType> <keychain>
    app_store_signing.py profile <profileType> <bundle identifier> <certificateType> <output file>
    app_store_signing.py cleanup-earlier <artifact name> <bundle identifier> <IOS | MAC_OS>

Reads ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_PATH (the .p8 file), and APPLE_SIGNING_KEY_PATH, the signing key as PEM;
`cleanup-earlier` also GITHUB_REPOSITORY and GH_TOKEN, for the gh command line tool, with the actions: write permission.
"""

import base64
import glob
import http.client
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

API = "https://api.appstoreconnect.apple.com/v1"
# LibreSSL, which every macOS has: it signs the token the same way everywhere, and it is not whatever a runner image
# happens to put first on the PATH.
OPENSSL = "/usr/bin/openssl"
# Two months: far longer than any review takes, so a build is never signed with a certificate that could expire before
# App Review has decided it, and long enough that a release in the last weeks is not the only chance to renew.
RENEWAL_DAYS = 60
# The states of a version that has been submitted and is not on the store yet: the build attached to one is still checked
# against its certificate. An approved one counts until it is on the store: keeping a certificate one run longer costs
# nothing, and revoking it early gets the build refused.
AWAITING_STATES = {
    "READY_FOR_REVIEW", "WAITING_FOR_REVIEW", "IN_REVIEW", "WAITING_FOR_EXPORT_COMPLIANCE", "PENDING_CONTRACT",
    "ACCEPTED", "PROCESSING_FOR_APP_STORE", "PENDING_APPLE_RELEASE", "PENDING_DEVELOPER_RELEASE",
}
# The states of a version that has not been submitted but may still be with the build attached to it: one a run prepared
# and left for screenshots, or one rejected and sent again as it is. Its build is submitted later, by hand, and is
# refused if its certificate was revoked in between.
UNSUBMITTED_STATES = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED"}
# How often a GET is asked before its failure is final, and how long any request may take: a connection that hangs
# would otherwise hold the job until GitHub's six-hour limit.
REQUEST_ATTEMPTS = 4
REQUEST_TIMEOUT_SECONDS = 60


def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def token():
    """An ES256 JSON Web Token for the API key, valid for ten minutes, which is the most the API accepts."""
    encode = lambda data: base64.urlsafe_b64encode(data).rstrip(b"=")
    now = int(time.time())
    header = encode(json.dumps({"alg": "ES256", "kid": os.environ["ASC_KEY_ID"], "typ": "JWT"}).encode())
    claims = encode(json.dumps({
        "iss": os.environ["ASC_ISSUER_ID"],
        "iat": now,
        "exp": now + 600,
        "aud": "appstoreconnect-v1",
    }).encode())
    message = header + b"." + claims
    der = subprocess.run(
        [OPENSSL, "dgst", "-sha256", "-sign", os.environ["ASC_KEY_PATH"]],
        input=message, capture_output=True, check=True,
    ).stdout
    # openssl writes the signature as an ASN.1 sequence of two integers, and a JWT wants them as 32 bytes each.
    parsed = subprocess.run([OPENSSL, "asn1parse", "-inform", "DER"], input=der, capture_output=True, check=True).stdout
    integers = [line.split(":")[-1] for line in parsed.decode().splitlines() if "INTEGER" in line]
    signature = b"".join(bytes.fromhex(value.rjust(64, "0"))[-32:] for value in integers)
    return (message + b"." + encode(signature)).decode()


def should_retry(method, error, attempt, attempts):
    """Whether a failed request is asked again: only a GET, only a failure the service or the network may not repeat."""
    if method != "GET" or attempt >= attempts:
        return False
    if isinstance(error, urllib.error.HTTPError):
        return error.code == 429 or error.code >= 500
    return isinstance(error, (OSError, http.client.HTTPException))


def request(method, path, body=None, missing_ok=False, conflict_ok=False):
    """
    One call of the API. A GET is asked again where the service or the network failed it, since one bad answer among
    the polls that follow an upload would otherwise fail a job that cannot simply be run again: App Store Connect
    refuses the build number it has already seen.
    """
    data = json.dumps(body).encode() if body is not None else None
    for attempt in range(1, REQUEST_ATTEMPTS + 1):
        # Asked for on every attempt, since a token lasts minutes and the attempts wait between them.
        headers = {"Authorization": f"Bearer {token()}", "Content-Type": "application/json"}
        try:
            call = urllib.request.Request(API + path, data=data, headers=headers, method=method)
            with urllib.request.urlopen(call, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            if should_retry(method, error, attempt, REQUEST_ATTEMPTS):
                print(f"{method} {path} failed ({error.code}), asking again in {10 * attempt} s.")
                time.sleep(10 * attempt)
                continue
            details = error.read().decode()
            try:
                details = "; ".join(item.get("detail") or item.get("title", "") for item in json.loads(details)["errors"])
            except (ValueError, KeyError):
                pass
            if missing_ok and error.code == 404:
                return None
            if conflict_ok and error.code == 409:
                print(f"{method} {path} answered 409: {details}", file=sys.stderr)
                return None
            fail(f"{method} {path} answered {error.code}: {details}")
        # urllib wraps only the errors of sending a request in URLError: a connection dropped while the answer is
        # awaited or read arrives as it was raised.
        except (OSError, http.client.HTTPException) as error:
            if should_retry(method, error, attempt, REQUEST_ATTEMPTS):
                print(f"{method} {path} failed ({error}), asking again in {10 * attempt} s.")
                time.sleep(10 * attempt)
                continue
            fail(f"{method} {path} failed: {error}")


def run(*command, input=None):
    return subprocess.run(command, input=input, check=True, capture_output=True).stdout


def parse_expiration(text):
    """The API's expirationDate, such as 2027-09-29T19:26:01.000+00:00, as an aware datetime."""
    return datetime.fromisoformat(text.replace("Z", "+00:00"))


def newest_valid(certificates, now):
    """The certificate of the list that expires last, of those that have not expired yet, or None."""
    valid = [item for item in certificates if item["expiration"] > now]
    return max(valid, key=lambda item: item["expiration"], default=None)


def needs_renewal(certificate, now):
    return certificate["expiration"] - now < timedelta(days=RENEWAL_DAYS)


def key_modulus():
    return run(OPENSSL, "rsa", "-in", os.environ["APPLE_SIGNING_KEY_PATH"], "-noout", "-modulus").strip()


def certificates_for_key(certificate_type):
    """Every certificate of the type that was made for the signing key, expired ones included."""
    listed = request(
        "GET", f"/certificates?filter[certificateType]={certificate_type}&limit=200"
        "&fields[certificates]=name,certificateContent,expirationDate",
    )["data"]
    modulus = key_modulus()
    found = []
    for item in listed:
        content = base64.b64decode(item["attributes"]["certificateContent"])
        # The public key is all that ties a certificate to the key: the API says nothing else about which key it was for.
        if run(OPENSSL, "x509", "-inform", "DER", "-noout", "-modulus", input=content).strip() == modulus:
            found.append({
                "id": item["id"],
                "name": item["attributes"].get("name"),
                "content": content,
                "expiration": parse_expiration(item["attributes"]["expirationDate"]),
            })
    return found


def create_certificate(certificate_type):
    """A new certificate of the type for the signing key, or None where Apple refuses one because the team has its most."""
    with tempfile.TemporaryDirectory() as directory:
        csr = os.path.join(directory, "request.csr")
        run(OPENSSL, "req", "-new", "-key", os.environ["APPLE_SIGNING_KEY_PATH"], "-out", csr, "-subj", "/CN=GitHub Actions")
        with open(csr) as file:
            csr_content = file.read()
    created = request("POST", "/certificates", {"data": {
        "type": "certificates",
        "attributes": {"certificateType": certificate_type, "csrContent": csr_content},
    }}, conflict_ok=True)
    if created is None:
        return None
    attributes = created["data"]["attributes"]
    print(f"Created {certificate_type} certificate {created['data']['id']} ({attributes.get('name')}), "
          f"expiring {attributes['expirationDate']}")
    return {
        "id": created["data"]["id"],
        "name": attributes.get("name"),
        "content": base64.b64decode(attributes["certificateContent"]),
        "expiration": parse_expiration(attributes["expirationDate"]),
    }


def current_certificate(certificate_type):
    current = newest_valid(certificates_for_key(certificate_type), datetime.now(timezone.utc))
    if current is None:
        fail(f"There is no {certificate_type} certificate for the signing key: run `certificate` first.")
    return current


def import_certificate(certificate_type, keychain):
    """Finds or renews the certificate of the type for the signing key, and imports both into the keychain."""
    now = datetime.now(timezone.utc)
    current = newest_valid(certificates_for_key(certificate_type), now)
    if current is None or needs_renewal(current, now):
        created = create_certificate(certificate_type)
        if created is not None:
            current = created
        elif current is not None:
            print(f"::warning::The {certificate_type} certificate {current['id']} expires on {current['expiration']:%Y-%m-%d} "
                  "and Apple refused a new one, since the team already has as many of the type as it may: revoke one that "
                  "is not this signing key's in the developer account, and the next run renews it.")
        else:
            fail(f"Apple refused a {certificate_type} certificate for the signing key, since the team already has as many "
                 "of the type as it may. Revoke one in the developer account that no build waiting for App Review was "
                 "signed with, and run this again.")
    else:
        print(f"Using {certificate_type} certificate {current['id']} ({current['name']}), "
              f"expiring {current['expiration']:%Y-%m-%d}")
    with tempfile.TemporaryDirectory() as directory:
        # The format `security` imports with -f openssl, whichever one the secret was written in.
        key = os.path.join(directory, "key.pem")
        certificate = os.path.join(directory, "certificate.cer")
        run(OPENSSL, "rsa", "-in", os.environ["APPLE_SIGNING_KEY_PATH"], "-out", key)
        with open(certificate, "wb") as file:
            file.write(current["content"])
        tools = ["-T", "/usr/bin/codesign", "-T", "/usr/bin/productbuild", "-T", "/usr/bin/security"]
        imported = subprocess.run(
            ["security", "import", key, "-k", keychain, "-t", "priv", "-f", "openssl", *tools], capture_output=True, text=True,
        )
        # The Mac build imports a certificate of each of its two types, and both are for this one key.
        if imported.returncode != 0 and "already exists" not in imported.stderr:
            fail(f"security could not import the signing key: {imported.stderr.strip()}")
        run("security", "import", certificate, "-k", keychain, "-t", "cert", "-f", "x509")


def find_bundle_id(bundle_identifier):
    matches = request("GET", f"/bundleIds?filter[identifier]={bundle_identifier}&limit=200")["data"]
    # The filter matches prefixes as well, so com.example.app also finds com.example.app.widget.
    bundle = next((item for item in matches if item["attributes"]["identifier"] == bundle_identifier), None)
    if bundle is None:
        fail(f"There is no App ID {bundle_identifier} in the developer account.")
    return bundle["id"]


def save_profile(profile_type, bundle_identifier, certificate_type, output):
    """Saves the active profile of the type for the bundle ID and the current certificate, creating it where there is none."""
    certificate = current_certificate(certificate_type)
    bundle = find_bundle_id(bundle_identifier)
    profiles = request(
        "GET", f"/profiles?filter[profileType]={profile_type}&filter[profileState]=ACTIVE&limit=200"
        "&include=bundleId,certificates&limit[certificates]=50&fields[profiles]=name,profileContent,bundleId,certificates",
    )["data"]
    profile = next((
        item for item in profiles
        if item["relationships"]["bundleId"]["data"]["id"] == bundle
        and certificate["id"] in [entry["id"] for entry in item["relationships"]["certificates"]["data"]]
    ), None)
    if profile is None:
        # Profile names are unique in an account, the expired and invalid ones included.
        name = f"CI {bundle_identifier} {os.environ.get('GITHUB_RUN_ID', int(time.time()))}"
        profile = request("POST", "/profiles", {"data": {
            "type": "profiles",
            "attributes": {"name": name, "profileType": profile_type},
            "relationships": {
                "bundleId": {"data": {"type": "bundleIds", "id": bundle}},
                "certificates": {"data": [{"type": "certificates", "id": certificate["id"]}]},
            },
        }})["data"]
        print(f"Created {profile_type} profile {profile['id']} ({name}) for {bundle_identifier}")
    else:
        print(f"Using {profile_type} profile {profile['id']} ({profile['attributes']['name']}) for {bundle_identifier}")
    with open(output, "wb") as file:
        file.write(base64.b64decode(profile["attributes"]["profileContent"]))


def remove(state):
    """Deletes the profiles and revokes the certificates of a state file; one that is gone already is not an error."""
    for profile in state["profiles"]:
        request("DELETE", f"/profiles/{profile}", missing_ok=True)
        print(f"Deleted profile {profile}")
    for certificate_type, certificate in state["certificates"].items():
        request("DELETE", f"/certificates/{certificate}", missing_ok=True)
        print(f"Revoked {certificate_type} certificate {certificate}")


def gh(*arguments):
    return subprocess.run(["gh", *arguments], check=True, capture_output=True, text=True).stdout


def find_app_id(bundle_identifier):
    apps = request("GET", f"/apps?filter[bundleId]={bundle_identifier}")["data"]
    app = next((item for item in apps if item["attributes"]["bundleId"] == bundle_identifier), None)
    if app is None:
        fail(f"There is no app with the bundle ID {bundle_identifier} in App Store Connect.")
    return app["id"]


def awaiting_versions(app_id, platform):
    """The platform's versions that have been submitted and are not on the store yet."""
    versions = request(
        "GET", f"/apps/{app_id}/appStoreVersions?filter[platform]={platform}&limit=50"
        "&fields[appStoreVersions]=versionString,appStoreState",
    )["data"]
    return [item for item in versions if item["attributes"]["appStoreState"] in AWAITING_STATES]


def pending_version_of_build(app_id, build):
    """The version the build is attached to, where that one is not on the store yet and may still be submitted with it."""
    builds = request(
        "GET", f"/builds?filter[app]={app_id}&filter[version]={build['number']}"
        f"&filter[preReleaseVersion.version]={build['version']}&filter[preReleaseVersion.platform]={build['platform']}",
    )["data"]
    if not builds:
        return None
    version = (request("GET", f"/builds/{builds[0]['id']}/appStoreVersion", missing_ok=True) or {}).get("data")
    return version if version and version["attributes"]["appStoreState"] in AWAITING_STATES | UNSUBMITTED_STATES else None


def still_needed(app_id, platform, state):
    """Why the identities of a state file must not be revoked yet, or None where they may be."""
    build = state.get("build")
    if build:
        version = pending_version_of_build(app_id, build)
        if version:
            return (f"build {build['version']} ({build['number']}) is attached to the {platform} version "
                    f"{version['attributes']['versionString']}, which is {version['attributes']['appStoreState']}")
        return None
    # A state file that names no build tells nothing about which one it signed, so it is kept for as long as any version
    # of the platform is waiting for Apple, since that may be the one.
    awaiting = awaiting_versions(app_id, platform)
    if awaiting:
        version = awaiting[0]["attributes"]
        return f"the {platform} version {version['versionString']} is {version['appStoreState']}"
    return None


def cleanup_earlier(artifact_name, bundle_identifier, platform):
    """
    Revokes what the state files of earlier runs name, except for a build that is still waiting for Apple, and deletes
    each artifact once nothing in it is kept any more - so identities that are kept are asked about again by the next run.
    """
    repository = os.environ["GITHUB_REPOSITORY"]
    artifacts = json.loads(gh("api", f"repos/{repository}/actions/artifacts?name={artifact_name}&per_page=100"))["artifacts"]
    app_id = None
    for artifact in artifacts:
        if artifact["expired"]:
            continue
        run_id = artifact["workflow_run"]["id"]
        kept = False
        with tempfile.TemporaryDirectory() as directory:
            gh("run", "download", str(run_id), "--repo", repository, "--name", artifact_name, "--dir", directory)
            for path in glob.glob(os.path.join(directory, "*.json")):
                with open(path) as file:
                    state = json.load(file)
                app_id = app_id or find_app_id(bundle_identifier)
                reason = still_needed(app_id, platform, state)
                if reason:
                    print(f"Keeping what run {run_id} made, since {reason}: revoking it would get that build refused.")
                    kept = True
                else:
                    remove(state)
        if not kept:
            gh("api", "--method", "DELETE", f"repos/{repository}/actions/artifacts/{artifact['id']}")
            print(f"Removed what run {run_id} kept for its build.")


if __name__ == "__main__":
    command, arguments = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else (None, [])
    if command == "certificate" and len(arguments) == 2:
        import_certificate(*arguments)
    elif command == "profile" and len(arguments) == 4:
        save_profile(*arguments)
    elif command == "cleanup-earlier" and len(arguments) == 3:
        cleanup_earlier(*arguments)
    else:
        fail(next(part for part in __doc__.split("\n\n") if part.lstrip().startswith("app_store_signing.py")))
