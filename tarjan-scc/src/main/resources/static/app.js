'use strict';

// ------------------------------------------------------------------ stare

const state = {
  stops: [],            // /api/network/stops
  stopById: new Map(),
  edges: [],            // /api/network/edges
  routes: [],           // /api/routes
  closed: new Set(),    // rutele bifate „închisă”
  detached: new Set(),  // stațiile desprinse la ultima simulare
  live: null,           // /api/analysis/live (când toggle-ul e activ)
  bridges: null,
  chart: null,
  algorithm: 'tarjan',  // tarjan | kosaraju | gabow
  algorithms: [],       // /api/algorithms
};

const ALGO_LABELS = { tarjan: 'Tarjan', kosaraju: 'Kosaraju', gabow: 'Gabow' };
const algoLabel = (id) => ALGO_LABELS[id] || id;
/** Parametrul de URL cu algoritmul ales. */
const algoQuery = () => `algorithm=${encodeURIComponent(state.algorithm)}`;

const COLORS = {
  giant: '#64748b',
  detached: '#dc2626',
  inactive: '#cbd5e1',
  edge: '#94a3b8',
  bridge: '#f59e0b',
};
// Culori distincte pentru SCC-urile mici.
const PALETTE = ['#e11d48', '#7c3aed', '#0891b2', '#16a34a', '#ea580c', '#c026d3', '#0d9488',
  '#ca8a04', '#4f46e5', '#be123c', '#059669', '#9333ea', '#0284c7', '#65a30d', '#db2777'];

const $ = (id) => document.getElementById(id);

// ------------------------------------------------------------------- API

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(body.error || `HTTP ${res.status}`);
    err.kind = body.kind;
    throw err;
  }
  return body;
}

const fmt = (x, digits = 0) => Number(x).toLocaleString('ro-RO', {
  minimumFractionDigits: digits, maximumFractionDigits: digits,
});

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

// ------------------------------------------------------------------ harta

const map = L.map('map', { preferCanvas: true }).setView([47.0245, 28.8323], 13);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap contributors · date: Tranzy Open Data',
}).addTo(map);

const renderer = L.canvas({ padding: 0.5 });
const edgeLayer = L.layerGroup().addTo(map);
const arrowLayer = L.layerGroup();
const bridgeLayer = L.layerGroup();
const stopLayer = L.layerGroup().addTo(map);
const ARROW_MIN_ZOOM = 15;

map.on('zoomend', () => {
  if (map.getZoom() >= ARROW_MIN_ZOOM) arrowLayer.addTo(map);
  else arrowLayer.remove();
});

/** Vârful de săgeată la 60% din segmentul a → b (două linii scurte). */
function arrowHead(a, b) {
  const lat = a.lat + (b.lat - a.lat) * 0.6;
  const lon = a.lon + (b.lon - a.lon) * 0.6;
  const k = Math.cos(lat * Math.PI / 180);           // corecție longitudine
  const dx = (b.lon - a.lon) * k;
  const dy = b.lat - a.lat;
  const len = Math.hypot(dx, dy);
  if (len === 0) return null;
  const size = 0.00012;                              // ~13 m
  const ux = dx / len, uy = dy / len;
  const wing = (angle) => {
    const c = Math.cos(angle), s = Math.sin(angle);
    const wx = -(ux * c - uy * s) * size;
    const wy = -(ux * s + uy * c) * size;
    return [lat + wy, lon + wx / k];
  };
  return [wing(0.5), [lat, lon], wing(-0.5)];
}

