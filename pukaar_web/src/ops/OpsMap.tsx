// D2 map: MapLibre GL with the Pukaar style. SOS go into a clustered GeoJSON source (with counts per
// status); what's on screen is drawn as HTML markers so pins, halos, pulses and cluster rings follow
// the handoff exactly. Located chat messages are smaller markers.
import * as maplibregl from 'maplibre-gl';
import type { GeoJSONFeature, GeoJSONSource, MapSourceDataEvent } from 'maplibre-gl';
import type { FeatureCollection, Point } from 'geojson';
import { useEffect, useRef, type RefObject } from 'react';
import { DISTRICT_CENTER, DISTRICT_ZOOM, isUrgent, people } from '../lib/format';
import type { ChatMessage, Sos } from '../lib/types';
import { IconButton, Icon } from '../ui/atoms';
import { useTheme } from '../ui/theme';
import { pukaarMapStyle } from './mapStyle';

export interface MapControl {
  flyTo: (lat: number, lon: number, zoom?: number) => void;
  /** Remembers the current view (before opening an SOS) and goes back to it (Esc). */
  saveView: () => void;
  restoreView: () => void;
}

interface Props {
  sos: Sos[];
  matchIds: Set<string>;
  selectedId?: string;
  freshIds: Map<string, number>;
  dimIncidents: boolean;
  messages: ChatMessage[];
  showChat: boolean;
  hoveredMessageId?: string;
  onSelect: (id: string) => void;
  onMessageClick?: (id: string) => void;
  control: RefObject<MapControl | null>;
}

const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const EMPTY: FeatureCollection = { type: 'FeatureCollection', features: [] };

function toGeoJson(list: Sos[]): FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: list
      .filter((s) => s.lat != null && s.lon != null)
      .map((s) => ({
        type: 'Feature',
        id: s.id,
        geometry: { type: 'Point', coordinates: [s.lon!, s.lat!] },
        properties: { id: s.id, status: s.status, people: s.people || 1, urgent: isUrgent(s), label: `${people(s.people || 1)} · ${s.area?.village || s.area?.block || s.id}` },
      })),
  };
}

function addSosSource(map: maplibregl.Map, data: FeatureCollection) {
  if (map.getSource('sos')) return;
  map.addSource('sos', {
    type: 'geojson',
    data,
    cluster: true,
    clusterRadius: 46,
    clusterMaxZoom: 11,
    clusterProperties: {
      nNew: ['+', ['case', ['==', ['get', 'status'], 'new'], 1, 0]],
      nAttended: ['+', ['case', ['==', ['get', 'status'], 'attended'], 1, 0]],
      nResolved: ['+', ['case', ['==', ['get', 'status'], 'resolved'], 1, 0]],
    },
  });
  // Invisible layer: the source must be used by a layer for querySourceFeatures to return anything.
  map.addLayer({ id: 'sos-hit', type: 'circle', source: 'sos', paint: { 'circle-radius': 1, 'circle-opacity': 0 } });
}

