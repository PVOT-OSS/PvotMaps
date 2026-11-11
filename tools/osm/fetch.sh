#!/usr/bin/env bash
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

# Downloads a Geofabrik extract into $PVOT_MAPS_DATA/osm and checks it against its MD5.
# Usage: tools/osm/fetch.sh <url>
set -euo pipefail

url="$1"
dir="${PVOT_MAPS_DATA:-$HOME/.cache/pvot-maps}/osm"
name="$(basename "$url")"
mkdir -p "$dir"
cd "$dir"
curl -fsSL -o "$name.md5" "$url.md5"
if ! md5sum --quiet -c "$name.md5" 2>/dev/null; then
    curl -fL --progress-bar -o "$name" "$url"
    md5sum -c "$name.md5"
fi
echo "$dir/$name"
