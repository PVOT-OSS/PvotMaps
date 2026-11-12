# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Builds the offline place search databases for regions from an OpenStreetMap extract.

Reads the extract once and files each place under every region whose shape contains it, writing
<out dir>/<region id>.db per region. Regions are a name from tools/osm/regions.py (a box) or a
GeoJSON file of polygons such as tools/osm/boundaries.py writes (a country's states).

Each database has a `places` table with what the app shows, and an FTS4 table over a folded form
of every name (see fold()), which the app queries with the same folding applied to what was typed.

Usage: python tools/search/build_index.py <regions> <source.osm.pbf> <out dir>
"""

import json
import os
import sqlite3
import sys
import time
import unicodedata

import osmium
from shapely import STRtree, total_bounds
from shapely.geometry import Point
from shapely.prepared import prep

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "osm"))
from areas import load_areas  # noqa: E402

SCHEMA_VERSION = 1

# (tag key, value or None for any) -> (kind, rank). Lower ranks come first in results.
PLACES = {
    ("place", "city"): ("place", 0),
    ("place", "town"): ("place", 0),
    ("place", "suburb"): ("place", 1),
    ("place", "quarter"): ("place", 1),
    ("place", "neighbourhood"): ("place", 1),
    ("place", "village"): ("place", 1),
    ("place", "locality"): ("place", 2),
}
LANDMARKS = {
    ("railway", "station"): ("transport", 2),
    ("railway", "halt"): ("transport", 2),
    ("station", "subway"): ("transport", 2),
    ("aeroway", "aerodrome"): ("transport", 2),
    ("amenity", "bus_station"): ("transport", 2),
    ("leisure", "park"): ("nature", 2),
    ("leisure", "garden"): ("nature", 2),
    ("natural", "water"): ("nature", 2),
    ("water", "lake"): ("nature", 2),
    ("amenity", "hospital"): ("health", 2),
    ("amenity", "university"): ("education", 2),
    ("amenity", "college"): ("education", 2),
    ("shop", "mall"): ("shop", 2),
    ("tourism", "attraction"): ("sight", 2),
    ("tourism", "museum"): ("sight", 2),
    ("historic", None): ("sight", 2),
    ("highway", "bus_stop"): ("transport", 5),
}
KIND_BY_KEY = {
    "amenity": "amenity",
    "shop": "shop",
    "tourism": "sight",
    "leisure": "leisure",
    "office": "office",
    "healthcare": "health",
    "craft": "shop",
    "sport": "leisure",
    "public_transport": "transport",
}
FOOD = {"restaurant", "cafe", "fast_food", "bar", "pub", "food_court", "ice_cream", "bakery"}
HEALTH = {"pharmacy", "clinic", "doctors", "dentist", "hospital"}
EDUCATION = {"school", "college", "university", "kindergarten", "library"}
WORSHIP = {"place_of_worship"}
STREETS = {
    "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
    "living_street", "pedestrian", "road",
}
# Transit stop positions and platforms repeat the stations and stops themselves.
SKIPPED = {("public_transport", "stop_position"), ("public_transport", "platform")}
# Words people search with that the tags don't spell out, by category.
SYNONYMS = {"subway": "metro", "bus_stop": "bus", "aerodrome": "airport"}
DUPLICATE_CELL = 0.002  # about 200 m: same-named places closer than this are one place
# Names in India's scheduled languages, any of which may be the local one, and all searchable.
LOCAL_NAMES = ("name:kn", "name:hi", "name:ta", "name:te", "name:ml", "name:mr", "name:bn",
               "name:gu", "name:pa", "name:or", "name:as", "name:ur")
# Past this size, node locations go in a file on disk instead of memory.
LARGE_SOURCE_BYTES = 500 << 20
OTHER_NAMES = ("name:en", "alt_name", "old_name", "short_name", "official_name", "loc_name")
STREET_CELL = 0.01  # about a kilometre: same-named street segments closer than this merge
DROPPED = "'\u2019\u02bc."
AREA_RADIUS = 0.03  # degrees: how far a place looks for the neighbourhood it belongs to


def fold(text):
    """Folds a name for matching; the app's PlaceSearch must fold queries exactly the same way.

    Lower-case, decompose, drop accents on Latin letters (combining marks below U+0370) but keep
    other scripts' marks such as Kannada vowel signs, drop apostrophes, full stops ("M.G." is "MG")
    and invisible joiners, turn
    anything else that isn't a letter, digit or mark into a space, and collapse runs of the same
    letter: "Laalbagh" and "Lalbagh" both fold to "lalbagh"; numbers stay as they are.
    """
    text = unicodedata.normalize("NFKD", text.lower())
    kept = []
    for c in text:
        category = unicodedata.category(c)
        if (category == "Mn" and ord(c) < 0x0370) or category == "Cf" or c in DROPPED:
            continue
        kept.append(c if category[0] in "LNM" else " ")
    collapsed = []
    for c in kept:
        if c.isalpha() and collapsed and collapsed[-1] == c:
            continue
        collapsed.append(c)
    return " ".join("".join(collapsed).split())


def classify(tags):
    for (key, value), result in PLACES.items():
        if tags.get(key) == value:
            return (value,) + result
    for (key, value), result in LANDMARKS.items():
        if key in tags and (value is None or tags[key] == value):
            return (tags[key] if value is None else value,) + result
    for key, kind in KIND_BY_KEY.items():
        value = tags.get(key)
        if value and value not in ("yes", "no"):
            if value in FOOD:
                kind = "food"
            elif value in HEALTH:
                kind = "health"
            elif value in EDUCATION:
                kind = "education"
            elif value in WORSHIP:
                kind = "worship"
            return (value, kind, 3)
    return None


def centroid(points):
    if not points:
        return None
    return sum(p[0] for p in points) / len(points), sum(p[1] for p in points) / len(points)


def names(tags, language):
    """The name to show, the local-script name when it differs, and every other name to search."""
    shown = tags.get("name:en") or tags.get("name")
    preferred = (f"name:{language}",) + LOCAL_NAMES
    local = next((tags[k] for k in preferred if tags.get(k) and tags[k] != shown), None)
    if local is None and tags.get("name") and tags.get("name") != shown:
        local = tags.get("name")
    others = [tags[k] for k in OTHER_NAMES + LOCAL_NAMES if tags.get(k)] + [tags.get("name") or ""]
    return shown, local, others


class Collector:
    def __init__(self, areas):
        self.areas = areas
        self.tree = STRtree([area.geometry for area in areas])
        self.shapes = [prep(area.geometry) for area in areas]
        self.bounds = tuple(total_bounds([area.geometry for area in areas]))
        self.languages = {area.id: area.language for area in areas}
        self.places = {area.id: [] for area in areas}
        self.streets = {area.id: {} for area in areas}

    def areas_at(self, lon, lat):
        min_lon, min_lat, max_lon, max_lat = self.bounds
        if not (min_lon <= lon <= max_lon and min_lat <= lat <= max_lat):
            return []
        point = Point(lon, lat)
        return [self.areas[i].id for i in self.tree.query(point) if self.shapes[i].contains(point)]

    def add(self, osm_id, tags, lon, lat):
        if not tags.get("name"):
            return
        if any(tags.get(key) == value for key, value in SKIPPED):
            return
        found = classify(tags)
        if found is None:
            return
        inside = self.areas_at(lon, lat)
        if not inside:
            return
        category, kind, rank = found
        if tags.get("station") == "subway":
            category = "subway"
        language = self.languages[inside[0]]
        shown, local, others = names(tags, language)
        place = {
            "osm": osm_id,
            "name": shown,
            "local": local,
            "others": others,
            "category": category,
            "kind": kind,
            "rank": rank,
            "lon": lon,
            "lat": lat,
            "street": tags.get("addr:street"),
            "housenumber": tags.get("addr:housenumber"),
        }
        for area in inside:
            self.places[area].append(place)

    def add_street(self, osm_id, tags, points):
        mid = points[len(points) // 2] if points else None
        if not (tags.get("name:en") or tags.get("name")) or mid is None:
            return
        for area in self.areas_at(*mid):
            shown, local, others = names(tags, self.languages[area])
            key = (fold(shown), round(mid[0] / STREET_CELL), round(mid[1] / STREET_CELL))
            if key in self.streets[area]:
                continue
            self.streets[area][key] = {
                "osm": osm_id,
                "name": shown,
                "local": local,
                "others": others,
                "category": tags["highway"],
                "kind": "street",
                "rank": 4,
                "lon": mid[0],
                "lat": mid[1],
                "street": None,
                "housenumber": None,
            }


def collect(areas, source, scratch):
    collector = Collector(areas)
    processor = osmium.FileProcessor(source)
    if os.path.getsize(source) > LARGE_SOURCE_BYTES:
        processor = processor.with_locations(f"sparse_file_array,{scratch}")
    else:
        processor = processor.with_locations()
    for obj in processor.with_areas():
        if obj.is_node():
            if obj.tags:
                collector.add(f"n{obj.id}", obj.tags, obj.location.lon, obj.location.lat)
        elif obj.is_way():
            tags = obj.tags
            if tags.get("highway") in STREETS and not obj.is_closed():
                points = [(n.lon, n.lat) for n in obj.nodes if n.location.valid()]
                collector.add_street(f"w{obj.id}", tags, points)
        elif obj.is_area():
            if not obj.tags.get("name"):
                continue
            points = [(n.lon, n.lat) for ring in obj.outer_rings() for n in ring]
            centre = centroid(points)
            if centre:
                prefix = "w" if obj.from_way() else "r"
                collector.add(f"{prefix}{obj.orig_id()}", obj.tags, *centre)
    return {area.id: collector.places[area.id] + list(collector.streets[area.id].values()) for area in areas}


def dedupe(places):
    """Keeps one of each name in each small cell: the most important, such as a station over its stop."""
    best = {}
    for place in places:
        key = (fold(place["name"]), round(place["lat"] / DUPLICATE_CELL), round(place["lon"] / DUPLICATE_CELL))
        if key not in best or place["rank"] < best[key]["rank"]:
            best[key] = place
    return list(best.values())


def attach_areas(places):
    """Gives every place the name of the nearest neighbourhood, suburb or locality around it."""
    areas = [p for p in places if p["kind"] == "place" and p["category"] != "city"]
    grid = {}
    for area in areas:
        grid.setdefault((int(area["lon"] / AREA_RADIUS), int(area["lat"] / AREA_RADIUS)), []).append(area)
    for place in places:
        cx, cy = int(place["lon"] / AREA_RADIUS), int(place["lat"] / AREA_RADIUS)
        best, best_distance = None, AREA_RADIUS ** 2
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for area in grid.get((cx + dx, cy + dy), ()):
                    if area is place:
                        continue
                    d = (area["lon"] - place["lon"]) ** 2 + (area["lat"] - place["lat"]) ** 2
                    if d < best_distance:
                        best, best_distance = area, d
        place["area"] = best["name"] if best else None


def write(places, area, source, target):
    if os.path.exists(target):
        os.remove(target)
    db = sqlite3.connect(target)
    db.executescript("""
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE places (
            id INTEGER PRIMARY KEY,
            osm TEXT NOT NULL,
            name TEXT NOT NULL,
            folded_name TEXT NOT NULL,
            local_name TEXT,
            category TEXT NOT NULL,
            kind TEXT NOT NULL,
            rank INTEGER NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            area TEXT,
            street TEXT,
            housenumber TEXT
        );
        CREATE VIRTUAL TABLE places_fts USING fts4(terms, tokenize=simple);
    """)
    for i, p in enumerate(places, start=1):
        words = [p["name"], *p["others"], p["category"].replace("_", " "), SYNONYMS.get(p["category"])]
        terms = " ".join(dict.fromkeys(fold(w) for w in words if w))
        db.execute(
            "INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (i, p["osm"], p["name"], fold(p["name"]), p["local"], p["category"], p["kind"],
             p["rank"], round(p["lat"], 6), round(p["lon"], 6), p["area"], p["street"],
             p["housenumber"]),
        )
        db.execute("INSERT INTO places_fts (docid, terms) VALUES (?, ?)", (i, terms))
    manifest_path = f"{source}.json"
    manifest = json.load(open(manifest_path)) if os.path.exists(manifest_path) else {}
    header = osmium.io.Reader(source).header()
    meta = {
        "schema": str(SCHEMA_VERSION),
        "region": area.id,
        "name": area.name,
        "bbox": ",".join(f"{v:.4f}" for v in area.geometry.bounds),
        "osm_timestamp": manifest.get("osm_timestamp")
        or header.get("osmosis_replication_timestamp", ""),
        "built": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "count": str(len(places)),
    }
    db.executemany("INSERT INTO meta VALUES (?, ?)", meta.items())
    db.execute("INSERT INTO places_fts (places_fts) VALUES ('optimize')")
    db.commit()
    db.execute("VACUUM")
    db.close()


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    spec, source, out = sys.argv[1:]
    os.makedirs(out, exist_ok=True)
    areas = load_areas(spec)
    scratch = os.path.join(out, ".node-locations")
    try:
        by_area = collect(areas, source, scratch)
    finally:
        if os.path.exists(scratch):
            os.remove(scratch)
    for area in areas:
        places = dedupe(by_area[area.id])
        attach_areas(places)
        target = os.path.join(out, f"{area.id}.db")
        write(places, area, source, target)
        print(f"{area.id}: {len(places)} places, {os.path.getsize(target) // 1024} KiB")