function drawEdges() {
  edgeLayer.clearLayers();
  arrowLayer.clearLayers();
  const activeRoutes = state.live ? new Set(state.live.activeRouteIds) : null;
  for (const e of state.edges) {
    const a = state.stopById.get(e.from);
    const b = state.stopById.get(e.to);
    if (!a || !b) continue;
    const open = e.routes.filter((r) => !state.closed.has(r) && (!activeRoutes || activeRoutes.has(r)));
    const style = open.length
      ? { color: COLORS.edge, weight: 1.2, opacity: 0.7 }
      : { color: COLORS.detached, weight: 1, opacity: 0.25, dashArray: '3 4' };
    L.polyline([[a.lat, a.lon], [b.lat, b.lon]], { ...style, renderer, interactive: false }).addTo(edgeLayer);
    const head = open.length ? arrowHead(a, b) : null;
    if (head) L.polyline(head, { ...style, renderer, interactive: false }).addTo(arrowLayer);
  }
}

/** Culoarea unei stații după componenta ei (în rețeaua completă sau live). */
function stopStyle(stop) {
  if (state.detached.has(stop.id)) {
    return { fillColor: COLORS.detached, radius: 6, color: '#7f1d1d' };
  }
  let comp = stop;
  if (state.live) {
    comp = state.live.componentById.get(stop.id);
    if (!comp) return { fillColor: COLORS.inactive, radius: 3, color: '#94a3b8' };
  }
  if (comp.inLargest) return { fillColor: COLORS.giant, radius: 4, color: '#334155' };
  return { fillColor: PALETTE[comp.scc % PALETTE.length], radius: 6, color: '#111827' };
}

function drawStops() {
  stopLayer.clearLayers();
  for (const s of state.stops) {
    const st = stopStyle(s);
    const m = L.circleMarker([s.lat, s.lon], {
      renderer, weight: 1, fillOpacity: 0.9, opacity: 0.9, ...st,
    });
    m.bindPopup(() => stopPopup(s));
    m.addTo(stopLayer);
  }
}

function stopPopup(s) {
  const comp = state.live ? state.live.componentById.get(s.id) : s;
  const sccLine = comp
    ? `SCC #${comp.scc} (${fmt(comp.sccSize)} stații)${comp.inLargest ? ' — componenta mare' : ''}`
    : 'fără rute active acum';
  return `<b>${esc(s.name)}</b><br><span class="muted">id ${esc(s.id)}</span><br>${sccLine}`
    + `<br>Rute: ${esc(s.routes.join(', ') || '—')}`
    + (state.detached.has(s.id) ? '<br><b style="color:#dc2626">desprinsă după simulare</b>' : '');
}

function drawBridges() {
  bridgeLayer.clearLayers();
  if (!state.bridges) return;
  for (const b of state.bridges.bridges) {
    L.polyline([[b.fromLat, b.fromLon], [b.toLat, b.toLon]], {
      color: COLORS.bridge, weight: 4, opacity: 0.9, renderer,
    }).bindTooltip(`${esc(b.fromName)} — ${esc(b.toName)}<br>rute: ${esc(b.routes.join(', '))}`)
      .addTo(bridgeLayer);
  }
}

function redraw() {
  drawEdges();
  drawStops();
}

// ---------------------------------------------------------------- sumar

function renderSummary(s) {
  const live = state.live;
  const cur = live
    ? { stops: live.stops, edges: live.edges, scc: live.sccCount, big: live.largestSccSize, pct: live.largestSccPercent }
    : { stops: s.stops, edges: s.edges, scc: s.sccCount, big: s.largestSccSize, pct: s.largestSccPercent };
  $('summary').innerHTML = `
    ${live ? `<div><span class="badge live">live</span> ${fmt(live.vehicles)} vehicule, ${fmt(live.activeRouteIds.length)} rute active</div>` : ''}
    <div>V = <b>${fmt(cur.stops)}</b> stații · E = <b>${fmt(cur.edges)}</b> arce · ${fmt(s.activeRoutes)} rute</div>
    <div>SCC: <b>${fmt(cur.scc)}</b> · cea mai mare: <b>${fmt(cur.big)}</b> (${fmt(cur.pct, 1)}%)</div>
    <div><span class="algo-badge">${esc(s.algorithm.label)}</span>
      ${fmt(s.algorithm.millis, 3)} ms · ${fmt(s.algorithm.operations)} operații</div>
    <div class="muted">operator ${esc(s.agencyId)}</div>`;
}

