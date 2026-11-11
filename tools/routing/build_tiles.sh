#!/usr/bin/env bash
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

# Builds Valhalla routing tiles for a region and packs them into one tiles.tar for the app.
# The admin database (states, for access rules and driving side) comes from the larger source
# extract; a regional extract has no country polygon, so Valhalla can't tell that a left-hand traffic
# country drives on the left. Pass "left" to mark every admin area in it so.
# Usage: tools/routing/build_tiles.sh <region.osm.pbf> <source.osm.pbf> <output dir> [left]
set -euo pipefail

region_pbf="$1"
source_pbf="$2"
out="$3"
side="${4:-right}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"

valhalla_build_config \
    --mjolnir-tile-dir "$out/tiles" \
    --mjolnir-tile-extract "$out/tiles.tar" \
    --mjolnir-admin "$out/admins.sqlite" \
    --mjolnir-timezone "" \
    --mjolnir-concurrency "$(nproc)" \
    > "$out/valhalla.json"

valhalla_build_admins -c "$out/valhalla.json" "$source_pbf"
if [ "$side" = left ]; then
    python3 -I -c 'import sqlite3, sys; db = sqlite3.connect(sys.argv[1]); db.execute("UPDATE admins SET drive_on_right = 0"); db.commit()' "$out/admins.sqlite"
fi
valhalla_build_tiles -c "$out/valhalla.json" "$region_pbf"
valhalla_build_extract -c "$out/valhalla.json" --overwrite
du -sh "$out/tiles.tar"
