# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Extracts a country's first-level divisions (states, provinces) from an extract as polygons.

Reads the extract three times, keeping only what the boundaries need: the boundary relations,
then their ways, then those ways' nodes. Writes a GeoJSON FeatureCollection whose features carry
`id` (the ISO 3166-2 code, lower case, such as "in-ka"), `name` and `iso`, simplified to about
200 m, which is plenty for choosing tiles and places.

Usage: python tools/osm/boundaries.py <extract.osm.pbf> <country ISO code, e.g. IN> <out.geojson>
"""

import json
import sys

import osmium
from shapely.geometry import LineString, Point, Polygon, mapping
from shapely.ops import linemerge, polygonize, unary_union

SIMPLIFY_DEGREES = 0.002


def close_gaps(lines):
    """Chains an outline's open pieces end to nearest end into one ring and makes it a polygon."""
    merged = linemerge(unary_union(lines))
    pieces = list(merged.geoms) if hasattr(merged, "geoms") else [merged]
    if not pieces:
        return None
    ring = list(pieces.pop(0).coords)
    while pieces:
        end = ring[-1]
        best = min(
            range(len(pieces)),
            key=lambda i: min(
                Point(end).distance(Point(pieces[i].coords[0])),
                Point(end).distance(Point(pieces[i].coords[-1])),
            ),
        )
        piece = list(pieces.pop(best).coords)
        if Point(end).distance(Point(piece[-1])) < Point(end).distance(Point(piece[0])):
            piece.reverse()
        ring.extend(piece)
    polygon = Polygon(ring + [ring[0]]).buffer(0)
    return polygon


def boundaries(source, country):
    prefix = f"{country.upper()}-"
    relations = {}
    for rel in osmium.FileProcessor(source, osmium.osm.RELATION):
        tags = rel.tags
        iso = tags.get("ISO3166-2", "")
        if tags.get("boundary") != "administrative" or tags.get("admin_level") != "4":
            continue
        if not iso.startswith(prefix):
            continue
        outer = [m.ref for m in rel.members if m.type == "w" and m.role in ("outer", "")]
        relations[iso] = {"name": tags.get("name:en") or tags.get("name") or iso, "ways": outer}
    wanted_ways = {w for r in relations.values() for w in r["ways"]}

    way_nodes = {}
    for way in osmium.FileProcessor(source, osmium.osm.WAY):
        if way.id in wanted_ways:
            way_nodes[way.id] = [n.ref for n in way.nodes]
    wanted_nodes = {n for nodes in way_nodes.values() for n in nodes}

    coords = {}
    for node in osmium.FileProcessor(source, osmium.osm.NODE):
        if node.id in wanted_nodes:
            coords[node.id] = (node.location.lon, node.location.lat)

    features = []
    for iso, rel in sorted(relations.items()):
        lines = []
        for way_id in rel["ways"]:
            points = [coords[n] for n in way_nodes.get(way_id, []) if n in coords]
            if len(points) >= 2:
                lines.append(LineString(points))
        area = unary_union(list(polygonize(unary_union(lines))))
        if area.is_empty:
            # The extract cut the outline, as along a disputed border; close the gaps.
            area = close_gaps(lines)
            if area is None or area.is_empty:
                print(f"skipping {iso}: its outline can't be closed", file=sys.stderr)
                continue
            print(f"{iso}: closed gaps in its outline cut by the extract's edge", file=sys.stderr)
        area = area.simplify(SIMPLIFY_DEGREES, preserve_topology=True)
        features.append({
            "type": "Feature",
            "properties": {"id": iso.lower(), "name": rel["name"], "iso": iso},
            "geometry": mapping(area),
        })
    return {"type": "FeatureCollection", "features": features}


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    collection = boundaries(sys.argv[1], sys.argv[2])
    with open(sys.argv[3], "w") as f:
        json.dump(collection, f)
    print(f"{len(collection['features'])} areas")