// ------------------------------------------------------------ simulare

function renderRoutes() {
  const q = $('routeFilter').value.trim().toLowerCase();
  const active = state.live ? new Set(state.live.activeRouteIds) : null;
  const html = state.routes
    .filter((r) => r.edges > 0)
    .filter((r) => !q || r.label.toLowerCase().includes(q) || (r.longName || '').toLowerCase().includes(q))
    .map((r) => {
      const closed = state.closed.has(r.id);
      const inactive = active && !active.has(r.id);
      return `<label class="route-item ${closed ? 'closed' : ''} ${inactive ? 'inactive-route' : ''}">
        <input type="checkbox" data-route="${esc(r.id)}" ${closed ? 'checked' : ''}>
        <span class="route-label">${esc(r.label)}</span>
        <span class="route-name" title="${esc(r.longName)}">${esc(r.longName || '')}</span>
        <span class="badge">${esc(r.type)}</span>
        ${active && active.has(r.id) ? '<span class="badge live">activ</span>' : ''}
      </label>`;
    }).join('');
  $('routeList').innerHTML = html || '<div class="route-item muted">Nicio rută.</div>';
  $('closedCount').textContent = state.closed.size ? `${state.closed.size} închise` : '';
}

$('routeList').addEventListener('change', (ev) => {
  const id = ev.target.dataset.route;
  if (!id) return;
  if (ev.target.checked) state.closed.add(id); else state.closed.delete(id);
  renderRoutes();
});

$('routeFilter').addEventListener('input', renderRoutes);

