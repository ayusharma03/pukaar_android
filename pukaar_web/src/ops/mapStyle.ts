// MapLibre style from the Pukaar tokens (handoff "Map style"): water in primaryContainer, roads in
// surfaceContainerHigh/Highest, dashed block boundaries in outlineVariant, labels in
// onSurfaceVariant at 12 px. Same OpenFreeMap vector tiles as the Android app, no API key.
import type { StyleSpecification } from 'maplibre-gl';
import { token } from '../ui/theme';

export function pukaarMapStyle(): StyleSpecification {
  const c = (name: string) => token(name) || '#888888';
  const label = c('on-surface-variant');
  const halo = c('surface-container-lowest');
  return {
    version: 8,
    sources: {
      omt: { type: 'vector', url: 'https://tiles.openfreemap.org/planet', attribution: '© OpenStreetMap contributors · OpenFreeMap' },
    },
    glyphs: 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf',
    layers: [
      { id: 'background', type: 'background', paint: { 'background-color': c('surface-container-low') } },
      {
        id: 'landcover',
        type: 'fill',
        source: 'omt',
        'source-layer': 'landcover',
        filter: ['match', ['get', 'class'], ['wetland'], true, false],
        paint: { 'fill-color': c('primary-container'), 'fill-opacity': 0.18 },
      },
      { id: 'water', type: 'fill', source: 'omt', 'source-layer': 'water', paint: { 'fill-color': c('primary-container'), 'fill-opacity': 0.9 } },
      {
        id: 'waterway',
        type: 'line',
        source: 'omt',
        'source-layer': 'waterway',
        paint: {
          'line-color': c('primary-container'),
          'line-width': ['interpolate', ['linear'], ['zoom'], 8, ['match', ['get', 'class'], 'river', 1.6, 0.4], 14, ['match', ['get', 'class'], 'river', 6, 1.5]],
        },
      },
      {
        id: 'roads-minor',
        type: 'line',
        source: 'omt',
        'source-layer': 'transportation',
        minzoom: 11,
        filter: ['match', ['get', 'class'], ['minor', 'service', 'track'], true, false],
        paint: { 'line-color': c('surface-container-high'), 'line-width': ['interpolate', ['linear'], ['zoom'], 11, 0.5, 16, 4] },
      },
      {
        id: 'roads-major',
        type: 'line',
        source: 'omt',
        'source-layer': 'transportation',
        filter: ['match', ['get', 'class'], ['motorway', 'trunk', 'primary', 'secondary', 'tertiary'], true, false],
        paint: {
          'line-color': c('surface-container-highest'),
          'line-width': ['interpolate', ['linear'], ['zoom'], 7, 0.6, 12, 2.2, 16, 8],
        },
      },
      {
        id: 'rail',
        type: 'line',
        source: 'omt',
        'source-layer': 'transportation',
        minzoom: 10,
        filter: ['==', ['get', 'class'], 'rail'],
        paint: { 'line-color': c('outline-variant'), 'line-width': 1, 'line-dasharray': [3, 3] },
      },
      {
        id: 'boundaries',
        type: 'line',
        source: 'omt',
        'source-layer': 'boundary',
        filter: ['all', ['>=', ['get', 'admin_level'], 4], ['<=', ['get', 'admin_level'], 8]],
        paint: {
          'line-color': c('outline-variant'),
          'line-width': ['match', ['get', 'admin_level'], 4, 1.6, 1],
          'line-dasharray': [4, 3],
        },
      },
      {
        id: 'water-names',
        type: 'symbol',
        source: 'omt',
        'source-layer': 'waterway',
        minzoom: 9,
        filter: ['==', ['get', 'class'], 'river'],
        layout: {
          'symbol-placement': 'line',
          'text-field': ['coalesce', ['get', 'name:en'], ['get', 'name']],
          'text-font': ['Noto Sans Italic'],
          'text-size': 12,
        },
        paint: { 'text-color': c('on-primary-container'), 'text-halo-color': halo, 'text-halo-width': 1 },
      },
      {
        id: 'place-names',
        type: 'symbol',
        source: 'omt',
        'source-layer': 'place',
        filter: ['match', ['get', 'class'], ['city', 'town', 'village', 'suburb'], true, false],
        layout: {
          'text-field': ['coalesce', ['get', 'name:en'], ['get', 'name']],
          'text-font': ['Noto Sans Regular'],
          'text-size': ['match', ['get', 'class'], ['city', 'town'], 14, 12],
          'symbol-sort-key': ['match', ['get', 'class'], 'city', 0, 'town', 1, 2],
        },
        paint: { 'text-color': label, 'text-halo-color': halo, 'text-halo-width': 1.2 },
      },
    ],
  };
}
