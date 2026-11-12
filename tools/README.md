<!--
     SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
     SPDX-License-Identifier: Apache-2.0
-->

# Data tools

Scripts that turn OpenStreetMap data into what the app reads on the phone. Their output stays out
of git; it goes in `$PVOT_MAPS_DATA` (default `~/.cache/pvot-maps`).

## Setup

```sh
python3 -m venv .venv && .venv/bin/pip install -r tools/requirements.txt
```

## Regions, end to end

Regions are boxes in `tools/osm/regions.py`, each cut from a larger source extract. Routing is
built once for the whole source and every region gets the tiles that touch it, so neighbouring
regions share their border tiles and a phone with both installed routes across them. Search is
built per region. For the Bengaluru, Hosur and Mysuru test regions:

```sh
pbf=$(tools/osm/fetch.sh https://download.geofabrik.de/asia/india/southern-zone-latest.osm.pbf)
data=${PVOT_MAPS_DATA:-$HOME/.cache/pvot-maps}
mkdir -p "$data/regions" "$data/search"
PATH=$PWD/.venv/bin:$PATH tools/routing/build_tiles.sh "$pbf" "$data/routing/southern-zone" left
for region in bengaluru hosur mysuru; do
    .venv/bin/python -I tools/osm/clip.py $region "$pbf" "$data/regions/$region.osm.pbf"
    .venv/bin/python -I tools/search/build_index.py $region "$data/regions/$region.osm.pbf" "$data/search/$region.db"
    .venv/bin/python -I tools/regions/package.py $region "$data/search/$region.db" "$data/routing/southern-zone" site/
done
```

- `fetch.sh` downloads a Geofabrik extract and checks its MD5 (about 560 MB for India's southern
  zone).
- `build_tiles.sh` builds Valhalla's routing tiles for the whole extract (`left`: India drives on
  the left, which a regional extract can't tell Valhalla). For the southern zone: about two
  minutes, 1.4 GB of tiles, and **26 GB of memory** at 16 threads.
- `clip.py` cuts a region out (about 3 minutes and 3 GB of memory each) and writes `<file>.json`
  with the OpenStreetMap date.
- `build_index.py` writes the region's place search database (Bengaluru: 50,000 places, 10 MB): a
  `places` table and an FTS4 index over folded names. The app folds what is typed the same way
  (`core/search/.../SearchText.kt`); change both together.
- `package.py` copies the region's search database, an archive of its routing tiles and the shared
  routing config into `site/<version>/`, and lists them in `site/regions.json` with each file's
  size and SHA-256. Publish every region from the same routing build: a phone keeps all its
  regions on one version and updates them together.

## Publishing

Upload `site/` to any static host, then build the app pointed at it:

```sh
./gradlew assembleRelease -PpvotMapsRegionsUrl=https://example.org/pvot-maps
```

The app's Map modes sheet lists the regions under "Offline data" and downloads, updates and
deletes them, checking every file before using it. Without `pvotMapsRegionsUrl` the build can't
download regions. A debug build allows plain HTTP to `localhost`, so a local server works through
`adb reverse tcp:8765 tcp:8765` and `-PpvotMapsRegionsUrl=http://localhost:8765`.

The data is © OpenStreetMap contributors under the ODbL; the map keeps its attribution on screen.
