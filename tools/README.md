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

## A region, end to end

Regions are boxes in `tools/osm/regions.py`. For Bengaluru:

```sh
pbf=$(tools/osm/fetch.sh https://download.geofabrik.de/asia/india/southern-zone-latest.osm.pbf)
data=${PVOT_MAPS_DATA:-$HOME/.cache/pvot-maps}
mkdir -p "$data/regions" "$data/search"
.venv/bin/python -I tools/osm/clip.py bengaluru "$pbf" "$data/regions/bengaluru.osm.pbf"
.venv/bin/python -I tools/search/build_index.py bengaluru "$data/regions/bengaluru.osm.pbf" "$data/search/places.db"
```

Routing tiles for the same region (Valhalla's tools come with `pyvalhalla`, so put the venv's `bin`
on `PATH`; `left` because India drives on the left):

```sh
PATH=$PWD/.venv/bin:$PATH tools/routing/build_tiles.sh "$data/regions/bengaluru.osm.pbf" "$pbf" "$data/routing/bengaluru" left
```

- `fetch.sh` downloads a Geofabrik extract and checks its MD5 (about 560 MB for India's southern
  zone).
- `clip.py` cuts the region out (about 3 minutes and 3 GB of memory for Bengaluru) and writes
  `<file>.json` with the OpenStreetMap date.
- `build_index.py` writes the place search database (about 10 MB and 50,000 places for
  Bengaluru): a `places` table and an FTS4 index over folded names. The app folds what is typed
  the same way (`core/search/.../SearchText.kt`); change both together.
- `build_tiles.sh` builds Valhalla's routing tiles and packs them into `tiles.tar` (about 84 MB and
  ten seconds for Bengaluru). The admin database comes from the larger extract; a regional one has
  no country polygon, so the driving side is set by hand.

## Trying it on a phone

Until regions can be downloaded, copy the database into a debug build's storage:

```sh
adb push "$data/search/places.db" /data/local/tmp/places.db
adb shell "run-as org.prauga.maps sh -c 'mkdir -p files/search && cp /data/local/tmp/places.db files/search/'"
adb shell rm /data/local/tmp/places.db
```

The data is © OpenStreetMap contributors under the ODbL; the map keeps its attribution on screen.
