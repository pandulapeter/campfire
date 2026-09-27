# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Signing identities that last one workflow run, and until the next one where that run uploaded a build.

Apple's distribution certificates expire after a year, and a certificate kept in a repository secret is a release that
fails on the day it does. The App Store Connect API key does not expire, and an Admin key may create certificates and
provisioning profiles - so a run makes its own: a private key that never leaves the runner, a certificate for it,
the profiles that name that certificate. A build has to stay signed by a valid certificate until App Review has
approved it, not only until it is uploaded or processed: one whose certificate is revoked in the meantime is refused as
an invalid binary (ITMS-90238, CSSMERR_TP_CERT_REVOKED), even after it has been attached and submitted. So a run that
uploaded a build keeps its identities, and the workflow hands the state file on as an artifact, which the next run of
the same workflow revokes before it makes its own (`cleanup-earlier`); a run that uploaded nothing revokes its own at
the end. Revoking a distribution certificate does not touch what Apple has already approved, which it signs again, so
nothing but the build still waiting for review ever depends on it - and the next upload of the platform is the build
that replaces that one. Apple tells certificates made here and certificates made by hand apart in no way the API
shows, so what a run made is known only from its state file: an artifact that expires before the next run leaves its
certificates to expire on their own after a year. Never use this for a Developer ID certificate: an app signed outside
the store is checked against it on every Mac that opens it, and revoking it breaks every copy already downloaded.

Everything this creates is written into a state file first, and `cleanup` removes exactly that and nothing else, so an
identity somebody made by hand is never touched. It only needs the Python standard library and the system's openssl.

    app_store_signing.py certificate <certificateType> <keychain>
    app_store_signing.py profile <profileType> <bundle identifier> <certificateType> <output file>
    app_store_signing.py cleanup
    app_store_signing.py cleanup-earlier <artifact name>