export function OpsMap(props: Props) {
  const { theme } = useTheme();
  const box = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markers = useRef(new Map<string, { marker: maplibregl.Marker; el: HTMLElement }>());
  const chatMarkers = useRef(new Map<string, { marker: maplibregl.Marker; el: HTMLElement }>());
  const latest = useRef(props);
  latest.current = props;
  const savedView = useRef<{ center: maplibregl.LngLat; zoom: number } | null>(null);
  const dataRef = useRef<FeatureCollection>(EMPTY);

  // Create the map once.
  useEffect(() => {
    const map = new maplibregl.Map({
      container: box.current!,
      style: pukaarMapStyle(),
      center: DISTRICT_CENTER,
      zoom: DISTRICT_ZOOM,
      attributionControl: { compact: true },
      dragRotate: false,
      pitchWithRotate: false,
    });
    map.touchZoomRotate.disableRotation();
    mapRef.current = map;
    if (import.meta.env.DEV) (window as unknown as { __pkMap?: maplibregl.Map }).__pkMap = map;
    map.on('style.load', () => {
      addSosSource(map, dataRef.current);
      sync();
    });
    map.on('moveend', sync);
    map.on('idle', sync);
    map.on('sourcedata', (e: MapSourceDataEvent) => {
      if (e.sourceId === 'sos' && e.isSourceLoaded) sync();
    });
    props.control.current = {
      flyTo: (lat, lon, zoom = 14) => {
        const opts = { center: [lon, lat] as [number, number], zoom: Math.max(zoom, map.getZoom()) };
        if (reducedMotion()) map.jumpTo(opts);
        else map.flyTo({ ...opts, duration: 600, essential: true });
      },
      saveView: () => {
        if (!savedView.current) savedView.current = { center: map.getCenter(), zoom: map.getZoom() };
      },
      restoreView: () => {
        const v = savedView.current;
        savedView.current = null;
        if (!v) return;
        if (reducedMotion()) map.jumpTo(v);
        else map.flyTo({ ...v, duration: 600 });
      },
    };
    return () => {
      markers.current.clear();
      chatMarkers.current.clear();
      map.remove();
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Theme: rebuild the style from the new token values (markers are HTML and restyle themselves).
  const firstTheme = useRef(true);
  useEffect(() => {
    if (firstTheme.current) {
      firstTheme.current = false;
      return;
    }
    mapRef.current?.setStyle(pukaarMapStyle(), { diff: false });
  }, [theme]);

  // Data.
  useEffect(() => {
    dataRef.current = toGeoJson(props.sos);
    const src = mapRef.current?.getSource('sos') as GeoJSONSource | undefined;
    src?.setData(dataRef.current);
    sync();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [props.sos]);

  // Selection, filters, highlights: restyle the markers on screen.
  useEffect(() => {
    sync();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [props.selectedId, props.matchIds, props.freshIds, props.dimIncidents]);

  // Chat markers.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const wanted = props.showChat ? props.messages.filter((m) => m.lat != null && m.lon != null) : [];
    const keep = new Set(wanted.map((m) => m.id));
    for (const [id, m] of chatMarkers.current) {
      if (!keep.has(id)) {
        m.marker.remove();
        chatMarkers.current.delete(id);
      }
    }
    for (const msg of wanted) {
      let entry = chatMarkers.current.get(msg.id);
      if (!entry) {
        const el = document.createElement('button');
        el.type = 'button';
        el.className = 'pk-chat';
        el.textContent = 'chat_bubble';
        el.setAttribute('aria-label', `Chat from ${msg.sender}: ${msg.text}`);
        el.title = `${msg.sender}: ${msg.text}`;
        el.addEventListener('click', (e) => {
          e.stopPropagation();
          latest.current.onMessageClick?.(msg.id);
        });
        const marker = new maplibregl.Marker({ element: el }).setLngLat([msg.lon!, msg.lat!]).addTo(map);
        entry = { marker, el };
        chatMarkers.current.set(msg.id, entry);
      }
      entry.el.classList.toggle('pk-chat--hover', msg.id === props.hoveredMessageId);
    }
  }, [props.messages, props.showChat, props.hoveredMessageId]);

  /** Draws what the clustered source has on screen as HTML markers. */
  function sync() {
    const map = mapRef.current;
    // Not isStyleLoaded(): that stays false while any map tiles are still loading.
    if (!map || !map.getSource('sos')) return;
    const p = latest.current;
    const features = map.querySourceFeatures('sos');
    const seen = new Set<string>();
    for (const f of features) {
      const key = f.properties.cluster ? `c${f.properties.cluster_id}` : String(f.properties.id);
      if (seen.has(key)) continue;
      seen.add(key);
      const coords = (f.geometry as Point).coordinates as [number, number];
      let entry = markers.current.get(key);
      if (!entry) {
        const el = document.createElement('button');
        el.type = 'button';
        const marker = new maplibregl.Marker({ element: el }).setLngLat(coords).addTo(map);
        entry = { marker, el };
        markers.current.set(key, entry);
        if (f.properties.cluster) {
          const clusterId = f.properties.cluster_id as number;
          el.addEventListener('click', async (e) => {
            e.stopPropagation();
            const zoom = await (map.getSource('sos') as GeoJSONSource).getClusterExpansionZoom(clusterId);
            map.easeTo({ center: coords, zoom: zoom + 0.5, duration: reducedMotion() ? 0 : 400 });
          });
        } else {
          const id = String(f.properties.id);
          el.addEventListener('click', (e) => {
            e.stopPropagation();
            latest.current.onSelect(id);
          });
        }
      } else {
        entry.marker.setLngLat(coords);
      }
      if (f.properties.cluster) drawCluster(entry.el, f, p);
      else drawPin(entry.el, f, p);
    }
    for (const [key, m] of markers.current) {
      if (!seen.has(key)) {
        m.marker.remove();
        markers.current.delete(key);
      }
    }
  }

  return (
    <div className="relative h-full w-full">
      {/* MapLibre's CSS makes its container position: relative, so it fills an absolute wrapper. */}
      <div className="absolute inset-0">
        <div ref={box} className="h-full w-full" aria-label="Map of SOS" />
      </div>
      <Legend />
      <div className="absolute right-3 bottom-8 flex flex-col gap-1 rounded-card bg-surface-container-low p-1">
        <IconButton icon="add" label="Zoom in" onClick={() => mapRef.current?.zoomIn()} />
        <IconButton icon="remove" label="Zoom out" onClick={() => mapRef.current?.zoomOut()} />
        <IconButton
          icon="fit_screen"
          label="Show the whole district"
          onClick={() => mapRef.current?.flyTo({ center: DISTRICT_CENTER, zoom: DISTRICT_ZOOM, duration: reducedMotion() ? 0 : 600 })}
        />
      </div>
    </div>
  );
}

function drawPin(el: HTMLElement, f: GeoJSONFeature, p: Props) {
  const { id, status, people: n, urgent, label } = f.properties as { id: string; status: string; people: number; urgent: boolean; label: string };
  const size = n >= 6 ? 'l' : n >= 3 ? 'm' : 's';
  const fresh = p.freshIds.has(id);
  const cls = [
    'pk-pin',
    `pk-pin--${status}`,
    status !== 'resolved' && `pk-pin--${size}`,
    id === p.selectedId && 'pk-pin--selected',
    fresh && 'pk-pin--fresh',
    (!p.matchIds.has(id) || p.dimIncidents) && id !== p.selectedId && 'pk-pin--dim',
  ]
    .filter(Boolean)
    .join(' ');
  const signature = `${cls}|${n}|${urgent}|${label}`;
  if (el.dataset.sig === signature) return;
  el.dataset.sig = signature;
  el.className = cls;
  el.setAttribute('aria-label', `${status === 'new' ? 'New SOS' : status === 'attended' ? 'Attended SOS' : 'Resolved SOS'}, ${label}`);
  const halo = urgent && status !== 'resolved' ? '<span class="pk-pin__halo"></span>' : '';
  const rings = fresh ? '<span class="pk-pin__ring pk-pin__ring--1"></span><span class="pk-pin__ring pk-pin__ring--2"></span>' : '';
  const body = status === 'resolved' ? 'check' : String(n);
  el.innerHTML = `${rings}${halo}<span class="pk-pin__body">${body}</span><span class="pk-pin__label"></span>`;
  el.querySelector('.pk-pin__label')!.textContent = label;
  el.style.zIndex = id === p.selectedId ? '5' : status === 'new' ? '3' : status === 'attended' ? '2' : '1';
}

function drawCluster(el: HTMLElement, f: GeoJSONFeature, p: Props) {
  const { point_count: total, nNew, nAttended } = f.properties as { point_count: number; nNew: number; nAttended: number };
  const a = (nNew / total) * 100;
  const b = a + (nAttended / total) * 100;
  const size = total >= 100 ? 56 : total >= 20 ? 48 : 42;
  const signature = `${total}|${nNew}|${nAttended}|${p.dimIncidents}`;
  if (el.dataset.sig === signature) return;
  el.dataset.sig = signature;
  el.className = 'pk-cluster';
  el.style.width = el.style.height = `${size}px`;
  el.style.setProperty('--a', `${a}%`);
  el.style.setProperty('--b', `${b}%`);
  el.style.opacity = p.dimIncidents ? '0.45' : '1';
  el.style.zIndex = '4';
  el.setAttribute('aria-label', `${total} SOS: ${nNew} new, ${nAttended} attended. Click to zoom in.`);
  el.innerHTML = `<span>${total}</span>`;
}

function Legend() {
  return (
    <div className="absolute top-3 left-3 flex items-center gap-4 rounded-card bg-surface-container-low/90 px-3 py-2 text-caption text-on-surface-variant">
      <span className="flex items-center gap-1.5">
        <span className="size-3 rounded-full bg-sos-fill" />
        New
      </span>
      <span className="flex items-center gap-1.5">
        <span className="size-3 rounded-[3px] bg-primary" />
        Attended
      </span>
      <span className="flex items-center gap-1.5">
        <span className="size-3 rounded-full bg-confirmed-container" />
        Resolved
      </span>
      <span className="flex items-center gap-1.5">
        <span className="size-3 rounded-full bg-sos-fill outline-4 outline-sos-fill/30" />
        Injured or trapped
      </span>
      <span>Number = people</span>
      <span className="flex items-center gap-1">
        <Icon name="chat_bubble" size={14} />
        Chat message
      </span>
    </div>
  );
}

/** Small non-interactive map for the detail panel, zoomed in on the SOS. */
export function MiniMap({ lat, lon, status }: { lat: number; lon: number; status: Sos['status'] }) {
  const box = useRef<HTMLDivElement>(null);
  const { theme } = useTheme();
  useEffect(() => {
    const map = new maplibregl.Map({
      container: box.current!,
      style: pukaarMapStyle(),
      center: [lon, lat],
      zoom: 14.5,
      interactive: false,
      attributionControl: false,
    });
    const el = document.createElement('div');
    el.className = `pk-pin pk-pin--${status} pk-pin--s`;
    el.innerHTML = `<span class="pk-pin__body">${status === 'resolved' ? 'check' : ''}</span>`;
    new maplibregl.Marker({ element: el }).setLngLat([lon, lat]).addTo(map);
    return () => map.remove();
  }, [lat, lon, status, theme]);
  return <div ref={box} className="h-36 w-full overflow-hidden rounded-card" aria-hidden />;
}

