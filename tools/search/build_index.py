# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Builds the offline place search database for a region from its OpenStreetMap extract.

The database has a `places` table with what the app shows, and an FTS4 table over a folded form of
every name (see fold()), which the app queries with the same folding applied to what was typed.

Usage: python tools/search/build_index.py <region> <region.osm.pbf> <places.db>
"""

import json
import os
import sqlite3
import sys
import time
import unicodedata

import osmium

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "osm"))
from regions import REGIONS, inside  # noqa: E402

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


class Collector:
    def __init__(self, bbox):
        self.bbox = bbox
        self.places = []
        self.streets = {}

    def add(self, osm_id, tags, lon, lat):
        name = tags.get("name")
        if not name or not inside(self.bbox, lon, lat):
            return
        if any(tags.get(key) == value for key, value in SKIPPED):
            return
        found = classify(tags)
        if found is None:
            return
        category, kind, rank = found
        if tags.get("station") == "subway":
            category = "subway"
        self.places.append({
            "osm": osm_id,
            "name": tags.get("name:en") or name,
            "local": tags.get("name:kn") if tags.get("name:kn") != (tags.get("name:en") or name) else None,
            "others": [tags[k] for k in OTHER_NAMES if tags.get(k)] + [name, tags.get("name:kn") or ""],
            "category": category,
            "kind": kind,
            "rank": rank,
            "lon": lon,
            "lat": lat,
            "street": tags.get("addr:street"),
            "housenumber": tags.get("addr:housenumber"),
        })

    def add_street(self, osm_id, tags, points):
        name = tags.get("name:en") or tags.get("name")
        mid = points[len(points) // 2] if points else None
        if not name or mid is None or not inside(self.bbox, *mid):
            return
        key = (fold(name), round(mid[0] / STREET_CELL), round(mid[1] / STREET_CELL))
        if key in self.streets:
            return
        self.streets[key] = {
            "osm": osm_id,
            "name": name,
            "local": tags.get("name:kn"),
            "others": [tags[k] for k in OTHER_NAMES if tags.get(k)] + [tags.get("name") or ""],
            "category": tags["highway"],
            "kind": "street",
            "rank": 4,
            "lon": mid[0],
            "lat": mid[1],
            "street": None,
            "housenumber": None,
        }


def collect(region, source):
    collector = Collector(REGIONS[region]["bbox"])
    for obj in osmium.FileProcessor(source).with_locations().with_areas():
        if obj.is_node():
            if obj.tags:
                collector.add(f"n{obj.id}", obj.tags, obj.location.lon, obj.location.lat)
        elif obj.is_way():
            tags = obj.tags
            if tags.get("highway") in STREETS and not obj.is_closed():
                points = [(n.lon, n.lat) for n in obj.nodes if n.location.valid()]
                collector.add_street(f"w{obj.id}", tags, points)
        elif obj.is_area():
            points = [(n.lon, n.lat) for ring in obj.outer_rings() for n in ring]
            centre = centroid(points)
            if centre:
                prefix = "w" if obj.from_way() else "r"
                collector.add(f"{prefix}{obj.orig_id()}", obj.tags, *centre)
    return collector.places + list(collector.streets.values())


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


def write(places, region, source, target):
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
        "region": region,
        "bbox": ",".join(map(str, REGIONS[region]["bbox"])),
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
    region, source, target = sys.argv[1:]
    places = dedupe(collect(region, source))
    attach_areas(places)
    write(places, region, source, target)
    print(f"{len(places)} places -> {target} ({os.path.getsize(target) // 1024} KiB)")
