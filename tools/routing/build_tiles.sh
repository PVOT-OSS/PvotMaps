#!/usr/bin/env bash
# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

# Builds Valhalla routing tiles for a whole source extract, once; tools/regions/package.py then
# hands each region the tiles that touch it, so neighbouring regions share identical border tiles
# and routes run across them. The admin database (states, for access rules and driving side) comes
# from the same extract; a regional extract has no country polygon, so Valhalla can't tell that a
# left-hand traffic country drives on the left. Pass "left" to mark every admin area in it so.
# Memory grows with the thread count; set PVOT_MAPS_THREADS to use fewer than every core.
# Usage: tools/routing/build_tiles.sh <source.osm.pbf> <output dir> [left]
set -euo pipefail

source_pbf="$1"
out="$2"
side="${3:-right}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"

valhalla_build_config \
    --mjolnir-tile-dir "$out/tiles" \
    --mjolnir-tile-extract "" \
    --mjolnir-admin "$out/admins.sqlite" \
    --mjolnir-timezone "" \
    --mjolnir-concurrency "${PVOT_MAPS_THREADS:-$(nproc)}" \
    > "$out/valhalla.json"

valhalla_build_admins -c "$out/valhalla.json" "$source_pbf"
if [ "$side" = left ]; then
    python3 -I -c 'import sqlite3, sys; db = sqlite3.connect(sys.argv[1]); db.execute("UPDATE admins SET drive_on_right = 0"); db.commit()' "$out/admins.sqlite"
fi
valhalla_build_tiles -c "$out/valhalla.json" "$source_pbf"
du -sh "$out/tiles"
