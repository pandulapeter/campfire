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

The App Store profiles (`profile`) are found the same way: the active one of the type for the bundle ID that names the
certificate is used again, and one is created where there is none. A profile expires with its certificate, so nothing
ever deletes one either. Both builds sign with these by hand and never give xcodebuild the API key: Xcode's automatic
signing makes an Apple Development certificate of its own on every runner, whose key dies with the runner, until the
team has as many as Apple allows and every build is refused.

Never use this for a Developer ID certificate: an app signed outside the store is checked against it on every Mac that
opens it. It only needs the Python standard library and the system's openssl.

    app_store_signing.py certificate <certificateType> <keychain>
    app_store_signing.py profile <profileType> <bundle identifier> <certificateType> <output file>

Reads ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_PATH (the .p8 file), and APPLE_SIGNING_KEY_PATH, the signing key as PEM.
"""

import base64
import json
import os
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timedelta, timezone

from store_http import fail, request_json

API = "https://api.appstoreconnect.apple.com/v1"
# LibreSSL, which every macOS has: it signs the token the same way everywhere, and it is not whatever a runner image
# happens to put first on the PATH.
OPENSSL = "/usr/bin/openssl"
# Two months: far longer than any review takes, so a build is never signed with a certificate that could expire before
# App Review has decided it, and long enough that a release in the last weeks is not the only chance to renew.
RENEWAL_DAYS = 60


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


def error_details(error):
    """What App Store Connect said was wrong: the details of its errors, or the body as it is where it has none."""
    details = error.read().decode()
    try:
        details = "; ".join(item.get("detail") or item.get("title", "") for item in json.loads(details)["errors"])
    except (ValueError, KeyError):
        pass
    return details


def request(method, path, body=None, conflict_ok=False):
    """
    One call of the API. A GET is asked again where the service or the network failed it, since one bad answer among
    the polls that follow an upload would otherwise fail a job that cannot simply be run again: App Store Connect
    refuses the build number it has already seen.
    """
    answers = {}
    if conflict_ok:
        answers[409] = None
    return request_json(method, API + path, token, body, error_details=error_details, answers=answers, label=path)


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


if __name__ == "__main__":
    command, arguments = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else (None, [])
    if command == "certificate" and len(arguments) == 2:
        import_certificate(*arguments)
    elif command == "profile" and len(arguments) == 4:
        save_profile(*arguments)
    else:
        fail(next(part for part in __doc__.split("\n\n") if part.lstrip().startswith("app_store_signing.py")))
