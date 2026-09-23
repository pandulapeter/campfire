# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.
"""
The Campfire Sync app folder of a Dropbox account, driven from the command line: what the sync tests call
"dropbox.com standing in for a device", and the snapshot and restore that make it safe to test on an account whose
library matters. Python 3.8 or later, standard library only.

It borrows the connection of a Campfire desktop installation made for testing: the refresh token in that
installation's `preferences/sync-credentials.json`, and the app key from `local.properties`. Every path is relative to
the app folder (`/songs/x.cho`), which is all the token can reach. The short-lived access token it trades the refresh
token for is kept next to this script as `.dropbox_access_token`, readable by you only; never commit it.

    dropbox_folder.py --credentials <file> ls [/songs]
    dropbox_folder.py --credentials <file> snapshot <dir>        download songs/ and setlists/ into <dir>
    dropbox_folder.py --credentials <file> restore <dir>         make the folder hold exactly <dir>, then verify it
    dropbox_folder.py --credentials <file> verify <dir>          compare the folder with <dir> by content hash
    dropbox_folder.py --credentials <file> put <local> <remote>
    dropbox_folder.py --credentials <file> rm <remote>...
    dropbox_folder.py --credentials <file> empty /songs          delete every file in one folder (one batch job)
    dropbox_folder.py --credentials <file> rewrite <remote> <seconds>   overwrite a file every half second
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPOSITORY = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
TOKEN_CACHE = os.path.join(HERE, ".dropbox_access_token")
FOLDERS = ("songs", "setlists")


def app_key():
    with open(os.path.join(REPOSITORY, "local.properties"), encoding="utf-8") as properties:
        for line in properties:
            if line.startswith("campfire.dropbox.appKey="):
                return line.split("=", 1)[1].strip()
    sys.exit("local.properties has no campfire.dropbox.appKey")


def access_token(credentials):
    if os.path.exists(TOKEN_CACHE) and time.time() - os.path.getmtime(TOKEN_CACHE) < 3000:
        with open(TOKEN_CACHE) as cache:
            return cache.read()
    with open(credentials, encoding="utf-8") as file:
        refresh_token = json.load(file)["refreshToken"]
    body = urllib.parse.urlencode({"grant_type": "refresh_token", "refresh_token": refresh_token, "client_id": app_key()})
    token = json.load(urllib.request.urlopen("https://api.dropboxapi.com/oauth2/token", body.encode()))["access_token"]
    with open(os.open(TOKEN_CACHE, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as cache:
        cache.write(token)
    return token


class Folder:
    def __init__(self, credentials):
        self.credentials = credentials

    def _send(self, request, attempts=6):
        # Dropbox answers bursts of writes with 429 or a 409 "too_many_write_operations"; both mean wait and retry.
        for attempt in range(attempts):
            try:
                return urllib.request.urlopen(request).read()
            except urllib.error.HTTPError as error:
                body = error.read().decode(errors="replace")
                if error.code == 429 or error.code >= 500 or "too_many_write_operations" in body:
                    time.sleep(min(2 ** attempt, 30))
                    continue
                raise RuntimeError(f"{error.code} {body[:300]}")
        raise RuntimeError("Dropbox kept asking to slow down")

    def rpc(self, endpoint, argument):
        request = urllib.request.Request(
            "https://api.dropboxapi.com/2/" + endpoint,
            json.dumps(argument).encode(),
            {"Authorization": "Bearer " + access_token(self.credentials), "Content-Type": "application/json"},
        )
        return json.loads(self._send(request))

    def upload(self, remote, data):
        argument = json.dumps({"path": remote, "mode": "overwrite", "mute": True}, ensure_ascii=True)
        request = urllib.request.Request(
            "https://content.dropboxapi.com/2/files/upload",
            data,
            {
                "Authorization": "Bearer " + access_token(self.credentials),
                "Content-Type": "application/octet-stream",
                "Dropbox-API-Arg": argument,
            },
        )
        return json.loads(self._send(request))

    def download(self, remote):
        request = urllib.request.Request(
            "https://content.dropboxapi.com/2/files/download",
            b"",
            {
                "Authorization": "Bearer " + access_token(self.credentials),
                "Dropbox-API-Arg": json.dumps({"path": remote}, ensure_ascii=True),
            },
        )
        return self._send(request)

    def files(self, folder):
        try:
            result = self.rpc("files/list_folder", {"path": folder})
        except RuntimeError as error:
            if "not_found" in str(error):
                return []
            raise
        entries = result["entries"]
        while result.get("has_more"):
            result = self.rpc("files/list_folder/continue", {"cursor": result["cursor"]})
            entries += result["entries"]
        return [entry for entry in entries if entry[".tag"] == "file"]

    def delete_all(self, paths):
        if not paths:
            return
        job = self.rpc("files/delete_batch", {"entries": [{"path": path} for path in paths]})
        job_id = job.get("async_job_id")
        while job.get(".tag") in ("async_job_id", "in_progress"):
            time.sleep(1)
            job = self.rpc("files/delete_batch/check", {"async_job_id": job_id})
        if job.get(".tag") != "complete":
            raise RuntimeError(f"delete_batch ended with {job}")


def content_hash(data):
    """Dropbox's content hash: SHA-256 over the concatenated SHA-256 of every 4 MiB block."""
    blocks = [hashlib.sha256(data[i:i + 4 * 1024 * 1024]).digest() for i in range(0, len(data), 4 * 1024 * 1024)]
    return hashlib.sha256(b"".join(blocks)).hexdigest()


