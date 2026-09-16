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

  const $ = (sel) => document.querySelector(sel);

  document.addEventListener('DOMContentLoaded', () => {
    bindStatics();
    loadPrefs();
    setupHeader();
    setupDialogs();
    setupDelegation();
    setupFileInput();
    setupShortcuts();
    initTooltip();
    if (!loadHashState() && !loadState()) loadExample(0, false);
    window.addEventListener('hashchange', () => {
      if (/[#&]state=/.test(location.hash)) location.reload();
    });
    window.addEventListener('storage', (e) => {
      if (e.key && e.key !== LS_STATE && e.key !== LS_VER && e.key !== LS_LANG && e.key !== LS_ADV) return;
      loadPrefs();
      if (!loadState()) renderAll();
    });
    schedulePreview(0);
  });

  /* ------------------------------ helpers ------------------------------ */

  function esc(s) {
    return String(s === undefined || s === null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
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

  function saveState() {
    try {
      const payload = {
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
      localStorage.setItem(LS_STATE, JSON.stringify(payload));
      storageWarned = false;
    } catch (e) {
      // storage may be unavailable under file:// or quota exceeded — warn once
      // so the user knows a refresh would lose their work.
      if (!storageWarned) {
        storageWarned = true;
        toast(t('toast.saveFailed'), true);
      }
    }
  }

  function loadState() {
    try {
      const raw = localStorage.getItem(LS_STATE);
      if (!raw) return false;
      const payload = JSON.parse(raw);
      if (!payload || typeof payload !== 'object' || !payload.state) return false;
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
    if (view === 'prob' || view === 'json') previewView = view;
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
    };
    for (const id of Object.keys(map)) {
      const el = document.getElementById(id);
      if (el) el.textContent = t(map[id]);
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
  }

  /* ------------------------------ header / dialogs ------------------------------ */

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
    verSel.innerHTML = DATA.versions.map((v) =>
      '<option value="' + esc(v.key) + '">' + esc(v.label[I18N.lang]) + '</option>').join('');
    verSel.value = state.versionKey;
    verSel.addEventListener('change', () => {
      pushHistory();
      state.versionKey = verSel.value;
      localStorage.setItem(LS_VER, state.versionKey);
      renderAll();
      const v = version();
      toast(t('version.note') + ': ' + v.note[I18N.lang]);
    });

    $('#btn-new').addEventListener('click', () => {
      pushHistory();
      const prevTacz = state.taczEnabled;
      state = NS.emptyState();
      state.versionKey = state.versionKey || '1.21.1';
      state.taczEnabled = prevTacz;
      renderAll();
      pushHistory(); // snapshot the fresh state; undo then returns to it
      toast(t('toast.newDone'));
    });

    $('#btn-import').addEventListener('click', () => {
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
    if (viewJsonTab && viewProbTab) {
      setPreviewView(previewView); // apply remembered/inline view state + labels
      viewJsonTab.addEventListener('click', () => {
        setPreviewView('json');
        rebuildPreview();
      });
      viewProbTab.addEventListener('click', () => {
        setPreviewView('prob');
        rebuildPreview();
      });
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
    $('#import-cancel').addEventListener('click', () => imp.close());
    $('#import-do').addEventListener('click', () => {
      const text = document.getElementById('import-textarea').value;
      if (text.trim()) {
        applyImportText(text, null);
        document.getElementById('import-textarea').value = '';
      }
      imp.close();
    });

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
    list.innerHTML = DATA.examples.map((ex, i) => {
      return '<li class="example-item">' +
        '<div class="example-desc">' +
        '<strong>' + esc(ex.desc[I18N.lang]) + '</strong>' +
        '<code>' + esc(ex.file) + '</code>' +
        '</div>' +
        '<button class="btn small" data-action="load-example" data-index="' + i + '">' + esc(t('example.load')) + '</button>' +
        '</li>';
    }).join('');
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
    let msg = parts.join('；');
    if (bad) msg += '；' + t('toast.crateBadDocs', { n: bad });
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
        const raw = localStorage.getItem(LS_STATE);
        if (raw) {
          const p = JSON.parse(raw);
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
          rebuildGrade(gi);
          schedulePreview();
          break;
        }
        case 'del-item': {
          pushHistory();
          const gi = Number(btn.dataset.grade);
          const ii = Number(btn.dataset.index);
          state.grades[gi].splice(ii, 1);
          rebuildGrade(gi);
          schedulePreview();
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
          rebuildMeta();
          break;
        }
        case 'del-entity': {
          pushHistory();
          const idx = Number(btn.dataset.index);
          state.meta.entity.splice(idx, 1);
          if (!state.meta.entity.length) state.meta.entity.push({ id: '', rate: '' });
          rebuildMeta();
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
          $('#tutorial-body').innerHTML = t('tacz.tutorial');
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
    list.innerHTML = (DATA.entitySuggestions || []).map((e) =>
      '<option value="minecraft:' + esc(e.id) + '" label="' + esc(e[I18N.lang] || e.en || e.id) + '">').join('');
  }

  function renderAll() {
    bindStatics();
    buildEntityDatalist();
    const verSel = $('#version-select');
    verSel.innerHTML = DATA.versions.map((v) =>
      '<option value="' + esc(v.key) + '">' + esc(v.label[I18N.lang]) + '</option>').join('');
    verSel.value = state.versionKey;
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
      field('term.restock', 'term.restockHelp', input('meta.restockMinutes', m.restockMinutes, '30', false)) +
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

  function rebuildGrade(gi) {
    const host = $('#grade-items-' + gi);
    if (!host) return;
    const items = state.grades[gi];
    const v = version();
    if (!items.length) {
      host.innerHTML = '<div class="muted">' + esc(t('grade.empty')) + '</div>';
      return;
    }
    withFocusRestore(() => {
      host.innerHTML = items.map((it, ii) => renderItem(gi, ii, it, v)).join('');
      const badge = document.querySelector('.grade-card[data-grade="' + gi + '"] .count-badge');
      if (badge) badge.textContent = items.length;
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
      '" placeholder="' + (it.source === 'tag' ? '#minecraft:swords' : it.source === 'loot_table' ? 'minecraft:chests/simple_dungeon' : 'minecraft:diamond_sword') + '" class="grow">' +
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
    el.innerHTML =
      '<div class="card-head"><h2>' + esc(t('prices.title')) + '</h2>' +
      '<button class="btn small" data-action="import-crate-prices">' + esc(t('prices.importCrates')) + '</button>' +
      '<button class="btn small" data-action="paste-prices">' + esc(t('prices.paste')) + '</button></div>' +
      '<p class="help">' + esc(t('prices.help')) + '</p>' +
      '<table class="price-table"><thead><tr>' +
      '<th>' + esc(t('prices.key')) + '</th>' +
      '<th>' + esc(t('prices.price')) + '</th>' +
      '<th>' + esc(t('prices.recycle')) + '</th>' +
      '<th>' + esc(t('prices.state')) + '</th><th></th>' +
      '</tr></thead><tbody>' + bodyRows + '</tbody></table>' +
      '<div class="add-key-row">' +
      '<input id="extra-key-input" data-help="prices.addKeyHelp" placeholder="' + esc(t('prices.addKeyPh')) + '" class="grow">' +
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
    previewTimer = setTimeout(() => {
      rebuildPreview();
      clearTimeout(saveTimer);
      saveTimer = setTimeout(saveState, 250);
    }, delay === undefined ? 90 : delay);
  }

  function currentJson() {
    return isPricesPage() ? NS.buildPrices(state) : NS.buildBox(state);
  }

  function rebuildPreview() {
    decorateCollapsibleCards();
    const pre = $('#json-preview');
    const boxObj = NS.buildBox(state, !compact);
    const pricesObj = NS.buildPrices(state);
    const out = isPricesPage() ? pricesObj : boxObj;
    pre.textContent = JSON.stringify(out, null, 2);
    rebuildProbPanel();

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

    const undoBtn = $('#btn-undo');
    const redoBtn = $('#btn-redo');
    if (undoBtn) undoBtn.disabled = !history.undo.length;
    if (redoBtn) redoBtn.disabled = !history.redo.length;
  }

  /** Right-sidebar view switch: JSON preview ↔ probability table. */
  let previewView = 'json'; // 'json' | 'prob'
  const LS_VIEW = 'cs2box-editor-view';

  function setPreviewView(view) {
    previewView = view === 'prob' ? 'prob' : 'json';
    try { localStorage.setItem(LS_VIEW, previewView); } catch (e) { /* file:// */ }
    const jsonTab = $('#view-json');
    const probTab = $('#view-prob');
    const pre = $('#json-preview');
    const panel = $('#prob-panel');
    if (jsonTab) jsonTab.classList.toggle('active', previewView === 'json');
    if (probTab) probTab.classList.toggle('active', previewView === 'prob');
    if (pre) pre.hidden = previewView !== 'json';
    if (panel) panel.hidden = previewView !== 'prob';
  }

  /** Renders the probability table (or keeps the panel empty when the box
   *  page is not the active view). Probabilities come from the pure
   *  NS.computeProbabilities so they stay in sync with the in-game model. */
  function rebuildProbPanel() {
    const panel = $('#prob-panel');
    if (!panel) return;
    if (isPricesPage() || previewView !== 'prob') return;
    const { openable, grades } = NS.computeProbabilities(state);
    const rows = grades.map((g) => {
      const gradeName = esc((DATA.gradeNames[g.gi] || {})[I18N.lang] || (DATA.gradeNames[g.gi] || {}).zh || 'grade' + (g.gi + 1));
      const gradeRows = g.items.length
        ? g.items.map((it) => {
          // A note (备注) replaces the raw id as the display name; the source
          // id stays visible in a muted style for traceability.
          const nameHtml = it.note
            ? esc(it.note) + ' <span class="prob-src-id">' + esc(it.value) + '</span>'
            : esc(it.value);
          return '<tr class="prob-item">' +
          '<td class="prob-item-name">' + nameHtml + (it.disabled ? ' <span class="prob-disabled">' + esc(t('prob.disabled')) + '</span>' : '') + '</td>' +
          '<td>' + (it.disabled ? '—' : esc(fmtProb(it.itemProb))) + '</td>' +
          '<td>' + (it.disabled ? '—' : esc(fmtProb(g.gradeProb * it.itemProb))) + '</td>' +
          '</tr>';
        }).join('')
        : '<tr class="prob-empty"><td colspan="3">' + esc(t('prob.emptyGrade')) + '</td></tr>';
      return '<tr class="prob-grade">' +
        '<td><b>' + gradeName + '</b></td>' +
        '<td>' + esc(String(g.weight)) + '</td>' +
        '<td>' + esc(fmtProb(g.gradeProb)) + '</td>' +
        '</tr>' + gradeRows;
    }).join('');
    panel.innerHTML = '<h2>' + esc(t('prob.title')) + '</h2>' +
      (openable ? '' : '<p class="prob-warn">' + esc(t('prob.unopenable')) + '</p>') +
      '<table class="prob-table"><thead><tr>' +
      '<th>' + esc(t('prob.item')) + '</th>' +
      '<th>' + esc(t('prob.inGrade')) + '</th>' +
      '<th>' + esc(t('prob.overall')) + '</th>' +
      '</tr></thead><tbody>' + rows + '</tbody></table>' +
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

  function renderIssues(issues) {
    const root = $('#issues-root');
    const errors = issues.filter((i) => i.level === 'error').length;
    const warns = issues.filter((i) => i.level === 'warn').length;
    const infos = issues.filter((i) => i.level === 'info').length;
    let html = '<h2>' + esc(t('validate.title')) + '</h2>';
    if (!issues.length) {
      html += '<div class="issue ok">' + esc(t('validate.ok')) + '</div>';
    } else {
      html += '<div class="issue-summary">' +
        '<span class="badge error">' + esc(t('validate.errors', { n: errors })) + '</span>' +
        '<span class="badge warn">' + esc(t('validate.warns', { n: warns })) + '</span>' +
        '<span class="badge info">' + esc(t('validate.infos', { n: infos })) + '</span>' +
        '</div>' +
        '<ul class="issue-list">' +
        issues.map((i) =>
          '<li class="issue ' + i.level + '" data-issue-path="' + esc(i.path) + '" title="' + esc(t('validate.jump')) + '"><span class="issue-path">' + esc(i.path) + '</span> ' +
          esc(t(i.key, localizeIssueVars(i.vars))) + '</li>').join('') +
        '</ul>';
    }
    root.innerHTML = html;
  }

  /* ------------------------------ import / export ------------------------------ */

  function applyImportText(text, fileName) {
    let obj;
    try {
      obj = JSON.parse(text);
    } catch (e) {
      toast(t('toast.invalidJson', { err: e.message }), true);
      return;
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
    } else if (type === 'prices') {
      pushHistory();
      const n = NS.mergePrices(state, obj);
      rebuildPrices();
      schedulePreview();
      pushHistory(); // snapshot imported prices so undo steps back through them
      toast(t('toast.importPrices', { n }));
    } else {
      toast(t('toast.unknownType'), true);
    }
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
    const revNote = reversedWeights ? t('toast.legacyWeightsReversed') + '；' : '';
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