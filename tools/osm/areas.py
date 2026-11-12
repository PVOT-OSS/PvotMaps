# SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0

"""Regions as shapes: the named boxes in regions.py, or polygons from a GeoJSON file."""

import json
import os
import sys

from shapely.geometry import box, shape

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from regions import REGIONS  # noqa: E402


# The main language of each Indian state, for the local-script name shown under a place's name.
# ISO 3166-2 code -> OpenStreetMap language suffix; anywhere else falls back to Hindi.
LANGUAGE_BY_ISO = {
    "IN-KA": "kn", "IN-TN": "ta", "IN-PY": "ta", "IN-KL": "ml", "IN-LD": "ml", "IN-AP": "te",
    "IN-TG": "te", "IN-TS": "te", "IN-MH": "mr", "IN-GA": "kok", "IN-GJ": "gu", "IN-PB": "pa",
    "IN-OR": "or", "IN-OD": "or", "IN-WB": "bn", "IN-TR": "bn", "IN-AS": "as", "IN-MN": "mni",
    "IN-JK": "ur", "IN-AN": "hi",
}
DEFAULT_LANGUAGE = "hi"


class Area:
    def __init__(self, id, name, geometry, language):
        self.id = id
        self.name = name
        self.geometry = geometry
        self.language = language


def load_areas(spec):
    """`spec` is a region name from regions.py, or a GeoJSON file such as boundaries.py writes."""
    if spec in REGIONS:
        region = REGIONS[spec]
        return [Area(spec, region["name"], box(*region["bbox"]), region.get("language", DEFAULT_LANGUAGE))]
    with open(spec) as f:
        collection = json.load(f)
    return [
        Area(
            feature["properties"]["id"],
            feature["properties"]["name"],
            shape(feature["geometry"]),
            LANGUAGE_BY_ISO.get(feature["properties"]["iso"], DEFAULT_LANGUAGE),
        )
        for feature in collection["features"]
    ]
