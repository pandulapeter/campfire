#!/usr/bin/env bash
# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.
#
# Regenerates the Campfire promo videos from the app's current UI into ~/Desktop/Campfire Promo Video, the way the
# store screenshots are exported to the Desktop. See CLAUDE.md next to this file.
#
#   ./regenerate.sh            everything: footage and the three videos
#   ./regenerate.sh footage    record the app again and re-encode the clips and stills (the slow part)
#   ./regenerate.sh video      render the three formats and put the soundtrack (soundtrack.m4a) on them
#
# CAMPFIRE_PROMO_OUT overrides where the videos are written.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
OUT="${CAMPFIRE_PROMO_OUT:-$HOME/Desktop/Campfire Promo Video}"
FORMATS=("Promo:vertical-9x16" "PromoSquare:square-1x1" "PromoLandscape:landscape-16x9")

log() { printf '\n== %s\n' "$*"; }

footage() {
  log "Recording the app (tools/screenshots, --only promo)"
  local raw
  raw="$(mktemp -d)"
  (cd "$REPO" && ./gradlew :tools:screenshots:run --args="--out $raw --only promo" -q)
  local promo="$raw/Promo"
  mkdir -p "$HERE/public/clips" "$HERE/public/stills" "$HERE/public/fonts" "$HERE/public/icons"
  log "Encoding clips and stills"
  # A folder of numbered frames is a clip; a PNG without a folder of the same name is a still.
  for dir in "$promo"/*/; do
    local name
    name="$(basename "$dir")"
    # A phone clip is shown at most about 900 px wide; a landscape tablet's fills the frame and is zoomed into, so it
    # keeps every pixel.
    ffmpeg -loglevel error -y -framerate 60 -i "$dir/%05d.jpg" -vf "scale='if(gt(iw,ih),iw,900)':-2:flags=lanczos" \
      -c:v libx264 -crf 12 -preset slow -pix_fmt yuv420p -movflags +faststart "$HERE/public/clips/$name.mp4"
    echo "clip  $name ($(ls "$dir" | wc -l | tr -d ' ') frames)"
  done
  for png in "$promo"/*.png; do
    local name
    name="$(basename "$png" .png)"
    [ -d "$promo/$name" ] && continue
    ffmpeg -loglevel error -y -i "$png" -vf "scale='min(1800,iw)':-2:flags=lanczos" -q:v 2 "$HERE/public/stills/$name.jpg"
    echo "still $name"
  done
  rm -rf "$raw"
  log "Copying fonts and icons"
  # The app's own font files, which it is set in on every platform, so the video's type is the app's.
  rm -f "$HERE"/public/fonts/*.woff2
  cp "$REPO"/presentation/src/commonMain/composeResources/font/inter_regular.ttf \
    "$REPO"/presentation/src/commonMain/composeResources/font/inter_medium.ttf \
    "$REPO"/presentation/src/commonMain/composeResources/font/inter_bold.ttf \
    "$REPO"/presentation/src/commonMain/composeResources/font/jetbrains_mono_bold.ttf "$HERE/public/fonts/"
  cp "$REPO"/app/desktop/src/main/composeResources/drawable/app_icon*.png "$HERE/public/icons/"
  # The app's own icons for the chips, its vector drawables as SVG, since the fonts have no music or interface symbols.
  python3 -I "$HERE/drawables_to_svg.py" "$REPO/presentation/src/commonMain/composeResources/drawable" "$HERE/public/icons"
}

video() {
  log "Rendering the three formats"
  [ -d "$HERE/node_modules" ] || (cd "$HERE" && npm ci --silent)
  mkdir -p "$HERE/out"
  for entry in "${FORMATS[@]}"; do
    local id="${entry%%:*}" name="${entry##*:}"
    (cd "$HERE" && npx remotion render src/index.ts "$id" "out/silent-$name.mp4" --codec=h264 --crf=14 --log=error)
    ffmpeg -loglevel error -y -i "$HERE/out/silent-$name.mp4" -i "$HERE/soundtrack.m4a" \
      -map 0:v -map 1:a -c:v copy -c:a copy -shortest -movflags +faststart "$OUT/campfire-promo-$name.mp4"
    echo "wrote campfire-promo-$name.mp4"
  done
}

mkdir -p "$OUT"

case "${1:-all}" in
  footage) footage ;;
  video) video ;;
  all) footage; video ;;
  *) echo "usage: $0 [all|footage|video]" >&2; exit 1 ;;
esac