async function simulate() {
  const btn = $('simulateBtn');
  btn.disabled = true;
  try {
    const r = await api('/api/simulate/close', {
      method: 'POST',
      body: JSON.stringify({ routeIds: [...state.closed], liveOnly: !!state.live, algorithm: state.algorithm }),
    });
    state.detached = new Set(r.detachedStops.map((s) => s.id));
    redraw();
    $('simResult').innerHTML = `
      <div class="result">
        <h3>Rute închise: ${esc(r.closedRoutes.map((x) => x.label).join(', ') || '—')}${r.liveOnly ? ' <span class="badge live">rețea live</span>' : ''}</h3>
        <div class="muted">Recalculat cu <span class="algo-badge">${esc(r.algorithm.label)}</span>
             în ${fmt(r.algorithm.millis, 3)} ms (${fmt(r.algorithm.operations)} operații)</div>
        <div><span class="big ${r.detachedCount ? '' : 'ok'}">${fmt(r.detachedCount)}</span> stații desprinse din componenta mare</div>
        <div>SCC: ${fmt(r.before.sccCount)} → <b>${fmt(r.after.sccCount)}</b> ·
             cea mai mare SCC: ${fmt(r.before.largestSize)} → <b>${fmt(r.after.largestSize)}</b>
             (${fmt(r.after.largestPercent, 1)}%)</div>
        ${r.unknownRoutes.length ? `<div class="muted">Rute necunoscute: ${esc(r.unknownRoutes.join(', '))}</div>` : ''}
        ${r.detachedStops.length ? `<ul class="stops">${r.detachedStops.map((s) =>
          `<li><button class="link" data-stop="${esc(s.id)}">${esc(s.name)}</button>
               <span class="muted">— rute: ${esc(s.routes.join(', '))}</span></li>`).join('')}</ul>` : ''}
      </div>`;
  } catch (e) {
    $('simResult').innerHTML = `<div class="result">Eroare: ${esc(e.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
}

$('simulateBtn').addEventListener('click', simulate);
$('resetBtn').addEventListener('click', () => {
  state.closed.clear();
  state.detached.clear();
  $('simResult').innerHTML = '';
  renderRoutes();
  redraw();
});

// Click pe o stație din liste → zoom pe hartă.
document.addEventListener('click', (ev) => {
  const id = ev.target.dataset && ev.target.dataset.stop;
  if (!id) return;
  const s = state.stopById.get(id);
  if (s) {
    map.setView([s.lat, s.lon], 16);
    L.popup().setLatLng([s.lat, s.lon]).setContent(stopPopup(s)).openOn(map);
  }
});

// -------------------------------------------------------- rute critice

let criticalLoaded = false;

async function loadCritical() {
  if (criticalLoaded) return;
  criticalLoaded = true;
  try {
    const rows = await api('/api/analysis/critical-routes?limit=50&' + algoQuery());
    $('criticalTable').innerHTML = `<p class="muted">Calculat cu <span class="algo-badge">${esc(algoLabel(state.algorithm))}</span></p><table>
      <thead><tr><th>#</th><th>Ruta</th><th>Tip</th><th class="num">Stații pierdute</th><th class="num">SCC după</th><th></th></tr></thead>
      <tbody>${rows.map((x) => `<tr>
        <td>${x.rank}</td>
        <td title="${esc(x.longName)}"><b>${esc(x.label)}</b></td>
        <td>${esc(x.type)}</td>
        <td class="num">${fmt(x.lostStops)}</td>
        <td class="num">${fmt(x.sccAfter)}</td>
        <td><button class="link" data-close-route="${esc(x.routeId)}">simulează</button></td>
      </tr>`).join('')}</tbody></table>`;
  } catch (e) {
    criticalLoaded = false;
    $('criticalTable').innerHTML = `Eroare: ${esc(e.message)}`;
  }
}

$('criticalTable').addEventListener('click', (ev) => {
  const id = ev.target.dataset.closeRoute;
  if (!id) return;
  state.closed = new Set([id]);
  renderRoutes();
  switchTab('simulate');
  simulate();
});

// ------------------------------------------------------------ structură

let structureLoaded = false;

async function loadStructure() {
  if (structureLoaded) return;
  structureLoaded = true;
  try {
    const [b, c] = await Promise.all([
      api('/api/analysis/bridges?' + algoQuery()), api('/api/analysis/condensation?' + algoQuery()),
    ]);
    state.bridges = b;
    drawBridges();
    $('structureInfo').innerHTML = `
      <h4>Graful condensat (DAG de SCC-uri) <span class="algo-badge">${esc(algoLabel(state.algorithm))}</span></h4>
      <table>
        <tr><td>Componente</td><td class="num">${fmt(c.componentCount)}</td></tr>
        <tr><td>Arce între componente</td><td class="num">${fmt(c.dagEdges)}</td></tr>
        <tr><td>Surse (grad intrare 0)</td><td class="num">${fmt(c.sources)}</td></tr>
        <tr><td>Destinații (grad ieșire 0)</td><td class="num">${fmt(c.sinks)}</td></tr>
        <tr><td>Componente izolate</td><td class="num">${fmt(c.isolated)}</td></tr>
        <tr><td><b>Arce minime de adăugat</b> pentru tare conexitate</td><td class="num"><b>${fmt(c.minEdgesToAdd)}</b></td></tr>
      </table>
      <h4>Punți: ${fmt(b.bridgeCount)} · puncte de articulație: ${fmt(b.articulationCount)}</h4>
      <p class="hint">Pe graful neorientat: o punte este un segment fără alternativă, un punct de articulație o stație fără ocolire.</p>
      <ul class="stops">${b.bridges.slice(0, 200).map((x) =>
        `<li><button class="link" data-stop="${esc(x.fromId)}">${esc(x.fromName)}</button> —
             <button class="link" data-stop="${esc(x.toId)}">${esc(x.toName)}</button>
             <span class="muted">${esc(x.routes.join(', '))}</span></li>`).join('')}</ul>
      <h4>Cele mai mari componente</h4>
      <table><thead><tr><th>SCC</th><th class="num">Stații</th><th class="num">In</th><th class="num">Out</th><th>Exemple</th></tr></thead>
      <tbody>${c.components.slice(0, 30).map((k) => `<tr>
        <td>#${k.id}${k.source ? ' <span class="badge">sursă</span>' : ''}${k.sink ? ' <span class="badge">dest.</span>' : ''}</td>
        <td class="num">${fmt(k.size)}</td><td class="num">${k.inDegree}</td><td class="num">${k.outDegree}</td>
        <td class="muted">${esc(k.sampleStops.join(', '))}</td></tr>`).join('')}</tbody></table>`;
  } catch (e) {
    structureLoaded = false;
    $('structureInfo').innerHTML = `Eroare: ${esc(e.message)}`;
  }
}

$('bridgesToggle').addEventListener('change', (ev) => {
  if (ev.target.checked) bridgeLayer.addTo(map); else bridgeLayer.remove();
});

// ------------------------------------------------------------ benchmark

async function runBenchmark() {
  const btn = $('benchmarkBtn');
  btn.disabled = true;
  $('bmStatus').textContent = 'Rulează… (poate dura până la un minut)';
  try {
    const body = { avgDegree: Number($('bmDegree').value), runs: Number($('bmRuns').value) };
    const run = await api('/api/benchmark', { method: 'POST', body: JSON.stringify(body) });
    const rows = run.result.rows;
    $('bmStatus').innerHTML = `Toți algoritmii au găsit aceleași componente. CSV: <code>${esc(run.csvFile)}</code>`;
    $('bmTable').innerHTML = `<table>
      <thead><tr><th>Graf</th><th class="num">V</th><th class="num">E</th><th>Algoritm</th>
        <th class="num">Timp (ms)</th><th class="num">Operații</th><th class="num">SCC</th></tr></thead>
      <tbody>${rows.map((r) => `<tr><td>${esc(r.graph)}</td><td class="num">${fmt(r.nodes)}</td>
        <td class="num">${fmt(r.edges)}</td><td>${esc(r.algorithm)}</td>
        <td class="num">${fmt(r.avgMillis, 3)}</td><td class="num">${fmt(r.avgOperations)}</td>
        <td class="num">${fmt(r.sccCount)}</td></tr>`).join('')}</tbody></table>`;
    drawChart(rows.filter((r) => r.graph.startsWith('aleator-')));
  } catch (e) {
    $('bmStatus').textContent = 'Eroare: ' + e.message;
  } finally {
    btn.disabled = false;
  }
}

function drawChart(rows) {
  const algorithms = [...new Set(rows.map((r) => r.algorithm))];
  const colors = ['#2563eb', '#16a34a', '#ea580c', '#dc2626'];
  const datasets = algorithms.map((a, i) => ({
    label: a,
    data: rows.filter((r) => r.algorithm === a).map((r) => ({ x: r.nodes, y: Math.max(r.avgMillis, 0.001) })),
    borderColor: colors[i % colors.length],
    backgroundColor: colors[i % colors.length],
    tension: 0.2,
  }));
  if (state.chart) state.chart.destroy();
  state.chart = new Chart($('bmChart'), {
    type: 'line',
    data: { datasets },
    options: {
      parsing: false,
      scales: {
        x: { type: 'logarithmic', title: { display: true, text: 'n (noduri)' } },
        y: { type: 'logarithmic', title: { display: true, text: 'timp mediu (ms)' } },
      },
      plugins: { title: { display: true, text: 'Timp vs n (scară logaritmică)' } },
    },
  });
}

$('benchmarkBtn').addEventListener('click', runBenchmark);

// ---------------------------------------------------------------- live

$('liveToggle').addEventListener('change', async (ev) => {
  state.detached.clear();
  $('simResult').innerHTML = '';
  if (!ev.target.checked) {
    state.live = null;
  } else {
    try {
      const live = await api('/api/analysis/live?' + algoQuery());
      live.componentById = new Map(live.stopComponents.map((s) => [s.id, s]));
      state.live = live;
      if (live.vehicles === 0) showBanner('Tranzy nu raportează niciun vehicul în circulație acum.', false);
    } catch (e) {
      ev.target.checked = false;
      showBanner('Rețeaua live nu poate fi încărcată: ' + e.message, true);
    }
  }
  renderRoutes();
  redraw();
  renderSummary(state.summary);
});

// ------------------------------------------------------------ algoritm

/** Componentele și analizele care depind de algoritmul ales. */
async function loadAlgorithmData() {
  const [summary, stops] = await Promise.all([
    api('/api/network/summary?' + algoQuery()), api('/api/network/stops?' + algoQuery()),
  ]);
  state.summary = summary;
  state.stops = stops;
  state.stopById = new Map(stops.map((s) => [s.id, s]));
  criticalLoaded = false;
  structureLoaded = false;
  if (state.live) {
    const live = await api('/api/analysis/live?' + algoQuery());
    live.componentById = new Map(live.stopComponents.map((s) => [s.id, s]));
    state.live = live;
  }
}

async function selectAlgorithm(id) {
  if (id === state.algorithm) return;
  state.algorithm = id;
  try { localStorage.setItem('scc-algorithm', id); } catch (_) { /* fără stocare locală */ }
  document.querySelectorAll('#algoPicker button').forEach((b) => b.classList.toggle('active', b.dataset.algo === id));
  try {
    await loadAlgorithmData();
    state.detached.clear();
    $('simResult').innerHTML = '';
    renderSummary(state.summary);
    redraw();
    const active = document.querySelector('.tabs button.active').dataset.tab;
    if (active !== 'simulate') switchTab(active);
    if (state.closed.size) await simulate();   // reface simularea cu noul algoritm
  } catch (e) {
    showBanner('Eroare la schimbarea algoritmului: ' + e.message, true);
  }
}

document.querySelectorAll('#algoPicker button').forEach((b) =>
  b.addEventListener('click', () => selectAlgorithm(b.dataset.algo)));

/** Rulează cei trei algoritmi pe rețeaua reală și compară rezultatele. */
async function compareAlgorithms() {
  const btn = $('compareBtn');
  btn.disabled = true;
  try {
    const ids = state.algorithms.length ? state.algorithms.map((a) => a.id) : Object.keys(ALGO_LABELS);
    const results = await Promise.all(ids.map((id) => api('/api/network/summary?algorithm=' + id)));
    const ref = results[0];
    $('compareTable').innerHTML = `<table>
      <thead><tr><th>Algoritm</th><th class="num">SCC</th><th class="num">SCC maximă</th>
        <th class="num">Timp (ms)</th><th class="num">Operații</th><th>Acord</th></tr></thead>
      <tbody>${results.map((r) => {
        const same = r.sccCount === ref.sccCount && r.largestSccSize === ref.largestSccSize;
        const desc = (state.algorithms.find((a) => a.id === r.algorithm.id) || {}).description || '';
        return `<tr><td title="${esc(desc)}"><b>${esc(r.algorithm.label)}</b></td>
          <td class="num">${fmt(r.sccCount)}</td><td class="num">${fmt(r.largestSccSize)}</td>
          <td class="num">${fmt(r.algorithm.millis, 3)}</td><td class="num">${fmt(r.algorithm.operations)}</td>
          <td class="${same ? 'ok' : 'bad'}">${same ? '✓' : '✗'}</td></tr>`;
      }).join('')}</tbody></table>
      <p class="hint">Timpul e al primei rulări (fără warm-up); pentru medii folosiți benchmark-ul de mai jos.</p>`;
  } catch (e) {
    $('compareTable').textContent = 'Eroare: ' + e.message;
  } finally {
    btn.disabled = false;
  }
}

$('compareBtn').addEventListener('click', compareAlgorithms);

// ---------------------------------------------------------------- tab-uri

function switchTab(name) {
  document.querySelectorAll('.tabs button').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.id === 'tab-' + name));
  if (name === 'critical') loadCritical();
  if (name === 'structure') loadStructure();
}

document.querySelectorAll('.tabs button').forEach((b) => b.addEventListener('click', () => switchTab(b.dataset.tab)));

// ---------------------------------------------------------------- încărcare

function showBanner(text, isError) {
  const el = $('banner');
  $('bannerText').textContent = text;
  el.classList.toggle('error', !!isError);
  el.classList.remove('hidden');
  map.invalidateSize();   // bannerul schimbă înălțimea hărții
}

function hideBanner() {
  $('banner').classList.add('hidden');
  map.invalidateSize();
}

$('bannerClose').addEventListener('click', hideBanner);

/** Cel mult 3 avertismente, ca bannerul să nu acopere pagina. */
function summarizeWarnings(warnings) {
  const shown = warnings.slice(0, 3);
  if (warnings.length > 3) shown.push(`… și încă ${warnings.length - 3} avertismente (vezi logul serverului).`);
  return shown.join('\n');
}

async function loadAll() {
  hideBanner();
  const status = await api('/api/status');
  if (!status.loaded) {
    $('status').textContent = 'Date neîncărcate';
    let msg = status.error || 'Datele nu sunt încărcate.';
    if (!status.agencyId && status.apiKeyConfigured) {
      try {
        const agencies = await api('/api/agencies');
        msg += '\n\nOperatori disponibili: ' + agencies.map((a) => `${a.id} = ${a.name}`).join(' · ');
      } catch (_) { /* mesajul principal e suficient */ }
    }
    showBanner(msg, true);
    return;
  }
  if (status.warnings.length) showBanner(summarizeWarnings(status.warnings), false);

  const [edges, routes, algorithms] = await Promise.all([
    api('/api/network/edges'), api('/api/routes'), api('/api/algorithms'), loadAlgorithmData(),
  ]);
  const summary = state.summary;
  const stops = state.stops;
  state.edges = edges;
  state.routes = routes;
  state.algorithms = algorithms;
  for (const a of algorithms) {
    const b = document.querySelector(`#algoPicker button[data-algo="${a.id}"]`);
    if (b) b.title = a.description;
  }

  const origins = [...new Set(status.sources.map((s) => s.origin))].join(', ');
  $('status').textContent = `Operator ${status.agencyId} · încărcat ${new Date(status.loadedAt).toLocaleString('ro-RO')} · sursă: ${origins}`;
  renderSummary(summary);
  renderRoutes();
  redraw();
  if (stops.length) {
    map.fitBounds(L.latLngBounds(stops.map((s) => [s.lat, s.lon])), { padding: [20, 20] });
  }
  const active = document.querySelector('.tabs button.active').dataset.tab;
  if (active !== 'simulate') switchTab(active);
}

$('refreshBtn').addEventListener('click', async () => {
  const btn = $('refreshBtn');
  btn.disabled = true;
  try {
    await api('/api/data/refresh', { method: 'POST' });
    state.closed.clear();
    state.detached.clear();
    await loadAll();
  } catch (e) {
    showBanner('Reîncărcarea a eșuat: ' + e.message, true);
  } finally {
    btn.disabled = false;
  }
});

try {
  const saved = localStorage.getItem('scc-algorithm');
  if (saved && ALGO_LABELS[saved]) state.algorithm = saved;
} catch (_) { /* fără stocare locală */ }
document.querySelectorAll('#algoPicker button')
  .forEach((b) => b.classList.toggle('active', b.dataset.algo === state.algorithm));

loadAll().catch((e) => showBanner('Eroare la încărcare: ' + e.message, true));
