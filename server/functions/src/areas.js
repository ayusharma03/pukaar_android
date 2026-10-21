// Area names for the dashboard ("Block › Village"), looked up from an SOS location.
//
// The district is Darbhanga, Bihar (handoff decision). These are APPROXIMATE block centres, good
// enough to name the block for sample data and a demo. For real use, replace them with the official
// block and village boundaries (Local Government Directory / Census GIS) and do a point-in-polygon
// lookup; villages need that data, so `village` is empty until it's added.
'use strict';

const DARBHANGA_BLOCKS = [
  ['Darbhanga', 26.152, 85.897],
  ['Bahadurpur', 26.105, 85.955],
  ['Hayaghat', 26.045, 86.03],
  ['Keoti', 26.3, 85.92],
  ['Jale', 26.38, 85.8],
  ['Singhwara', 26.2, 85.75],
  ['Benipur', 26.04, 86.12],
  ['Biraul', 25.98, 86.25],
  ['Kusheshwar Asthan', 25.88, 86.27],
  ['Kusheshwar Asthan Purbi', 25.9, 86.36],
  ['Ghanshyampur', 26.07, 86.29],
  ['Manigachhi', 26.27, 86.05],
  ['Hanuman Nagar', 26.13, 86.0],
  ['Tardih', 26.24, 86.13],
  ['Baheri', 26.06, 85.98],
  ['Alinagar', 26.02, 86.05],
  ['Gaura Bauram', 25.98, 86.1],
  ['Kiratpur', 25.95, 86.2],
];

const MAX_KM = 25;

function km(lat1, lon1, lat2, lon2) {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLon = (lon2 - lon1) * rad;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(a));
}

/** { block, village } for the nearest block centre within 25 km, or null (outside the district, no fix). */
function areaFor(lat, lon, blocks = DARBHANGA_BLOCKS) {
  if (typeof lat !== 'number' || typeof lon !== 'number') return null;
  let best = null;
  for (const [block, bLat, bLon] of blocks) {
    const d = km(lat, lon, bLat, bLon);
    if (d <= MAX_KM && (!best || d < best.d)) best = { block, d };
  }
  return best ? { block: best.block, village: '' } : null;
}

module.exports = { areaFor, DARBHANGA_BLOCKS };
