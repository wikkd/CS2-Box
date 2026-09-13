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

  let state = NS.emptyState();
  let currentTab = 'box'; // 'box' | 'prices'
  let compact = true;
  let previewTimer = null;
  let saveTimer = null;
  let gradeOpen = [true, true, true, true, true];
  const history = { undo: [], redo: [] };
  let pasteContext = null; // { mode: 'items'|'prices', grade?: number }

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
      if (e.key && e.key !== LS_STATE && e.key !== LS_VER && e.key !== LS_LANG) return;
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

  function pushHistory() {
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
    if (!history.undo.length) return;
    history.redo.push(snapshotState());
    applySnapshot(history.undo.pop());
    schedulePreview(0);
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
    } catch (e) { /* storage may be unavailable under file:// */ }
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
  }

  /* ------------------------------ static text ------------------------------ */

  function bindStatics() {
    const map = {
      'app-title': 'app.title',
      'app-subtitle': 'app.subtitle',
      'btn-new': 'app.new',
      'btn-import': 'app.import',
      'btn-example': 'app.example',
      'app-foot': 'app.foot',
      'import-title': 'import.title',
      'import-do': 'import.do',
      'import-cancel': 'import.cancel',
      'example-title': 'example.title',
      'example-close': 'example.close',
      'lang-label': 'app.lang',
      'version-label': 'app.version',
      'compact-label': 'preview.compact',
      'btn-undo': 'app.undo',
      'btn-redo': 'app.redo',
      'btn-share': 'app.share',
      'btn-copy': 'preview.copy',
      'btn-download': 'preview.download',
      'btn-download-all': 'preview.downloadAll',
      'nav-box': 'app.navBox',
      'nav-prices': 'app.navPrices',
    };
    for (const id of Object.keys(map)) {
      const el = document.getElementById(id);
      if (el) el.textContent = t(map[id]);
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
      toast(t('toast.newDone'));
    });

    $('#btn-import').addEventListener('click', () => {
      document.getElementById('import-dialog').showModal();
    });

    $('#btn-example').addEventListener('click', () => {
      renderExampleDialog();
      document.getElementById('example-dialog').showModal();
    });
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

    const exd = $('#example-dialog');
    $('#example-close').addEventListener('click', () => exd.close());

    const pad = $('#paste-dialog');
    $('#paste-cancel').addEventListener('click', () => pad.close());
    $('#paste-do').addEventListener('click', () => {
      const text = document.getElementById('paste-textarea').value;
      applyPasteText(text);
      document.getElementById('paste-textarea').value = '';
      pad.close();
    });
  }

  function renderExampleDialog() {
    const list = $('#example-list');
    list.innerHTML = DATA.examples.map((ex, i) => {
      const label = (DATA.gradeNames[0] || {}).zh; // unused guard; keep simple
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

  function openPasteDialog(ctx) {
    const pd = $('#paste-dialog');
    if (!pd) return;
    $('#paste-title').textContent = ctx.mode === 'prices' ? t('prices.pasteTitle') : t('item.pasteTitle');
    $('#paste-hint').textContent = ctx.mode === 'prices' ? t('prices.pastePh') : t('item.pastePh');
    document.getElementById('paste-textarea').value = '';
    pasteContext = ctx;
    pd.showModal();
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
    } catch (e) { /* not JSON -> line mode */ }
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
          if (/^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(k) && Number.isInteger(v) && v >= 0) ok.push({ key: k, price: v });
          else bad++;
        }
        return { ok, bad };
      }
    } catch (e) { /* line mode */ }
    for (const line of trimmed.split(/\r?\n/)) {
      const s = line.trim();
      if (!s) continue;
      const m = /^([a-z0-9_.-]+:[a-z0-9_./-]+(?:#[^ ]+)?)[\t ,:]+(\d+)$/.exec(s);
      if (m) {
        const price = Number(m[2]);
        if (Number.isInteger(price) && price >= 0) ok.push({ key: m[1], price });
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
      if (bad) toast(t('toast.pasteBad', { n: bad }), true);
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

  function copyText(text, okMsg) {
    const done = () => toast(okMsg);
    const fail = () => toast(t('preview.copyFailed'), true);
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, fail);
    } else {
      fail();
    }
  }

  function shareLink() {
    try {
      const json = JSON.stringify(snapshotState());
      const enc = btoa(unescape(encodeURIComponent(json)))
        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
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
      const b64 = m[1].replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(escape(atob(b64)));
      const snap = JSON.parse(json);
      if (!snap || typeof snap !== 'object') return false;
      state = Object.assign(NS.emptyState(), snap);
      state.versionKey = state.versionKey || '1.21.1';
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
      const f = el.dataset.f;
      if (!f) return;
      applyField(f, el.value);
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
        case 'share': {
          shareLink();
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
  }

  /* ------------------------------ field updates ------------------------------ */

  function applyField(path, value) {
    pushHistory();
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
    pushHistory();
    const key = el.dataset.key;
    if (!state.priceRows[key]) state.priceRows[key] = { price: '', pinned: true };
    state.priceRows[key].price = el.value;
    updatePriceRowCells(key);
    schedulePreview();
  }

  /* ------------------------------ rendering ------------------------------ */

  function renderAll() {
    bindStatics();
    const verSel = $('#version-select');
    verSel.innerHTML = DATA.versions.map((v) =>
      '<option value="' + esc(v.key) + '">' + esc(v.label[I18N.lang]) + '</option>').join('');
    verSel.value = state.versionKey;
    const langSel = $('#lang-select');
    langSel.value = I18N.lang;
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
        '<section class="card" id="card-tacz"></section>' +
        '<section class="card" id="card-drop"></section>' +
        (state.meta.type === 'terminal' ? '<section class="card" id="card-term"></section>' : '') +
        '<section class="card" id="card-grades"></section>' +
        '<div class="version-note">' + esc(t('version.note') + ': ' + v.note[I18N.lang]) + '</div>';
    if (pricesPage) {
      rebuildPrices();
    } else {
      rebuildMeta();
      rebuildTaczCard();
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
      field('meta.name', 'meta.nameHelp', input('meta.name', m.name, '#FF5555 ' + (I18N.lang === 'zh' ? '名字' : 'Name'), false)) +
      field('meta.type', '', select('meta.type', [
        ['csbox', t('meta.type.csbox')],
        ['terminal', t('meta.type.terminal')],
      ], m.type)) +
      field('meta.key', 'meta.keyHelp',
        '<input data-f="meta.key" list="csbox-key-list" value="' + esc(m.key) + '" placeholder="minecraft:iron_ingot"' +
        (m.type === 'terminal' ? ' disabled class="danger-input"' : '') + '>') +
      field('meta.drop', 'meta.dropHelp', input('meta.drop', m.drop, '0.05', false)) +
      field('meta.icon', 'meta.iconHelp', input('meta.icon', m.icon, 'minecraft:ender_chest', false)) +
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

  function rebuildDrop() {
    const el = card('card-drop');
    if (!el) return;
    const m = state.meta;
    el.innerHTML =
      '<h2>' + esc(t('drop.title')) + '</h2>' +
      '<div class="row">' +
      '<label>' + esc(t('drop.random')) +
      '<div class="random-row">' +
      m.random.map((v, i) =>
        '<span class="random-slot" data-help="drop.randomHelp"><b>' + esc((DATA.gradeNames[i] || {}).zh) + '</b>' +
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
      '<input data-help="drop.entityIdHelp" data-f="meta.entity.' + i + '.id" value="' + esc(row.id) + '" placeholder="minecraft:zombie" class="grow">' +
      '<input data-help="drop.entityRateHelp" data-f="meta.entity.' + i + '.rate" value="' + esc(row.rate) + '" placeholder="0.1" type="number" min="0" max="1" step="0.01" class="rate">' +
      '<button class="btn small danger" data-action="del-entity" data-index="' + i + '">✕</button>' +
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
          '<span class="muted">' + esc(t('grade.defaultPrice')) + ' ' + DATA.gradeDefaultPrices[gi] +
          ' · ' + esc(t('grade.recycle')) + ' ' + DATA.gradeRecycleYields[gi] + '</span>' +
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

  function rebuildGrade(gi) {
    const host = $('#grade-items-' + gi);
    if (!host) return;
    const items = state.grades[gi];
    const v = version();
    if (!items.length) {
      host.innerHTML = '<div class="muted">' + esc(t('grade.empty')) + '</div>';
      return;
    }
    host.innerHTML = items.map((it, ii) => renderItem(gi, ii, it, v)).join('');
    const badge = document.querySelector('.grade-card[data-grade="' + gi + '"] .count-badge');
    if (badge) badge.textContent = items.length;
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
      ['fixed', '固定 / Fixed'],
      ['range', '区间 / Range'],
    ];
    const header = '<div class="item-head">' +
      '<span class="item-no">#' + (ii + 1) + '</span>' +
      select(p + 'source', srcOptions, it.source, 'item.sourceHelp') +
      '<input data-help="item.sourceHelp" data-f="' + esc(p + 'value') + '" value="' + esc(it.value) +
      '" placeholder="' + (it.source === 'tag' ? '#minecraft:swords' : it.source === 'loot_table' ? 'minecraft:chests/simple_dungeon' : 'minecraft:diamond_sword') + '" class="grow">' +
      '<button class="btn small" data-action="move-item" data-grade="' + gi + '" data-index="' + ii + '" data-dir="-1" data-help="item.up">↑</button>' +
      '<button class="btn small" data-action="move-item" data-grade="' + gi + '" data-index="' + ii + '" data-dir="1" data-help="item.down">↓</button>' +
      '<button class="btn small" data-action="copy-item" data-grade="' + gi + '" data-index="' + ii + '" data-help="item.copy">⧉</button>' +
      '<button class="btn small danger" data-action="del-item" data-grade="' + gi + '" data-index="' + ii + '" data-help="item.remove">✕</button>' +
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
      '<label class="mini" data-help="item.enchantHelp">' + esc(t('item.enchant')) + ' ' + select(p + 'enchantMode', enchantOptions, it.enchantMode) + '</label>' +
      (it.enchantMode === 'custom'
        ? '<label class="mini" data-help="item.enchantIdHelp">' + esc(t('item.enchantId')) + '<input data-f="' + esc(p + 'enchantId') + '" value="' + esc(it.enchantId) + '" placeholder="minecraft:sharpness" class="w14"></label>' +
          '<label class="mini" data-help="item.enchantLevelHelp">' + esc(t('item.enchantLevel')) + ' ' + select(p + 'enchantLevelMode', levelOptions, it.enchantLevelMode) + '</label>' +
          (it.enchantLevelMode === 'range'
            ? '<label class="mini" data-help="item.enchantLevelHelp"><input data-f="' + esc(p + 'enchantLevelMin') + '" value="' + esc(it.enchantLevelMin) + '" type="number" min="1" class="w7"></label>' +
              '<span class="mini sep">' + esc(t('item.to')) + '</span>' +
              '<label class="mini" data-help="item.enchantLevelHelp"><input data-f="' + esc(p + 'enchantLevelMax') + '" value="' + esc(it.enchantLevelMax) + '" type="number" min="1" class="w7"></label>'
            : '<label class="mini" data-help="item.enchantLevelHelp"><input data-f="' + esc(p + 'enchantLevel') + '" value="' + esc(it.enchantLevel) + '" type="number" min="1" class="w7"></label>')
        : '') +
      '</div>' +

      (taczVisible()
        ? '<div class="row inline">' +
          '<label class="mini" data-help="item.variantHelp">' + esc(t('item.variant')) + '<input data-f="' + esc(p + 'variant') + '" value="' + esc(it.variant) + '" placeholder="tacz:ak47" class="w14"></label>' +
          '<label class="mini" data-help="item.variantFieldHelp">' + esc(t('item.variantField')) + ' ' +
          select(p + 'variantField', [['GunId', 'GunId'], ['AmmoId', 'AmmoId']], it.variantField) + '</label>' +
          '<label class="mini" data-help="item.nbtHelp">' + esc(t('item.nbt')) + '<input data-f="' + esc(p + 'tagRaw') + '" value="' + esc(it.tagRaw) + '" placeholder=\'{GunId:"tacz:ak47"}\' class="w24"></label>' +
          '</div>'
        : '') +

      (v.components
        ? '<div class="row"><label class="mini grow" data-help="item.componentsHelp">' + esc(t('item.components')) +
          '<textarea data-f="' + esc(p + 'components') + '" placeholder="' + esc(t('item.componentsHelp')) + '" rows="2" class="grow">' + esc(it.components) + '</textarea></label></div>'
        : (it.components ? '<div class="row"><span class="help">' + esc(t('v.componentsIgnored')) + '</span></div>' : '')) +
      '</div>';

    return '<div class="item-card">' + header + body + '</div>';
  }

  function rebuildPrices() {
    const el = card('card-prices');
    if (!el) return;
    const { rows } = NS.collectPriceRows(state, state.versionKey);
    const bodyRows = rows.map((r) => {
      const price = r.row.price;
      const priced = price !== '' && price !== null && price !== undefined && Number.isInteger(Number(price)) && Number(price) >= 0;
      let recycle = '';
      if (priced) recycle = String(Math.ceil(Number(price) * 0.9));
      else if (r.grades.size === 1) recycle = String(DATA.gradeRecycleYields[[...r.grades][0] - 1]);
      else if (r.grades.size > 1) recycle = '-';
      let status = priced ? t('prices.state.priced') : (r.key.includes('#') && !taczVisible() ? t('prices.variantUnsupported') : t('prices.state.default'));
      const gradeTag = r.grades.size
        ? [...r.grades].sort((a, b) => a - b).map((g) => 'g' + g).join(',')
        : '<span class="extra-tag">' + esc(t('prices.state.extra')) + '</span>';
      const removable = r.row.pinned && !r.grades.size;
      return '<tr data-price-key="' + esc(r.key) + '">' +
        '<td class="key-cell"><code>' + esc(r.key) + '</code><span class="tiny muted">' + gradeTag + '</span></td>' +
        '<td><input data-help="prices.priceHelp" data-role="price-input" data-key="' + esc(r.key) + '" value="' + esc(price === '' ? '' : price) + '" type="number" min="0" step="1" class="price-input" placeholder="—"></td>' +
        '<td class="recycle-cell">' + esc(recycle) + '</td>' +
        '<td class="status-cell status-' + (priced ? 'ok' : (r.key.includes('#') && !taczVisible() ? 'warn' : 'muted')) + '">' + esc(status) + '</td>' +
        '<td>' + (removable ? '<button class="btn small danger" data-action="del-price-key" data-key="' + esc(r.key) + '">' + esc(t('prices.remove')) + '</button>' : '') + '</td>' +
        '</tr>';
    }).join('');
    el.innerHTML =
      '<div class="card-head"><h2>' + esc(t('prices.title')) + '</h2>' +
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
    const priced = price !== '' && price !== null && price !== undefined && Number.isInteger(Number(price)) && Number(price) >= 0;
    const rc = row.querySelector('.recycle-cell');
    const sc = row.querySelector('.status-cell');
    if (rc) rc.textContent = priced ? String(Math.ceil(Number(price) * 0.9)) : '';
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
    } else {
      el = document.querySelector('[data-f="' + CSS.escape(path) + '"]');
    }
    if (!el) el = document.getElementById('card-meta');
    flashElement(el);
  }

  function flashElement(el) {
    if (!el) return;
    if (typeof el.scrollIntoView === 'function') el.scrollIntoView({ block: 'center', behavior: 'smooth' });
    el.classList.add('flash-highlight');
    clearTimeout(el._flashT);
    el._flashT = setTimeout(() => el.classList.remove('flash-highlight'), 2000);
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
    const boxObj = NS.buildBox(state);
    const pricesObj = NS.buildPrices(state);
    const out = isPricesPage() ? pricesObj : boxObj;
    pre.textContent = JSON.stringify(out, null, 2);

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

    const issues = NS.validate(state, state.versionKey);
    renderIssues(issues);
    saveStateDebounced();

    const undoBtn = $('#btn-undo');
    const redoBtn = $('#btn-redo');
    if (undoBtn) undoBtn.disabled = !history.undo.length;
    if (redoBtn) redoBtn.disabled = !history.redo.length;
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
          esc(t(i.key, i.vars)) + '</li>').join('') +
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
      if (mig.migrated) toast(t('toast.migrated', { n: mig.migrated }));
      else if (mig.bad) toast(t('toast.ignoredBadPrice', { n: mig.bad }), true);
      else toast(t('toast.importBox', { file: fileName || base }));
    } else if (type === 'prices') {
      pushHistory();
      const n = NS.mergePrices(state, obj);
      rebuildPrices();
      schedulePreview();
      toast(t('toast.importPrices', { n }));
    } else {
      toast(t('toast.unknownType'), true);
    }
  }

  function detectType(obj) {
    if (!obj || typeof obj !== 'object' || Array.isArray(obj) || !Object.keys(obj).length) return null;
    for (let i = 1; i <= 5; i++) {
      if (obj['grade' + i] !== undefined) return 'box';
    }
    if (obj.name !== undefined || obj.type !== undefined) return 'box';
    const keys = Object.keys(obj);
    if (keys.every((k) => /^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(k)) &&
        keys.every((k) => typeof obj[k] === 'number')) {
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
    if (announce) toast(t('toast.example', { name: ex.desc[I18N.lang] }));
  }

  function currentText() {
    return $('#json-preview').textContent;
  }

  function copyCurrent() {
    const text = currentText();
    const done = () => toast(t('preview.copied'));
    const fail = () => {
      const pre = $('#json-preview');
      const range = document.createRange();
      range.selectNodeContents(pre);
      const sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
      toast(t('preview.copyFailed'), true);
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, fail);
    } else {
      fail();
    }
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