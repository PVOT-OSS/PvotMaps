# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Regions the data tools can build, as bounding boxes (min lon, min lat, max lon, max lat)."""

REGIONS = {
    # Bengaluru (BBMP) with its edges: Yelahanka to Electronic City, Kengeri to Whitefield.
    "bengaluru": {
        "name": "Bengaluru",
        "source": "https://download.geofabrik.de/asia/india/southern-zone-latest.osm.pbf",
        "bbox": (77.40, 12.80, 77.85, 13.20),
        "drives_on": "left",
    },
}


def inside(bbox, lon, lat):
    min_lon, min_lat, max_lon, max_lat = bbox
    return min_lon <= lon <= max_lon and min_lat <= lat <= max_lat
