# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Packs a region's built data for static hosting and adds it to the regions manifest.

A region gets its place search database and an archive of every routing tile that touches its
box, taken from one routing build of the whole source extract. Neighbouring regions packed from
the same build share identical border tiles, so a phone with both installed routes across them.
The routing config, the same for every region of a build, is published once beside them.

Files land in <site>/<version>/, where the version is the OpenStreetMap date of the data; a phone
keeps every installed region on one version, so all regions are published from the same build.

Usage: python tools/regions/package.py <region> <places.db> <routing build dir> <site dir>
"""

import hashlib
import json
import os
import shutil
import sqlite3
import sys
import tarfile

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "osm"))
from regions import REGIONS  # noqa: E402

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


def touches(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def region_tiles(tiles_dir, bbox):
    """Tile files, relative to tiles_dir, whose area overlaps bbox."""
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
            if touches(tile_bbox(level, tile_id), bbox):
                picked.append(relative)
    return sorted(picked)


def file_entry(path, url, target, kind="file"):
    return {"url": url, "target": target, "kind": kind, "size": os.path.getsize(path), "sha256": sha256(path)}


def package(region, places, routing, site):
    meta = dict(sqlite3.connect(places).execute("SELECT key, value FROM meta"))
    if meta.get("region") != region:
        sys.exit(f"{places} was built for {meta.get('region')}, not {region}")
    version = meta["osm_timestamp"][:10]
    folder = os.path.join(site, version)
    os.makedirs(folder, exist_ok=True)

    search = os.path.join(folder, f"{region}.db")
    shutil.copyfile(places, search)
    tiles = region_tiles(os.path.join(routing, "tiles"), REGIONS[region]["bbox"])
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
        "name": REGIONS[region]["name"],
        "version": version,
        "bbox": list(REGIONS[region]["bbox"]),
        "files": files,
    })
    manifest["regions"].sort(key=lambda r: r["name"])
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
    return version, len(tiles), sum(f["size"] for f in files)


if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    version, count, size = package(*sys.argv[1:])
    print(f"{sys.argv[1]} {version}: {count} tiles, {size // (1 << 20)} MiB")