Reads ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_PATH (the .p8 file), and APP_STORE_SIGNING_STATE, the state file;
`cleanup-earlier` also GITHUB_REPOSITORY and GH_TOKEN, for the gh command line tool, with the actions: write permission.
"""

import base64
import glob
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

API = "https://api.appstoreconnect.apple.com/v1"
# LibreSSL, which every macOS has: it signs the token the same way everywhere, and it is not whatever a runner image
# happens to put first on the PATH.
OPENSSL = "/usr/bin/openssl"


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


def request(method, path, body=None, missing_ok=False):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Authorization": f"Bearer {token()}", "Content-Type": "application/json"}
    try:
        with urllib.request.urlopen(urllib.request.Request(API + path, data=data, headers=headers, method=method)) as response:
            content = response.read()
            return json.loads(content) if content else None
    except urllib.error.HTTPError as error:
        if missing_ok and error.code == 404:
            return None
        details = error.read().decode()
        try:
            details = "; ".join(item.get("detail") or item.get("title", "") for item in json.loads(details)["errors"])
        except (ValueError, KeyError):
            pass
        fail(f"{method} {path} answered {error.code}: {details}")


def state_path():
    return os.environ.get("APP_STORE_SIGNING_STATE") or os.path.join(os.environ.get("RUNNER_TEMP", "."), "app-store-signing.json")


def read_state():
    try:
        with open(state_path()) as file:
            return json.load(file)
    except FileNotFoundError:
        return {"certificates": {}, "profiles": []}


def write_state(state):
    with open(state_path(), "w") as file:
        json.dump(state, file)


def run(*command):
    subprocess.run(command, check=True, capture_output=True)


def create_certificate(certificate_type, keychain):
    """Creates a key and a certificate of the given type for it, and imports both into the keychain."""
    with tempfile.TemporaryDirectory() as directory:
        key = os.path.join(directory, "key.pem")
        csr = os.path.join(directory, "request.csr")
        certificate = os.path.join(directory, "certificate.cer")
        run(OPENSSL, "genrsa", "-out", key, "2048")
        run(OPENSSL, "req", "-new", "-key", key, "-out", csr, "-subj", "/CN=GitHub Actions")
        with open(csr) as file:
            csr_content = file.read()
        created = request("POST", "/certificates", {"data": {
            "type": "certificates",
            "attributes": {"certificateType": certificate_type, "csrContent": csr_content},
        }})["data"]
        # Recorded before anything else can fail, so that cleanup revokes it whatever happens next.
        state = read_state()
        state["certificates"][certificate_type] = created["id"]
        write_state(state)
        with open(certificate, "wb") as file:
            file.write(base64.b64decode(created["attributes"]["certificateContent"]))
        tools = ["-T", "/usr/bin/codesign", "-T", "/usr/bin/productbuild", "-T", "/usr/bin/security"]
        run("security", "import", key, "-k", keychain, "-t", "priv", "-f", "openssl", *tools)
        run("security", "import", certificate, "-k", keychain, "-t", "cert", "-f", "x509")
    attributes = created["attributes"]
    print(f"Created {certificate_type} certificate {created['id']} ({attributes.get('name')}), expiring {attributes.get('expirationDate')}")


def create_profile(profile_type, bundle_identifier, certificate_type, output):
    """Creates a profile of the given type for the bundle ID and the certificate this run created, and saves it."""
    certificate = read_state()["certificates"].get(certificate_type)
    if not certificate:
        fail(f"This run has created no {certificate_type} certificate to make a profile for.")
    matches = request("GET", f"/bundleIds?filter[identifier]={bundle_identifier}&limit=200")["data"]
    # The filter matches prefixes as well, so com.example.app also finds com.example.app.widget.
    bundle = next((item for item in matches if item["attributes"]["identifier"] == bundle_identifier), None)
    if bundle is None:
        fail(f"There is no App ID {bundle_identifier} in the developer account.")
    # Profile names are unique in an account; the run's id keeps two runs of the same workflow apart.
    name = f"CI {bundle_identifier} {os.environ.get('GITHUB_RUN_ID', int(time.time()))}"
    created = request("POST", "/profiles", {"data": {
        "type": "profiles",
        "attributes": {"name": name, "profileType": profile_type},
        "relationships": {
            "bundleId": {"data": {"type": "bundleIds", "id": bundle["id"]}},
            "certificates": {"data": [{"type": "certificates", "id": certificate}]},
        },
    }})["data"]
    state = read_state()
    state["profiles"].append(created["id"])
    write_state(state)
    with open(output, "wb") as file:
        file.write(base64.b64decode(created["attributes"]["profileContent"]))
    print(f"Created {profile_type} profile {created['id']} ({name}) for {bundle_identifier}")


def remove(state):
    """Deletes the profiles and revokes the certificates of a state file; one that is gone already is not an error."""
    for profile in state["profiles"]:
        request("DELETE", f"/profiles/{profile}", missing_ok=True)
        print(f"Deleted profile {profile}")
    for certificate_type, certificate in state["certificates"].items():
        request("DELETE", f"/certificates/{certificate}", missing_ok=True)
        print(f"Revoked {certificate_type} certificate {certificate}")


def cleanup():
    """Deletes the profiles and revokes the certificates this run created, and nothing else."""
    remove(read_state())
    write_state({"certificates": {}, "profiles": []})


def gh(*arguments):
    return subprocess.run(["gh", *arguments], check=True, capture_output=True, text=True).stdout


def cleanup_earlier(artifact_name):
    """Revokes what earlier runs kept for their builds under review, and deletes each artifact once it has done so."""
    repository = os.environ["GITHUB_REPOSITORY"]
    artifacts = json.loads(gh("api", f"repos/{repository}/actions/artifacts?name={artifact_name}&per_page=100"))["artifacts"]
    for artifact in artifacts:
        if artifact["expired"]:
            continue
        with tempfile.TemporaryDirectory() as directory:
            gh("run", "download", str(artifact["workflow_run"]["id"]), "--repo", repository, "--name", artifact_name, "--dir", directory)
            for path in glob.glob(os.path.join(directory, "*.json")):
                with open(path) as file:
                    remove(json.load(file))
        gh("api", "--method", "DELETE", f"repos/{repository}/actions/artifacts/{artifact['id']}")
        print(f"Removed what run {artifact['workflow_run']['id']} kept for its build.")


if __name__ == "__main__":
    command, arguments = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else (None, [])
    if command == "certificate" and len(arguments) == 2:
        create_certificate(*arguments)
    elif command == "profile" and len(arguments) == 4:
        create_profile(*arguments)
    elif command == "cleanup" and not arguments:
        cleanup()
    elif command == "cleanup-earlier" and len(arguments) == 1:
        cleanup_earlier(*arguments)
    else:
        fail(__doc__.strip().split("\n\n")[3])
