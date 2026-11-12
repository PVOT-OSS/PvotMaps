# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Packs a region's built data for static hosting and adds it to the regions manifest.

A region gets its place search database and an archive of every routing tile that touches its
shape, taken from one routing build of the whole source extract. Neighbouring regions packed from
the same build share identical border tiles, so a phone with both installed routes across them.
The routing config, the same for every region of a build, is published once beside them.

Files land in <site>/<version>/, where the version is the OpenStreetMap date of the data; a phone
keeps every installed region on one version, so all regions are published from the same build.

Regions are a name from tools/osm/regions.py or a GeoJSON file of polygons, as for
tools/search/build_index.py, whose <search dir>/<region id>.db files this packs.

Usage: python tools/regions/package.py <regions> <search dir> <routing build dir> <site dir>
"""

import hashlib
import json
import os
import shutil
import sqlite3
import sys
import tarfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "osm"))
from areas import load_areas  # noqa: E402
from shapely.geometry import box  # noqa: E402
from shapely.prepared import prep  # noqa: E402

MANIFEST_VERSION = 2
# Valhalla's tile hierarchy: level -> tile size in degrees.
TILE_LEVELS = {0: 4.0, 1: 1.0, 2: 0.25}


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def tile_bbox(level, tile_id):
    size = TILE_LEVELS[level]
    columns = int(360 / size)
    row, column = divmod(tile_id, columns)
    west, south = -180 + column * size, -90 + row * size
    return west, south, west + size, south + size


def region_tiles(tiles_dir, geometry):
    """Tile files, relative to tiles_dir, whose area overlaps geometry."""
    shape = prep(geometry)
    picked = []
    for root, _, names in os.walk(tiles_dir):
        for name in names:
            if not name.endswith(".gph"):
                continue
            relative = os.path.relpath(os.path.join(root, name), tiles_dir)
            parts = relative[: -len(".gph")].split(os.sep)
            level = int(parts[0])
            if level not in TILE_LEVELS:
                continue
            tile_id = int("".join(parts[1:]))
            tile = box(*tile_bbox(level, tile_id))
            # Overlapping, not just sharing an edge, as a box ending on a tile line would.
            if shape.intersects(tile) and geometry.intersection(tile).area > 0:
                picked.append(relative)
    return sorted(picked)


def file_entry(path, url, target, kind="file"):
    return {"url": url, "target": target, "kind": kind, "size": os.path.getsize(path), "sha256": sha256(path)}


def package(area, places, routing, site):
    region = area.id
    meta = dict(sqlite3.connect(places).execute("SELECT key, value FROM meta"))
    if meta.get("region") != region:
        sys.exit(f"{places} was built for {meta.get('region')}, not {region}")
    version = meta["osm_timestamp"][:10]
    folder = os.path.join(site, version)
    os.makedirs(folder, exist_ok=True)

    search = os.path.join(folder, f"{region}.db")
    shutil.copyfile(places, search)
    tiles = region_tiles(os.path.join(routing, "tiles"), area.geometry)
    if not tiles:
        sys.exit(f"no routing tiles touch {region}")
    archive = os.path.join(folder, f"{region}-routing.tar")
    with tarfile.open(archive, "w", format=tarfile.USTAR_FORMAT) as tar:
        for tile in tiles:
            tar.add(os.path.join(routing, "tiles", tile), arcname=tile)
    config = os.path.join(folder, "valhalla.json")
    shutil.copyfile(os.path.join(routing, "valhalla.json"), config)

    files = [
        file_entry(search, f"{version}/{region}.db", f"search/{region}.db"),
        file_entry(archive, f"{version}/{region}-routing.tar", "routing/tiles", kind="tiles"),
        file_entry(config, f"{version}/valhalla.json", "routing/valhalla.json"),
    ]
    manifest_path = os.path.join(site, "regions.json")
    manifest = {"version": MANIFEST_VERSION, "regions": []}
    if os.path.exists(manifest_path):
        with open(manifest_path) as f:
            manifest = json.load(f)
        if manifest.get("version") != MANIFEST_VERSION:
            manifest = {"version": MANIFEST_VERSION, "regions": []}
    manifest["regions"] = [r for r in manifest["regions"] if r["id"] != region]
    manifest["regions"].append({
        "id": region,
        "name": area.name,
        "version": version,
        "bbox": [round(v, 4) for v in area.geometry.bounds],
        "files": files,
    })
    manifest["regions"].sort(key=lambda r: r["name"])
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
    return version, len(tiles), sum(f["size"] for f in files)


if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    spec, search, routing, site = sys.argv[1:]
    for area in load_areas(spec):
        version, count, size = package(area, os.path.join(search, f"{area.id}.db"), routing, site)
        print(f"{area.id} {version}: {count} tiles, {size // (1 << 20)} MiB")
