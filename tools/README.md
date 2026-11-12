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

A region is either a box named in `tools/osm/regions.py` (the Bengaluru, Hosur and Mysuru test
regions) or a polygon from a GeoJSON file, such as a country's states extracted by
`boundaries.py`. Routing is built once for the whole source extract and every region gets the
tiles that touch it, so neighbouring regions share their border tiles and a phone with both
installed routes across them. Search is built per region, in one pass over the source.

For India's states:

```sh
pbf=$(tools/osm/fetch.sh https://download.openstreetmap.fr/extracts/asia/india-latest.osm.pbf)
data=${PVOT_MAPS_DATA:-$HOME/.cache/pvot-maps}
regions="$data/boundaries/in.geojson"
mkdir -p "$data/boundaries"
.venv/bin/python -I tools/osm/boundaries.py "$pbf" IN "$regions"
PATH=$PWD/.venv/bin:$PATH tools/routing/build_tiles.sh "$pbf" "$data/routing/in" left
.venv/bin/python -I tools/search/build_index.py "$regions" "$pbf" "$data/search/in"
.venv/bin/python -I tools/regions/package.py "$regions" "$data/search/in" "$data/routing/in" site/
```

For a box region, pass its name instead of the GeoJSON file, e.g. `build_index.py bengaluru`.

- `fetch.sh` downloads an extract into a folder per host, resuming and retrying, and checks the
  host's MD5 when it publishes one (Geofabrik does, openstreetmap.fr doesn't).
- `boundaries.py` reads the first-level divisions (admin level 4 with an ISO 3166-2 code) out of
  the extract as polygons. A division only partly inside the extract can't close and is skipped,
  so take a whole country.
- `build_tiles.sh` builds Valhalla's routing tiles for the whole extract (`left`: India drives on
  the left, which a regional extract can't tell Valhalla). For India's southern zone: about two
  minutes, 1.4 GB of tiles, and **26 GB of memory** at 16 threads.
- `build_index.py` writes `<region>.db` per region: a `places` table and an FTS4 index over folded
  names. The app folds what is typed the same way (`core/search/.../SearchText.kt`); change both
  together. Each region's local-script name follows its main language (`tools/osm/areas.py`).
  Past 500 MB of source, node locations go to a file on disk rather than memory.
- `package.py` copies each region's search database, an archive of its routing tiles and the
  shared routing config into `site/<version>/`, and lists them in `site/regions.json` with each
  file's size and SHA-256. Publish every region from the same routing build: a phone keeps all its
  regions on one version and updates them together.
- `clip.py` cuts a box region out of a larger extract, for working on one city quickly.

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
