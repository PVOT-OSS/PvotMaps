#!/usr/bin/env bash
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

# Downloads an OpenStreetMap extract into $PVOT_MAPS_DATA/osm/<host>/ and prints its path.
# Checks it against the host's .md5 when there is one (Geofabrik has them, openstreetmap.fr
# doesn't; a damaged file then fails when the tools decode it). Files are kept apart by host, so a
# partial download is only ever resumed from the same host.
# Usage: tools/osm/fetch.sh <url>
set -euo pipefail

url="$1"
host="$(echo "$url" | sed -E 's#^[a-z]+://([^/]+)/.*#\1#')"
dir="${PVOT_MAPS_DATA:-$HOME/.cache/pvot-maps}/osm/$host"
name="$(basename "$url")"
mkdir -p "$dir"
cd "$dir"
if curl -fsSL -o "$name.md5" "$url.md5"; then
    if md5sum --quiet -c "$name.md5" 2>/dev/null; then
        echo "$dir/$name"
        exit 0
    fi
else
    rm -f "$name.md5"
    echo "no checksum published for $name" >&2
fi
# Resumes a partial file, and treats under 10 KB/s for a minute as a stalled connection.
curl -fL --continue-at - --retry 10 --retry-all-errors \
    --speed-limit 10240 --speed-time 60 --no-progress-meter -o "$name" "$url"
if [ -f "$name.md5" ]; then md5sum -c "$name.md5" >&2; fi
echo "$dir/$name"
