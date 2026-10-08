# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

# Starts a desktop release build once before it is published, for publish-linux.yml, publish-windows.yml and
# publish-macos.yml. ProGuard breaks an application in ways that only starting it shows - a VerifyError on the first
# frame is how the last one looked. The demo library appearing in an empty data directory proves that Koin started, the
# window composed and the library was written; the process still being there a while later proves the first frames did
# not kill it. With --class-load, most of the classes it loaded have to come from the class data sharing archive the
# packaging recorded: a start that does not map it is as slow as one without it, and says so only in a warning.
#
# Each workflow prepares the environment first (the display, the home directory, the sandbox copy) and calls
#
#     bash .github/scripts/start_release_build.sh --launcher PATH --data DIR --log FILE \
#         [--class-load FILE --min-shared-percent 80] [--windows] [--started MESSAGE] [-- COMMAND ...]
#
# COMMAND starts the launcher where it needs a wrapper (xvfb-run); without it the launcher is started on its own. On
# Windows the launcher is not a process Git Bash can signal, so it is asked about and stopped by its image name, and the
# app writes its own log into the data directory rather than to the launcher's output. Written for the bash 3.2 macOS
# ships and for Git Bash alike.

LAUNCHER=""
DATA=""
LOG=""
CLASS_LOAD=""
MIN_SHARED_PERCENT=80
WINDOWS=false
STARTED="The release build started"
while [ $# -gt 0 ]; do
  case "$1" in
    --launcher) LAUNCHER="$2"; shift 2 ;;
    --data) DATA="$2"; shift 2 ;;
    --log) LOG="$2"; shift 2 ;;
    --class-load) CLASS_LOAD="$2"; shift 2 ;;
    --min-shared-percent) MIN_SHARED_PERCENT="$2"; shift 2 ;;
    --windows) WINDOWS=true; shift ;;
    --started) STARTED="$2"; shift 2 ;;
    --) shift; break ;;
    *) echo "::error::Unknown argument: $1" >&2; exit 2 ;;
  esac
done
if [ -z "$LAUNCHER" ] || [ -z "$DATA" ] || [ -z "$LOG" ]; then
  echo "::error::--launcher, --data and --log are required." >&2
  exit 2
fi
if [ $# -eq 0 ]; then
  set -- "$LAUNCHER"
fi

if [ "$WINDOWS" = true ]; then
  IMAGE=$(basename "$LAUNCHER")
  "$@" &
  is_running() {
    tasklist //FI "IMAGENAME eq $IMAGE" | grep -q "$IMAGE"
  }
  stop() {
    taskkill //F //IM "$IMAGE" > /dev/null 2>&1 || true
  }
else
  "$@" > "$LOG" 2>&1 &
  PID=$!
  is_running() {
    kill -0 "$PID" 2> /dev/null
  }
  stop() {
    pkill -f "$LAUNCHER" || true
    kill "$PID" 2> /dev/null || true
  }
fi
fail() {
  echo "::error::$1"
  cat "$LOG" 2> /dev/null || true
  stop
  exit 1
}

for _ in $(seq 120); do
  is_running || fail "The release build exited on its own before the library was written."
  if [ -f "$DATA/preferences/preferences.json" ] && ls "$DATA/library/songs/"*.cho > /dev/null 2>&1; then
    break
  fi
  sleep 1
done
ls "$DATA/library/songs/"*.cho > /dev/null 2>&1 || fail "The release build did not write the demo library in two minutes."
sleep 15
is_running || fail "The release build exited on its own after it had written the library."
[ -f "$LOG" ] || fail "The release build wrote no log."
grep -q "Exception" "$LOG" && fail "The release build logged an exception."
stop
cat "$LOG"
if [ -n "$CLASS_LOAD" ]; then
  [ -s "$CLASS_LOAD" ] || fail "The release build logged no class loading."
  LOADED=$(wc -l < "$CLASS_LOAD")
  SHARED=$(grep -c 'source: shared objects file' "$CLASS_LOAD" || true)
  echo "$SHARED of $LOADED classes came from the class data sharing archives."
  [ $((SHARED * 100)) -ge $((LOADED * MIN_SHARED_PERCENT)) ] || fail "The release build did not use its class data sharing archive."
fi
echo "$STARTED and wrote: $(ls "$DATA/library/songs")"