def local_files(directory):
    result = {}
    for folder in FOLDERS:
        path = os.path.join(directory, folder)
        for name in sorted(os.listdir(path)) if os.path.isdir(path) else []:
            with open(os.path.join(path, name), "rb") as file:
                result[f"/{folder}/{name}"] = file.read()
    return result


def differences(folder, directory):
    wanted = local_files(directory)
    remote = {f"/{name}/{entry['name']}": entry for name in FOLDERS for entry in folder.files(f"/{name}")}
    wrong = [path for path, data in wanted.items() if remote.get(path, {}).get("content_hash") != content_hash(data)]
    extra = [entry["path_lower"] for path, entry in remote.items() if path not in wanted]
    return wanted, wrong, extra


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--credentials", required=True, help="a test installation's preferences/sync-credentials.json")
    parser.add_argument("command")
    parser.add_argument("arguments", nargs="*")
    options = parser.parse_args()
    folder, arguments = Folder(options.credentials), options.arguments

    if options.command == "ls":
        for entry in folder.files(arguments[0] if arguments else "/songs"):
            print(entry["name"])
    elif options.command == "snapshot":
        for name in FOLDERS:
            os.makedirs(os.path.join(arguments[0], name), exist_ok=True)
            for entry in folder.files(f"/{name}"):
                with open(os.path.join(arguments[0], name, entry["name"]), "wb") as file:
                    file.write(folder.download(entry["path_lower"]))
        print(f"{sum(len(files) for _, _, files in os.walk(arguments[0]))} files in {arguments[0]}")
    elif options.command in ("restore", "verify"):
        for attempt in range(8):
            wanted, wrong, extra = differences(folder, arguments[0])
            print(f"{len(wanted)} files wanted, {len(wrong)} missing or different, {len(extra)} extra")
            if options.command == "verify" or not (wrong or extra):
                sys.exit(1 if wrong or extra else 0)
            folder.delete_all(extra)
            with concurrent.futures.ThreadPoolExecutor(4) as pool:
                list(pool.map(lambda path: folder.upload(path, wanted[path]), wrong))
        sys.exit("the folder still differs from the snapshot")
    elif options.command == "put":
        with open(arguments[0], "rb") as file:
            print(folder.upload(arguments[1], file.read())["name"])
    elif options.command == "rm":
        for path in arguments:
            folder.rpc("files/delete_v2", {"path": path})
    elif options.command == "empty":
        paths = [entry["path_lower"] for entry in folder.files(arguments[0])]
        folder.delete_all(paths)
        print(f"deleted {len(paths)}")
    elif options.command == "rewrite":
        deadline, count = time.time() + float(arguments[1]), 0
        while time.time() < deadline:
            folder.upload(arguments[0], f"{{title: Rewritten}}\nrewrite {count} at {time.time()}\n".encode())
            count += 1
            time.sleep(0.5)
        print(f"{count} rewrites")
    else:
        parser.error(f"unknown command {options.command}")


if __name__ == "__main__":
    main()
