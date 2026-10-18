/* CS2-Box box editor — UI wiring. */
(function () {
  'use strict';

  const DATA = window.CSBDATA;
  const NS = window.CSBoxEdit;
  const I18N = window.CSBI18N;
  const t = I18N.t.bind(I18N);

  const LS_STATE = 'cs2box-editor-state-v1';
  const LS_LANG = 'cs2box-editor-lang';
  const LS_VER = 'cs2box-editor-version';
  const LS_GRADE_OPEN = 'cs2box-editor-grade-open-v1';
  const LS_COLS = 'cs2box-editor-cols-v1';
  const LS_ADV = 'cs2box-editor-advanced';
  const LS_FILES = 'cs2box-editor-files-v1';
  const LS_FILEBAR = 'cs2box-editor-filebar-v1';
  const LS_THEME = 'cs2box-editor-theme';
  const LS_GAME_LISTS = 'cs2box-editor-game-lists-v1';

  /* Theme: 'light' | 'dark' stored explicitly once the user toggles; absent
   * follows prefers-color-scheme. Applied at script eval (html exists already)
   * so a dark-mode user does not get a cream flash before DOMContentLoaded. */
  function systemPrefersDark() {
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  function applyTheme() {
    let stored = null;
    try { stored = localStorage.getItem(LS_THEME); } catch (e) { /* file:// */ }
    const theme = stored === 'dark' || stored === 'light'
      ? stored
      : (systemPrefersDark() ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', theme);
    const btn = document.getElementById('btn-theme');
    if (btn) btn.textContent = theme === 'dark' ? '☀' : '☾';
  }

  function initTheme() {
    applyTheme();
    const btn = $('#btn-theme');
    if (btn) {
      btn.addEventListener('click', () => {
        const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        try { localStorage.setItem(LS_THEME, next); } catch (e) { /* file:// */ }
        applyTheme();
      });
      // title text is refreshed by bindStatics' titleMap on language switches
    }
    if (window.matchMedia) {
      try {
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
          let stored = null;
          try { stored = localStorage.getItem(LS_THEME); } catch (e) { /* file:// */ }
          if (stored !== 'dark' && stored !== 'light') applyTheme();
        });
      } catch (e) { /* older browsers: addListener unsupported */ }
    }
  }

  /** PWA: register the offline service worker — ONLY for built output
   *  (dist / GitHub Pages, where build.mjs has rewritten __VER__). The dev
   *  server serves the raw source with a literal __VER__, which would make
   *  the SW cache name static and serve stale assets after every code edit. */
  function initServiceWorker() {
    if (!('serviceWorker' in navigator)) return;
    if (location.protocol !== 'https:' && location.protocol !== 'http:') return;
    if (document.documentElement.innerHTML.includes('__VER__')) return;
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('sw.js').catch(() => { /* blocked/unsupported — editor still works */ });
    });
  }

  /** Common item-model icon presets for the advanced "custom icon" picker. */
  const ICON_PRESETS = [
    'minecraft:ender_chest',
    'minecraft:barrel',
    'minecraft:chest',
    'minecraft:trapped_chest',
    'minecraft:shulker_box',
    'minecraft:anvil',
    'minecraft:furnace',
    'minecraft:crafting_table',
    'minecraft:cauldron',
    'minecraft:bookshelf',
    'minecraft:spawner',
  ];

  let state = NS.emptyState();
  let currentTab = 'box'; // 'box' | 'prices'
  let compact = true;
  let advanced = false;
  let previewTimer = null;
  let saveTimer = null;
  let gradeOpen = [true, true, true, true, true];
  const history = { undo: [], redo: [] };
  let pasteContext = null; // { mode: 'items'|'prices', grade?: number }
  let legacyFileName = null; // file name picked in the legacy dialog (drives terminal.json inference)

  /* Multi-draft workspace: every box the user edits is a separate file entry.
   * `payload` mirrors the legacy LS_STATE shape; the entry's display name is
   * derived from payload.state.fileName (the box id itself). */
  let filesStore = null; // { files: [{id, payload, savedAt}], activeId } | null

  /* Game registry lists carried by #pack= links (/csbox editor): item and
   * entity ids feed the autocomplete, so box items can be picked in the
   * browser without copying ids/NBT in-game. Persisted to localStorage. */
  let gameLists = { items: [], entities: [] };

  applyTheme(); // immediate: avoid a cream flash before DOMContentLoaded

  const $ = (sel) => document.querySelector(sel);

  document.addEventListener('DOMContentLoaded', () => { void boot(); });

  async function boot() {
    bindStatics();
    initTheme();
    loadPrefs();
    setupHeader();
    setupDialogs();
    setupDelegation();
    setupFileInput();
    setupShortcuts();
    initTooltip();
    initFileBar();
    setupSimPanel();
    initServiceWorker();
    filesStore = loadFilesStore();
    loadGameLists();
    // #pack= (mod /csbox editor link) is decoded asynchronously; adopt the
    // previous active draft into `state` first so importPack()'s saveState()
    // flush cannot clobber it with the initial empty state.
    let packed = false;
    let worked = false;
    if (/[#&]pack=/.test(location.hash)) {
      loadState();
      packed = await loadHashPack();
    } else if (/[#&]workspace=/.test(location.hash)) {
      loadState();
      worked = await loadHashWorkspace();
    }
    if (!packed && !worked && !loadHashState() && !loadState()) loadExample(0, false);
    // Fresh browser (no drafts): adopt what is on screen (the default example
    // or a shared link) as the first file entry so the sidebar lists it.
    if (!filesStore || !filesStore.files.length) {
      saveState();
      renderFileBar();
    }
    window.addEventListener('hashchange', () => {
      if (/[#&](state|pack|workspace)=/.test(location.hash)) location.reload();
    });
    window.addEventListener('storage', (e) => {
      if (e.key && e.key !== LS_STATE && e.key !== LS_VER && e.key !== LS_LANG && e.key !== LS_ADV && e.key !== LS_FILES) return;
      loadPrefs();
      if (e.key === LS_FILES || e.key === null) {
        const reloaded = loadFilesStore();
        if (reloaded || e.key === null) filesStore = reloaded;
        renderFileBar();
      }
      if (!loadState()) renderAll();
    });
    schedulePreview(0);
  }

  /* ------------------------------ helpers ------------------------------ */

  function esc(s) {
    return String(s === undefined || s === null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /** Compact relative time for source badges / tooltips ("3m ago"). */
  function relTime(ts, now) {
    const d = Math.max(0, (now || Date.now()) - Number(ts));
    if (d < 60000) return t('time.justNow');
    const min = Math.floor(d / 60000);
    if (min < 60) return t('time.minAgo', { n: min });
    const h = Math.floor(min / 60);
    if (h < 24) return t('time.hourAgo', { n: h });
    return t('time.dayAgo', { n: Math.floor(h / 24) });
  }

  /** OS-level "reduce motion" setting: motion helpers skip their timed parts
   *  (the CSS layer also collapses durations to ~0 for safety). */
  function prefersReducedMotion() {
    return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  }

  function version() {
    return DATA.versions.find((v) => v.key === state.versionKey) || DATA.versions[0];
  }

  /** True on the dedicated price-table page (prices.html). */
  function isPricesPage() {
    return /prices\.html([?#]|$)/i.test(location.pathname);
  }

  function nameColorValue(name) {
    const m = /^#([0-9a-fA-F]{6})(\s|$)/.exec(String(name || '').trim());
    return m ? '#' + m[1].toLowerCase() : '#ff5555';
  }

  function onNameColor(color) {
    pushHistory();
    color = String(color || '').toLowerCase();
    const cur = String(state.meta.name || '');
    const hasPrefix = /^#[0-9a-fA-F]{6}(\s|$)/.test(cur.trim());
    let next;
    if (hasPrefix) {
      next = cur.replace(/^#[0-9a-fA-F]{6}(\s|$)/, color + ' ');
    } else if (cur.trim()) {
      next = color + ' ' + cur.trim();
    } else {
      next = color + ' ';
    }
    state.meta.name = next;
    const nameInput = document.querySelector('[data-f="meta.name"]');
    if (nameInput) nameInput.value = next;
    schedulePreview();
  }

  /** Effective TACZ field visibility: manual switch wins, else follows version. */
  function taczVisible() {
    return state.taczEnabled == null ? version().taczVariants : !!state.taczEnabled;
  }

  /* ------------------------------ undo / redo ------------------------------ */

  function snapshotState() {
    return JSON.parse(JSON.stringify({
      fileName: state.fileName,
      meta: state.meta,
      grades: state.grades,
      priceRows: state.priceRows,
      versionKey: state.versionKey,
      taczEnabled: state.taczEnabled,
    }));
  }

  let historyTimer = null;

  /** Debounced history snapshot: text inputs fire per keystroke, so record a
   *  snapshot only after 600 ms of quiet typing (undo granularity = one editing
   *  burst). Button-level operations call pushHistory() directly, which cancels
   *  any pending debounce. */
  function scheduleHistory() {
    if (historyTimer !== null) return;
    historyTimer = setTimeout(() => {
      historyTimer = null;
      pushHistory();
    }, 600);
  }

  function pushHistory() {
    if (historyTimer !== null) {
      clearTimeout(historyTimer);
      historyTimer = null;
    }
    const snap = snapshotState();
    const last = history.undo[history.undo.length - 1];
    if (last && JSON.stringify(last) === JSON.stringify(snap)) return;
    history.undo.push(snap);
    if (history.undo.length > 60) history.undo.shift();
    history.redo = [];
    const undoBtn = $('#btn-undo');
    const redoBtn = $('#btn-redo');
    if (undoBtn) undoBtn.disabled = false;
    if (redoBtn) redoBtn.disabled = true;
  }

  function applySnapshot(snap) {
    state = Object.assign(NS.emptyState(), snap);
    renderAll();
  }

  function undo() {
    // Skip snapshots identical to the current state (e.g. a debounced snapshot
    // taken right before an undo click) so one click always visibly steps back.
    while (history.undo.length) {
      const snap = history.undo[history.undo.length - 1];
      if (JSON.stringify(snap) === JSON.stringify(snapshotState())) {
        history.undo.pop();
        continue;
      }
      history.redo.push(snapshotState());
      applySnapshot(history.undo.pop());
      return;
    }
  }

  function redo() {
    if (!history.redo.length) return;
    history.undo.push(snapshotState());
    applySnapshot(history.redo.pop());
    schedulePreview(0);
  }

  /* ------------------------------ hover tooltip ------------------------------ */

  let tooltipEl = null;
  let tooltipTimer = null;

  function initTooltip() {
    tooltipEl = document.createElement('div');
    tooltipEl.className = 'field-tooltip';
    tooltipEl.setAttribute('role', 'tooltip');
    document.body.appendChild(tooltipEl);

    document.addEventListener('mouseover', (e) => {
      const host = e.target.closest('[data-help]');
      if (host) tooltipSchedule(host);
    });
    document.addEventListener('mousemove', (e) => {
      if (tooltipEl.classList.contains('show')) tooltipMove(e.clientX, e.clientY);
    });
    document.addEventListener('mouseout', (e) => {
      const host = e.target.closest('[data-help]');
      if (!host) return;
      const to = e.relatedTarget;
      if (to && to instanceof Node && host.contains(to)) return; // still inside host
      tooltipHide();
    });
    document.addEventListener('focusin', (e) => {
      const host = e.target.closest('[data-help]');
      if (host) tooltipShowNow(host);
    });
    document.addEventListener('focusout', (e) => {
      const host = e.target.closest('[data-help]');
      if (host) tooltipHide();
    });

    // Touch: hover does not exist, so a ~450ms press-and-hold shows the tip
    // (like long-press in native apps). Moving the finger >10px cancels
    // (scroll intent); tapping anywhere while a tip is up dismisses it.
    let touchHost = null;
    let touchX = 0;
    let touchY = 0;
    document.addEventListener('pointerdown', (e) => {
      if (e.pointerType !== 'touch') return;
      const host = e.target.closest('[data-help]');
      if (host) {
        touchHost = host;
        touchX = e.clientX;
        touchY = e.clientY;
        clearTimeout(tooltipTimer);
        tooltipTimer = setTimeout(() => tooltipShowNow(host), 450);
      } else {
        tooltipHide();
      }
    }, true);
    document.addEventListener('pointermove', (e) => {
      if (e.pointerType !== 'touch' || !touchHost) return;
      if (Math.hypot(e.clientX - touchX, e.clientY - touchY) > 10) {
        clearTimeout(tooltipTimer);
        touchHost = null;
      }
    }, true);
    document.addEventListener('pointerup', () => {
      touchHost = null; // keep a shown tooltip up; next tap elsewhere hides it
    }, true);
    document.addEventListener('pointercancel', () => {
      clearTimeout(tooltipTimer);
      touchHost = null;
      tooltipHide(); // scrolling on touch always cancels the pointer first
    }, true);
  }

  function tooltipSchedule(host) {
    clearTimeout(tooltipTimer);
    tooltipTimer = setTimeout(() => tooltipShow(host), 180);
  }

  function tooltipShowNow(host) {
    clearTimeout(tooltipTimer);
    tooltipShow(host);
  }

  function tooltipShow(host) {
    if (!tooltipEl) return;
    const key = host.dataset.help;
    const text = key ? t(key) : '';
    if (!text || text === key) { tooltipHide(); return; }
    tooltipEl.textContent = text;
    tooltipEl.classList.add('show');
    const r = host.getBoundingClientRect();
    let left = r.left + r.width / 2 - tooltipEl.offsetWidth / 2;
    left = Math.max(8, Math.min(left, window.innerWidth - tooltipEl.offsetWidth - 8));
    let top = r.top - tooltipEl.offsetHeight - 8;
    if (top < 4) top = r.bottom + 8; // flip below when no room above
    tooltipEl.style.left = left + 'px';
    tooltipEl.style.top = top + 'px';
  }

  function tooltipMove(x, y) {
    if (!tooltipEl) return;
    const left = Math.max(8, Math.min(x + 16, window.innerWidth - tooltipEl.offsetWidth - 8));
    tooltipEl.style.left = left + 'px';
    tooltipEl.style.top = (y + 18) + 'px';
  }

  function tooltipHide() {
    clearTimeout(tooltipTimer);
    if (tooltipEl) tooltipEl.classList.remove('show');
  }

  function toast(msg, isError) {
    const el = $('#toast');
    el.textContent = msg;
    el.className = 'toast show' + (isError ? ' error' : '');
    clearTimeout(el._tm);
    el._tm = setTimeout(() => { el.className = 'toast'; }, 3200);
  }

  let storageWarned = false;

  function storageWarnOnce() {
    // storage may be unavailable under file:// or quota exceeded — warn once
    // so the user knows a refresh would lose their work.
    if (!storageWarned) {
      storageWarned = true;
      toast(t('toast.saveFailed'), true);
    }
  }

  function uid() {
    return 'f' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  }

  function buildPayload() {
    return {
      versionKey: state.versionKey,
      compact,
      currentTab,
      taczEnabled: state.taczEnabled,
      state: JSON.parse(JSON.stringify({
        fileName: state.fileName,
        meta: state.meta,
        grades: state.grades,
        priceRows: state.priceRows,
      })),
    };
  }

  function loadFilesStore() {
    try {
      const raw = localStorage.getItem(LS_FILES);
      if (raw) {
        const s = JSON.parse(raw);
        if (s && Array.isArray(s.files) && s.files.length &&
            s.files.every((f) => f && f.id && f.payload && f.payload.state) &&
            s.activeId && s.files.some((f) => f.id === s.activeId)) {
          return s;
        }
      }
    } catch (e) { /* corrupted store — fall through to legacy migration */ }
    // Legacy migration: adopt the pre-workspace single draft as the first file.
    // The old key stays in place untouched as a fallback; it is never read again.
    try {
      const raw = localStorage.getItem(LS_STATE);
      if (raw) {
        const payload = JSON.parse(raw);
        if (payload && payload.state) {
          const entry = { id: uid(), payload, savedAt: Date.now() };
          return { files: [entry], activeId: entry.id };
        }
      }
    } catch (e) { /* no legacy draft */ }
    return null;
  }

  function saveFilesStore() {
    try {
      localStorage.setItem(LS_FILES, JSON.stringify(filesStore));
    } catch (e) {
      storageWarnOnce();
    }
  }

  function saveGameLists() {
    try {
      localStorage.setItem(LS_GAME_LISTS, JSON.stringify({
        v: 1,
        ts: Date.now(),
        items: gameLists.items,
        entities: gameLists.entities,
      }));
    } catch (e) {
      storageWarnOnce(); // over quota: the lists still work this session
    }
  }

  function loadGameLists() {
    try {
      const raw = localStorage.getItem(LS_GAME_LISTS);
      if (!raw) return;
      const obj = JSON.parse(raw);
      if (!obj || obj.v !== 1) return;
      gameLists = {
        items: Array.isArray(obj.items) ? obj.items.filter((s) => typeof s === 'string') : [],
        entities: Array.isArray(obj.entities) ? obj.entities.filter((s) => typeof s === 'string') : [],
      };
      syncGameRegistry();
    } catch (e) { /* corrupt: keep the empty default */ }
  }

  /** Mirrors the registry lists into NS.gameRegistry for the validator's
   *  unknown-id cross-checks (pack import and plain reload both land here). */
  function syncGameRegistry() {
    const lists = gameLists || { items: [], entities: [] };
    NS.gameRegistry = {
      items: new Set(lists.items || []),
      entities: new Set(lists.entities || []),
    };
  }

  function activeFileEntry() {
    if (!filesStore) return null;
    return filesStore.files.find((f) => f.id === filesStore.activeId) || null;
  }

  function applyPayload(payload) {
    state = Object.assign(NS.emptyState(), {
      fileName: payload.state.fileName || 'my_box',
      meta: Object.assign(NS.emptyMeta(), payload.state.meta),
      grades: normalizeGrades(payload.state.grades),
      priceRows: payload.state.priceRows || {},
    });
    state.versionKey = payload.versionKey || state.versionKey || '1.21.1';
    state.taczEnabled = payload.taczEnabled == null ? null : !!payload.taczEnabled;
    compact = payload.compact !== false;
    currentTab = isPricesPage() ? 'prices' : 'box';
    renderAll();
  }

  function saveState() {
    try {
      const payload = buildPayload();
      let entry = activeFileEntry();
      if (!entry) {
        entry = { id: uid(), payload, savedAt: 0 };
        if (!filesStore) filesStore = { files: [], activeId: null };
        filesStore.files.unshift(entry);
        filesStore.activeId = entry.id;
      } else {
        // Preserve pack provenance (payload.slot) across edits: buildPayload()
        // writes a fresh legacy-shaped payload and would otherwise forget the
        // dedup marker that makes #pack link re-imports idempotent.
        if (entry.payload && entry.payload.slot) payload.slot = entry.payload.slot;
        entry.payload = payload;
      }
      entry.savedAt = Date.now();
      saveFilesStore();
      syncFileBarLabels();
      storageWarned = false;
    } catch (e) {
      storageWarnOnce();
    }
  }

  function loadState() {
    const entry = activeFileEntry();
    if (!entry || !entry.payload || !entry.payload.state) return false;
    try {
      applyPayload(entry.payload);
      return true;
    } catch (e) {
      return false;
    }
  }

  function normalizeGrades(g) {
    const out = [[], [], [], [], []];
    if (Array.isArray(g)) {
      for (let i = 0; i < 5; i++) {
        const items = Array.isArray(g[i]) ? g[i] : [];
        out[i] = items.map((it) => Object.assign(NS.emptyItem(), it));
      }
    }
    return out;
  }

  function loadPrefs() {
    try {
      const rawOpen = localStorage.getItem(LS_GRADE_OPEN);
      if (rawOpen) {
        const arr = JSON.parse(rawOpen);
        if (Array.isArray(arr)) gradeOpen = [0, 1, 2, 3, 4].map((i) => arr[i] !== false);
      }
    } catch (e) { /* keep defaults */ }
    const lang = localStorage.getItem(LS_LANG);
    if (lang === 'en' || lang === 'zh') {
      I18N.setLang(lang);
    } else {
      I18N.setLang(navigator.language && navigator.language.toLowerCase().startsWith('zh') ? 'zh' : 'en');
    }
    let ver = localStorage.getItem(LS_VER);
    if (!DATA.versions.some((v) => v.key === ver)) ver = '1.21.1';
    state.versionKey = ver;
    advanced = localStorage.getItem(LS_ADV) === '1';
    const view = localStorage.getItem(LS_VIEW);
    if (view === 'prob' || view === 'json' || view === 'sim') previewView = view;
  }

  /* ------------------------------ file workspace ------------------------------ */

  function resetHistory() {
    if (historyTimer !== null) {
      clearTimeout(historyTimer);
      historyTimer = null;
    }
    history.undo.length = 0;
    history.redo.length = 0;
    const undoBtn = $('#btn-undo');
    const redoBtn = $('#btn-redo');
    if (undoBtn) undoBtn.disabled = true;
    if (redoBtn) redoBtn.disabled = true;
  }

  function defaultPayload() {
    const s = NS.emptyState();
    s.versionKey = state.versionKey || '1.21.1';
    return {
      versionKey: s.versionKey,
      compact,
      currentTab: 'box',
      taczEnabled: state.taczEnabled,
      state: JSON.parse(JSON.stringify({
        fileName: s.fileName,
        meta: s.meta,
        grades: s.grades,
        priceRows: {},
      })),
    };
  }

  function createFile(basePayload, toastKey) {
    saveState(); // flush the current draft before moving away
    if (!filesStore) filesStore = { files: [], activeId: null };
    const entry = { id: uid(), payload: basePayload || defaultPayload(), savedAt: Date.now() };
    filesStore.files.unshift(entry);
    filesStore.activeId = entry.id;
    saveFilesStore();
    resetHistory();
    loadState();
    renderFileBar();
    toast(t(toastKey || 'toast.fileCreated'));
  }

  function duplicateFile(id) {
    const src = filesStore.files.find((f) => f.id === id);
    if (!src) return;
    const payload = JSON.parse(JSON.stringify(src.payload));
    const name = ((payload.state && payload.state.fileName) || 'my_box') + '_copy';
    payload.state.fileName = name;
    createFile(payload, 'toast.fileDuplicated');
  }

  function switchFile(id) {
    if (!filesStore || id === filesStore.activeId) return;
    const entry = filesStore.files.find((f) => f.id === id);
    if (!entry) return;
    saveState(); // flush the current draft before moving away
    filesStore.activeId = id;
    saveFilesStore();
    resetHistory();
    loadState();
    renderFileBar();
  }

  function deleteFile(id) {
    if (!filesStore) return;
    const idx = filesStore.files.findIndex((f) => f.id === id);
    if (idx < 0) return;
    const wasActive = filesStore.activeId === id;
    filesStore.files.splice(idx, 1);
    if (wasActive) {
      if (!filesStore.files.length) {
        const entry = { id: uid(), payload: defaultPayload(), savedAt: Date.now() };
        filesStore.files.push(entry);
        filesStore.activeId = entry.id;
      } else {
        filesStore.activeId = filesStore.files[Math.max(0, idx - 1)].id;
      }
      resetHistory();
      loadState();
    }
    saveFilesStore();
    renderFileBar();
    toast(t('toast.fileDeleted'));
  }

  function renameFile(id, newName) {
    const name = String(newName || '').trim();
    if (!name) return;
    const entry = filesStore.files.find((f) => f.id === id);
    if (!entry) return;
    if (id === filesStore.activeId) {
      pushHistory(); // renaming the open draft is undoable like any field edit
      state.fileName = name;
      renderAll();
      schedulePreview();
      toast(t('toast.fileRenamed', { name }));
    } else {
      entry.payload.state.fileName = name;
      entry.savedAt = Date.now();
      saveFilesStore();
      renderFileBar();
      toast(t('toast.fileRenamed', { name }));
    }
  }

  /** Cheap label refresh while typing in the file-name field (no structural
   *  re-render — would eat clicks on the hover actions). */
  function syncFileBarLabels() {
    if (!filesStore) return;
    const el = document.querySelector('.file-item.active .file-name');
    if (el && el.textContent !== state.fileName) el.textContent = state.fileName;
  }

  function renderFileBar() {
    const list = $('#file-list');
    if (!list) return;
    if (!filesStore || !filesStore.files.length) {
      list.innerHTML = '<div class="file-empty">' + esc(t('files.empty')) + '</div>';
      return;
    }
    const verName = (k) => {
      const v = DATA.versions.find((x) => x.key === k);
      if (!v) return k || '';
      return (v.label && (v.label[I18N.lang] || v.label.zh)) || v.key;
    };
    list.innerHTML = filesStore.files.map((f) => {
      const name = (f.payload && f.payload.state && f.payload.state.fileName) || 'my_box';
      const active = f.id === filesStore.activeId;
      const slot = f.payload && f.payload.slot;
      let tag = '';
      let tip = '';
      if (slot && slot.name && (slot.mc || slot.ts)) {
        // Pack provenance badge: shows the game version the link carried and
        // (when built) how long ago the pack was imported.
        const src = slot.mc ? esc(verName(slot.mc)) : '';
        const ago = slot.ts ? relTime(slot.ts, Date.now()) : '';
        tag = '<span class="file-tag">' + (src || 'pack') + '</span>';
        tip = t('files.slotTitle', { name: slot.name }) + (ago ? ' · ' + ago : '');
      }
      return '<div class="file-item' + (active ? ' active' : '') + '" data-file-id="' + esc(f.id) + '" title="' + esc(tip || name) + '">' +
        '<span class="file-name">' + esc(name) + '</span>' + tag +
        '<span class="file-actions">' +
        '<button class="file-btn" data-file-action="rename" title="' + esc(t('files.rename')) + '">✎</button>' +
        '<button class="file-btn" data-file-action="dup" title="' + esc(t('files.dup')) + '">⧉</button>' +
        '<button class="file-btn" data-file-action="del" title="' + esc(t('files.del')) + '">✕</button>' +
        '</span></div>';
    }).join('');
  }

  function initFileBar() {
    const bar = $('#filebar');
    if (!bar) return;
    // default: collapsed on narrow screens unless the user chose otherwise
    let collapsed = null;
    try {
      const pref = localStorage.getItem(LS_FILEBAR);
      if (pref === '1' || pref === '0') collapsed = pref === '1';
    } catch (e) { /* file:// */ }
    if (collapsed === null) collapsed = window.innerWidth < 1080;
    bar.classList.toggle('collapsed', collapsed);
    const toggle = $('#filebar-toggle');
    if (toggle) {
      toggle.addEventListener('click', () => {
        const nowCollapsed = !bar.classList.contains('collapsed');
        bar.classList.toggle('collapsed', nowCollapsed);
        try { localStorage.setItem(LS_FILEBAR, nowCollapsed ? '1' : '0'); } catch (e) { /* file:// */ }
      });
    }
    const add = $('#file-add');
    if (add) add.addEventListener('click', () => createFile());
    const list = $('#file-list');
    list.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-file-action]');
      if (btn) {
        const item = btn.closest('.file-item');
        if (!item) return;
        const id = item.dataset.fileId;
        const act = btn.dataset.fileAction;
        if (act === 'del') openFileDialog('delete', id);
        else if (act === 'rename') openFileDialog('rename', id);
        else if (act === 'dup') duplicateFile(id);
        return;
      }
      const item = e.target.closest('.file-item');
      if (item) switchFile(item.dataset.fileId);
    });
  }

  /* Generic small dialog for file rename / delete confirmation. */
  let fileDialogMode = null; // 'rename' | 'delete'
  let fileDialogId = null;

  /* Pack fix flow (broken JSON from a #pack= link): each unparseable file is
   * opened pre-filled in the import dialog in turn; while packImportName is
   * set, a successful 'box' import lands in a NEW workspace draft instead of
   * overwriting the active one. Reset by opening the dialog manually. */
  let packImportName = null;
  const packBrokenQueue = [];

  function openImportPrefilled(fileName, text) {
    packImportName = fileName;
    document.getElementById('import-textarea').value = text;
    document.getElementById('import-dialog').showModal();
  }

  function advancePackBroken() {
    document.getElementById('import-textarea').value = '';
    const next = packBrokenQueue.shift();
    if (next) openImportPrefilled(next.name, next.text);
    else packImportName = null;
  }

  function openFileDialog(mode, id) {
    const dlg = $('#file-dialog');
    if (!dlg || !filesStore) return;
    const entry = filesStore.files.find((f) => f.id === id);
    if (!entry) return;
    const name = (entry.payload && entry.payload.state && entry.payload.state.fileName) || 'my_box';
    fileDialogMode = mode;
    fileDialogId = id;
    $('#file-dialog-title').textContent = t(mode === 'rename' ? 'files.renameTitle' : 'files.deleteTitle');
    const hint = $('#file-dialog-hint');
    if (mode === 'rename') {
      hint.textContent = t('files.renameHint');
      $('#file-dialog-input').value = name;
      $('#file-dialog-input').hidden = false;
      $('#file-dialog-do').textContent = t('files.renameDo');
    } else {
      hint.textContent = t('files.deleteHint', { name });
      $('#file-dialog-input').hidden = true;
      $('#file-dialog-do').textContent = t('files.deleteDo');
    }
    $('#file-dialog-do').classList.toggle('danger', mode === 'delete');
    dlg.showModal();
    if (mode === 'rename') $('#file-dialog-input').focus();
  }

  /* ------------------------------ static text ------------------------------ */

  function bindStatics() {
    const map = {
      'app-title': 'app.title',
      'app-subtitle': 'app.subtitle',
      'btn-new': 'app.new',
      'btn-import': 'app.import',
      'btn-import-legacy': 'app.importLegacy',
      'btn-example': 'app.example',
      'app-foot': 'app.foot',
      'import-title': 'import.title',
      'import-do': 'import.do',
      'import-cancel': 'import.cancel',
      'legacy-title': 'legacy.title',
      'legacy-hint': 'legacy.hint',
      'legacy-file-btn': 'legacy.fileBtn',
      'legacy-do': 'legacy.do',
      'legacy-cancel': 'legacy.cancel',
      'example-title': 'example.title',
      'example-close': 'example.close',
      'lang-label': 'app.lang',
      'version-label': 'app.version',
      'compact-label': 'preview.compact',
      'btn-undo': 'app.undo',
      'btn-redo': 'app.redo',
      'btn-share': 'app.share',
      'btn-share-ws': 'app.shareWs',
      'btn-apply-game': 'app.applyGame',
      'btn-read-game': 'app.readGame',
      'advanced-label': 'app.advanced',
      'tutorial-close': 'tutorial.close',
      'paste-title': 'prices.pasteTitle',
      'paste-hint': 'prices.pastePh',
      'crate-import-title': 'prices.crateTitle',
      'crate-import-hint': 'prices.crateHint',
      'crate-import-pick': 'prices.crateFiles',
      'crate-import-do': 'import.do',
      'crate-import-cancel': 'import.cancel',
      'btn-copy': 'preview.copy',
      'btn-download': 'preview.download',
      'btn-download-all': 'preview.downloadAll',
      'nav-box': 'app.navBox',
      'nav-prices': 'app.navPrices',
      'view-json': 'view.json',
      'view-prob': 'view.prob',
      'view-sim': 'view.sim',
      'filebar-title': 'files.title',
      'file-dialog-title': 'files.title',
      'file-dialog-cancel': 'files.cancel',
      'swap-dialog-title': 'swap.title',
    };
    for (const id of Object.keys(map)) {
      const el = document.getElementById(id);
      if (el) el.textContent = t(map[id]);
    }
    // title attributes cannot ride the textContent map above
    const titleMap = {
      'file-add': 'files.add',
      'filebar-toggle': 'files.title',
      'btn-theme': 'app.theme',
      'btn-apply-game': 'app.applyGameTitle',
      'btn-read-game': 'app.readGameTitle',
      'btn-share-ws': 'app.shareWsTitle',
    };
    for (const id of Object.keys(titleMap)) {
      const el = document.getElementById(id);
      if (el) el.title = t(titleMap[id]);
    }
    // aria-labels cannot ride the textContent map above
    const ariaMap = { 'view-switch': 'view.switch' };
    for (const id of Object.keys(ariaMap)) {
      const el = document.getElementById(id);
      if (el) el.setAttribute('aria-label', t(ariaMap[id]));
    }
    document.title = isPricesPage() ? t('app.titlePrices') : t('app.title');
    const importPh = document.getElementById('import-textarea');
    if (importPh) importPh.placeholder = t('import.placeholder');
    const extraKeyInput = document.getElementById('extra-key-input');
    if (extraKeyInput) extraKeyInput.placeholder = t('prices.addKeyPh');
    // Chromium-only File System Access features: hide the buttons where
    // unsupported (_apply_ needs readwrite, _read_ only read — same API).
    for (const id of ['btn-apply-game', 'btn-read-game']) {
      const el = document.getElementById(id);
      if (el && typeof window.showDirectoryPicker !== 'function') {
        el.hidden = true;
      }
    }
  }

  /* ------------------------------ header / dialogs ------------------------------ */

  /** Fills the version <select> and syncs its value; shared by setupHeader
   *  and renderAll (the header is rebuilt after language switches). */
  function fillVersionSelect(sel) {
    if (!sel) return;
    sel.innerHTML = DATA.versions.map((v) =>
      '<option value="' + esc(v.key) + '">' + esc(v.label[I18N.lang]) + '</option>').join('');
    sel.value = state.versionKey;
  }

  function setupHeader() {
    const langSel = $('#lang-select');
    langSel.value = I18N.lang;
    langSel.addEventListener('change', () => {
      I18N.setLang(langSel.value);
      localStorage.setItem(LS_LANG, I18N.lang);
      lastIssuesKey = ''; // unchanged issues would skip renderIssues, leaving the old language
      renderAll();
    });

    const verSel = $('#version-select');
    fillVersionSelect(verSel);
    verSel.addEventListener('change', () => {
      pushHistory();
      state.versionKey = verSel.value;
      localStorage.setItem(LS_VER, state.versionKey);
      renderAll();
      const v = version();
      toast(t('version.note') + ': ' + v.note[I18N.lang]);
    });

    $('#btn-new').addEventListener('click', () => {
      createFile();
    });

    $('#btn-import').addEventListener('click', () => {
      packImportName = null; // plain import overwrites the active draft, as before
      document.getElementById('import-dialog').showModal();
    });

    const btnImportLegacy = $('#btn-import-legacy');
    if (btnImportLegacy) {
      btnImportLegacy.addEventListener('click', () => {
        legacyFileName = null; // each open starts fresh; only "Choose file" sets a name
        document.getElementById('legacy-dialog').showModal();
      });
    }

    const viewJsonTab = $('#view-json');
    const viewProbTab = $('#view-prob');
    const viewSimTab = $('#view-sim');
    if (viewJsonTab && viewProbTab) {
      setPreviewView(previewView); // apply remembered/inline view state + labels
      probAnimateNext = false; // no entrance stagger on page load, only on user switch
      viewJsonTab.addEventListener('click', () => {
        setPreviewView('json');
        rebuildPreview();
      });
      viewProbTab.addEventListener('click', () => {
        setPreviewView('prob');
        rebuildPreview();
      });
      if (viewSimTab) {
        viewSimTab.addEventListener('click', () => {
          setPreviewView('sim');
          rebuildPreview();
        });
      }
    }

    $('#btn-example').addEventListener('click', () => {
      renderExampleDialog();
      document.getElementById('example-dialog').showModal();
    });

    const advToggle = $('#advanced-toggle');
    if (advToggle) {
      advToggle.checked = advanced;
      advToggle.addEventListener('change', () => {
        advanced = advToggle.checked;
        try { localStorage.setItem(LS_ADV, advanced ? '1' : '0'); } catch (e) { /* file:// */ }
        renderAll();
      });
    }
  }

  function setupDialogs() {
    const imp = $('#import-dialog');
    $('#import-do').addEventListener('click', () => {
      const text = document.getElementById('import-textarea').value;
      if (!text.trim()) { imp.close(); return; }
      const name = packImportName;
      const imported = applyImportText(text, name);
      if (!imported) return;
      if (name && imported === 'box') {
        // Put the fixed file in its own draft instead of clobbering the
        // active one. applyImportText only mutated the global state; the
        // active entry's payload is still the pre-import draft.
        const payload = buildPayload();
        // Pack provenance (same shape as importPack): makes re-opening the
        // link treat this name as handled (openPackBrokenFlow skips it).
        try { payload.slot = { name, sig: fnv1a(JSON.stringify(JSON.parse(text))), ts: Date.now() }; } catch (e) { /* slotless fallback */ }
        if (!filesStore) filesStore = { files: [], activeId: null };
        const entry = { id: uid(), payload, savedAt: Date.now() };
        filesStore.files.unshift(entry);
        filesStore.activeId = entry.id;
        saveFilesStore();
        resetHistory();
        loadState();
        renderFileBar();
      }
      document.getElementById('import-textarea').value = '';
      imp.close();
    });
    $('#import-cancel').addEventListener('click', () => imp.close());
    imp.addEventListener('close', () => {
      if (packBrokenQueue.length) advancePackBroken();
      else packImportName = null;
    });

    const fdg = $('#file-dialog');
    if (fdg) {
      $('#file-dialog-cancel').addEventListener('click', () => fdg.close());
      $('#file-dialog-do').addEventListener('click', () => {
        const mode = fileDialogMode;
        const id = fileDialogId;
        fdg.close();
        if (mode === 'rename') renameFile(id, $('#file-dialog-input').value);
        else if (mode === 'delete') deleteFile(id);
        fileDialogMode = null;
        fileDialogId = null;
      });
      $('#file-dialog-input').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          $('#file-dialog-do').click();
        }
      });
    }

    const ldg = $('#legacy-dialog');
    if (ldg) {
      $('#legacy-cancel').addEventListener('click', () => ldg.close());
      $('#legacy-file-btn').addEventListener('click', () => {
        document.getElementById('legacy-file-input').click();
      });
      $('#legacy-do').addEventListener('click', () => {
        const text = document.getElementById('legacy-textarea').value;
        if (text.trim()) {
          applyLegacyImportText(text, legacyFileName);
          document.getElementById('legacy-textarea').value = '';
        }
        legacyFileName = null;
        ldg.close();
      });
    }

    const exd = $('#example-dialog');
    $('#example-close').addEventListener('click', () => exd.close());

    const sdg = $('#swap-dialog');
    if (sdg) {
      $('#swap-skip').addEventListener('click', () => setSwapDecision('skip'));
      $('#swap-keep').addEventListener('click', () => setSwapDecision('keep'));
      $('#swap-overwrite').addEventListener('click', () => setSwapDecision('overwrite'));
      // Escape / outside click = skip (default decision preset per file).
      sdg.addEventListener('close', () => {
        if (swapCurrent) {
          swapDecisions[swapCurrent.name] = swapDecision || 'skip';
          swapCurrent = null;
        }
        drivePackSwap();
      });
    }

    const td = $('#tutorial-dialog');
    if (td) $('#tutorial-close').addEventListener('click', () => td.close());

    const pad = $('#paste-dialog');
    $('#paste-cancel').addEventListener('click', () => pad.close());
    $('#paste-do').addEventListener('click', () => {
      const text = document.getElementById('paste-textarea').value;
      applyPasteText(text);
      document.getElementById('paste-textarea').value = '';
      pad.close();
    });

    // prices-page only: multi-crate JSON import (dialog absent on index.html)
    const cid = $('#crate-import-dialog');
    if (cid) {
      $('#crate-import-cancel').addEventListener('click', () => cid.close());
      $('#crate-import-pick').addEventListener('click', () => $('#crate-import-files').click());
      $('#crate-import-files').addEventListener('change', async (ev) => {
        const files = [...((ev.target && ev.target.files) || [])];
        if (!files.length) return;
        const texts = await Promise.all(files.map((f) => f.text()));
        const ta = document.getElementById('crate-import-textarea');
        ta.value = (ta.value.trim() ? ta.value.trimEnd() + '\n\n' : '') + texts.join('\n\n');
        ev.target.value = ''; // allow re-picking the same file later
        toast(t('toast.crateFilesLoaded', { n: files.length }));
      });
      $('#crate-import-do').addEventListener('click', () => {
        const text = document.getElementById('crate-import-textarea').value;
        applyCrateImport(text);
        document.getElementById('crate-import-textarea').value = '';
        cid.close();
      });
    }
  }

  function renderExampleDialog() {
    const list = $('#example-list');
    let html = DATA.examples.map((ex, i) => {
      return '<li class="example-item">' +
        '<div class="example-desc">' +
        '<strong>' + esc(ex.desc[I18N.lang]) + '</strong>' +
        '<code>' + esc(ex.file) + '</code>' +
        '</div>' +
        '<button class="btn small" data-action="load-example" data-index="' + i + '">' + esc(t('example.load')) + '</button>' +
        '</li>';
    }).join('');
    const packs = DATA.compatPacks || [];
    if (packs.length) {
      html += '<li class="example-group">' + esc(t('example.compatTitle')) + '</li>';
      html += packs.map((p, i) => {
        return '<li class="example-item">' +
          '<div class="example-desc">' +
          '<strong>' + esc(p.name[I18N.lang]) + '</strong>' +
          '<code>' + esc(Object.keys(p.files).join(' · ')) + '</code>' +
          '</div>' +
          '<button class="btn small" data-action="load-compat" data-index="' + i + '">' + esc(t('example.compatLoad')) + '</button>' +
          '</li>';
      }).join('');
    }
    list.innerHTML = html; // esc-exempt: every interpolated value above is esc()d
  }

  /* ------------------------------ paste / share ------------------------------ */

  /** UTF-8 → URL-safe base64 without the deprecated escape/unescape pair. */
  function utf8Base64UrlEncode(text) {
    const bytes = new TextEncoder().encode(text);
    let bin = '';
    const CHUNK = 0x8000;
    for (let i = 0; i < bytes.length; i += CHUNK) {
      bin += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
    }
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  /** URL-safe base64 → UTF-8 string. */
  function base64UrlDecodeToUtf8(s) {
    const b64 = s.replace(/-/g, '+').replace(/_/g, '/');
    const bin = atob(b64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return new TextDecoder().decode(bytes);
  }

  function openPasteDialog(ctx) {
    const pd = $('#paste-dialog');
    if (!pd) return;
    $('#paste-title').textContent = ctx.mode === 'prices' ? t('prices.pasteTitle') : t('item.pasteTitle');
    $('#paste-hint').textContent = ctx.mode === 'prices' ? t('prices.pastePh') : t('item.pastePh');
    document.getElementById('paste-textarea').value = '';
    pasteContext = ctx;
    pd.showModal();
  }

  /** Finds complete JSON objects inside a text blob (e.g. a full chat block
   *  copied from /csbox nbt hand, with its header/button decoration lines).
   *  Braces are matched across lines while ignoring braces inside quoted
   *  strings (so SNBT inside a "tag" value does not confuse the scanner);
   *  only fragments that actually parse as plain objects are returned. */
  function extractHandJsonObjects(text) {
    const out = [];
    let i = 0;
    const n = text.length;
    while (i < n) {
      const start = text.indexOf('{', i);
      if (start < 0) break;
      let depth = 0, inStr = false, esc = false, end = -1;
      for (let j = start; j < n; j++) {
        const ch = text[j];
        if (inStr) {
          if (esc) esc = false;
          else if (ch === '\\') esc = true;
          else if (ch === '"') inStr = false;
          continue;
        }
        if (ch === '"') inStr = true;
        else if (ch === '{') depth++;
        else if (ch === '}') {
          depth--;
          if (depth === 0) { end = j; break; }
        }
      }
      if (end < 0) break; // unclosed braces -> truncated output, stop
      const frag = text.slice(start, end + 1);
      try {
        const v = JSON.parse(frag);
        if (v && typeof v === 'object' && !Array.isArray(v)) out.push(v);
      } catch (e) { /* non-JSON braces (e.g. an SNBT fragment) — skip */ }
      i = end + 1;
    }
    return out;
  }

  /** True when the pasted text looks like a truncated /csbox nbt hand chat
   *  output (the chat prints a preview cut at MAX_NBT_CHARS; only the chat
   *  copy button holds the full JSON). */
  function looksTruncatedHandJson(text) {
    return /(truncated|截断|过长)/i.test(text);
  }

  function parsePasteItems(text) {
    const out = [];
    const trimmed = text.trim();
    if (!trimmed) return out;
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        for (const o of parsed) {
          out.push(o && typeof o === 'object' ? NS.parseItem(o, []) : null);
        }
      } else if (parsed && typeof parsed === 'object') {
        out.push(NS.parseItem(parsed, []));
      } else {
        out.push(null);
      }
      return out;
    } catch (e) { /* not whole-JSON -> try chat output, then line mode */ }
    // /csbox nbt hand chat output: header / copy-button decoration lines
    // around one or more JSON objects — ignore decoration, parse every object.
    const objects = extractHandJsonObjects(trimmed);
    if (objects.length) {
      for (const o of objects) out.push(NS.parseItem(o, []));
      return out;
    }
    for (const line of trimmed.split(/\r?\n/)) {
      const s = line.trim();
      if (!s) continue;
      out.push(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(s) ? NS.parseItem({ id: s }, []) : null);
    }
    return out;
  }

  function parsePastePrices(text) {
    const ok = [];
    let bad = 0;
    const trimmed = text.trim();
    if (!trimmed) return { ok, bad };
    try {
      const parsed = JSON.parse(trimmed);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        for (const k of Object.keys(parsed)) {
          const v = parsed[k];
          const p = NS.parsePriceInput(v);
          if (/^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(k) && p) ok.push({ key: k, price: p.min === p.max ? String(p.min) : p.min + '-' + p.max });
          else bad++;
        }
        return { ok, bad };
      }
    } catch (e) { /* line mode */ }
    for (const line of trimmed.split(/\r?\n/)) {
      const s = line.trim();
      if (!s) continue;
      const m = /^([a-z0-9_.-]+:[a-z0-9_./-]+(?:#[^ ]+)?)[\t ,:]+(\S+)$/.exec(s);
      if (m) {
        const p = NS.parsePriceInput(m[2]);
        if (p) ok.push({ key: m[1], price: p.min === p.max ? String(p.min) : p.min + '-' + p.max });
        else bad++;
      } else bad++;
    }
    return { ok, bad };
  }

  function applyPasteText(text) {
    if (!pasteContext || !text.trim()) { pasteContext = null; return; }
    if (pasteContext.mode === 'items') {
      const parsed = parsePasteItems(text);
      const bad = parsed.filter((x) => x === null).length;
      const items = parsed.filter((x) => x !== null);
      if (items.length) {
        pushHistory();
        for (const it of items) state.grades[pasteContext.grade].push(it);
        rebuildGrade(pasteContext.grade);
        schedulePreview();
        toast(t('toast.pastedItems', { n: items.length }));
      }
      if (bad) {
        if (looksTruncatedHandJson(text)) toast(t('toast.handTruncated'), true);
        else toast(t('toast.pasteBad', { n: bad }), true);
      }
    } else if (pasteContext.mode === 'prices') {
      const res = parsePastePrices(text);
      if (res.ok.length) {
        pushHistory();
        for (const r of res.ok) state.priceRows[r.key] = { price: String(r.price), pinned: true };
        rebuildPrices();
        schedulePreview();
        toast(t('toast.pastedPrices', { n: res.ok.length }));
      }
      if (res.bad) toast(t('toast.pasteBad', { n: res.bad }), true);
    }
    pasteContext = null;
  }

  function openCrateImportDialog() {
    const cid = $('#crate-import-dialog');
    if (!cid) return;
    document.getElementById('crate-import-textarea').value = '';
    cid.showModal();
  }

  /** Multi-crate import: parse every JSON document in the blob, auto-detect
   *  each as a box (grades) or a price table, and merge both into the price
   *  table. Inline legacy prices fill the table (existing prices win,
   *  duplicates averaged); unpriced item keys land as empty rows to fill. */
  function applyCrateImport(text) {
    const trimmed = text.trim();
    if (!trimmed) return;
    const { docs, bad } = NS.parseJsonDocuments(trimmed);
    const entries = [];
    const priceObjs = [];
    let boxes = 0;
    for (const doc of docs) {
      const kind = detectType(doc);
      if (kind === 'box') {
        boxes++;
        const res = NS.collectCratePriceEntries(doc);
        for (const e of res.keys) entries.push(e);
      } else if (kind === 'prices') {
        priceObjs.push(doc);
      }
    }
    if (!boxes && !priceObjs.length) {
      toast(t('toast.crateImportNone'), true);
      return;
    }
    pushHistory();
    let priceRowsMerged = 0;
    for (const po of priceObjs) priceRowsMerged += NS.mergePrices(state, po);
    const merged = NS.mergeCratePriceEntries(state, entries);
    rebuildPrices();
    schedulePreview();
    const parts = [];
    if (boxes) parts.push(t('toast.crateImportedBoxes', { n: boxes }));
    if (merged.priced) parts.push(t('toast.crateImportedPriced', { n: merged.priced }));
    if (priceRowsMerged) parts.push(t('toast.crateImportedPriceRows', { n: priceRowsMerged }));
    if (merged.addedEmpty) parts.push(t('toast.crateImportedEmpty', { n: merged.addedEmpty }));
    if (merged.keptPrices) parts.push(t('toast.crateImportedKept', { n: merged.keptPrices }));
    let msg = parts.join(t('common.sep'));
    if (bad) msg += t('common.sep') + t('toast.crateBadDocs', { n: bad });
    toast(msg, !parts.length);
  }

  /** execCommand fallback for environments without the async Clipboard API
   *  (LAN http origins, old browsers, some file:// setups). */
  function execCopyFallback(text) {
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.setAttribute('readonly', '');
      ta.style.position = 'fixed';
      ta.style.top = '0';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.focus();
      ta.select();
      const ok = document.execCommand('copy');
      ta.remove();
      return ok;
    } catch (e) {
      return false;
    }
  }

  function copyText(text, okMsg) {
    const done = () => toast(okMsg);
    const fail = () => toast(t('preview.copyFailed'), true);
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, () => {
        if (execCopyFallback(text)) done(); else fail();
      });
    } else if (execCopyFallback(text)) {
      done();
    } else {
      fail();
    }
  }

  function shareLink() {
    try {
      const json = JSON.stringify(snapshotState());
      const enc = utf8Base64UrlEncode(json);
      const url = location.href.split('#')[0] + '#state=' + enc;
      copyText(url, t('toast.linkCopied'));
      if (enc.length > 8000) toast(t('toast.linkTooLong', { n: enc.length }), true);
    } catch (e) {
      toast(t('preview.copyFailed'), true);
    }
  }

  function loadHashState() {
    const m = /[#&]state=([A-Za-z0-9_-]+)/.exec(location.hash);
    if (!m) return false;
    try {
      const json = base64UrlDecodeToUtf8(m[1]);
      const snap = JSON.parse(json);
      if (!snap || typeof snap !== 'object') return false;
      // Opening a share link silently overwrites the local draft — preserve the
      // draft as the first undo entry so Ctrl+Z (or the button) brings it back.
      try {
        const entry = activeFileEntry();
        const p = entry && entry.payload;
        if (p && p.state && typeof p.state === 'object') {
          const draft = {
            fileName: p.state.fileName || 'my_box',
            meta: Object.assign(NS.emptyMeta(), p.state.meta),
            grades: normalizeGrades(p.state.grades),
            priceRows: p.state.priceRows || {},
            versionKey: p.versionKey || '1.21.1',
            taczEnabled: p.taczEnabled == null ? null : !!p.taczEnabled,
          };
          history.undo.push(draft);
          const undoBtn = $('#btn-undo');
          if (undoBtn) undoBtn.disabled = false;
        }
      } catch (e) { /* storage unavailable under file:// */ }
      // Normalize like loadState() so old/tampered share links cannot smuggle
      // raw item objects with undefined fields into the renderer.
      state = Object.assign(NS.emptyState(), {
        fileName: typeof snap.fileName === 'string' ? snap.fileName : 'my_box',
        meta: Object.assign(NS.emptyMeta(), snap.meta),
        grades: normalizeGrades(snap.grades),
        priceRows: snap.priceRows && typeof snap.priceRows === 'object' ? snap.priceRows : {},
      });
      state.versionKey = snap.versionKey || '1.21.1';
      if (!DATA.versions.some((v) => v.key === state.versionKey)) state.versionKey = '1.21.1';
      state.taczEnabled = snap.taczEnabled == null ? null : !!snap.taczEnabled;
      renderAll();
      toast(t('toast.linkLoaded'));
      return true;
    } catch (e) {
      return false;
    }
  }

  /* ------------------------------ game pack (#pack= fragment) ---------------- */

  function base64UrlToBytes(s) {
    const b64 = s.replace(/-/g, '+').replace(/_/g, '/');
    const pad = b64.length % 4 ? '===='.slice(b64.length % 4) : '';
    const bin = atob(b64 + pad);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return bytes;
  }

  async function inflateZlib(bytes) {
    if (typeof DecompressionStream === 'undefined') {
      const err = new Error('DecompressionStream unsupported');
      err.packUnsupported = true;
      throw err;
    }
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate'));
    return await new Response(stream).text();
  }

  /** Reads a #pack= fragment produced by the mod's /csbox editor command
   *  (common EditorPack: zlib-deflate + base64url JSON manifest
   *  {"v":1,"mc":<version key>,"files":{name:box,...},
   *  "broken":{name:<raw text>,...}?,"prices":{...}|null,
   *  "items":"id\nid\n..."?,"entities":"id\n..."?}).
   *  Every box file becomes a workspace draft; _prices.json merges into each
   *  draft's price table; unparseable files open pre-filled in the import
   *  dialog for the player to fix in place; the registry lists feed the
   *  item/entity autocomplete (stored locally, no re-entry needed).
   *  Returns true when a pack was applied. Async because DecompressionStream
   *  is a stream API. */
  async function loadHashPack() {
    const m = /[#&]pack=([A-Za-z0-9_-]+)/.exec(location.hash);
    if (!m) return false;
    // Drop the fragment immediately (the code is already captured):
    // importPack creates a workspace draft per file on every page load, so a
    // lingering #pack would duplicate every draft on any later reload or
    // re-open of the same link — up to a draft-per-refresh growth loop.
    // window.history (NOT the shadowing undo-manager `history` const);
    // replacing pathname+search does not fire hashchange, so the
    // #state/#pack reload guard below never trips.
    try {
      window.history.replaceState(null, '', location.pathname + location.search);
    } catch (e) { /* file:// throws SecurityError — keep the hash in dev */ }
    let pack = null;
    try {
      pack = JSON.parse(await inflateZlib(base64UrlToBytes(m[1])));
    } catch (e) {
      toast(e && e.packUnsupported ? t('toast.packUnsupported') : t('toast.packFailed'), true);
      return false;
    }
    return importPack(pack);
  }

  /** Small stable string hash (FNV-1a, 32-bit, base36) — cheap duplicate
   *  detection for re-opened #pack links. */
  function fnv1a(str) {
    let h = 0x811c9dc5;
    for (let i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = Math.imul(h, 0x01000193);
    }
    return (h >>> 0).toString(36);
  }

  /** True when the workspace already holds a draft imported from the same
   *  pack file. Valid boxes match on name + content signature (a genuinely
   *  changed box still imports as a fresh draft); broken-file handling only
   *  needs to know whether that source name was handled at all (fixed drafts
   *  carry the fixed content's signature, which never matches the raw text). */
  function packSlotSeen(name, sig) {
    const files = (filesStore && filesStore.files) || [];
    return files.some((e) => {
      const s = e.payload && e.payload.slot;
      return !!s && s.name === name && !!s.sig && s.sig === sig;
    });
  }

  function packSlotNamed(name) {
    const files = (filesStore && filesStore.files) || [];
    return files.some((e) => {
      const s = e.payload && e.payload.slot;
      return !!s && s.name === name;
    });
  }

  /** #pack import with a game-file difference resolver: when the link
   *  re-imports a box whose source file was already imported but whose
   *  content changed since, the player picks overwrite / keep-both / skip
   *  per file (queued dialog). Otherwise behaves as before — idempotent on
   *  identical packs. */
  async function importPack(pack) {
    if (!pack || typeof pack !== 'object' || pack.v !== 1) {
      toast(t('toast.packFailed'), true);
      return false;
    }
    const filesObj = (pack.files && typeof pack.files === 'object' && !Array.isArray(pack.files)) ? pack.files : {};
    const brokenObj = (pack.broken && typeof pack.broken === 'object' && !Array.isArray(pack.broken)) ? pack.broken : {};
    const pricesObj = (pack.prices && typeof pack.prices === 'object' && !Array.isArray(pack.prices)) ? pack.prices : null;
    const mcKey = (typeof pack.mc === 'string' && DATA.versions.some((v) => v.key === pack.mc)) ? pack.mc : null;
    const items = typeof pack.items === 'string' ? pack.items.split('\n').filter(Boolean) : [];
    const entities = typeof pack.entities === 'string' ? pack.entities.split('\n').filter(Boolean) : [];
    const mcDate = Date.now();
    const candidates = [];
    let dups = 0;
    for (const name of Object.keys(filesObj)) {
      if (!/\.json$/i.test(name)) continue;
      const doc = filesObj[name];
      if (!doc || typeof doc !== 'object' || Array.isArray(doc)) continue;
      if (detectType(doc) !== 'box') continue;
      const sig = fnv1a(JSON.stringify(doc));
      if (packSlotSeen(name, sig)) { dups++; continue; }
      candidates.push({ name, doc, sig });
    }
    const changed = candidates.filter((c) => packSlotNamed(c.name) && !packSlotSeen(c.name, c.sig));
    const decisions = changed.length ? await resolvePackSwaps(changed) : {};
    const newEntries = [];
    const overwriteOps = [];
    let overwrote = 0;
    let kept = 0;
    let skipped = dups;
    for (const c of candidates) {
      const dec = changed.includes(c) ? (decisions[c.name] || 'skip') : 'keepnew';
      if (dec === 'skip') { skipped++; continue; }
      const base = c.name.replace(/\.json$/i, '');
      const res = NS.boxToState(c.doc, base);
      const st = res.state;
      NS.mergeMigrations(st, res.migrations);
      if (pricesObj) NS.mergePrices(st, pricesObj);
      const payload = {
        versionKey: mcKey || (DATA.versions.some((v) => v.key === st.versionKey) ? st.versionKey : '1.21.1'),
        compact: true,
        currentTab: 'box',
        taczEnabled: null,
        // Pack provenance: enables idempotent re-imports of the same link;
        // mc/ts power the workspace source badge.
        slot: { name: c.name, sig: c.sig, mc: mcKey || undefined, ts: mcDate },
        state: {
          fileName: st.fileName,
          meta: st.meta,
          grades: st.grades,
          priceRows: st.priceRows,
        },
      };
      if (dec === 'overwrite') {
        const target = ((filesStore && filesStore.files) || []).find(
          (e) => e.payload && e.payload.slot && e.payload.slot.name === c.name
        );
        if (target) {
          // Replacements apply in the commit phase, AFTER the current editor
          // state is flushed — otherwise saveState() would clobber the
          // overwrite when the target is the active draft.
          overwriteOps.push({ target, payload });
          overwrote++;
          continue;
        }
        // Target vanished (should not happen): fall through to keep-both.
      }
      newEntries.push({ id: uid(), savedAt: mcDate, payload });
      if (dec === 'overwrite') kept++;
    }
    const parts = [];
    let ok = false;
    if (newEntries.length || overwrote) {
      // Flush the currently open draft first (boot() adopted it into `state`),
      // then group the imported drafts at the top of the workspace.
      if (filesStore && filesStore.files.length) saveState();
      else filesStore = { files: [], activeId: null };
      for (const op of overwriteOps) op.target.payload = op.payload;
      if (newEntries.length) {
        filesStore.files.unshift(...newEntries);
        filesStore.activeId = newEntries[0].id;
      }
      saveFilesStore();
      resetHistory();
      loadState();
      renderFileBar();
      if (newEntries.length) parts.push(t('toast.packImported', { n: newEntries.length }));
      if (pricesObj) parts.push(t('toast.packPrices', { n: Object.keys(pricesObj).length }));
      ok = true;
    } else if (pricesObj && activeFileEntry()) {
      // Prices-only pack: merge into the active draft.
      pushHistory();
      const n = NS.mergePrices(state, pricesObj);
      rebuildPrices();
      schedulePreview();
      saveState();
      if (n) {
        parts.push(t('toast.packPrices', { n }));
        ok = true;
      }
    }
    if (overwrote) parts.push(t('toast.packOverwrote', { n: overwrote }));
    if (kept) parts.push(t('toast.packKept', { n: kept }));
    if (applyGameLists(items, entities, parts)) ok = true;
    if (ok) {
      toast(parts.join(t('common.sep')));
      openPackBrokenFlow(brokenObj);
      return true;
    }
    if (openPackBrokenFlow(brokenObj)) return true;
    // Re-opened link whose every valid box was already imported (or skipped
    // by the player in the difference dialog): say so instead of the
    // misleading empty-pack toast.
    if (skipped) {
      toast(t('toast.packDup'), true);
      return true;
    }
    toast(t('toast.packNone'), true);
    return false;
  }

  /* ---------------- pack difference dialog (overwrite/keep/skip) ---------------- */

  const packSwapQueue = [];
  const swapDecisions = {};
  let swapCurrent = null;
  let swapDecision = null;
  let askSwapDone = null;

  /** Asks the player what to do with each changed pack file. Resolves to a
   *  map { name -> 'overwrite' | 'keep' | 'skip' } (default 'skip' when the
   *  dialog is unavailable, e.g. headless testspressing Escape). */
  function resolvePackSwaps(changed) {
    changed.forEach((c) => packSwapQueue.push(c));
    return new Promise((resolve) => {
      askSwapDone = resolve;
      drivePackSwap();
    });
  }

  function drivePackSwap() {
    if (!packSwapQueue.length) {
      swapCurrent = null;
      const done = askSwapDone;
      askSwapDone = null;
      if (done) done(swapDecisions);
      return;
    }
    swapCurrent = packSwapQueue.shift();
    swapDecision = 'skip';
    const hint = document.getElementById('swap-dialog-hint');
    if (hint) hint.textContent = t('swap.hint', { name: swapCurrent.name });
    const dlg = document.getElementById('swap-dialog');
    if (dlg) dlg.showModal();
    else {
      // No dialog markup (defensive): auto-skip the remainder.
      swapDecisions[swapCurrent.name] = 'skip';
      swapCurrent = null;
      drivePackSwap();
    }
  }

  /** Records the decision for the currently shown file. The dialog close
   *  event (button or Escape) is the single advance point. */
  function setSwapDecision(decision) {
    swapDecision = decision;
    const dlg = document.getElementById('swap-dialog');
    if (dlg) dlg.close();
  }

  /** Stores the game registry lists carried by a pack and appends a toast
   *  part per non-empty list. They power the item/entity autocomplete so box
   *  items can be picked in the browser without copying ids/NBT in-game.
   *  Returns true when a list was stored. */
  function applyGameLists(items, entities, parts) {
    if (!items.length && !entities.length) return false;
    gameLists = { items, entities };
    // Mirror for the validator: unknown-id cross-checks read NS.gameRegistry.
    syncGameRegistry();
    saveGameLists();
    buildItemDatalist();
    buildEntityDatalist();
    if (items.length) parts.push(t('toast.packItems', { n: items.length }));
    if (entities.length) parts.push(t('toast.packEntities', { n: entities.length }));
    return true;
  }

  /* ---------------- workspace share & game write-back ---------------- */

  /** zlib deflate + base64url — the encode antipode of inflateZlib. */
  function bytesToBase64Url(bytes) {
    let bin = '';
    const CHUNK = 0x8000;
    for (let i = 0; i < bytes.length; i += CHUNK) {
      bin += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
    }
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  async function deflateB64Url(str) {
    if (typeof CompressionStream === 'undefined') {
      const err = new Error('CompressionStream unsupported');
      err.wsUnsupported = true;
      throw err;
    }
    const stream = new Blob([str]).stream().pipeThrough(new CompressionStream('deflate'));
    const buf = await new Response(stream).arrayBuffer();
    return bytesToBase64Url(new Uint8Array(buf));
  }

  /** Encodes the WHOLE workspace into a #workspace= link (zlib + base64url).
   *  Same fragment-cleanup discipline as packs: read once, strip the hash,
   *  fingerprint-dedup on re-open. */
  async function shareWorkspace() {
    if (!filesStore || !filesStore.files.length) {
      toast(t('toast.workspaceEmpty'), true);
      return;
    }
    const manifest = {
      v: 1,
      w: 1,
      drafts: filesStore.files.map((e) => ({ savedAt: e.savedAt, payload: e.payload })),
    };
    let enc;
    try {
      enc = await deflateB64Url(JSON.stringify(manifest));
    } catch (e) {
      toast(e && e.wsUnsupported ? t('toast.wsUnsupported') : t('toast.workspaceBad'), true);
      return;
    }
    if (enc.length > 200000) {
      toast(t('toast.workspaceTooLong', { n: enc.length }), true);
      return;
    }
    const url = location.href.split('#')[0] + '#workspace=' + enc;
    copyText(url, t('toast.workspaceCopied'));
  }

  /** Shared-workspace ingestion: validates fragments loosely, normalizes
   *  payloads, dedupes on slot (name + sig) so re-opening a workspace link
   *  never duplicates drafts, groups valid drafts at the top and opens the
   *  newest. Returns true when the workspace changed. */
  async function loadHashWorkspace() {
    const m = /[#&]workspace=([A-Za-z0-9_-]+)/.exec(location.hash);
    if (!m) return false;
    try {
      window.history.replaceState(null, '', location.pathname + location.search);
    } catch (e) { /* file:// throws SecurityError — keep the hash in dev */ }
    let pack = null;
    try {
      pack = JSON.parse(await inflateZlib(base64UrlToBytes(m[1])));
    } catch (e) {
      toast(e && e.wsUnsupported ? t('toast.wsUnsupported') : t('toast.workspaceBad'), true);
      return false;
    }
    if (!pack || typeof pack !== 'object' || pack.v !== 1 || pack.w !== 1 || !Array.isArray(pack.drafts)) {
      toast(t('toast.workspaceBad'), true);
      return false;
    }
    const newEntries = [];
    let dups = 0;
    for (const d of pack.drafts) {
      const p = d && d.payload;
      if (!p || !p.state || typeof p.state !== 'object') continue;
      const s = p.state;
      // Dedupe on re-import: pack-origin drafts keep their original slot
      // (name/sig refer to the pack document); manual drafts get a
      // synthesized slot from the manifest payload so opening the same
      // workspace link twice never duplicates drafts.
      const slot = (p.slot && p.slot.name && p.slot.sig)
        ? { name: p.slot.name, sig: p.slot.sig, mc: p.slot.mc, ts: p.slot.ts }
        : { name: String(s.fileName || 'my_box') + '.json', sig: fnv1a(JSON.stringify(p)) };
      if (packSlotSeen(slot.name, slot.sig)) {
        dups++;
        continue;
      }
      const payload = {
        versionKey: DATA.versions.some((v) => v.key === p.versionKey) ? p.versionKey : '1.21.1',
        compact: p.compact !== false,
        currentTab: p.currentTab === 'prices' ? 'prices' : 'box',
        taczEnabled: p.taczEnabled == null ? null : !!p.taczEnabled,
        slot,
        state: {
          fileName: String(s.fileName || 'my_box'),
          meta: s.meta && typeof s.meta === 'object' ? s.meta : NS.emptyMeta(),
          grades: Array.isArray(s.grades) ? s.grades.map((arr) => (Array.isArray(arr) ? arr : [])) : [],
          priceRows: s.priceRows && typeof s.priceRows === 'object' ? s.priceRows : {},
        },
      };
      newEntries.push({ id: uid(), savedAt: Number(d.savedAt) || Date.now(), payload });
    }
    if (!newEntries.length) {
      toast(t(dups ? 'toast.workspaceDup' : 'toast.workspaceBad'), true);
      return false;
    }
    if (filesStore && filesStore.files.length) saveState();
    else filesStore = { files: [], activeId: null };
    filesStore.files.unshift(...newEntries);
    filesStore.activeId = newEntries[0].id;
    saveFilesStore();
    resetHistory();
    loadState();
    renderFileBar();
    toast(t('toast.workspaceImported', { n: newEntries.length }));
    return true;
  }

  /** "Read from game": the reverse of Apply to Game — picks the player's
   *  config/csbox folder, reads every *.json (plus `_prices.json`) and feeds
   *  the whole set into importPack(), reusing its dedupe / overwrite / broken
   *  fix flow. Read-only access is enough; the folder is never written. */
  async function readFromGame() {
    if (typeof window.showDirectoryPicker !== 'function') {
      toast(t('app.applyUnsupported'), true);
      return;
    }
    let dir;
    try {
      dir = await window.showDirectoryPicker({ id: 'csbox-apply', mode: 'read' });
    } catch (e) {
      return; // picker cancelled
    }
    const filesObj = {};
    const brokenObj = {};
    let found = 0;
    try {
      for await (const entry of dir.values()) {
        if (entry.kind !== 'file' || !/\.json$/i.test(entry.name)) continue;
        if (entry.name.startsWith('_')) continue; // helper files: _prices.json, _tutorial_*.md …
        let text = '';
        try {
          text = await (await entry.getFile()).text();
        } catch (err) {
          continue; // unreadable file — skip, the game folder stays untouched
        }
        found++;
        try {
          filesObj[entry.name] = JSON.parse(text);
        } catch (err) {
          brokenObj[entry.name] = text;
        }
      }
    } catch (e) {
      toast(t('app.readFailed'), true);
      return;
    }
    let prices = null;
    try {
      const fh = await dir.getFileHandle('_prices.json');
      prices = JSON.parse(await (await fh.getFile()).text());
    } catch (e) {
      // optional — a config/csbox folder without a price table is legal
    }
    if (!found && !prices) {
      toast(t('toast.packNone'), true);
      return;
    }
    await importPack({ v: 1, files: filesObj, broken: brokenObj, prices, mc: null });
  }

  /** "Apply to game": writes every draft (bar JSON per draft + the merged
   *  `_prices.json`) straight into the player's config/csbox folder via the
   *  File System Access API. Chromium-only; the button is hidden elsewhere.
   *  The player picks the folder once per session — no server, no upload. */
  async function applyToGame() {
    if (typeof window.showDirectoryPicker !== 'function') {
      toast(t('app.applyUnsupported'), true);
      return;
    }
    const entries = (filesStore && filesStore.files) || [];
    if (!entries.length) {
      toast(t('app.applyEmpty'), true);
      return;
    }
    let dir;
    try {
      dir = await window.showDirectoryPicker({ id: 'csbox-apply', mode: 'readwrite' });
    } catch (e) {
      return; // picker cancelled
    }
    const plan = NS.applyManifest(entries);
    if (!plan.files.length) {
      toast(t('app.applyEmpty'), true);
      return;
    }
    let written = 0;
    try {
      for (const f of plan.files) {
        const fh = await dir.getFileHandle(f.name, { create: true });
        const w = await fh.createWritable();
        await w.write(f.text);
        await w.close();
        written++;
      }
      if (plan.prices) {
        const fh = await dir.getFileHandle('_prices.json', { create: true });
        const w = await fh.createWritable();
        await w.write(JSON.stringify(plan.prices, null, 2) + '\n');
        await w.close();
      }
    } catch (e) {
      toast(t('app.applyFailed'), true);
      return;
    }
    let msg = t('app.applyDone', { n: written });
    if (plan.prices) msg += t('common.sep') + t('app.applyPrices');
    if (plan.conflicts) msg += t('common.sep') + t('app.applyConflicts', { n: plan.conflicts });
    toast(msg + t('common.sep') + t('app.applyReload'));
  }

  /** Queues broken (#pack= manifest) files and opens the first one
   *  pre-filled in the import dialog so the player fixes each in place;
   *  the dialog's close event advances the queue. Returns true when any
   *  broken file was present. Previously imported/fixed names are skipped so
   *  re-opening the link does not re-open fix dialogs for handled files. */
  function openPackBrokenFlow(brokenObj) {
    const names = Object.keys(brokenObj).filter((n) =>
      /\.json$/i.test(n) && typeof brokenObj[n] === 'string');
    const fresh = names.filter((n) => !packSlotNamed(n));
    if (!fresh.length) return false;
    for (const n of fresh) packBrokenQueue.push({ name: n, text: brokenObj[n] });
    toast(t('toast.packBroken', { n: fresh.length }), true);
    advancePackBroken();
    return true;
  }

  /* ------------------------------ event delegation ------------------------------ */

  function setupDelegation() {
    const root = document;

    document.addEventListener('toggle', (e) => {
      if (e.target && e.target.classList && e.target.classList.contains('grade-card')) {
        const gi = Number(e.target.dataset.grade);
        if (gi >= 0 && gi < 5) {
          gradeOpen[gi] = e.target.open;
          try { localStorage.setItem(LS_GRADE_OPEN, JSON.stringify(gradeOpen)); } catch (err) { /* file:// */ }
        }
      }
    }, true);

    root.addEventListener('input', (e) => {
      const el = e.target;
      if (el.dataset.role === 'price-input') {
        onPriceInput(el);
        return;
      }
      if (el.dataset.role === 'name-color') {
        onNameColor(el.value);
        return;
      }
      const f = el.dataset.f;
      if (!f) return;
      applyField(f, el.value);
      if (f === 'meta.name') {
        const colorInput = $('#name-color');
        if (colorInput) colorInput.value = nameColorValue(el.value);
      }
      schedulePreview();
    });

    root.addEventListener('change', (e) => {
      const el = e.target;
      if (el.id === 'tacz-toggle') {
        pushHistory();
        const cur = state.taczEnabled;
        if (cur === null) state.taczEnabled = true;
        else if (cur === true) state.taczEnabled = false;
        else state.taczEnabled = null;
        rebuildTaczCard();
        buildItemDatalist();
        rebuildGrades();
        rebuildPrices();
        schedulePreview();
        return;
      }
      if (el.id === 'icon-preset' && el.value) {
        pushHistory();
        state.meta.icon = el.value;
        const inp = document.querySelector('[data-f="meta.icon"]');
        if (inp) inp.value = el.value;
        el.value = '';
        rebuildMeta();
        schedulePreview();
        return;
      }
      const f = el.dataset.f;
      if (!f) return;
      if (el.dataset.kind === 'checkbox') {
        applyField(f, el.checked);
        schedulePreview();
        return;
      }
      applyField(f, el.value);
      const parts = f.split('.');
      if (parts[0] === 'grades' && parts.length >= 4) {
        const field = parts[3];
        if (['source', 'countMode', 'enchantMode', 'enchantLevelMode'].includes(field)) {
          rebuildGrade(Number(parts[1]));
        }
      }
      if (f === 'meta.type') renderForm();
      schedulePreview();
    });

    root.addEventListener('click', (e) => {
      const issue = e.target.closest('.issue[data-issue-path]');
      if (issue) {
        locateIssue(issue.dataset.issuePath);
        return;
      }
      const btn = e.target.closest('[data-action]');
      if (!btn) {
        const colHead = e.target.closest('.col-head[data-col]');
        if (colHead) toggleCol(colHead.dataset.col);
        return;
      }
      const action = btn.dataset.action;
      switch (action) {
        case 'add-item': {
          pushHistory();
          const gi = Number(btn.dataset.grade);
          state.grades[gi].push(NS.emptyItem());
          rebuildGrade(gi, { enterIndex: state.grades[gi].length - 1 });
          schedulePreview();
          break;
        }
        case 'del-item': {
          const gi = Number(btn.dataset.grade);
          const ii = Number(btn.dataset.index);
          const host = $('#grade-items-' + gi);
          const card = host && host.querySelectorAll('.item-card')[ii];
          if (card && !card.dataset.removing && !prefersReducedMotion()) {
            card.dataset.removing = '1';
            // State is authoritative immediately (same snapshot semantics as
            // every other action); only the list DOM catches up after the
            // collapse animation. Any interleaved undo/redo re-renders the
            // grade from live state, so the pending rebuild stays consistent.
            pushHistory();
            state.grades[gi].splice(ii, 1);
            schedulePreview();
            card.style.height = card.offsetHeight + 'px';
            card.classList.add('item-leave');
            requestAnimationFrame(() => requestAnimationFrame(() => {
              card.style.height = '0px';
              card.style.opacity = '0';
              card.style.marginTop = '0';
              card.style.marginBottom = '0';
              card.style.paddingTop = '0';
              card.style.paddingBottom = '0';
            }));
            setTimeout(() => rebuildGrade(gi), 150);
          } else {
            pushHistory();
            state.grades[gi].splice(ii, 1);
            rebuildGrade(gi);
            schedulePreview();
          }
          break;
        }
        case 'move-item': {
          pushHistory();
          const gi = Number(btn.dataset.grade);
          const ii = Number(btn.dataset.index);
          const dir = Number(btn.dataset.dir);
          const arr = state.grades[gi];
          const jj = ii + dir;
          if (jj < 0 || jj >= arr.length) break;
          const tmp = arr[ii];
          arr[ii] = arr[jj];
          arr[jj] = tmp;
          rebuildGrade(gi);
          schedulePreview();
          break;
        }
        case 'add-entity': {
          pushHistory();
          state.meta.entity.push({ id: '', rate: '' });
          // The entity rows live in #card-drop, not #card-meta: rebuilding the
          // wrong card leaves state and DOM out of sync (the row is added to
          // state but never rendered, and a preview export then carries rows
          // the user cannot see).
          rebuildDrop();
          schedulePreview();
          break;
        }
        case 'del-entity': {
          pushHistory();
          const idx = Number(btn.dataset.index);
          // Emptying the list is a legal state: model.js omits `entity`
          // entirely when no row is filled in. No auto re-seed, otherwise the
          // last row looks undeletable.
          if (idx >= 0 && idx < state.meta.entity.length) state.meta.entity.splice(idx, 1);
          rebuildDrop();
          schedulePreview();
          break;
        }
        case 'add-price-key': {
          pushHistory();
          const input = $('#extra-key-input');
          const key = input.value.trim();
          if (key) {
            if (!state.priceRows[key]) {
              state.priceRows[key] = { price: '', pinned: true };
            }
            input.value = '';
            rebuildPrices();
            schedulePreview();
          }
          break;
        }
        case 'del-price-key': {
          pushHistory();
          const key = btn.dataset.key;
          delete state.priceRows[key];
          rebuildPrices();
          schedulePreview();
          break;
        }
        case 'load-example': {
          pushHistory();
          loadExample(Number(btn.dataset.index), true);
          document.getElementById('example-dialog').close();
          break;
        }
        case 'load-compat': {
          const pack = (DATA.compatPacks || [])[Number(btn.dataset.index)];
          if (pack) {
            pushHistory();
            void importPack({ v: 1, files: pack.files, prices: pack.prices || null });
            document.getElementById('example-dialog').close();
          }
          break;
        }
        case 'collapse-all': {
          document.querySelectorAll('.grade-card').forEach((d) => { d.open = false; });
          break;
        }
        case 'expand-all': {
          document.querySelectorAll('.grade-card').forEach((d) => { d.open = true; });
          break;
        }
        case 'undo': {
          undo();
          break;
        }
        case 'redo': {
          redo();
          break;
        }
        case 'copy-item': {
          const gi = Number(btn.dataset.grade);
          const ii = Number(btn.dataset.index);
          const it = state.grades[gi] && state.grades[gi][ii];
          if (!it) break;
          const obj = NS.buildItem(it) || { id: it.value || '' };
          copyText(JSON.stringify(obj, null, 2), t('item.copied'));
          break;
        }
        case 'paste-items': {
          openPasteDialog({ mode: 'items', grade: Number(btn.dataset.grade) });
          break;
        }
        case 'paste-prices': {
          openPasteDialog({ mode: 'prices' });
          break;
        }
        case 'import-crate-prices': {
          openCrateImportDialog();
          break;
        }
        case 'share': {
          shareLink();
          break;
        }
        case 'share-workspace': {
          void shareWorkspace();
          break;
        }
        case 'apply-game': {
          void applyToGame();
          break;
        }
        case 'read-game': {
          void readFromGame();
          break;
        }
        case 'enable-advanced': {
          advanced = true;
          try { localStorage.setItem(LS_ADV, '1'); } catch (e) { /* file:// */ }
          renderAll();
          toast(t('toast.advancedOn'));
          break;
        }
        case 'tacz-tutorial': {
          const td = $('#tutorial-dialog');
          if (!td) break;
          $('#tutorial-title').textContent = t('tacz.tutorialTitle');
          $('#tutorial-body').innerHTML = t('tacz.tutorial'); /* esc-exempt: trusted i18n tutorial text (contains intentional markup) */
          td.showModal();
          break;
        }
        case 'tab': {
          currentTab = btn.dataset.tab;
          rebuildPreview();
          break;
        }
        case 'copy': {
          copyCurrent();
          break;
        }
        case 'download': {
          downloadCurrent();
          break;
        }
        case 'download-all': {
          downloadBox();
          setTimeout(() => downloadPrices(), 350);
          break;
        }
      }
    });

    document.getElementById('compact-toggle').addEventListener('change', (e) => {
      compact = e.target.checked;
      rebuildPreview();
    });
  }

  function setupShortcuts() {
    document.addEventListener('keydown', (e) => {
      const tag = e.target && e.target.tagName;
      const inField = tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT';
      const mod = e.ctrlKey || e.metaKey;
      if (mod && !e.shiftKey && !e.altKey && (e.key === 'z' || e.key === 'Z')) {
        if (!inField) { e.preventDefault(); undo(); } // allow native undo inside text fields
      } else if (mod && e.shiftKey && (e.key === 'z' || e.key === 'Z')) {
        e.preventDefault(); redo();
      } else if (mod && !e.altKey && (e.key === 'y' || e.key === 'Y')) {
        e.preventDefault(); redo();
      }
    });
  }

  function setupFileInput() {
    const fi = $('#file-input');
    fi.addEventListener('change', () => {
      const file = fi.files && fi.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => applyImportText(String(reader.result || ''), file.name);
      reader.readAsText(file);
      fi.value = '';
    });

    const lfi = $('#legacy-file-input');
    if (lfi) {
      lfi.addEventListener('change', () => {
        const file = lfi.files && lfi.files[0];
        if (!file) return;
        legacyFileName = file.name; // drives legacy terminal.json recognition
        const reader = new FileReader();
        reader.onload = () => {
          const ta = document.getElementById('legacy-textarea');
          if (ta) ta.value = String(reader.result || '');
        };
        reader.readAsText(file);
        lfi.value = '';
      });
    }
  }

  /* ------------------------------ field updates ------------------------------ */

  function applyField(path, value) {
    scheduleHistory();
    const parts = path.split('.');
    if (parts[0] === 'meta') {
      if (parts[1] === 'fileName') {
        state.fileName = value;
        return;
      }
      if (parts[1] === 'entity') {
        const i = Number(parts[2]);
        const k = parts[3];
        (state.meta.entity[i] = state.meta.entity[i] || { id: '', rate: '' })[k] = value;
      } else if (parts[1] === 'random') {
        state.meta.random[Number(parts[2])] = value;
      } else {
        state.meta[parts[1]] = value;
      }
      return;
    }
    if (parts[0] === 'grades') {
      const gi = Number(parts[1]);
      const ii = Number(parts[2]);
      const item = state.grades[gi][ii];
      if (!item) return;
      item[parts[3]] = value;
      // range helpers keep max >= min
      if (parts[3] === 'countMin' && Number(value) > Number(item.countMax)) item.countMax = value;
      if (parts[3] === 'countMax' && Number(value) < Number(item.countMin)) item.countMin = value;
      if (parts[3] === 'enchantLevelMin' && Number(value) > Number(item.enchantLevelMax)) item.enchantLevelMax = value;
      if (parts[3] === 'enchantLevelMax' && Number(value) < Number(item.enchantLevelMin)) item.enchantLevelMin = value;
      return;
    }
  }

  function onPriceInput(el) {
    scheduleHistory(); // debounced like other text inputs (600 ms quiet burst)
    const key = el.dataset.key;
    if (!state.priceRows[key]) state.priceRows[key] = { price: '', pinned: true };
    state.priceRows[key].price = el.value;
    updatePriceRowCells(key);
    schedulePreview();
  }

  /* ------------------------------ rendering ------------------------------ */

  function buildEntityDatalist() {
    const list = document.getElementById('csbox-entity-list');
    if (!list) return;
    const seen = new Set();
    let html = (DATA.entitySuggestions || []).map((e) => {
      const id = 'minecraft:' + e.id;
      seen.add(id);
      return '<option value="' + esc(id) + '" label="' + esc(e[I18N.lang] || e.en || e.id) + '">';
    }).join('');
    // Game registry ids synced via a #pack= link: plain values, after the
    // curated suggestions, deduped.
    html += gameLists.entities.filter((id) => !seen.has(id)).map((id) => {
      seen.add(id);
      return '<option value="' + esc(id) + '">';
    }).join('');
    list.innerHTML = html; /* esc-exempt: every dynamic part of `html` is esc()'d above */
  }

  /** Item-id autocomplete for pool value inputs and the price-table extra-key
   *  input. TACZ entries are gated by the effective TACZ visibility; game
   *  registry ids synced via a #pack= link (all loaded mods) come last. */
  function buildItemDatalist() {
    const list = document.getElementById('csbox-item-list');
    if (!list) return;
    const tacz = taczVisible();
    const seen = new Set();
    let html = (DATA.itemSuggestions || []).filter((e) => !e.tacz || tacz).map((e) => {
      const ns = e.tacz ? 'tacz:' : 'minecraft:';
      const id = ns + e.id;
      seen.add(id);
      return '<option value="' + esc(id) + '" label="' + esc(e[I18N.lang] || e.en || e.id) + '">';
    }).join('');
    html += gameLists.items.filter((id) => !seen.has(id)).map((id) => {
      seen.add(id);
      return '<option value="' + esc(id) + '">';
    }).join('');
    list.innerHTML = html; /* esc-exempt: every dynamic part of `html` is esc()'d above */
  }

  function renderAll() {
    bindStatics();
    buildEntityDatalist();
    buildItemDatalist();
    fillVersionSelect($('#version-select'));
    renderFileBar();
    const langSel = $('#lang-select');
    langSel.value = I18N.lang;
    const advToggle = $('#advanced-toggle');
    if (advToggle) advToggle.checked = advanced;
    renderForm();
    rebuildPreview();
  }

  function renderForm() {
    const root = $('#form-root');
    const v = version();
    const pricesPage = isPricesPage();
    root.innerHTML = pricesPage
      ? '<section class="card" id="card-prices"></section>' +
        '<div class="version-note">' + esc(t('version.note') + ': ' + v.note[I18N.lang]) + '</div>'
      : '<section class="card" id="card-meta"></section>' +
        (advanced ? '<section class="card" id="card-tacz"></section>' : '') +
        '<section class="card" id="card-drop"></section>' +
        (state.meta.type === 'terminal' ? '<section class="card" id="card-term"></section>' : '') +
        '<section class="card" id="card-grades"></section>' +
        '<div class="version-note">' + esc(t('version.note') + ': ' + v.note[I18N.lang]) + '</div>';
    if (pricesPage) {
      rebuildPrices();
    } else {
      rebuildMeta();
      if (advanced) rebuildTaczCard();
      rebuildDrop();
      if (state.meta.type === 'terminal') rebuildTerm();
      rebuildGrades();
    }
    const compactToggle = $('#compact-toggle');
    if (compactToggle) compactToggle.checked = compact;
  }

  function card(id) { return document.getElementById(id); }

  function rebuildTaczCard() {
    const el = card('card-tacz');
    if (!el) return;
    const v = version();
    const auto = state.taczEnabled == null;
    const on = auto ? v.taczVariants : !!state.taczEnabled;
    const stateLabel = auto
      ? t('tacz.state.auto') + ' · ' + (on ? t('tacz.state.yes') : t('tacz.state.no'))
      : (on ? t('tacz.state.on') : t('tacz.state.off'));
    el.innerHTML =
      '<div class="split-row tacz-row">' +
      '<div>' +
      '<h2>' + esc(t('tacz.title')) + '</h2>' +
      '<p class="help">' + esc(t('tacz.help')) + '</p>' +
      '<button class="btn small" data-action="tacz-tutorial">' + esc(t('tacz.tutorialBtn')) + '</button>' +
      '</div>' +
      '<label class="chk tacz-switch" data-help="tacz.tip">' +
      '<input type="checkbox" id="tacz-toggle"' + (on ? ' checked' : '') + '>' +
      '<span id="tacz-state">' + esc(stateLabel) + '</span>' +
      '</label>' +
      '</div>';
    const cb = $('#tacz-toggle');
    if (cb && auto) cb.indeterminate = true;
  }

  function rebuildMeta() {
    const el = card('card-meta');
    if (!el) return;
    const m = state.meta;
    el.innerHTML =
      '<h2>' + esc(t('meta.title')) + '</h2>' +
      '<div class="grid2">' +
      field('meta.fileName', 'meta.fileNameHelp', input('meta.fileName', state.fileName, 'my_box', false)) +
      field('meta.name', 'meta.nameHelp',
        '<span class="name-color-row">' +
        '<input data-f="meta.name" value="' + esc(m.name) + '" placeholder="' + esc('#FF5555 ' + (I18N.lang === 'zh' ? '名字' : 'Name')) + '" class="grow">' +
        '<input type="color" id="name-color" data-role="name-color" value="' + nameColorValue(m.name) + '" title="' + esc(t('meta.nameColor')) + '">' +
        '</span>') +
      field('meta.type', '', select('meta.type', [
        ['csbox', t('meta.type.csbox')],
        ['terminal', t('meta.type.terminal')],
      ], m.type)) +
      field('meta.key', 'meta.keyHelp',
        '<input data-f="meta.key" list="csbox-key-list" value="' + esc(m.key) + '" placeholder="minecraft:iron_ingot"' +
        (m.type === 'terminal' ? ' disabled class="danger-input"' : '') + '>') +
      field('meta.drop', 'meta.dropHelp', input('meta.drop', m.drop, '0.05', false)) +
      iconField(m) +
      '</div>' +
      '<div class="split-row">' +
      '<label class="chk"><input type="checkbox" data-f="meta.enabled" data-kind="checkbox"' + (m.enabled ? ' checked' : '') + '> ' + esc(t('meta.enabled')) + '</label>' +
      '<div class="grow"></div>' +
      '</div>' +
      '<div class="row">' +
      '<label data-help="meta.requiresHelp">' + esc(t('meta.requires')) + '<input data-f="meta.requiresText" value="' + esc(m.requiresText) + '" placeholder="tacz" class="grow"></label>' +
      '</div>' +
      '<datalist id="csbox-key-list">' +
      (DATA.keySuggestions || []).map((k) =>
        '<option value="' + esc(k.id) + '" label="' + esc(k[I18N.lang] || k.id) + '">').join('') +
      '</datalist>';
  }

  /** 自定义贴图（icon）字段：默认收纳，仅「高级」开关开启时展示完整编辑；
   *  已配置但处于普通模式时给出一条提示，避免配置静默不可见。 */
  function iconField(m) {
    if (!advanced) {
      const icon = String(m.icon || '').trim();
      if (!icon) return '';
      return '<div class="icon-hidden-hint">' + esc(t('meta.iconHiddenHint')) + '</div>';
    }
    const v = version();
    const itemModelNote = v.itemModel
      ? t('icon.itemModelOk')
      : t('icon.itemModelNo');
    const presetOptions = ICON_PRESETS.map((id) =>
      '<option value="' + esc(id) + '">' + esc(id) + '</option>').join('');
    return field('meta.icon', 'meta.iconAdvancedHelp',
      '<span class="icon-row">' +
      '<input data-f="meta.icon" list="csbox-icon-list" value="' + esc(m.icon) + '" placeholder="' + esc(t('icon.placeholder')) + '" class="grow">' +
      '<select id="icon-preset" data-role="icon-preset" aria-label="' + esc(t('icon.preset')) + '">' +
      '<option value="">' + esc(t('icon.presetPh')) + '</option>' +
      presetOptions +
      '</select>' +
      '</span>' +
      '<datalist id="csbox-icon-list">' + ICON_PRESETS.map((id) => '<option value="' + esc(id) + '">').join('') + '</datalist>' +
      '<span class="help">' + esc(itemModelNote) + '</span>');
  }

  function rebuildDrop() {    const el = card('card-drop');
    if (!el) return;
    const m = state.meta;
    el.innerHTML =
      '<h2>' + esc(t('drop.title')) + '</h2>' +
      '<div class="row">' +
      '<label>' + esc(t('drop.random')) +
      '<div class="random-row">' +
      m.random.map((v, i) =>
        '<span class="random-slot" data-help="drop.randomHelp"><b>' + esc((DATA.gradeNames[i] || {})[I18N.lang] || (DATA.gradeNames[i] || {}).zh || '') + '</b>' +
        '<input data-f="meta.random.' + i + '" value="' + esc(v) + '" type="number" min="0" max="10000"></span>').join('') +
      '</div>' +
      '<span class="help">' + esc(t('drop.randomHelp')) + '</span>' +
      '</label>' +
      '</div>' +
      '<h3>' + esc(t('drop.entity')) + '</h3>' +
      '<div id="entity-rows"></div>' +
      '<button class="btn small" data-action="add-entity">' + esc(t('drop.entityAdd')) + '</button>';
    rebuildEntityRows();
  }

  function rebuildEntityRows() {
    const host = $('#entity-rows');
    if (!host) return;
    if (!state.meta.entity.length) {
      host.innerHTML = '<p class="entity-empty">' + esc(t('drop.entityEmpty')) + '</p>';
      return;
    }
    host.innerHTML = state.meta.entity.map((row, i) =>
      '<div class="entity-row">' +
      '<input data-help="drop.entityIdHelp" data-f="meta.entity.' + i + '.id" list="csbox-entity-list" value="' + esc(row.id) + '" placeholder="minecraft:zombie" class="grow">' +
      '<input data-help="drop.entityRateHelp" data-f="meta.entity.' + i + '.rate" value="' + esc(row.rate) + '" placeholder="0.1" type="number" min="0" max="1" step="0.01" class="rate">' +
      '<button class="btn small danger" data-action="del-entity" data-index="' + i + '" aria-label="' + esc(t('drop.entityDel')) + '">✕</button>' +
      '</div>').join('');
  }

  function rebuildTerm() {
    const el = card('card-term');
    if (!el) return;
    const m = state.meta;
    el.innerHTML =
      '<h2>' + esc(t('term.title')) + '</h2>' +
      '<div class="grid2">' +
      field('term.discount', 'term.discountHelp', input('meta.discount', m.discount, '0.2', false)) +
      field('term.stock', 'term.stockHelp', input('meta.stock', m.stock, '-1', false)) +
      field('term.maxPer', 'term.maxPerHelp', input('meta.maxPerPlayer', m.maxPerPlayer, '-1', false)) +
      field('term.cooldown', 'term.cooldownHelp', input('meta.cooldownSeconds', m.cooldownSeconds, '0', false)) +
      field('term.permission', 'term.permissionHelp', input('meta.permission', m.permission, 'csgobox.open', false)) +
      field('term.pityGrade', 'term.pityGradeHelp', input('meta.pityGrade', m.pityGrade, '', false)) +
      field('term.pityEvery', 'term.pityEveryHelp', input('meta.pityEvery', m.pityEvery, '20', false)) +
      '</div>';
  }

  /* Args: (labelKey, helpKey, inputHtml). helpKey is an i18n key (may be '');
   * the widget HTML follows. data-help on the label drives the hover tooltip. */
  function field(labelKey, helpKey, inputHtml) {
    const help = helpKey ? t(helpKey) : '';
    return '<label class="field"' + (helpKey ? ' data-help="' + esc(helpKey) + '"' : '') + '>' +
      '<span class="f-label">' + esc(t(labelKey)) + '</span>' +
      inputHtml +
      (help ? '<span class="help">' + esc(help) + '</span>' : '') +
      '</label>';
  }

  function input(f, value, ph, disabled, redFlag) {
    return '<input data-f="' + esc(f) + '" value="' + esc(value) + '" placeholder="' + esc(ph) + '"' +
      (disabled ? ' disabled' : '') +
      (redFlag ? ' class="danger-input"' : '') +
      '>';
  }

  function select(f, options, value, helpKey) {
    return '<select data-f="' + esc(f) + '"' + (helpKey ? ' data-help="' + esc(helpKey) + '"' : '') + '>' +
      options.map((o) => '<option value="' + esc(o[0]) + '"' + (String(value) === String(o[0]) ? ' selected' : '') + '>' + esc(o[1]) + '</option>').join('') +
      '</select>';
  }

  function rebuildGrades() {
    const el = card('card-grades');
    if (!el) return;
    const v = version();
    el.innerHTML =
      '<div class="card-head">' +
      '<h2>' + esc(t('grade.title')) + '</h2>' +
      '<div class="head-actions">' +
      '<button class="btn small" data-action="expand-all">' + esc(t('grade.expandAll')) + '</button>' +
      '<button class="btn small" data-action="collapse-all">' + esc(t('grade.collapseAll')) + '</button>' +
      '</div></div>' +
      '<div class="grade-list">' +
      DATA.gradeNames.map((g, gi) => {
        const count = state.grades[gi].length;
        return '<details class="grade-card" data-grade="' + gi + '"' + (gradeOpen[gi] ? ' open' : '') + '>' +
          '<summary>' +
          '<span class="grade-badge g' + (gi + 1) + '">' + esc(g[I18N.lang]) + '</span>' +
          '<code>grade' + (gi + 1) + '</code>' +
          '<span class="muted">' + esc(t('grade.priceHint')) + '</span>' +
          '<span class="count-badge">' + count + '</span>' +
          '</summary>' +
          '<div class="grade-items" id="grade-items-' + gi + '"></div>' +
          '<button class="btn small accent" data-action="add-item" data-grade="' + gi + '">' + esc(t('grade.add')) + '</button>' +
          '<button class="btn small" data-action="paste-items" data-grade="' + gi + '">' + esc(t('item.pasteItems')) + '</button>' +
          '</details>';
      }).join('') +
      '</div>';
    for (let gi = 0; gi < 5; gi++) rebuildGrade(gi);
  }

  /** Rebuild the DOM, then restore focus/caret to the element the user was
   *  editing (identified by data-f or id). Rebuilds destroy the focused
   *  select/input, which drops keyboard users back to <body>. */
  const NON_SELECTABLE_INPUTS = new Set([
    'checkbox', 'radio', 'file', 'number', 'range', 'color', 'date', 'time',
    'datetime-local', 'month', 'week', 'submit', 'button', 'reset', 'image',
  ]);

  function withFocusRestore(fn, preferField) {
    const active = document.activeElement;
    let mark = null;
    if (preferField) {
      mark = preferField;
    } else if (active && active instanceof HTMLElement) {
      mark = active.getAttribute('data-f') || (active.id ? '#' + active.id : null);
    }
    fn();
    if (!mark) return;
    const el = mark.startsWith('#')
      ? document.getElementById(mark.slice(1))
      : document.querySelector('[data-f="' + CSS.escape(mark) + '"]');
    if (el) {
      el.focus();
      // Restore the caret only for inputs that actually support a selection —
      // setSelectionRange throws on checkbox/radio/number/file/etc., and an
      // exception here would abort the caller's render loop (e.g. leaving
      // later grade cards empty when the top-bar Advanced checkbox keeps focus).
      const selectable = el instanceof HTMLTextAreaElement ||
        (el instanceof HTMLInputElement && !NON_SELECTABLE_INPUTS.has(el.type));
      if (selectable) {
        try {
          const len = el.value.length;
          el.setSelectionRange(len, len);
        } catch (e) { /* defensive: exotic input types */ }
      }
    }
  }

  function rebuildGrade(gi, opts) {
    const host = $('#grade-items-' + gi);
    if (!host) return;
    const items = state.grades[gi];
    const v = version();
    if (!items.length) {
      host.innerHTML = '<div class="muted">' + esc(t('grade.empty')) + '</div>';
      return;
    }
    withFocusRestore(() => {
      host.innerHTML = items.map((it, ii) => renderItem(gi, ii, it, v)).join(''); /* esc-exempt: renderItem escapes its own output */
      const badge = document.querySelector('.grade-card[data-grade="' + gi + '"] .count-badge');
      if (badge) badge.textContent = items.length;
      if (opts && opts.enterIndex != null) {
        const card = host.querySelectorAll('.item-card')[opts.enterIndex];
        if (card) {
          card.classList.add('item-enter');
          // Timeout-based cleanup: animationend is unreliable here (throttled
          // / occluded panes never dispatch it and the class would linger).
          setTimeout(() => card.classList.remove('item-enter'), 200);
        }
      }
    });
  }

  function renderItem(gi, ii, it, v) {
    const p = 'grades.' + gi + '.' + ii + '.';
    const id = (it.source === 'tag' ? '#' : '') + (it.value || '') || '';
    const srcOptions = [
      ['id', t('item.source.id')],
      ['tag', t('item.source.tag')],
      ['loot_table', t('item.source.loot')],
    ];
    const countOptions = [
      ['single', t('item.count.single')],
      ['fixed', t('item.count.fixed')],
      ['range', t('item.count.range')],
    ];
    const enchantOptions = [
      ['none', t('item.enchant.none')],
      ['any', t('item.enchant.any')],
      ['custom', t('item.enchant.custom')],
    ];
    const levelOptions = [
      ['fixed', t('item.enchantLevel.fixed')],
      ['range', t('item.enchantLevel.range')],
    ];
    // Advanced fields configured but not editable in basic mode → a badge that
    // flips the top-bar "Advanced" toggle on (same pattern as the icon hint).
    const hiddenFields = [];
    if (it.enchantMode !== 'none') hiddenFields.push(t('item.hiddenAdvEnchant'));
    if ((it.variant || '').trim()) hiddenFields.push(t('item.hiddenAdvTacz'));
    if ((it.tagRaw || '').trim()) hiddenFields.push(t('item.hiddenAdvNbt'));
    const badge = (!advanced && hiddenFields.length)
      ? '<button class="hidden-adv-badge btn small" data-action="enable-advanced" title="' + esc(t('item.hiddenAdvHint')) + '">⚙ ' + esc(hiddenFields.join(' / ')) + '</button>'
      : '';
    const header = '<div class="item-head">' +
      '<span class="item-no">#' + (ii + 1) + '</span>' +
      select(p + 'source', srcOptions, it.source, 'item.sourceHelp') +
      '<input data-help="item.sourceHelp" data-f="' + esc(p + 'value') + '" value="' + esc(it.value) +
      '" list="csbox-item-list" placeholder="' + (it.source === 'tag' ? '#minecraft:swords' : it.source === 'loot_table' ? 'minecraft:chests/simple_dungeon' : 'minecraft:diamond_sword') + '" class="grow">' +
      '<button class="btn small" data-action="move-item" data-grade="' + gi + '" data-index="' + ii + '" data-dir="-1" data-help="item.up" aria-label="' + esc(t('item.up')) + '">↑</button>' +
      '<button class="btn small" data-action="move-item" data-grade="' + gi + '" data-index="' + ii + '" data-dir="1" data-help="item.down" aria-label="' + esc(t('item.down')) + '">↓</button>' +
      '<button class="btn small" data-action="copy-item" data-grade="' + gi + '" data-index="' + ii + '" data-help="item.copy" aria-label="' + esc(t('item.copy')) + '">⧉</button>' +
      '<button class="btn small danger" data-action="del-item" data-grade="' + gi + '" data-index="' + ii + '" data-help="item.remove" aria-label="' + esc(t('item.remove')) + '">✕</button>' +
      badge +
      '</div>';

    const body = '<div class="item-body">' +

      '<div class="row inline">' +
      '<label class="mini" data-help="item.countHelp">' + esc(t('item.count')) + ' ' +
      select(p + 'countMode', countOptions, it.countMode) + '</label>' +
      (it.countMode === 'fixed'
        ? '<label class="mini" data-help="item.countHelp">' + esc(t('item.count')) + '<input data-f="' + esc(p + 'count') + '" value="' + esc(it.count) + '" type="number" min="1" class="w7"></label>'
        : it.countMode === 'range'
          ? '<label class="mini" data-help="item.countHelp">' + esc(t('item.count')) + ' <input data-f="' + esc(p + 'countMin') + '" value="' + esc(it.countMin) + '" type="number" min="1" class="w7"> … <input data-f="' + esc(p + 'countMax') + '" value="' + esc(it.countMax) + '" type="number" min="1" class="w7"></label>'
          : '') +
      '<label class="mini" data-help="item.weightHelp">' + esc(t('item.weight')) + '<input data-f="' + esc(p + 'weight') + '" value="' + esc(it.weight) + '" type="number" min="0" class="w7"></label>' +
      '<label class="mini" data-help="item.noteHelp">' + esc(t('item.note')) + '<input data-f="' + esc(p + 'note') + '" value="' + esc(it.note || '') + '" placeholder="' + esc(t('item.notePh')) + '" class="w14"></label>' +
      (advanced
        ? '<label class="mini" data-help="item.enchantHelp">' + esc(t('item.enchant')) + ' ' + select(p + 'enchantMode', enchantOptions, it.enchantMode) + '</label>'
        : '') +
      (advanced && it.enchantMode === 'custom'
        ? '<label class="mini" data-help="item.enchantIdHelp">' + esc(t('item.enchantId')) + '<input data-f="' + esc(p + 'enchantId') + '" value="' + esc(it.enchantId) + '" placeholder="minecraft:sharpness" class="w14"></label>' +
          '<label class="mini" data-help="item.enchantLevelHelp">' + esc(t('item.enchantLevel')) + ' ' + select(p + 'enchantLevelMode', levelOptions, it.enchantLevelMode) + '</label>' +
          (it.enchantLevelMode === 'range'
            ? '<label class="mini" data-help="item.enchantLevelHelp"><input data-f="' + esc(p + 'enchantLevelMin') + '" value="' + esc(it.enchantLevelMin) + '" type="number" min="1" class="w7"></label>' +
              '<span class="mini sep">' + esc(t('item.to')) + '</span>' +
              '<label class="mini" data-help="item.enchantLevelHelp"><input data-f="' + esc(p + 'enchantLevelMax') + '" value="' + esc(it.enchantLevelMax) + '" type="number" min="1" class="w7"></label>'
            : '<label class="mini" data-help="item.enchantLevelHelp"><input data-f="' + esc(p + 'enchantLevel') + '" value="' + esc(it.enchantLevel) + '" type="number" min="1" class="w7"></label>')
        : '') +
      '</div>' +

      (advanced && taczVisible()
        ? '<div class="row inline">' +
          '<label class="mini" data-help="item.variantHelp">' + esc(t('item.variant')) + '<input data-f="' + esc(p + 'variant') + '" value="' + esc(it.variant) + '" placeholder="tacz:ak47" class="w14"></label>' +
          '<label class="mini" data-help="item.variantFieldHelp">' + esc(t('item.variantField')) + ' ' +
          select(p + 'variantField', [['GunId', 'GunId'], ['AmmoId', 'AmmoId']], it.variantField) + '</label>' +
          '</div>'
        : '') +

      ((advanced && taczVisible()) || v.components || !!(it.components || '').trim() ? advancedBlock(it, p, v) : '') +
      '</div>';

    return '<div class="item-card">' + header + body + '</div>';
  }

  /** Advanced per-item data (raw NBT tag / data components) is tucked into a
   *  collapsible block so casual builders are not confronted with it. It stays
   *  expanded and labelled once a value is present (e.g. pasted from
   *  /csbox nbt hand), so nothing silently disappears. */
  function advancedBlock(it, p, v) {
    const has = !!((it.tagRaw || '').trim() || (it.components || '').trim());
    let inner = '';
    if (advanced && taczVisible()) {
      inner += '<div class="row inline">' +
        '<label class="mini" data-help="item.nbtHelp">' + esc(t('item.nbt')) + '<input data-f="' + esc(p + 'tagRaw') + '" value="' + esc(it.tagRaw) + '" placeholder=\'{GunId:"tacz:ak47"}\' class="w24"></label>' +
        '</div>';
    }
    if (v.components) {
      inner += '<div class="row"><label class="mini grow" data-help="item.componentsHelp">' + esc(t('item.components')) +
        '<textarea data-f="' + esc(p + 'components') + '" placeholder="' + esc(t('item.componentsHelp')) + '" rows="2" class="grow">' + esc(it.components) + '</textarea></label></div>';
    } else if (it.components) {
      inner += '<div class="row"><span class="help">' + esc(t('v.componentsIgnored')) + '</span></div>';
    }
    return '<details class="item-adv"' + (has ? ' open' : '') + '>' +
      '<summary>' + esc(t('item.adv')) +
      (has ? '<span class="adv-badge">' + esc(t('item.advFilled')) + '</span>' : '') +
      '</summary>' + inner + '</details>';
  }

  function rebuildPrices() {
    const el = card('card-prices');
    if (!el) return;
    const { rows } = NS.collectPriceRows(state, state.versionKey);
    const bodyRows = rows.map((r) => {
      const price = r.row.price;
      const parsed = NS.parsePriceInput(price);
      const priced = !!parsed;
      // Unpriced items are not recyclable at all (no grade-ladder fallback).
      let recycle = '0';
      if (priced) {
        recycle = parsed.min === parsed.max
          ? String(Math.ceil(parsed.min * 0.9))
          : String(Math.ceil(parsed.min * 0.9)) + '–' + String(Math.ceil(parsed.max * 0.9));
      }
      let status = priced ? t('prices.state.priced') : (r.key.includes('#') && !taczVisible() ? t('prices.variantUnsupported') : t('prices.state.default'));
      const gradeTag = r.grades.size
        ? [...r.grades].sort((a, b) => a - b).map((g) => 'g' + g).join(',')
        : '<span class="extra-tag">' + esc(t('prices.state.extra')) + '</span>';
      const removable = r.row.pinned && !r.grades.size;
      return '<tr data-price-key="' + esc(r.key) + '">' +
        '<td class="key-cell"><code>' + esc(r.key) + '</code><span class="tiny muted">' + gradeTag + '</span></td>' +
        '<td><input data-help="prices.priceHelp" data-role="price-input" data-key="' + esc(r.key) + '" value="' + esc(price === '' ? '' : price) + '" type="text" inputmode="numeric" class="price-input" placeholder="—"></td>' +
        '<td class="recycle-cell">' + esc(recycle) + '</td>' +
        '<td class="status-cell status-' + (priced ? 'ok' : (r.key.includes('#') && !taczVisible() ? 'warn' : 'muted')) + '">' + esc(status) + '</td>' +
        '<td>' + (removable ? '<button class="btn small danger" data-action="del-price-key" data-key="' + esc(r.key) + '" aria-label="' + esc(t('prices.remove')) + '">' + esc(t('prices.remove')) + '</button>' : '') + '</td>' +
        '</tr>';
    }).join('');
    // EV / value panel: per-grade chance and expected value per open, priced
    // at the midpoint (ranges) — mirrors the recycle column's 90% rule.
    const en = NS.boxEconomy(state);
    const gradeLabel = (gi) => ((DATA.gradeNames[gi] || {})[I18N.lang] || (DATA.gradeNames[gi] || {}).zh || ('grade' + (gi + 1)));
    const pct = (x) => (x * 100).toFixed(1) + '%';
    const econRows = en.grades.map((gr) =>
      '<tr><td>' + esc(gradeLabel(gr.gi)) + '</td>' +
      '<td>' + esc(pct(gr.prob)) + '</td>' +
      '<td class="tiny muted">' + esc(t('econ.colItems', { n: gr.items })) + '</td>' +
      '<td class="econ-ev' + (gr.ev > 0 ? '' : ' muted') + '">' + esc(gr.ev.toFixed(1)) + '</td></tr>'
    ).join('');
    const econWarns = en.warnings.map((w) => '<li>' + esc(t(w.key, w.vars)) + '</li>').join('');
    const econPanel =
      '<details class="econ-panel"><summary>' + esc(t('econ.title')) +
      '<span class="tiny muted">' + esc(t('econ.summary', { n: en.priced, u: en.unpriced })) + '</span></summary>' +
      (en.openable
        ? '<table class="price-table econ-table"><thead><tr><th>' + esc(t('econ.colGrade')) + '</th><th>' + esc(t('econ.colChance')) + '</th><th></th><th>' + esc(t('econ.colEV')) + '</th></tr></thead><tbody>' + econRows + '</tbody></table>' +
          '<p class="help">' + esc(t('econ.total')) + ' <b>' + esc(en.totalEv.toFixed(1)) + '</b>' +
          t('common.sep') + esc(t('econ.recycle')) + ' <b>' + esc(en.recycleEv.toFixed(1)) + '</b></p>'
        : '') +
      (econWarns ? '<ul class="econ-warns">' + econWarns + '</ul>' : '') +
      '<p class="help tiny">' + esc(t('econ.note')) + '</p>' +
      '</details>';
    el.innerHTML =
      '<div class="card-head"><h2>' + esc(t('prices.title')) + '</h2>' +
      '<button class="btn small" data-action="import-crate-prices">' + esc(t('prices.importCrates')) + '</button>' +
      '<button class="btn small" data-action="paste-prices">' + esc(t('prices.paste')) + '</button></div>' +
      '<p class="help">' + esc(t('prices.help')) + '</p>' +
      econPanel +
      '<table class="price-table"><thead><tr>' +
      '<th>' + esc(t('prices.key')) + '</th>' +
      '<th>' + esc(t('prices.price')) + '</th>' +
      '<th>' + esc(t('prices.recycle')) + '</th>' +
      '<th>' + esc(t('prices.state')) + '</th><th></th>' +
      '</tr></thead><tbody>' + bodyRows + '</tbody></table>' +
      '<div class="add-key-row">' +
      '<input id="extra-key-input" data-help="prices.addKeyHelp" list="csbox-item-list" placeholder="' + esc(t('prices.addKeyPh')) + '" class="grow">' +
      '<button class="btn small accent" data-action="add-price-key">' + esc(t('prices.addKey')) + '</button>' +
      '</div>';
  }

  function updatePriceRowCells(key) {
    const row = document.querySelector('#card-prices tr[data-price-key="' + CSS.escape(key) + '"]');
    if (!row) return;
    const price = state.priceRows[key] && state.priceRows[key].price;
    const parsed = NS.parsePriceInput(price);
    const priced = !!parsed;
    const rc = row.querySelector('.recycle-cell');
    const sc = row.querySelector('.status-cell');
    if (rc) rc.textContent = priced
      ? (parsed.min === parsed.max
          ? String(Math.ceil(parsed.min * 0.9))
          : String(Math.ceil(parsed.min * 0.9)) + '–' + String(Math.ceil(parsed.max * 0.9)))
      : '0';
    if (sc) {
      let status;
      let cls;
      if (priced) { status = t('prices.state.priced'); cls = 'ok'; }
      else if (key.includes('#') && !taczVisible()) { status = t('prices.variantUnsupported'); cls = 'warn'; }
      else { status = t('prices.state.default'); cls = 'muted'; }
      sc.textContent = status;
      sc.className = 'status-cell status-' + cls;
    }
  }

  /* ------------------------------ collapsible sections ------------------------------ */

  let colState = null;

  function colCollapsed(id) {
    if (colState === null) {
      colState = {};
      try {
        const raw = localStorage.getItem(LS_COLS);
        if (raw) colState = JSON.parse(raw) || {};
      } catch (e) { /* defaults */ }
      // first-run defaults: advanced / conditional sections start collapsed
      const defaults = { drop: true, term: true, meta: false, tacz: false, grades: false, prices: false };
      for (const k of Object.keys(defaults)) {
        if (colState[k] === undefined) colState[k] = defaults[k];
      }
    }
    return !!colState[id];
  }

  function toggleCol(id) {
    colCollapsed(); // initialize + defaults
    colState[id] = !colState[id];
    try { localStorage.setItem(LS_COLS, JSON.stringify(colState)); } catch (e) { /* file:// */ }
    const sec = document.getElementById('card-' + id);
    if (sec) sec.classList.toggle('collapsed', colState[id]);
  }

  /** Wraps each section card into a clickable header + collapsible body at
   * runtime. Idempotent: re-wraps when a rebuild replaces the card HTML. */
  function decorateCollapsibleCards() {
    const ids = ['meta', 'tacz', 'drop', 'term', 'grades', 'prices'];
    for (const id of ids) {
      const sec = document.getElementById('card-' + id);
      if (!sec) continue;
      const existing = sec.firstElementChild;
      if (existing && existing.classList && existing.classList.contains('col-head')) {
        const head = existing.querySelector('.col-head-h2, h2');
        sec.classList.toggle('collapsed', colCollapsed(id));
        continue;
      }
      // find the native heading block: <h2> or the .card-head wrapper
      const head = sec.querySelector(':scope > h2') || sec.querySelector(':scope > .card-head');
      if (!head) continue; // e.g. TACZ card keeps its inline layout

      const wrapper = document.createElement('div');
      wrapper.className = 'col-head';
      wrapper.dataset.col = id;
      if (!head.classList.contains('card-head')) {
        head.classList.add('col-head-h2'); // keep original h2 styling hooks
      }
      sec.insertBefore(wrapper, head);
      wrapper.appendChild(head);

      const caret = document.createElement('span');
      caret.className = 'col-caret';
      caret.setAttribute('aria-hidden', 'true');
      wrapper.appendChild(caret);

      const body = document.createElement('div');
      body.className = 'col-body';
      while (sec.firstChild && sec.firstChild !== wrapper) body.appendChild(sec.firstChild);
      sec.appendChild(body);

      sec.classList.toggle('collapsed', colCollapsed(id));
    }
  }

  /* ------------------------------ issue navigation ------------------------------ */

  function locateIssue(path) {
    let el = null;
    const gradeMatch = /^grade([1-5])\[(\d+)\](?:\.([\w-]+))?$/.exec(path);
    if (gradeMatch) {
      const gi = Number(gradeMatch[1]) - 1;
      const ii = Number(gradeMatch[2]) - 1;
      const field = gradeMatch[3];
      const card = document.getElementById('grade-items-' + gi);
      if (card) {
        el = field ? card.querySelector('[data-f="grades.' + gi + '.' + ii + '.' + field + '"]')
                   : (card.children[ii] || null);
      }
    } else if (path === 'grades') {
      const first = document.querySelector('.grade-card');
      el = first || null;
    } else if (path.indexOf('prices.') === 0) {
      const key = path.slice('prices.'.length);
      el = document.querySelector('#card-prices tr[data-price-key="' + CSS.escape(key) + '"]');
      if (!el) el = document.getElementById('card-prices');
    } else if (path === 'meta.requires') {
      el = document.querySelector('[data-f="meta.requiresText"]');
    } else if (path === 'meta.random') {
      el = document.querySelector('[data-f="meta.random.0"]');
    } else if (path === 'meta.entity') {
      el = document.querySelector('[data-f="meta.entity.0.id"]');
    } else if (path === 'meta.pity') {
      el = document.querySelector('[data-f="meta.pityGrade"]');
    } else {
      el = document.querySelector('[data-f="' + CSS.escape(path) + '"]');
    }
    if (!el) el = document.getElementById('card-meta');
    revealForFlash(el);
    flashElement(el);
  }

  /** A flash target hidden inside a collapsed container would scroll to
   *  nothing visible, so expand every collapsed ancestor first: grade
   *  <details>, advanced-data <details> and collapsible section cards
   *  (updating their persisted open/collapsed state, same as manual
   *  toggling). */
  function revealForFlash(el) {
    if (!el) return;
    for (let n = el; n; n = n.parentElement) {
      if (n.tagName === 'DETAILS' && !n.open) {
        n.open = true;
        if (n.classList.contains('grade-card') && n.dataset.grade !== undefined) {
          const gi = Number(n.dataset.grade);
          gradeOpen[gi] = true;
          try { localStorage.setItem(LS_GRADE_OPEN, JSON.stringify(gradeOpen)); } catch (e) { /* file:// */ }
        }
      }
      if (n.classList && n.classList.contains('collapsed') && (n.id || '').indexOf('card-') === 0) {
        n.classList.remove('collapsed');
        colCollapsed(); // initialize the persisted map on first use
        colState[n.id.slice(5)] = false;
        try { localStorage.setItem(LS_COLS, JSON.stringify(colState)); } catch (e) { /* file:// */ }
      }
    }
  }

  function flashElement(el) {
    if (!el) return;
    if (typeof el.scrollIntoView === 'function') el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    el.classList.remove('flash-highlight');
    void el.offsetWidth; // restart the pulse animation on repeated clicks
    el.classList.add('flash-highlight');
    clearTimeout(el._flashT);
    el._flashT = setTimeout(() => el.classList.remove('flash-highlight'), 2200);
  }

  /* ------------------------------ preview & validation ------------------------------ */

  function schedulePreview(delay) {
    clearTimeout(previewTimer);
    previewTimer = setTimeout(() => rebuildPreview(), delay === undefined ? 90 : delay);
  }

  function currentJson() {
    return isPricesPage() ? NS.buildPrices(state) : NS.buildBox(state);
  }

  function rebuildPreview() {
    decorateCollapsibleCards();
    const pre = $('#json-preview');
    // Only the current page's JSON is shown; skip building the other one.
    const out = isPricesPage() ? NS.buildPrices(state) : NS.buildBox(state, !compact);
    pre.textContent = JSON.stringify(out, null, 2);
    // sync flash: light the left accent bar so the user sees the preview
    // re-synced; restart the animation on rapid keystrokes.
    pre.classList.remove('json-sync');
    void pre.offsetWidth;
    pre.classList.add('json-sync');
    rebuildProbPanel();
    renderSimPanel();

    const navBox = $('#nav-box');
    const navPrices = $('#nav-prices');
    if (navBox) navBox.className = 'tab' + (isPricesPage() ? '' : ' active');
    if (navPrices) navPrices.className = 'tab' + (isPricesPage() ? ' active' : '');
    const pathHint = $('#path-hint');
    if (pathHint) {
      const fname = (state.fileName || '').trim() || 'box';
      pathHint.textContent = isPricesPage()
        ? 'config/csbox/_prices.json'
        : 'config/csbox/' + fname + '.json';
    }

    scheduleValidation();
    saveStateDebounced();
    syncFileBarLabels();

    const undoBtn = $('#btn-undo');
    const redoBtn = $('#btn-redo');
    if (undoBtn) undoBtn.disabled = !history.undo.length;
    if (redoBtn) redoBtn.disabled = !history.redo.length;
  }

  /** Right-sidebar view switch: JSON preview ↔ probability table ↔ simulator. */
  let previewView = 'json'; // 'json' | 'prob' | 'sim'
  const LS_VIEW = 'cs2box-editor-view';
  /** One-shot: the next rebuildProbPanel renders rows with an entrance
   *  stagger (set when the user switches to the probability view). */
  let probAnimateNext = false;

  function setPreviewView(view) {
    previewView = view === 'prob' || view === 'sim' ? view : 'json';
    probAnimateNext = previewView === 'prob';
    try { localStorage.setItem(LS_VIEW, previewView); } catch (e) { /* file:// */ }
    const jsonTab = $('#view-json');
    const probTab = $('#view-prob');
    const simTab = $('#view-sim');
    const pre = $('#json-preview');
    const panel = $('#prob-panel');
    const sim = $('#sim-panel');
    if (jsonTab) jsonTab.classList.toggle('active', previewView === 'json');
    if (probTab) probTab.classList.toggle('active', previewView === 'prob');
    if (simTab) simTab.classList.toggle('active', previewView === 'sim');
    if (pre) pre.hidden = previewView !== 'json';
    if (panel) panel.hidden = previewView !== 'prob';
    if (sim) sim.hidden = previewView !== 'sim';
  }

  /** Renders the probability table (or keeps the panel empty when the box
   *  page is not the active view). Probabilities come from the pure
   *  NS.computeProbabilities so they stay in sync with the in-game model. */
  function rebuildProbPanel() {
    const panel = $('#prob-panel');
    if (!panel) return;
    if (isPricesPage() || previewView !== 'prob') return;
    const { openable, grades } = NS.computeProbabilities(state);
    // Entrance stagger runs once per view switch (inline animation styles on
    // this render only; ordinary keystroke re-renders stay still).
    const animate = probAnimateNext && !prefersReducedMotion();
    probAnimateNext = false;
    let rowIdx = 0;
    const rowStyle = () => {
      if (!animate) return '';
      const delay = Math.min(rowIdx++ * 12, 180);
      return ' style="animation: prob-row-in .18s ease backwards; animation-delay:' + delay + 'ms"';
    };
    // Expected value (Armory Points): mid of the configured fixed/range price;
    // null when the key is unpriced (shown as an em dash).
    const priceMidOf = (key) => {
      if (!key) return null;
      const row = state.priceRows[key];
      if (!row) return null;
      return NS.parsePriceInput(row.price); // {min, max} | null
    };
    const fmtMid = (n) => Number.isInteger(n) ? String(n) : n.toFixed(1);
    const evCell = (key) => {
      const p = priceMidOf(key);
      if (!p) return '<td class="prob-ev">—</td>';
      const mid = (p.min + p.max) / 2;
      const title = p.min === p.max ? '' : ' title="' + esc(t('ev.rangeTitle', { min: p.min, max: p.max, mid: fmtMid(mid) })) + '"';
      return '<td class="prob-ev"' + title + '>' + esc(fmtMid(mid)) + '</td>';
    };
    let evTotal = 0;
    let recycleTotal = 0;
    const rows = grades.map((g) => {
      const gradeName = esc((DATA.gradeNames[g.gi] || {})[I18N.lang] || (DATA.gradeNames[g.gi] || {}).zh || 'grade' + (g.gi + 1));
      let gradeEv = 0;
      const gradeRows = g.items.length
        ? g.items.map((it) => {
          // A note (备注) replaces the raw id as the display name; the source
          // id stays visible in a muted style for traceability.
          const nameHtml = it.note
            ? esc(it.note) + ' <span class="prob-src-id">' + esc(it.value) + '</span>'
            : esc(it.value);
          const p = priceMidOf(it.priceKey);
          if (p && !it.disabled) gradeEv += it.itemProb * (p.min + p.max) / 2;
          return '<tr class="prob-item"' + rowStyle() + '>' +
          '<td class="prob-item-name">' + nameHtml + (it.disabled ? ' <span class="prob-disabled">' + esc(t('prob.disabled')) + '</span>' : '') + '</td>' +
          '<td>' + (it.disabled ? '—' : esc(fmtProb(it.itemProb))) + '</td>' +
          '<td>' + (it.disabled ? '—' : esc(fmtProb(g.gradeProb * it.itemProb))) + '</td>' +
          evCell(it.priceKey) +
          '</tr>';
        }).join('')
        : '<tr class="prob-empty"' + rowStyle() + '><td colspan="4">' + esc(t('prob.emptyGrade')) + '</td></tr>';
      evTotal += g.gradeProb * gradeEv;
      recycleTotal += g.gradeProb * gradeEv * 0.9;
      return '<tr class="prob-grade"' + rowStyle() + '>' +
        '<td><b>' + gradeName + '</b></td>' +
        '<td>' + esc(String(g.weight)) + '</td>' +
        '<td>' + esc(fmtProb(g.gradeProb)) + '</td>' +
        '<td class="prob-ev">' + (gradeEv > 0 ? esc(fmtMid(Math.round(gradeEv * 10) / 10)) : '—') + '</td>' +
        '</tr>' + gradeRows;
    }).join('');
    const evFooter = openable
      ? '<tr class="prob-footer">' +
        '<td colspan="3">' + esc(t('ev.total')) + '</td>' +
        '<td class="prob-ev">' + esc(fmtMid(Math.round(evTotal * 10) / 10)) + '</td>' +
        '</tr>' +
        '<tr class="prob-footer muted">' +
        '<td colspan="3">' + esc(t('ev.recycle')) + '</td>' +
        '<td class="prob-ev">' + esc(fmtMid(Math.ceil(recycleTotal))) + '</td>' +
        '</tr>'
      : '';
    panel.innerHTML = '<h2>' + esc(t('prob.title')) + '</h2>' +
      (openable ? '' : '<p class="prob-warn">' + esc(t('prob.unopenable')) + '</p>') +
      '<table class="prob-table"><thead><tr>' +
      '<th>' + esc(t('prob.item')) + '</th>' +
      '<th>' + esc(t('prob.inGrade')) + '</th>' +
      '<th>' + esc(t('prob.overall')) + '</th>' +
      '<th>' + esc(t('ev.column')) + '</th>' +
      '</tr></thead><tbody>' + rows + evFooter + '</tbody></table>' +
      '<p class="help">' + esc(t('prob.note')) + '</p>';
  }

  /** Percent formatter: 0 → "0%"; small values keep 3 decimals so rare
   *  drops stay readable (e.g. 0.026% for the default classified tier). */
  function fmtProb(p) {
    if (!(p > 0)) return '0%';
    const pc = p * 100;
    if (pc >= 0.1) return pc.toFixed(2).replace(/\.?0+$/, '') + '%';
    if (pc >= 0.001) return pc.toFixed(3) + '%';
    return pc.toFixed(4) + '%';
  }

  let validateTimer = null;
  let lastIssuesKey = '';

  /** Idle validation: rebuildPreview reruns on every keystroke; validating
   *  and re-rendering the issues list is O(total items), so defer it 300 ms
   *  and skip the re-render when nothing changed. */
  function scheduleValidation() {
    clearTimeout(validateTimer);
    validateTimer = setTimeout(() => {
      validateTimer = null;
      const issues = NS.validate(state, state.versionKey);
      const key = JSON.stringify(issues);
      if (key !== lastIssuesKey) {
        lastIssuesKey = key;
        renderIssues(issues);
      }
    }, 300);
  }

  /** Translates validator-reported vars into the current UI language (grade
   *  tiers arrive as 'grade1'..'grade5' keys; the validator itself is
   *  language-agnostic). */
  function localizeIssueVars(vars) {
    if (!vars) return vars;
    const out = Object.assign({}, vars);
    if (typeof out.grade === 'string' && /^grade[1-5]$/.test(out.grade)) {
      const gi = Number(out.grade.slice(5)) - 1;
      const g = DATA.gradeNames[gi] || {};
      out.grade = g[I18N.lang] || g.zh || out.grade;
    }
    return out;
  }

  /** Counts from the previous renderIssues call, for the change-pulse on the
   *  summary badges (null until the first render → no pulse on load). */
  let lastIssueCounts = null;

  function renderIssues(issues) {
    const root = $('#issues-root');
    const errors = issues.filter((i) => i.level === 'error').length;
    const warns = issues.filter((i) => i.level === 'warn').length;
    const infos = issues.filter((i) => i.level === 'info').length;
    let pulsedLevel = null;
    if (lastIssueCounts) {
      for (const lvl of ['error', 'warn', 'info']) {
        const now = lvl === 'error' ? errors : lvl === 'warn' ? warns : infos;
        if (now !== lastIssueCounts[lvl]) { pulsedLevel = lvl; break; }
      }
    }
    lastIssueCounts = { error: errors, warn: warns, info: infos };
    const badgeCls = (lvl, label) =>
      '<span class="badge ' + lvl + (lvl === pulsedLevel ? ' badge-pulse' : '') + '">' +
      esc(t(label, { n: lvl === 'error' ? errors : lvl === 'warn' ? warns : infos })) + '</span>';
    let html = '<h2>' + esc(t('validate.title')) + '</h2>';
    if (!issues.length) {
      html += '<div class="issue ok">' + esc(t('validate.ok')) + '</div>';
    } else {
      html += '<div class="issue-summary">' +
        badgeCls('error', 'validate.errors') +
        badgeCls('warn', 'validate.warns') +
        badgeCls('info', 'validate.infos') +
        '</div>' +
        '<ul class="issue-list">' +
        issues.map((i) =>
          '<li class="issue ' + i.level + '" data-issue-path="' + esc(i.path) + '" title="' + esc(t('validate.jump')) + '"><span class="issue-path">' + esc(i.path) + '</span> ' +
          esc(t(i.key, localizeIssueVars(i.vars))) + '</li>').join('') +
        '</ul>';
    }
    root.innerHTML = html; /* esc-exempt: every dynamic part of `html` is esc()'d above */
  }

  /* ------------------------------ open simulator ------------------------------ */

  const SIM_CELLS = 48;        // cells in the roll strip
  const SIM_WIN_IDX = 42;      // winning cell (past the right edge of a narrow panel)
  const SIM_CELL_W = 96;       // px, must match .sim-cell CSS
  const SIM_ROLL_MS = 3800;    // must match the CSS transition duration

  let simStats = null; // { opened, gradeCounts:[5], items:[{gi,idx,value,note,hits,itemTotal,theory}], gradeTheory:[5], configKey, lastResult }
  let simRunning = false;

  function simCurrentConfigKey() {
    try { return JSON.stringify([state.grades, state.meta.random]); } catch (e) { return ''; }
  }

  function simEnsureStats() {
    const configKey = simCurrentConfigKey();
    if (!simStats || simStats.configKey !== configKey) {
      const { grades } = NS.computeProbabilities(state);
      simStats = {
        opened: 0,
        gradeCounts: [0, 0, 0, 0, 0],
        items: [],
        gradeTheory: grades.map((g) => g.gradeProb),
        configKey,
        lastResult: null,
      };
    }
    return simStats;
  }

  function simApplyResult(res) {
    const st = simEnsureStats();
    st.opened += res.opened;
    for (let i = 0; i < 5; i++) st.gradeCounts[i] += res.gradeCounts[i];
    const map = new Map(st.items.map((e) => [e.gi + ':' + e.idx, e]));
    for (const it of res.items) {
      const key = it.gi + ':' + it.idx;
      const agg = map.get(key);
      if (agg) {
        agg.hits += it.hits;
        agg.itemTotal += it.itemTotal;
      } else {
        const copy = Object.assign({}, it);
        st.items.push(copy);
        map.set(key, copy);
      }
    }
    if (res.last) st.lastResult = res.last;
  }

  function simBatch(n) {
    if (simRunning) return;
    const { openable } = NS.computeProbabilities(state);
    if (!openable) { renderSimPanel(); return; }
    simApplyResult(NS.simulateOpens(state, n));
    renderSimPanel();
  }

  function simSingle() {
    if (simRunning) return;
    const { openable } = NS.computeProbabilities(state);
    if (!openable) { renderSimPanel(); return; }
    if (!prefersReducedMotion()) {
      simRunning = true;
      const res = NS.simulateOpens(state, 1);
      renderSimPanel(); // fresh shell with disabled buttons before the roll
      simRollStrip(res.last, () => {
        simApplyResult(res);
        simRunning = false;
        renderSimPanel();
      });
    } else {
      const res = NS.simulateOpens(state, 1);
      simApplyResult(res);
      renderSimPanel();
    }
  }

  /** CS2-style roll: a strip of sampled items slides left and decelerates on
   *  the pre-determined winner. The result is known before the animation —
   *  same as the server-authoritative roll in-game. */
  function simRollStrip(winner, onDone) {
    const wrap = $('#sim-strip-wrap');
    const strip = $('#sim-strip');
    if (!wrap || !strip) { onDone(); return; }
    const cells = [];
    for (let i = 0; i < SIM_CELLS; i++) {
      cells.push(i === SIM_WIN_IDX ? winner : (NS.simulateOpens(state, 1).last || winner));
    }
    strip.innerHTML = cells.map((c, i) => {
      const gi = c ? c.gi : 0;
      const label = c ? (c.note || c.value) : '';
      return '<div class="sim-cell g' + (gi + 1) + (i === SIM_WIN_IDX ? ' sim-win" data-win="1"' : '"') + '>' +
        '<span>' + esc(label) + '</span></div>';
    }).join('');
    const offset = SIM_WIN_IDX * SIM_CELL_W + SIM_CELL_W / 2 - wrap.clientWidth / 2;
    strip.style.transition = 'none';
    strip.style.transform = 'translateX(0px)';
    void strip.offsetWidth; // commit the start position before animating
    strip.style.transition = 'transform ' + (SIM_ROLL_MS / 1000) + 's cubic-bezier(.12, .6, .08, 1)';
    strip.style.transform = 'translateX(-' + offset + 'px)';
    setTimeout(() => {
      const win = strip.querySelector('.sim-win');
      if (win) win.classList.add('hit');
      const line = $('#sim-result');
      if (line && winner) {
        line.innerHTML = '<span class="sim-result-label">' + esc(t('sim.result')) + ':</span> ' +
          '<span class="g' + (winner.gi + 1) + '">' + esc(winner.note || winner.value) + '</span>' +
          (winner.count > 1 ? ' ×' + winner.count : '');
      }
      onDone();
    }, SIM_ROLL_MS + 60);
  }

  function renderSimPanel() {
    const panel = $('#sim-panel');
    if (!panel || isPricesPage() || previewView !== 'sim') return;
    if (simRunning) return; // never rebuild under an in-flight roll animation
    const { openable } = NS.computeProbabilities(state);
    const stale = simStats && simStats.configKey !== simCurrentConfigKey();
    const dis = simRunning ? ' disabled' : '';
    // last single-roll result survives re-renders (renderSimPanel rebuilds the
    // panel after each run, which would otherwise wipe the line)
    const lr = simStats && simStats.lastResult;
    const resultHtml = lr
      ? '<span class="sim-result-label">' + esc(t('sim.result')) + ':</span> ' +
        '<span class="g' + (lr.gi + 1) + '">' + esc(lr.note || lr.value) + '</span>' +
        (lr.count > 1 ? ' ×' + lr.count : '')
      : '';
    let html = '<h2>' + esc(t('sim.title')) + '</h2>' +
      '<div class="sim-actions">' +
      '<button class="btn small accent" data-sim-action="run1"' + dis + '>' + esc(t('sim.run1')) + '</button>' +
      '<button class="btn small" data-sim-action="run10"' + dis + '>' + esc(t('sim.run10')) + '</button>' +
      '<button class="btn small" data-sim-action="run100"' + dis + '>' + esc(t('sim.run100')) + '</button>' +
      '<button class="btn small" data-sim-action="reset">' + esc(t('sim.reset')) + '</button>' +
      '</div>' +
      '<div class="sim-result-line" id="sim-result">' + resultHtml + '</div>' +
      '<div class="sim-strip-wrap" id="sim-strip-wrap"><div class="sim-strip" id="sim-strip"></div><div class="sim-marker"></div></div>';
    if (stale) html += '<p class="sim-stale">' + esc(t('sim.stale')) + '</p>';
    if (!openable) {
      html += '<p class="prob-warn">' + esc(t('sim.empty')) + '</p>';
    } else if (!simStats || !simStats.opened) {
      html += '<p class="help">' + esc(t('sim.intro')) + '</p>';
    } else {
      const st = simStats;
      html += '<p class="sim-count">' + esc(t('sim.count')) + ': <b>' + st.opened + '</b></p>';
      html += '<table class="prob-table sim-table"><thead><tr>' +
        '<th>' + esc(t('sim.item')) + '</th>' +
        '<th>' + esc(t('sim.hits')) + '</th>' +
        '<th>' + esc(t('sim.actual')) + '</th>' +
        '<th>' + esc(t('sim.theory')) + '</th>' +
        '</tr></thead><tbody>';
      const pct = (hits) => esc(fmtProb(st.opened ? hits / st.opened : 0));
      for (let gi = 0; gi < 5; gi++) {
        if (!st.gradeCounts[gi] && !(st.gradeTheory[gi] > 0)) continue;
        const gname = esc((DATA.gradeNames[gi] || {})[I18N.lang] || (DATA.gradeNames[gi] || {}).zh || 'grade' + (gi + 1));
        html += '<tr class="prob-grade"><td colspan="4"><span class="grade-badge g' + (gi + 1) + '">' + gname + '</span></td></tr>';
        const items = st.items.filter((it) => it.gi === gi).sort((a, b) => b.hits - a.hits);
        for (const it of items) {
          html += '<tr class="prob-item">' +
            '<td class="prob-item-name">' + esc(it.note || it.value) + (it.note ? ' <span class="prob-src-id">' + esc(it.value) + '</span>' : '') + '</td>' +
            '<td>' + it.hits + '</td>' +
            '<td>' + pct(it.hits) + '</td>' +
            '<td>' + esc(fmtProb(it.theory)) + '</td>' +
            '</tr>';
        }
        if (!items.length) {
          html += '<tr class="prob-item"><td colspan="4" class="muted">' + esc(t('sim.noHits')) + '</td></tr>';
        }
      }
      html += '</tbody></table>';
    }
    panel.innerHTML = html; /* esc-exempt: every dynamic part of `html` is esc()'d above */
  }

  function setupSimPanel() {
    const panel = $('#sim-panel');
    if (!panel) return;
    panel.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-sim-action]');
      if (!btn || btn.disabled) return;
      const act = btn.dataset.simAction;
      if (act === 'run1') simSingle();
      else if (act === 'run10') simBatch(10);
      else if (act === 'run100') simBatch(100);
      else if (act === 'reset') { simStats = null; renderSimPanel(); }
    });
  }

  /* ------------------------------ import / export ------------------------------ */

  /** Returns the detected import type ('box' | 'prices') so the import
   *  dialog can keep itself open (and keep the text!) when parsing fails —
   *  e.g. a broken config file from a #pack= link is fixed in place.
   *  Returns null when nothing was imported. */
  function applyImportText(text, fileName) {
    let obj;
    try {
      obj = JSON.parse(text);
    } catch (e) {
      toast(t('toast.invalidJson', { err: e.message }), true);
      return null;
    }
    const type = detectType(obj);
    if (type === 'box') {
      pushHistory();
      const base = fileName ? fileName.replace(/\.json$/i, '') : (obj.name && String(obj.name).replace(/[^a-z0-9_.\/-]/gi, '_').toLowerCase()) || 'imported';
      const prevTacz = state.taczEnabled;
      const res = NS.boxToState(obj, base);
      state = res.state;
      state.taczEnabled = prevTacz;
      state.versionKey = state.versionKey || '1.21.1';
      const mig = NS.mergeMigrations(state, res.migrations);
      renderAll();
      pushHistory(); // snapshot the imported state so undo can step back through it
      if (mig.migrated) toast(t('toast.migrated', { n: mig.migrated }));
      else if (mig.bad) toast(t('toast.ignoredBadPrice', { n: mig.bad }), true);
      else toast(t('toast.importBox', { file: fileName || base }));
      return 'box';
    } else if (type === 'prices') {
      pushHistory();
      const n = NS.mergePrices(state, obj);
      rebuildPrices();
      schedulePreview();
      pushHistory(); // snapshot imported prices so undo steps back through them
      toast(t('toast.importPrices', { n }));
      return 'prices';
    }
    toast(t('toast.unknownType'), true);
    return null;
  }

  /** "Legacy import" entry: pre-v2.0.1 box JSON (items may carry inline
   *  `price`). Same recognition as a normal import plus the legacy
   *  terminal.json inference; everything is exported in the current-version
   *  format (prices moved to _prices.json, terminal type explicit, key
   *  dropped for terminals). ChloePrime / pre-2.0.1 files stored the five
   *  grade weights REVERSED — if the `random` array looks ascending it is
   *  auto-reversed (undoable) so exported weights mean grade1..grade5. */
  function applyLegacyImportText(text, fileName) {
    let obj;
    try {
      obj = JSON.parse(text);
    } catch (e) {
      toast(t('toast.invalidJson', { err: e.message }), true);
      return;
    }
    if (detectType(obj) !== 'box') {
      toast(t('toast.legacyNotBox'), true);
      return;
    }
    let reversedWeights = false;
    if (Array.isArray(obj.random) && NS.weightOrderIsReversed(obj.random)) {
      obj = JSON.parse(text); // re-parse so we mutate a copy, not the pasted text
      obj.random = obj.random.slice().reverse();
      reversedWeights = true;
    }
    pushHistory();
    const base = fileName ? String(fileName).replace(/\.json$/i, '') : (obj.name && String(obj.name).replace(/[^a-z0-9_.\/-]/gi, '_').toLowerCase()) || 'imported';
    const prevTacz = state.taczEnabled;
    const res = NS.boxToState(obj, base, { legacy: true });
    state = res.state;
    state.taczEnabled = prevTacz;
    state.versionKey = state.versionKey || '1.21.1';
    const mig = NS.mergeMigrations(state, res.migrations);
    renderAll();
    pushHistory(); // snapshot the imported state so undo can step back through it
    const revNote = reversedWeights ? t('toast.legacyWeightsReversed') + t('common.sep') : '';
    const typeName = state.meta.type === 'terminal' ? t('type.terminal') : t('type.csbox');
    if (mig.migrated) {
      toast(revNote + t('toast.legacyImported', { file: fileName || base, n: mig.migrated, type: typeName }));
    } else {
      toast(revNote + t('toast.legacyImportedNoPrice', { file: fileName || base, type: typeName }));
    }
  }

  function detectType(obj) {
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null;
    if (!Object.keys(obj).length) {
      // An empty object is a legal empty price table (PriceTable.parse accepts
      // it); a box config can never legitimately be empty, so treat it as prices
      // rather than refusing the import.
      return 'prices';
    }
    for (let i = 1; i <= 5; i++) {
      if (obj['grade' + i] !== undefined) return 'box';
    }
    if (obj.name !== undefined || obj.type !== undefined) return 'box';
    const keys = Object.keys(obj);
    const priceValueOk = (v) =>
        typeof v === 'number' ||
        (Array.isArray(v) && v.length === 2 && v.every((x) => typeof x === 'number'));
    if (keys.every((k) => /^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(k)) &&
        keys.every((k) => priceValueOk(obj[k]))) {
      return 'prices';
    }
    return null;
  }

  function loadExample(index, announce) {
    const ex = DATA.examples[index];
    if (!ex) return;
    const prevTacz = state.taczEnabled;
    const res = NS.boxToState(ex.box, ex.file.replace(/\.json$/i, ''));
    state = res.state;
    state.taczEnabled = prevTacz;
    state.versionKey = state.versionKey || '1.21.1';
    NS.mergeMigrations(state, res.migrations);
    NS.mergePrices(state, ex.prices || {});
    renderAll();
    pushHistory(); // snapshot the loaded state so undo restores it (not just the old draft)
    if (announce) toast(t('toast.example', { name: ex.desc[I18N.lang] }));
  }

  function currentText() {
    return $('#json-preview').textContent;
  }

  function copyCurrent() {
    copyText(currentText(), t('preview.copied'));
  }

  function downloadBox() {
    const fname = (state.fileName || '').trim() || 'box';
    downloadJSON(fname + '.json', NS.buildBox(state));
  }

  function downloadPrices() {
    downloadJSON('_prices.json', NS.buildPrices(state));
  }

  function downloadCurrent() {
    if (isPricesPage()) downloadPrices();
    else downloadBox();
  }

  function downloadJSON(fileName, obj) {
    const blob = new Blob([JSON.stringify(obj, null, 2) + '\n'], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  }

  function saveStateDebounced() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(saveState, 300);
  }
})();