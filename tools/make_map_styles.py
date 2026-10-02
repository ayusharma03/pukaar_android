"""Generates the Pukaar offline map styles (assets/pukaar/map/style_dark.json, style_light.json).

Colours come from docs/design-tokens.md and the handoff (2p): land in surface tones, water in
primaryContainer at 60%, roads in surfaceContainerHigh/Highest. Tiles and fonts come from
OpenFreeMap (OpenStreetMap data, OpenMapTiles schema, no API key).

Run from the repo root:  python tools/make_map_styles.py
"""
import json
import os

TILES = "https://tiles.openfreemap.org/planet"
GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
FONT = ["Noto Sans Regular"]
FONT_BOLD = ["Noto Sans Bold"]

PALETTES = {
    "dark": {
        "background": "#121318",       # surface
        "land": "#1A1B21",             # surfaceContainerLow
        "green": "#1E2420",            # surfaceContainer, nudged green
        "water": "#324478",            # primaryContainer
        "building": "#292A2F",         # surfaceContainerHigh
        "road_minor": "#292A2F",       # surfaceContainerHigh
        "road_major": "#33343A",       # surfaceContainerHighest
        "road_casing": "#45464F",      # outlineVariant
        "boundary": "#8F909A",         # outline
        "label": "#C5C6D0",            # onSurfaceVariant
        "label_strong": "#E3E2E9",     # onSurface
        "halo": "#121318",
    },
    "light": {
        "background": "#FAF8FF",
        "land": "#F4F3FA",
        "green": "#E9EFE6",
        "water": "#DBE1FF",
        "building": "#E8E7EF",
        "road_minor": "#FFFFFF",
        "road_major": "#E3E2E9",
        "road_casing": "#C5C6D0",
        "boundary": "#757680",
        "label": "#45464F",
        "label_strong": "#1A1B21",
        "halo": "#FAF8FF",
    },
}

NAME = ["coalesce", ["get", "name:en"], ["get", "name:latin"], ["get", "name"]]


def width(stops):
    return ["interpolate", ["exponential", 1.4], ["zoom"], *[v for s in stops for v in s]]


def style(name, c):
    major = ["match", ["get", "class"], ["motorway", "trunk", "primary"], True, False]
    secondary = ["match", ["get", "class"], ["secondary", "tertiary"], True, False]
    minor = ["match", ["get", "class"], ["minor", "service", "track", "path"], True, False]
    layers = [
        {"id": "background", "type": "background", "paint": {"background-color": c["background"]}},
        {"id": "landuse", "type": "fill", "source": "omt", "source-layer": "landuse",
         "paint": {"fill-color": c["land"]}},
        {"id": "landcover", "type": "fill", "source": "omt", "source-layer": "landcover",
         "filter": ["match", ["get", "class"], ["wood", "grass", "farmland", "wetland"], True, False],
         "paint": {"fill-color": c["green"], "fill-opacity": 0.7}},
        {"id": "park", "type": "fill", "source": "omt", "source-layer": "park", "paint": {"fill-color": c["green"]}},
        {"id": "water", "type": "fill", "source": "omt", "source-layer": "water",
         "paint": {"fill-color": c["water"], "fill-opacity": 0.6}},
        {"id": "waterway", "type": "line", "source": "omt", "source-layer": "waterway",
         "paint": {"line-color": c["water"], "line-opacity": 0.8, "line-width": width([(8, 0.5), (14, 2), (18, 6)])}},
        {"id": "building", "type": "fill", "source": "omt", "source-layer": "building", "minzoom": 13,
         "paint": {"fill-color": c["building"]}},
        {"id": "road-minor", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 12,
         "filter": minor, "layout": {"line-cap": "round", "line-join": "round"},
         "paint": {"line-color": c["road_minor"], "line-width": width([(12, 0.5), (14, 2), (18, 10)])}},
        {"id": "road-secondary-casing", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 9,
         "filter": secondary, "layout": {"line-cap": "round", "line-join": "round"},
         "paint": {"line-color": c["road_casing"], "line-width": width([(9, 1), (14, 5), (18, 18)])}},
        {"id": "road-secondary", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 9,
         "filter": secondary, "layout": {"line-cap": "round", "line-join": "round"},
         "paint": {"line-color": c["road_major"], "line-width": width([(9, 0.5), (14, 3.5), (18, 15)])}},
        {"id": "road-major-casing", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 6,
         "filter": major, "layout": {"line-cap": "round", "line-join": "round"},
         "paint": {"line-color": c["road_casing"], "line-width": width([(6, 1), (14, 7), (18, 24)])}},
        {"id": "road-major", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 6,
         "filter": major, "layout": {"line-cap": "round", "line-join": "round"},
         "paint": {"line-color": c["road_major"], "line-width": width([(6, 0.6), (14, 5), (18, 20)])}},
        {"id": "boundary", "type": "line", "source": "omt", "source-layer": "boundary",
         "filter": ["<=", ["get", "admin_level"], 4],
         "paint": {"line-color": c["boundary"], "line-dasharray": [3, 2], "line-width": 1, "line-opacity": 0.6}},
        {"id": "road-label", "type": "symbol", "source": "omt", "source-layer": "transportation_name", "minzoom": 14,
         "layout": {"text-field": NAME, "text-font": FONT, "text-size": 12, "symbol-placement": "line"},
         "paint": {"text-color": c["label"], "text-halo-color": c["halo"], "text-halo-width": 1.5}},
        {"id": "water-label", "type": "symbol", "source": "omt", "source-layer": "water_name", "minzoom": 10,
         "layout": {"text-field": NAME, "text-font": FONT, "text-size": 12},
         "paint": {"text-color": c["label"], "text-halo-color": c["halo"], "text-halo-width": 1.5}},
        {"id": "place-village", "type": "symbol", "source": "omt", "source-layer": "place", "minzoom": 11,
         "filter": ["match", ["get", "class"], ["village", "hamlet", "suburb", "neighbourhood"], True, False],
         "layout": {"text-field": NAME, "text-font": FONT, "text-size": 13},
         "paint": {"text-color": c["label"], "text-halo-color": c["halo"], "text-halo-width": 1.5}},
        {"id": "place-town", "type": "symbol", "source": "omt", "source-layer": "place", "minzoom": 7,
         "filter": ["match", ["get", "class"], ["city", "town"], True, False],
         "layout": {"text-field": NAME, "text-font": FONT_BOLD, "text-size": ["interpolate", ["linear"], ["zoom"], 7, 12, 12, 16]},
         "paint": {"text-color": c["label_strong"], "text-halo-color": c["halo"], "text-halo-width": 1.5}},
    ]
    return {
        "version": 8,
        "name": f"Pukaar {name}",
        "sources": {"omt": {"type": "vector", "url": TILES, "attribution": "© OpenStreetMap contributors · OpenFreeMap"}},
        "glyphs": GLYPHS,
        "layers": layers,
    }


def main():
    out = os.path.join("app", "src", "main", "assets", "pukaar", "map")
    os.makedirs(out, exist_ok=True)
    for name, palette in PALETTES.items():
        with open(os.path.join(out, f"style_{name}.json"), "w", encoding="utf-8") as f:
            json.dump(style(name, palette), f, indent=1)
            f.write("\n")
        print("wrote", name)


if __name__ == "__main__":
    main()
