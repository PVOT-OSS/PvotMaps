# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Packs a region's built data for static hosting and adds it to the regions manifest.

Copies the search index and routing tiles into <site>/<region>/<version>/ and writes
<site>/regions.json, the manifest the app downloads first. The version is the OpenStreetMap date
the data was built from, so a newer build of the same region is offered to the app as an update.

Usage: python tools/regions/package.py <region> <data dir> <site dir>
"""

import hashlib
import json
import os
import shutil
import sqlite3
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "osm"))
from regions import REGIONS  # noqa: E402

MANIFEST_VERSION = 1
# Built file, relative to the data dir -> where the app keeps it, relative to its files dir.
FILES = {
    "search/places.db": "search/places.db",
    "routing/{region}/tiles.tar": "routing/tiles.tar",
    "routing/{region}/valhalla.json": "routing/valhalla.json",
}


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def package(region, data, site):
    meta = dict(sqlite3.connect(os.path.join(data, "search", "places.db")).execute("SELECT key, value FROM meta"))
    if meta.get("region") != region:
        sys.exit(f"search/places.db was built for {meta.get('region')}, not {region}")
    version = meta["osm_timestamp"][:10]
    folder = os.path.join(site, region, version)
    os.makedirs(folder, exist_ok=True)
    files = []
    for source, target in FILES.items():
        src = os.path.join(data, source.format(region=region))
        name = os.path.basename(src)
        shutil.copyfile(src, os.path.join(folder, name))
        files.append({
            "url": f"{region}/{version}/{name}",
            "target": target,
            "size": os.path.getsize(src),
            "sha256": sha256(src),
        })
    manifest_path = os.path.join(site, "regions.json")
    manifest = {"version": MANIFEST_VERSION, "regions": []}
    if os.path.exists(manifest_path):
        with open(manifest_path) as f:
            manifest = json.load(f)
    manifest["regions"] = [r for r in manifest["regions"] if r["id"] != region]
    manifest["regions"].append({
        "id": region,
        "name": REGIONS[region]["name"],
        "version": version,
        "bbox": list(REGIONS[region]["bbox"]),
        "files": files,
    })
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
    return version, sum(f["size"] for f in files)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    version, size = package(*sys.argv[1:])
    print(f"{sys.argv[1]} {version}: {size // (1 << 20)} MiB")
