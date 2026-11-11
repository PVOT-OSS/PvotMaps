# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Cuts a region out of a larger OpenStreetMap extract.

Keeps nodes inside the region's box, ways that touch it (whole, with all their nodes), and the
relations the app uses: multipolygons for named areas such as lakes and parks, and turn
restrictions for routing. Administrative boundaries are left out; they would pull in whole states.

Also writes <output>.json with the region, its box and the source's OpenStreetMap timestamp, which
the clipped file's own header no longer carries.

Usage: python tools/osm/clip.py <region> <input.osm.pbf> <output.osm.pbf>
"""

import json
import os
import sys

import osmium

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from regions import REGIONS, inside  # noqa: E402

KEPT_RELATIONS = {"multipolygon", "restriction"}


def clip(region, source, target):
    bbox = REGIONS[region]["bbox"]
    nodes = set()
    ways = set()
    counts = {"nodes": 0, "ways": 0, "relations": 0}
    with osmium.BackReferenceWriter(target, ref_src=source, overwrite=True) as writer:
        for obj in osmium.FileProcessor(source):
            if obj.is_node():
                if inside(bbox, obj.location.lon, obj.location.lat):
                    nodes.add(obj.id)
                    writer.add_node(obj)
                    counts["nodes"] += 1
            elif obj.is_way():
                if any(n.ref in nodes for n in obj.nodes):
                    ways.add(obj.id)
                    writer.add_way(obj)
                    counts["ways"] += 1
            elif obj.is_relation():
                if obj.tags.get("type") not in KEPT_RELATIONS:
                    continue
                if any(
                    (m.type == "w" and m.ref in ways) or (m.type == "n" and m.ref in nodes)
                    for m in obj.members
                ):
                    writer.add_relation(obj)
                    counts["relations"] += 1
    header = osmium.io.Reader(source).header()
    manifest = {
        "region": region,
        "bbox": bbox,
        "source": REGIONS[region]["source"],
        "osm_timestamp": header.get("osmosis_replication_timestamp") or header.get("timestamp"),
        **counts,
    }
    with open(f"{target}.json", "w") as f:
        json.dump(manifest, f, indent=2)
    return manifest


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    print(clip(*sys.argv[1:]))
