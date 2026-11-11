# Third-party notices

## Tabler Icons

The icons in `feature/map/src/main/res/drawable/` (`ic_search.xml`, `ic_location.xml`,
`ic_close.xml`, `ic_map.xml`, `ic_steering_wheel.xml`, `ic_home.xml`, `ic_briefcase.xml`,
`ic_plus.xml`, `ic_chevron_right.xml`, `ic_volume.xml`, `ic_volume_off.xml`, the place-kind icons `ic_kind_*.xml` and the turn arrows `ic_turn_*.xml`) are converted from [Tabler Icons](https://tabler.io/icons), Copyright (c)
2020-2026 Paweł Kuna, MIT License. The license text is in `LICENSES/MIT.txt`.

## OpenFreeMap Liberty map style

The map is drawn from OpenFreeMap's Liberty style
(<https://github.com/hyperknot/openfreemap-styles>), fetched at runtime and changed by the app:
its colours are replaced with our own palette (`engine/maplibre/.../MapPalette.kt`), a Natural
Earth relief layer and a globe projection are added. Liberty is a fork of
[OSM Liberty](https://github.com/maputnik/osm-liberty), itself derived from OSM Bright and Mapbox
Open Styles. The style's code is released under the BSD 3-Clause License and its design under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The OpenFreeMap styles repository is
MIT licensed, Copyright (c) 2023 Zsolt Ero. Map data © OpenStreetMap contributors (ODbL), shown
in the map's attribution. Natural Earth data is in the public domain.

## Valhalla and valhalla-mobile

Offline routing runs [Valhalla](https://github.com/valhalla/valhalla) (MIT License, Copyright (c)
2018 Valhalla contributors, Copyright (c) 2015-2017 Mapillary AB, Mapzen) on the device through
[valhalla-mobile](https://github.com/Rallista/valhalla-mobile) (MIT License, Copyright (c)
2024 Adventure Consortium Inc (dba Rallista)). The license text is in `LICENSES/MIT.txt`. The
routing data is built from OpenStreetMap (© OpenStreetMap contributors, ODbL).
