/* CS2-Box box editor — data model, (de)serialization, legacy price migration. */
window.CSBoxEdit = window.CSBoxEdit || {};
(function (NS) {
  'use strict';

  const DATA = window.CSBDATA;
  const VARIANT_RE = /\b(GunId|AmmoId)\b\s*[:=]\s*"?([A-Za-z0-9_.:+-]+)"?/;

  function numOrEmpty(v) {
    if (v === undefined || v === null || v === '') return '';
    const n = Number(v);
    return Number.isFinite(n) ? n : '';
  }

  function intOrEmpty(v) {
    if (v === undefined || v === null || v === '') return '';
    if (typeof v === 'number' && Number.isInteger(v)) return v;
    const s = String(v).trim();
    return /^-?\d+$/.test(s) ? parseInt(s, 10) : '';
  }

  function emptyMeta() {
    return {
      name: '',
      type: 'csbox', // csbox | terminal
      key: '',
      drop: '',
      icon: '',
      enabled: true,
      requiresText: '',
      entity: [{ id: '', rate: '' }],
      random: ['', '', '', '', ''],
      discount: '',
      stock: '',
      restockMinutes: '',
      maxPerPlayer: '',
      cooldownSeconds: '',
      permission: '',
    };
  }

  function emptyItem() {
    return {
      source: 'id', // id | tag | loot_table
      value: '',
      countMode: 'single', // single | fixed | range
      count: 1,
      countMin: 1,
      countMax: 1,
      weight: 1,
      enchantMode: 'none', // none | any | custom
      enchantId: '',
      enchantLevel: 1,
      enchantLevelMode: 'fixed', // fixed | range
      enchantLevelMin: 1,
      enchantLevelMax: 1,
      variant: '', // TACZ variant id (id#variant price key)
      variantField: 'GunId', // GunId | AmmoId
      tagRaw: '', // raw SNBT/JSON tag, overrides variant
      components: '', // JSON text
    };
  }

  function emptyState() {
    return {
      fileName: 'my_box',
      meta: emptyMeta(),
      grades: [[], [], [], [], []],
      priceRows: {}, // key -> { price: number|'' , pinned: bool }
    };
  }

  /** Registry/dotted id-ish pattern (used for keys and ids). */
  function looksLikeId(s) {
    return /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(s);
  }

  /** Returns the price-table key for an item row, or null when it cannot be
   *  priced (missing id, or tag/loot source). Variant part from the model. */
  function priceKeyOf(item) {
    if (!item || item.source !== 'id' || !item.value.trim()) return null;
    const base = item.value.trim();
    const variant = item.variant.trim();
    if (variant) {
      return base + '#' + variant;
    }
    return base;
  }

  /** Extract GunId/AmmoId from a legacy tag string (mirrors Java PriceTable). */
  function extractVariant(tagStr) {
    if (typeof tagStr !== 'string') return null;
    const m = VARIANT_RE.exec(tagStr);
    if (!m) return null;
    const variant = m[2].trim();
    if (!variant) return null;
    return { field: m[1], id: variant };
  }

  /** Collect price-table rows, merging auto keys from pool items with the
   *  user's priceRows (pinned keys and priced auto keys survive item removal). */
  function collectPriceRows(state, versionKey) {
    const version = DATA.versions.find((v) => v.key === versionKey) || DATA.versions[0];
    const auto = new Map(); // key -> Set(grade index 1..5)
    state.grades.forEach((items, gi) => {
      items.forEach((item) => {
        const key = priceKeyOf(item);
        if (!key) return;
        if (!auto.has(key)) auto.set(key, new Set());
        auto.get(key).add(gi + 1);
      });
    });

    const rows = [];
    const seen = new Set();
    for (const key of Object.keys(state.priceRows)) {
      const row = state.priceRows[key];
      const isAuto = auto.has(key);
      rows.push({ key, row, grades: auto.get(key) || new Set(), autoKey: isAuto });
      seen.add(key);
    }
    for (const [key, grades] of auto) {
      if (seen.has(key)) continue;
      rows.push({ key, row: { price: '', pinned: false }, grades, autoKey: true });
      seen.add(key);
    }
    rows.sort((a, b) => a.key.localeCompare(b.key));
    return { rows, version };
  }

  /* ------------------------------ building ------------------------------ */

  function buildItem(it) {
    const value = (it.value || '').trim();
    if (!value) return null;
    const o = {};
    if (it.source === 'tag') o.tag = value;
    else if (it.source === 'loot_table') o.loot_table = value;
    else o.id = value;

    if (it.countMode === 'range') {
      let a = intOrEmpty(it.countMin);
      let b = intOrEmpty(it.countMax);
      if (a === '') a = 1;
      if (b === '') b = 1;
      if (b < a) b = a;
      if (!(a === 1 && b === 1)) o.count = [a, b];
    } else if (it.countMode === 'fixed') {
      const c = intOrEmpty(it.count);
      if (c !== '' && c !== 1) o.count = c;
    }

    const w = intOrEmpty(it.weight);
    if (w !== '' && w !== 1) o.weight = w;

    if (it.enchantMode === 'any') {
      o.enchant = true;
    } else if (it.enchantMode === 'custom') {
      const eid = (it.enchantId || '').trim();
      if (eid) {
        if (it.enchantLevelMode === 'range') {
          let a = intOrEmpty(it.enchantLevelMin);
          let b = intOrEmpty(it.enchantLevelMax);
          if (a === '') a = 1;
          if (b === '') b = 1;
          if (b < a) b = a;
          o.enchant = { id: eid, level: [a, b] };
        } else {
          const lv = intOrEmpty(it.enchantLevel);
          o.enchant = { id: eid, level: lv === '' ? 1 : lv };
        }
      }
    }

    const variant = (it.variant || '').trim();
    const rawTag = (it.tagRaw || '').trim();
    if (variant) {
      const field = it.variantField === 'AmmoId' ? 'AmmoId' : 'GunId';
      o.tag = '{' + field + ':\\"' + variant + '\\"}';
    } else if (rawTag) {
      o.tag = rawTag;
    }

    const comp = (it.components || '').trim();
    if (comp) {
      try {
        const parsed = JSON.parse(comp);
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) o.components = parsed;
      } catch (e) { /* validator reports */ }
    }
    return o;
  }

  /** Serializes the editor state into a box JSON object. */
  function buildBox(state) {
    const obj = {};
    const meta = state.meta;

    const fileName = (state.fileName || '').trim();
    const boxId = fileName || 'box';

    const name = (meta.name || '').trim();
    if (name) obj.name = name;

    const type = meta.type === 'terminal' ? 'terminal' : 'csbox';
    if (type === 'terminal') obj.type = 'terminal';

    const key = (meta.key || '').trim();
    if (type !== 'terminal' && key) obj.key = key;

    const drop = numOrEmpty(meta.drop);
    if (drop !== '') obj.drop = drop;

    // random: 5 slots; any filled → emit all 5 (empty/NaN become 0 = default)
    const rn = meta.random.map((v) => numOrEmpty(v));
    if (rn.some((v) => v !== '')) {
      obj.random = rn.map((v) => (v === '' ? 0 : Math.max(0, Math.min(10000, Math.round(v)))));
    }

    // entity rows → flat alternating [id, rate?] list
    const entity = [];
    for (const row of meta.entity) {
      const id = (row.id || '').trim();
      if (!id) continue;
      entity.push(id);
      const r = numOrEmpty(row.rate);
      if (r !== '') entity.push(r);
    }
    if (entity.length) obj.entity = entity;

    if (meta.enabled === false) obj.enabled = false; // true is the default → omit

    const reqs = (meta.requiresText || '').split(',').map((s) => s.trim()).filter(Boolean);
    if (reqs.length) obj.requires = reqs;

    const icon = String(meta.icon || '').trim();
    if (icon !== '') obj.icon = /^-?\d+$/.test(icon) ? parseInt(icon, 10) : icon;

    // terminal-only fields (harmless on crates but only meaningful for terminals)
    const discount = numOrEmpty(meta.discount);
    if (discount !== '') obj.discount = discount;
    const stock = intOrEmpty(meta.stock);
    if (stock !== '') obj.stock = stock;
    const restock = intOrEmpty(meta.restockMinutes);
    if (restock !== '') obj.restock_minutes = restock;
    const maxPer = intOrEmpty(meta.maxPerPlayer);
    if (maxPer !== '') obj.max_per_player = maxPer;
    const cooldown = intOrEmpty(meta.cooldownSeconds);
    if (cooldown !== '') obj.cooldown_seconds = cooldown;
    const perm = (meta.permission || '').trim();
    if (perm) obj.permission = perm;

    for (let gi = 1; gi <= 5; gi++) {
      const items = [];
      for (const it of state.grades[gi - 1]) {
        const o = buildItem(it);
        if (o) items.push(o);
      }
      if (items.length) obj['grade' + gi] = items;
    }
    return obj;
  }

  /** Serializes the price table. Only priced, valid integer entries. */
  function buildPrices(state) {
    const out = {};
    const keys = Object.keys(state.priceRows).sort();
    for (const key of keys) {
      const row = state.priceRows[key];
      if (row.price === '' || row.price === null || row.price === undefined) continue;
      const n = Number(row.price);
      if (!Number.isInteger(n) || n < 0) continue;
      if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(key)) continue;
      out[key] = n;
    }
    return out;
  }

  /* ------------------------------ parsing ------------------------------ */

  function parseItem(obj, migrations) {
    const it = emptyItem();
    let tagStr = '';

    if (typeof obj.id === 'string' && obj.id) {
      it.source = 'id';
      it.value = obj.id;
    }
    if (typeof obj.tag === 'string' && obj.tag.startsWith('#')) {
      it.source = 'tag';
      it.value = obj.tag;
    } else if (typeof obj.tag === 'string' && obj.tag) {
      tagStr = obj.tag; // legacy NBT tag (kept for variant extraction / raw)
    } else if (obj.tag && typeof obj.tag === 'object') {
      tagStr = JSON.stringify(obj.tag);
      tagStr = tagStr.replace(/,/g, ', '); // light formatting
    }
    if (typeof obj.loot_table === 'string' && obj.loot_table) {
      it.source = 'loot_table';
      it.value = obj.loot_table;
      // loot entries cannot carry NBT variants
      tagStr = '';
    }

    // count
    if (Array.isArray(obj.count)) {
      it.countMode = 'range';
      it.countMin = intOrEmpty(obj.count[0]); if (it.countMin === '') it.countMin = 1;
      it.countMax = intOrEmpty(obj.count[1]); if (it.countMax === '') it.countMax = 1;
    } else if (typeof obj.count === 'number' || typeof obj.count === 'string') {
      const c = intOrEmpty(obj.count);
      if (c !== '' && c !== 1) { it.countMode = 'fixed'; it.count = c; }
      else { it.countMode = 'single'; it.count = 1; }
    }

    // weight
    const w = intOrEmpty(obj.weight);
    it.weight = w === '' ? 1 : w;

    // enchant
    if (obj.enchant === true) it.enchantMode = 'any';
    else if (obj.enchant && typeof obj.enchant === 'object') {
      it.enchantMode = 'custom';
      it.enchantId = obj.enchant.id || '';
      if (Array.isArray(obj.enchant.level)) {
        it.enchantLevelMode = 'range';
        it.enchantLevelMin = intOrEmpty(obj.enchant.level[0]); if (it.enchantLevelMin === '') it.enchantLevelMin = 1;
        it.enchantLevelMax = intOrEmpty(obj.enchant.level[1]); if (it.enchantLevelMax === '') it.enchantLevelMax = 1;
      } else {
        const lv = intOrEmpty(obj.enchant.level);
        it.enchantLevel = lv === '' ? 1 : lv;
      }
    }

    // components
    if (obj.components && typeof obj.components === 'object') {
      it.components = JSON.stringify(obj.components, null, 2);
    } else if (typeof obj.components === 'string' && obj.components) {
      it.components = obj.components;
    }

    // legacy tag → variant or raw
    if (tagStr) {
      const v = extractVariant(tagStr);
      if (v) {
        it.variant = v.id;
        it.variantField = v.field;
      } else {
        it.tagRaw = tagStr;
      }
    }

    // legacy inline price → migrate (valid non-negative int only)
    if (obj.hasOwnProperty('price')) {
      const p = intOrEmpty(obj.price);
      if (p !== '' && p >= 0 && it.source === 'id') {
        const key = priceKeyOf(it);
        if (key) migrations.push({ ok: true, key, price: p });
        else migrations.push({ ok: false, key: null, price: p });
      } else {
        migrations.push({ ok: false, key: null, price: -1 });
      }
    }
    return it;
  }

  /** Box JSON → editor state (migrations collects legacy price entries). */
  function boxToState(obj, fileName) {
    const st = emptyState();
    st.fileName = (fileName || 'my_box');
    const m = st.meta;
    m.name = typeof obj.name === 'string' ? obj.name : '';
    m.type = obj.type === 'terminal' ? 'terminal' : (obj.type === 'csbox' ? 'csbox' : (typeof obj.type === 'string' ? obj.type : 'csbox'));
    m.key = typeof obj.key === 'string' ? obj.key : '';
    m.drop = numOrEmpty(obj.drop);
    m.icon = obj.icon === undefined ? '' : String(obj.icon);
    m.enabled = obj.enabled !== false;
    m.requiresText = Array.isArray(obj.requires) ? obj.requires.join(', ') : '';
    m.entity = Array.isArray(obj.entity) && obj.entity.length ? entityToRows(obj.entity) : [{ id: '', rate: '' }];
    m.random = Array.isArray(obj.random) && obj.random.length === 5 ? obj.random.map((v) => String(v)) : ['', '', '', '', ''];
    m.discount = numOrEmpty(obj.discount);
    m.stock = intOrEmpty(obj.stock);
    m.restockMinutes = intOrEmpty(obj.restock_minutes);
    m.maxPerPlayer = intOrEmpty(obj.max_per_player);
    m.cooldownSeconds = intOrEmpty(obj.cooldown_seconds);
    m.permission = typeof obj.permission === 'string' ? obj.permission : '';

    const migrations = [];
    for (let gi = 1; gi <= 5; gi++) {
      const arr = obj['grade' + gi];
      if (!Array.isArray(arr)) continue;
      for (const item of arr) {
        if (!item || typeof item !== 'object') continue;
        st.grades[gi - 1].push(parseItem(item, migrations));
      }
    }
    return { state: st, migrations };
  }

  function entityToRows(arr) {
    const rows = [];
    for (let i = 0; i < arr.length; i++) {
      const v = arr[i];
      if (typeof v === 'string') {
        rows.push({ id: v, rate: '' });
      } else if (typeof v === 'number') {
        const last = rows[rows.length - 1];
        if (last && last.rate === '') last.rate = String(v);
        else rows.push({ id: '', rate: String(v) });
      }
    }
    return rows.length ? rows : [{ id: '', rate: '' }];
  }

  /** Merge legacy price migrations into priceRows: existing entries win;
   *  duplicated keys within the import are averaged (round half up). */
  function mergeMigrations(state, migrations) {
    const agg = new Map();
    let okCount = 0;
    let badCount = 0;
    for (const mig of migrations) {
      if (!mig.ok || mig.key == null) { badCount++; continue; }
      const row = state.priceRows[mig.key];
      if (row && row.price !== '' && row.price !== null && row.price !== undefined) continue; // existing wins
      const a = agg.get(mig.key) || { sum: 0, count: 0 };
      a.sum += mig.price;
      a.count++;
      agg.set(mig.key, a);
    }
    for (const [key, a] of agg) {
      const avg = Math.round(a.sum / a.count);
      if (!state.priceRows[key]) state.priceRows[key] = { price: '', pinned: true };
      if (state.priceRows[key].price === '' || state.priceRows[key].price === null || state.priceRows[key].price === undefined) {
        state.priceRows[key].price = avg;
      }
    }
    okCount = agg.size;
    return { migrated: okCount, bad: badCount };
  }

  /** Merge an imported _prices.json into priceRows (existing prices win). */
  function mergePrices(state, pricesObj) {
    let n = 0;
    for (const key of Object.keys(pricesObj)) {
      const v = pricesObj[key];
      if (typeof v !== 'number' || !Number.isInteger(v) || v < 0) continue;
      if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(key)) continue;
      if (!state.priceRows[key]) {
        state.priceRows[key] = { price: v, pinned: true };
        n++;
      } else if (state.priceRows[key].price === '' || state.priceRows[key].price === null || state.priceRows[key].price === undefined) {
        state.priceRows[key].price = v;
        n++;
      }
    }
    return n;
  }

  NS.emptyMeta = emptyMeta;
  NS.emptyItem = emptyItem;
  NS.emptyState = emptyState;
  NS.priceKeyOf = priceKeyOf;
  NS.extractVariant = extractVariant;
  NS.collectPriceRows = collectPriceRows;
  NS.looksLikeId = looksLikeId;
  NS.buildItem = buildItem;
  NS.buildBox = buildBox;
  NS.buildPrices = buildPrices;
  NS.parseItem = parseItem;
  NS.boxToState = boxToState;
  NS.mergeMigrations = mergeMigrations;
  NS.mergePrices = mergePrices;
})(window.CSBoxEdit);