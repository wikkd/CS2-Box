/* CS2-Box box editor — data model, (de)serialization, legacy price migration. */
window.CSBoxEdit = window.CSBoxEdit || {};
(function (NS) {
  'use strict';

  const DATA = window.CSBDATA;
  /** Variant marker in a tag SNBT string: TACZ guns (GunId), TACZ ammo
   *  (AmmoId), and the any-mod generic marker (VariantId, e.g. SlashBlade
   *  Resharped blades) — mirrors the runtime's PriceTable VARIANT_FIELD_RE. */
  const VARIANT_RE = /\b(GunId|AmmoId|VariantId)\b\s*[:=]\s*"?([A-Za-z0-9_.:+-]+)"?/;

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

  /** Java int upper bound: the in-game loader casts prices with `(int) v`
   *  (PriceTable), so anything above 2147483647 overflows negative and breaks
   *  parsing — the editor must refuse it before it reaches the game. */
  const MAX_INT = 2147483647;

  /** Parse a price-cell value: a fixed non-negative integer, a "min-max"
   *  string, or a [min, max] array. Returns {min, max} or null. */
  function parsePriceInput(v) {
    if (v === '' || v === null || v === undefined) return null;
    if (Array.isArray(v)) {
      if (v.length !== 2) return null;
      const a = Number(v[0]);
      const b = Number(v[1]);
      if (!Number.isInteger(a) || !Number.isInteger(b) || a < 0 || b < 0 || a > b) return null;
      if (a > MAX_INT || b > MAX_INT) return null;
      return { min: a, max: b };
    }
    const s = String(v).trim();
    const m = /^(\d+)\s*[-~]\s*(\d+)$/.exec(s);
    if (m) {
      const a = Number(m[1]);
      const b = Number(m[2]);
      if (a < 0 || b < 0 || a > b) return null;
      if (a > MAX_INT || b > MAX_INT) return null;
      return { min: a, max: b };
    }
    if (!/^\d+$/.test(s)) return null;
    const n = Number(s);
    if (!Number.isInteger(n) || n < 0) return null;
    if (n > MAX_INT) return null;
    return { min: n, max: n };
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
      maxPerPlayer: '',
      cooldownSeconds: '',
      permission: '',
      pityGrade: '', // v2.0.1 pity (保底) target grade id
      pityEvery: '', // v2.0.1 pity (保底) force threshold
      // v2.x gameplay fields (2.0.4 baseline alignment)
      pityFirst: false, // pity.first_open: the very first open is forced
      hideOdds: false, // hide_odds: no probability lines on the item tooltip
      hideOddsExplicit: false, // true = the source doc carried hide_odds (round-trip fidelity)
      allowBulk: true, // allow_bulk: Shift+right-click bulk open (default true)
      allowBulkExplicit: false, // true = the source doc carried allow_bulk
      typeExplicit: false, // true = the source doc carried an explicit type field
      keysText: '', // keys: extra any-of key ids, comma separated (max 8)
      wearMin: '', // wear: [min, max] float 0..1
      wearMax: '',
      bonusChance: '', // bonus: {chance 0..1, mode double|box}
      bonusMode: 'double',
      announceText: '', // announce_above: tier id / legacy grade id / level int
      availableFrom: '', // available_from: ISO-8601 date-time
      availableUntil: '', // available_until: ISO-8601 date-time
      openCost: '', // open_cost: armory points per open (>= 0)
      sound: '', // sound: custom open sound id
      upTarget: '', // upgrade: {target box id, consume >= 1}
      upConsume: '',
      negRounds: '', // negotiation: {rounds 0=5, timeout_minutes 0=180, price_bias}
      negTimeout: '',
      negBias: '',
    };
  }

  function emptyItem() {
    return {
      source: 'id', // id | tag | loot_table
      value: '',
      note: '', // 备注 (display name), shown in the probability table
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
      // v2.x custom tier metadata (the top-level "grades" array), lowest tier
      // first — aligned index-for-index with `grades`. null = legacy format
      // (random weights + grade1..5).
      tiers: null,
      priceRows: {}, // key -> { price: number|'' , pinned: bool }
      // TACZ field visibility: null = follow selected version metadata,
      // true = force enable, false = force disable (editor preference only).
      taczEnabled: null,
    };
  }

  /** Clamp a float into 0..1 (mirrors BoxGrades.clampDropRate and the wear
   *  clamp in BoxDefinition). */
  function clamp01(v) {
    const n = Number(v);
    if (!Number.isFinite(n)) return 0;
    return Math.min(Math.max(n, 0), 1);
  }

  /** Number of grade tiers for the current state: custom tiers use their own
   *  count (1..64), the legacy format always has five. */
  function tierCountOf(state) {
    return Array.isArray(state.tiers) && state.tiers.length ? state.tiers.length : 5;
  }

  /** Grade weight for tier index gi: custom tiers read tier.weight (default
   *  1, <= 0 rolls never), the legacy format reads meta.random. */
  function tierWeightOf(state, gi) {
    if (Array.isArray(state.tiers) && state.tiers.length) {
      const t = state.tiers[gi] || {};
      const w = Number(t.weight);
      return Number.isFinite(w) && w > 0 ? w : 0;
    }
    const raw = Array.isArray(state.meta && state.meta.random) ? state.meta.random : [];
    const n = Number(raw[gi]);
    return Number.isFinite(n) && n > 0 ? n : 0;
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

  /** Extract GunId/AmmoId/VariantId from a legacy tag string (mirrors Java
   *  PriceTable). */
  function extractVariant(tagStr) {
    if (typeof tagStr !== 'string') return null;
    const m = VARIANT_RE.exec(tagStr);
    if (!m) return null;
    const variant = m[2].trim();
    if (!variant) return null;
    return { field: m[1], id: variant };
  }

  /** Parse a tag string as a JSON object first, then as SNBT (the two string
   *  forms the tag field round-trips; the SNBT side lives in js/snbt.js).
   *  Returns the compound object or null when neither grammar accepts it. */
  function parseTagObject(raw) {
    try {
      const p = JSON.parse(raw);
      if (p && typeof p === 'object' && !Array.isArray(p)) return p;
    } catch (e) { /* SNBT below */ }
    try {
      const p = NS.parseSnbt(raw);
      if (p && typeof p === 'object' && !Array.isArray(p)) return p;
    } catch (e) { /* malformed */ }
    return null;
  }

  /** Variant marker from a tag STRING: full SNBT parse (single-quoted
   *  strings, spacing, escapes — shapes the regex cannot see) with the
   *  regex as a lenient fallback for slightly-malformed strings. */
  function snbtVariant(tagStr) {
    const obj = parseTagObject(tagStr);
    if (obj) {
      const v = variantFieldOf(obj);
      if (v) return v;
    }
    return extractVariant(tagStr);
  }

  /** The variant marker of a serialized item entry ({id, tag?, components?}),
   *  covering all three shapes the runtime's PriceTable.variantId knows: a
   *  tag SNBT string, a JSON-object tag (NBT eras), and
   *  components.minecraft:custom_data (1.21+). Returns {field, id} or null —
   *  the paste flow uses it to suggest id#variant price keys. */
  function variantFromEntry(obj) {
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null;
    if (typeof obj.tag === 'string' && obj.tag) {
      const v = snbtVariant(obj.tag);
      if (v) return v;
    } else if (obj.tag && typeof obj.tag === 'object') {
      const v = variantFieldOf(obj.tag);
      if (v) return v;
    }
    if (obj.components && typeof obj.components === 'object') {
      const v = variantFieldOf(obj.components['minecraft:custom_data']);
      if (v) return v;
    }
    return null;
  }

  /** GunId / AmmoId / VariantId from a flat string-field compound, in that
   *  priority order (TACZ guns, TACZ ammo, generic any-mod marker). */
  function variantFieldOf(obj) {
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return null;
    for (const f of ['GunId', 'AmmoId', 'VariantId']) {
      const v = obj[f];
      if (typeof v === 'string' && v.trim()) return { field: f, id: v.trim() };
    }
    return null;
  }

  /** Whether the form-conversion button has anything to work on: a JSON
   *  object on either side (components object, or a tagRaw that parses as a
   *  JSON object or an SNBT compound — js/snbt.js provides the grammar). */
  function canConvertItemForm(it) {
    const comp = (it.components || '').trim();
    if (comp) {
      try {
        const p = JSON.parse(comp);
        if (p && typeof p === 'object' && !Array.isArray(p) && Object.keys(p).length) return true;
      } catch (e) { /* nope */ }
    }
    const raw = (it.tagRaw || '').trim();
    if (raw.startsWith('{') && parseTagObject(raw)) return true;
    return false;
  }

  /** One-shot known-mod form conversion for one item: moves identity data   *  between the components era (minecraft:custom_data /
   *  minecraft:block_entity_data / slashblade:blade_state) and the NBT-era
   *  tag shapes ({GunId:...} / {BlockEntityTag:{...}} / {bladeState:{...},
   *  all three verified against the shipped compat-packs). The target side
   *  follows `compsEra` (the selected game version). The NBT-era tag accepts
   *  both JSON-object and hand-written SNBT-string forms (parsed by
   *  parseTagObject) and is re-emitted as canonical SNBT — numeric type
   *  suffixes collapse to plain numbers, the same documented approximation
   *  the runtime's tagToJson makes. Returns the list of moved keys, or null
   *  when nothing recognizable is present. */
  function convertItemForm(it, compsEra) {
    const comp = (it.components || '').trim();
    let compObj = null;
    if (comp) {
      try {
        const p = JSON.parse(comp);
        if (p && typeof p === 'object' && !Array.isArray(p)) compObj = p;
      } catch (e) { compObj = null; }
    }
    const raw = (it.tagRaw || '').trim();
    const tagObj = raw.startsWith('{') ? parseTagObject(raw) : null;
    if (!compObj && !tagObj) return null;

    const moves = [];
    let outComp = compObj ? Object.assign({}, compObj) : null;
    let outTag = tagObj ? Object.assign({}, tagObj) : null;

    if (compsEra) {
      if (tagObj) {
        if (!outComp) outComp = {};
        const custom = customDataOf(outComp);
        for (const f of ['GunId', 'AmmoId', 'VariantId']) {
          if (typeof tagObj[f] === 'string' && tagObj[f].trim()) {
            custom[f] = tagObj[f].trim();
            delete outTag[f];
            moves.push(f);
          }
        }
        if (moves.length) outComp['minecraft:custom_data'] = custom;
        if (tagObj.BlockEntityTag && typeof tagObj.BlockEntityTag === 'object') {
          outComp['minecraft:block_entity_data'] = tagObj.BlockEntityTag;
          delete outTag.BlockEntityTag;
          moves.push('BlockEntityTag');
        }
        if (tagObj.bladeState && typeof tagObj.bladeState === 'object') {
          outComp['slashblade:blade_state'] = tagObj.bladeState;
          delete outTag.bladeState;
          moves.push('bladeState');
        }
      }
    } else if (compObj) {
      if (!outTag) outTag = {};
      const custom = compObj['minecraft:custom_data'];
      let customMoved = false;
      if (custom && typeof custom === 'object' && !Array.isArray(custom)) {
        for (const f of ['GunId', 'AmmoId', 'VariantId']) {
          if (typeof custom[f] === 'string' && custom[f].trim()) {
            outTag[f] = custom[f].trim();
            moves.push(f);
            customMoved = true;
          }
        }
        if (customMoved) {
          const remaining = Object.assign({}, custom);
          for (const f of ['GunId', 'AmmoId', 'VariantId']) delete remaining[f];
          if (Object.keys(remaining).length) outComp['minecraft:custom_data'] = remaining;
          else delete outComp['minecraft:custom_data'];
        }
      }
      const bed = compObj['minecraft:block_entity_data'];
      if (bed && typeof bed === 'object' && !Array.isArray(bed)) {
        outTag.BlockEntityTag = bed;
        delete outComp['minecraft:block_entity_data'];
        moves.push('BlockEntityTag');
      }
      const bs = compObj['slashblade:blade_state'];
      if (bs && typeof bs === 'object' && !Array.isArray(bs)) {
        outTag.bladeState = bs;
        delete outComp['slashblade:blade_state'];
        moves.push('bladeState');
      }
    }
    if (!moves.length) return null;
    // Write back: components keep their pretty JSON form, the tag re-emits
    // as canonical SNBT (the tag field's native string form, accepted by
    // every loader era).
    it.components = (outComp && Object.keys(outComp).length)
      ? JSON.stringify(outComp, null, 2) : '';
    it.tagRaw = (outTag && Object.keys(outTag).length)
      ? NS.stringifySnbt(outTag) : '';
    return moves;
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

  /** Capability flags of the selected game version ({components: bool, …}). */
  function versionCapsOf(state) {
    return (DATA.versions && DATA.versions.find((v) => v.key === state.versionKey)) || {};
  }

  /** The minecraft:custom_data object of a components map (a safe copy), or
   *  an empty object when absent. */
  function customDataOf(compObj) {
    const c = compObj && compObj['minecraft:custom_data'];
    return (c && typeof c === 'object' && !Array.isArray(c)) ? Object.assign({}, c) : {};
  }

  /** Build one item object. `verbose` = emit explicit defaults (count 1,
   *  weight 1) instead of omitting them (the "精简输出" toggle). `compsEra`
   *  routes the TACZ/VariantId marker to components.minecraft:custom_data
   *  (1.21+/26.x) instead of the NBT-era tag SNBT string. */
  function buildItem(it, verbose, compsEra) {
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
      if (!(a === 1 && b === 1) || verbose) o.count = [a, b];
    } else if (it.countMode === 'fixed') {
      const c = intOrEmpty(it.count);
      if (c !== '' && (c !== 1 || verbose)) o.count = c;
    } else if (verbose) {
      o.count = 1; // single = explicit 1
    }

    const w = intOrEmpty(it.weight);
    if (w !== '' && (w !== 1 || verbose)) o.weight = w;

    const note = (it.note || '').trim();
    if (note) o.note = note;

    if (it.enchantMode === 'any') {
      o.enchant = true;
    } else if (it.enchantMode === 'custom') {
      const eid = (it.enchantId || '').trim();
      let level;
      if (it.enchantLevelMode === 'range') {
        let a = intOrEmpty(it.enchantLevelMin);
        let b = intOrEmpty(it.enchantLevelMax);
        if (a === '') a = 1;
        if (b === '') b = 1;
        if (b < a) b = a;
        level = [a, b];
      } else {
        const lv = intOrEmpty(it.enchantLevel);
        level = lv === '' ? 1 : lv;
      }
      // Runtime semantics (BoxItemResolver.applyRandomEnchant): an id pins the
      // enchantment; id-less {level} = a random applicable enchantment in the
      // level window — a legal config the editor must not drop.
      if (eid) o.enchant = { id: eid, level };
      else o.enchant = { level };
    }

    const variant = (it.variant || '').trim();
    const rawTag = (it.tagRaw || '').trim();
    const comp = (it.components || '').trim();
    let compObj = null;
    if (comp) {
      try {
        const parsed = JSON.parse(comp);
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) compObj = parsed;
      } catch (e) { /* validator reports */ }
    }
    if (variant) {
      const field = it.variantField === 'AmmoId' ? 'AmmoId'
        : (it.variantField === 'VariantId' ? 'VariantId' : 'GunId');
      if (compsEra) {
        // 1.21+/26.x write the marker into minecraft:custom_data (the home
        // TACZ guns / SlashBlade blades read it from themselves); merging
        // into an existing custom_data object keeps hand-written keys.
        const custom = customDataOf(compObj || {});
        custom[field] = variant;
        compObj = Object.assign({}, compObj || {}, { 'minecraft:custom_data': custom });
      } else {
        o.tag = NS.stringifySnbt({ [field]: variant });
      }
    } else if (rawTag) {
      o.tag = rawTag;
    }
    if (compObj && Object.keys(compObj).length) o.components = compObj;

    return o;
  }

  /** Serializes the editor state into a box JSON object. `verbose` emits
   *  explicit defaults (type/enabled/count/weight) — the "精简输出" off state. */
  function buildBox(state, verbose) {
    const obj = {};
    const meta = state.meta;

    const fileName = (state.fileName || '').trim();
    const boxId = fileName || 'box';

    const name = (meta.name || '').trim();
    if (name) obj.name = name;

    const type = meta.type === 'terminal' ? 'terminal' : 'csbox';
    // An explicit `type: "csbox"` in the source must survive a round-trip, so
    // explicitness is tracked separately from the value (compact output still
    // omits the field for drafts created in the editor).
    if (type === 'terminal' || meta.typeExplicit || verbose) obj.type = type;

    const key = (meta.key || '').trim();
    if (type !== 'terminal' && key) obj.key = key;

    const drop = numOrEmpty(meta.drop);
    if (drop !== '') obj.drop = drop;

    // random: 5 slots; any filled → emit all 5 (empty/NaN become 0 = default)
    const rn = meta.random.map((v) => numOrEmpty(v));
    if (rn.some((v) => v !== '')) {
      obj.random = rn.map((v) => (v === '' ? 0 : Math.max(0, Math.min(10000, Math.round(v)))));
    }

    // entity rows → flat alternating [id, rate?] list. Rows WITHOUT an id but
    // WITH a rate are emitted as bare numbers so a round-trip never drops the
    // value (misplaced / dangling numbers stay visible in the editor for the
    // user to fix; validator.js flags them).
    const entity = [];
    for (const row of meta.entity) {
      const id = (row.id || '').trim();
      if (id) entity.push(id);
      const r = numOrEmpty(row.rate);
      if (r !== '') entity.push(r);
    }
    if (entity.length) obj.entity = entity;

    if (meta.enabled === false || verbose) obj.enabled = meta.enabled !== false; // true is the default → omitted unless verbose

    const reqs = (meta.requiresText || '').split(',').map((s) => s.trim()).filter(Boolean);
    if (reqs.length) obj.requires = reqs;

    const icon = String(meta.icon || '').trim();
    if (icon !== '') obj.icon = /^-?\d+$/.test(icon) ? parseInt(icon, 10) : icon;

    // terminal-only fields (harmless on crates but only meaningful for terminals)
    const discount = numOrEmpty(meta.discount);
    if (discount !== '') obj.discount = discount;
    const stock = intOrEmpty(meta.stock);
    if (stock !== '') obj.stock = stock;
    const maxPer = intOrEmpty(meta.maxPerPlayer);
    if (maxPer !== '') obj.max_per_player = maxPer;
    const cooldown = intOrEmpty(meta.cooldownSeconds);
    if (cooldown !== '') obj.cooldown_seconds = cooldown;
    const perm = (meta.permission || '').trim();
    if (perm) obj.permission = perm;

    // v2.0.1 pity: only emitted when BOTH fields are valid (grade resolves to
    // a level, every >= 2); a half-filled pity config is dropped.
    const pityGrade = (meta.pityGrade || '').trim();
    const pityEveryStr = String(meta.pityEvery ?? '').trim();
    const pityEvery = Number(pityEveryStr);
    if (pityGrade && pityEveryStr !== '' && Number.isInteger(pityEvery) && pityEvery >= 2) {
      obj.pity = { grade: pityGrade, every: pityEvery };
      if (meta.pityFirst === true) obj.pity.first_open = true;
    }

    // v2.x gameplay fields (2.0.4 baseline). Defaults are omitted: hide_odds
    // false, allow_bulk true, wear [0,1], bonus off, announce off, no window,
    // open_cost 0, negotiation defaults. Explicit source values (even explicit
    // defaults) survive the round-trip via the *Explicit flags.
    if (meta.hideOdds === true || meta.hideOddsExplicit) obj.hide_odds = meta.hideOdds === true;
    if (meta.allowBulk === false || meta.allowBulkExplicit) obj.allow_bulk = meta.allowBulk !== false;
    const keys = (meta.keysText || '').split(',').map((s) => s.trim()).filter(Boolean);
    if (keys.length) obj.keys = keys;
    const wMin = numOrEmpty(meta.wearMin);
    const wMax = numOrEmpty(meta.wearMax);
    if (wMin !== '' || wMax !== '') {
      let a = wMin === '' ? 0 : clamp01(wMin);
      let b = wMax === '' ? 1 : clamp01(wMax);
      if (b < a) b = a; // runtime rule: max collapses onto min
      obj.wear = [a, b];
    }
    const bonusChance = numOrEmpty(meta.bonusChance);
    if (bonusChance !== '' && bonusChance > 0) {
      obj.bonus = { chance: clamp01(bonusChance), mode: meta.bonusMode === 'box' ? 'box' : 'double' };
    }
    const ann = (meta.announceText || '').trim();
    if (ann !== '') obj.announce_above = /^-?\d+$/.test(ann) ? parseInt(ann, 10) : ann;
    const availFrom = (meta.availableFrom || '').trim();
    if (availFrom) obj.available_from = availFrom;
    const availUntil = (meta.availableUntil || '').trim();
    if (availUntil) obj.available_until = availUntil;
    const openCost = intOrEmpty(meta.openCost);
    if (openCost !== '' && openCost >= 0) obj.open_cost = openCost;
    const sound = (meta.sound || '').trim();
    if (sound) obj.sound = sound;
    const upTarget = (meta.upTarget || '').trim();
    if (upTarget) {
      const upConsume = intOrEmpty(meta.upConsume);
      obj.upgrade = { target: upTarget, consume: upConsume === '' || upConsume < 1 ? 1 : upConsume };
    }
    const negRounds = intOrEmpty(meta.negRounds);
    const negTimeout = intOrEmpty(meta.negTimeout);
    const negBias = numOrEmpty(meta.negBias);
    if (negRounds !== '' || negTimeout !== '' || negBias !== '') {
      const neg = {};
      if (negRounds !== '') neg.rounds = Math.max(0, negRounds);
      if (negTimeout !== '') neg.timeout_minutes = Math.max(0, negTimeout);
      if (negBias !== '') neg.price_bias = Math.min(Math.max(negBias, -0.9), 9);
      obj.negotiation = neg;
    }

    // grades: the v2.x custom tier array replaces random/grade1..5 entirely
    // (the runtime parses either format, custom wins); empty tiers are
    // skipped exactly like the in-game loader does.
    if (Array.isArray(state.tiers) && state.tiers.length) {
      const arr = [];
      const compsEra = !!versionCapsOf(state).components;
      for (let ti = 0; ti < state.tiers.length; ti++) {
        const t = state.tiers[ti] || {};
        const items = [];
        for (const it of (state.grades[ti] || [])) {
          const o = buildItem(it, verbose, compsEra);
          if (o) items.push(o);
        }
        if (!items.length) continue;
        const tObj = { id: (t.id || '').trim() || ('tier' + (ti + 1)) };
        const tierName = (t.name || '').trim();
        if (tierName) tObj.name = tierName;
        const color = (t.color || '').trim();
        if (color) tObj.color = color;
        const w = intOrEmpty(t.weight);
        if (w !== '' && (w !== 1 || verbose)) tObj.weight = w;
        const stock = intOrEmpty(t.stock);
        if (stock !== '') tObj.stock = stock;
        tObj.items = items;
        arr.push(tObj);
      }
      if (arr.length) obj.grades = arr;
    } else {
      const compsEra = !!versionCapsOf(state).components;
      for (let gi = 1; gi <= 5; gi++) {
        const items = [];
        for (const it of state.grades[gi - 1]) {
          const o = buildItem(it, verbose, compsEra);
          if (o) items.push(o);
        }
        if (items.length) obj['grade' + gi] = items;
      }
    }
    return obj;
  }

  /** Serializes the price table. Only priced, valid entries: fixed integers
   *  stay integers, ranges are written as [min, max] arrays. */
  function buildPrices(state) {
    const out = {};
    const keys = Object.keys(state.priceRows).sort();
    for (const key of keys) {
      const row = state.priceRows[key];
      const parsed = parsePriceInput(row.price);
      if (!parsed) continue;
      if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(key)) continue;
      out[key] = parsed.min === parsed.max ? parsed.min : [parsed.min, parsed.max];
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
      // Canonical SNBT display (the tag field's native string form); every
      // loader era accepts the string form.
      tagStr = NS.stringifySnbt(obj.tag);
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

    // note (备注): optional display name, shown in the probability table
    if (typeof obj.note === 'string') it.note = obj.note.trim();

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
      const v = snbtVariant(tagStr);
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

  /** Basename (no path, no .json) of a box file name — used to detect the
   *  legacy `terminal.json` terminal before `type` existed (v2.0.0). */
  function legacyBaseName(fileName) {
    if (!fileName) return '';
    const name = String(fileName).replace(/\\/g, '/').split('/').pop();
    return name.replace(/\.json$/i, '');
  }

  /** Heuristic for the ChloePrime / pre-2.0.1 weight-order bug: those versions
   *  stored the five grade weights REVERSED (random[0] was grade5's weight).
   *  A strictly ascending 5-element array looks like such a reversed file;
   *  strictly descending or non-monotonic arrays are left alone (custom
   *  weights, already-correct files). Exported for the unit tests. */
  function weightOrderIsReversed(randomArr) {
    if (!Array.isArray(randomArr) || randomArr.length !== 5) return false;
    const nums = randomArr.map(Number);
    if (nums.some((n) => !Number.isFinite(n))) return false;
    let asc = true;
    for (let i = 1; i < 5; i++) {
      if (nums[i] <= nums[i - 1]) { asc = false; break; }
    }
    return asc;
  }

  /** Box JSON → editor state (migrations collects legacy price entries).
   *  `opts.legacy` enables pre-v2.0.1 migration semantics on top of the
   *  always-on inline-`price` handling:
   *    - a box with no `type` field whose file is named `terminal.json` was a
   *      terminal before v2.0.0 (the game auto-migrates it); without this it
   *      silently downgrades to a plain crate on export.
   *    - an inferred terminal must not keep a `key` (v2.0.0 rule). */
  function boxToState(obj, fileName, opts) {
    const st = emptyState();
    st.fileName = (fileName || 'my_box');
    const m = st.meta;
    const legacy = !!(opts && opts.legacy);
    m.name = typeof obj.name === 'string' ? obj.name : '';
    m.type = obj.type === 'terminal' ? 'terminal' : (obj.type === 'csbox' ? 'csbox' : (typeof obj.type === 'string' ? obj.type : 'csbox'));
    m.typeExplicit = typeof obj.type === 'string';
    if (legacy && typeof obj.type !== 'string' && legacyBaseName(fileName) === 'terminal') {
      m.type = 'terminal'; // pre-v2.0.0 terminal.json had no `type`
    }
    m.key = typeof obj.key === 'string' ? obj.key : '';
    if (legacy && m.type === 'terminal') m.key = ''; // terminals must not hold a key
    m.drop = numOrEmpty(obj.drop);
    m.icon = obj.icon === undefined ? '' : String(obj.icon);
    m.enabled = obj.enabled !== false;
    m.requiresText = Array.isArray(obj.requires) ? obj.requires.join(', ') : '';
    m.entity = Array.isArray(obj.entity) && obj.entity.length ? entityToRows(obj.entity) : [{ id: '', rate: '' }];
    m.random = Array.isArray(obj.random) && obj.random.length === 5 ? obj.random.map((v) => String(v)) : ['', '', '', '', ''];
    m.discount = numOrEmpty(obj.discount);
    m.stock = intOrEmpty(obj.stock);
    m.maxPerPlayer = intOrEmpty(obj.max_per_player);
    m.cooldownSeconds = intOrEmpty(obj.cooldown_seconds);
    m.permission = typeof obj.permission === 'string' ? obj.permission : '';
    const pityObj = obj.pity;
    m.pityGrade = pityObj && typeof pityObj.grade === 'string' ? pityObj.grade : '';
    m.pityEvery = pityObj ? intOrEmpty(pityObj.every) : '';
    m.pityFirst = !!(pityObj && pityObj.first_open === true);

    // v2.x gameplay fields (2.0.4 baseline)
    m.hideOdds = obj.hide_odds === true;
    m.hideOddsExplicit = typeof obj.hide_odds === 'boolean';
    m.allowBulk = obj.allow_bulk !== false;
    m.allowBulkExplicit = typeof obj.allow_bulk === 'boolean';
    m.keysText = Array.isArray(obj.keys)
      ? obj.keys.filter((s) => typeof s === 'string' && s.trim()).map((s) => s.trim()).join(', ')
      : '';
    if (Array.isArray(obj.wear) && obj.wear.length === 2) {
      m.wearMin = numOrEmpty(obj.wear[0]);
      m.wearMax = numOrEmpty(obj.wear[1]);
    }
    if (obj.bonus && typeof obj.bonus === 'object' && !Array.isArray(obj.bonus)) {
      m.bonusChance = numOrEmpty(obj.bonus.chance);
      m.bonusMode = obj.bonus.mode === 'box' ? 'box' : 'double';
    }
    m.announceText = typeof obj.announce_above === 'number' ? String(obj.announce_above)
      : (typeof obj.announce_above === 'string' ? obj.announce_above.trim() : '');
    m.availableFrom = typeof obj.available_from === 'string' ? obj.available_from : '';
    m.availableUntil = typeof obj.available_until === 'string' ? obj.available_until : '';
    m.openCost = intOrEmpty(obj.open_cost);
    m.sound = typeof obj.sound === 'string' ? obj.sound : '';
    if (obj.upgrade && typeof obj.upgrade === 'object' && !Array.isArray(obj.upgrade)) {
      m.upTarget = typeof obj.upgrade.target === 'string' ? obj.upgrade.target : '';
      m.upConsume = intOrEmpty(obj.upgrade.consume);
    }
    if (obj.negotiation && typeof obj.negotiation === 'object' && !Array.isArray(obj.negotiation)) {
      m.negRounds = intOrEmpty(obj.negotiation.rounds);
      m.negTimeout = intOrEmpty(obj.negotiation.timeout_minutes);
      m.negBias = numOrEmpty(obj.negotiation.price_bias);
    }

    const migrations = [];
    if (Array.isArray(obj.grades)) {
      // v2.x custom tier array (lowest → highest) — replaces the legacy
      // format entirely, exactly like the runtime loader.
      const tiers = [];
      const gradeItems = [];
      for (const te of obj.grades) {
        if (!te || typeof te !== 'object' || Array.isArray(te)) continue;
        const t = {
          id: typeof te.id === 'string' ? te.id.trim() : '',
          name: typeof te.name === 'string' ? te.name : '',
          color: typeof te.color === 'string' ? te.color : '',
          weight: 1,
          stock: intOrEmpty(te.stock),
        };
        const w = intOrEmpty(te.weight);
        if (w !== '') t.weight = w;
        const items = [];
        const itemsArr = Array.isArray(te.items) ? te.items : [];
        for (const item of itemsArr) {
          if (!item || typeof item !== 'object') continue;
          items.push(parseItem(item, migrations));
        }
        tiers.push(t);
        gradeItems.push(items);
      }
      if (tiers.length) {
        st.tiers = tiers;
        st.grades = gradeItems;
      }
    } else {
      for (let gi = 1; gi <= 5; gi++) {
        const arr = obj['grade' + gi];
        if (!Array.isArray(arr)) continue;
        for (const item of arr) {
          if (!item || typeof item !== 'object') continue;
          st.grades[gi - 1].push(parseItem(item, migrations));
        }
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

  /** Merge an imported _prices.json into priceRows (existing prices win).
   *  Fixed entries land as ints, ranges as "min-max" strings. */
  function mergePrices(state, pricesObj) {
    let n = 0;
    for (const key of Object.keys(pricesObj)) {
      const parsed = parsePriceInput(pricesObj[key]);
      if (!parsed) continue;
      if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(key)) continue;
      const display = parsed.min === parsed.max ? String(parsed.min) : parsed.min + '-' + parsed.max;
      if (!state.priceRows[key]) {
        state.priceRows[key] = { price: display, pinned: true };
        n++;
      } else if (state.priceRows[key].price === '' || state.priceRows[key].price === null || state.priceRows[key].price === undefined) {
        state.priceRows[key].price = display;
        n++;
      }
    }
    return n;
  }

  /* --------------------- crate JSON → price table ------------------------------ */

  /** Parse a blob that may hold several pretty-printed JSON documents
   *  (several box/terminal files pasted back to back, or a JSON array of
   *  boxes). First tries the whole text as one JSON value; otherwise scans
   *  for top-level `{...}` fragments balanced across lines while ignoring
   *  braces inside quoted strings (so SNBT in a "tag" value cannot derail
   *  the scan) and keeps the fragments that parse as plain objects.
   *  Returns { docs: [object], bad: n }. */
  function parseJsonDocuments(text) {
    const docs = [];
    let bad = 0;
    const trimmed = String(text || '').trim();
    if (!trimmed) return { docs, bad };
    try {
      const parsed = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        for (const o of parsed) {
          if (o && typeof o === 'object' && !Array.isArray(o)) docs.push(o);
          else bad++;
        }
      } else if (parsed && typeof parsed === 'object') {
        docs.push(parsed);
      } else {
        bad++;
      }
      return { docs, bad };
    } catch (e) { /* multiple documents -> sequential scan */ }
    let i = 0;
    const n = trimmed.length;
    while (i < n) {
      const start = trimmed.indexOf('{', i);
      if (start < 0) break;
      let depth = 0, inStr = false, esc = false, end = -1;
      for (let j = start; j < n; j++) {
        const ch = trimmed[j];
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
      if (end < 0) { bad++; break; } // unclosed braces -> truncated, stop
      const frag = trimmed.slice(start, end + 1);
      try {
        const v = JSON.parse(frag);
        if (v && typeof v === 'object' && !Array.isArray(v)) docs.push(v);
        else bad++;
      } catch (e) { bad++; }
      i = end + 1;
    }
    return { docs, bad };
  }

  /** Extract price-table entries from one parsed box/terminal document:
   *  every id-source pool item (including TACZ `id#variant` keys derived from
   *  a legacy GunId/AmmoId tag) becomes a key; a legacy inline `price` fills
   *  its price. tag / loot_table entries cannot carry a price key and count
   *  as skipped. Duplicate keys are preserved — mergeCratePriceEntries does
   *  the averaging. Returns { keys: [{ key, price }], skipped: n }. */
  function collectCratePriceEntries(obj) {
    const keys = [];
    let skipped = 0;
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return { keys, skipped };
    const res = boxToState(obj, '');
    const st = res.state;
    const inline = new Map();
    for (const mig of res.migrations) {
      if (mig.ok && mig.key != null) inline.set(mig.key, mig.price);
    }
    for (const items of st.grades) {
      for (const it of items) {
        const key = priceKeyOf(it);
        if (!key) { skipped++; continue; }
        if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/.test(key)) { skipped++; continue; }
        keys.push({ key, price: inline.has(key) ? inline.get(key) : null });
      }
    }
    return { keys, skipped };
  }

  /** Merge extracted crate price entries into state.priceRows. Semantics
   *  mirror mergeMigrations: existing table prices win; duplicate prices for
   *  one key are averaged (round half up); keys without an inline price land
   *  as pinned empty rows the user can fill in.
   *  Returns { priced, addedEmpty, keptPrices } for toast reporting. */
  function mergeCratePriceEntries(state, entries) {
    const blank = (p) => p === '' || p === null || p === undefined;
    const pricedAgg = new Map();
    const unpriced = new Set();
    let priced = 0;
    let addedEmpty = 0;
    let keptPrices = 0;
    for (const e of entries) {
      if (e.price == null) {
        unpriced.add(e.key);
        continue;
      }
      const row = state.priceRows[e.key];
      if (row && !blank(row.price)) { keptPrices++; continue; } // existing wins
      const a = pricedAgg.get(e.key) || { sum: 0, count: 0 };
      a.sum += e.price;
      a.count++;
      pricedAgg.set(e.key, a);
    }
    for (const [key, a] of pricedAgg) {
      if (!state.priceRows[key]) state.priceRows[key] = { price: '', pinned: true };
      if (blank(state.priceRows[key].price)) {
        state.priceRows[key].price = Math.round(a.sum / a.count);
        priced++;
      }
    }
    for (const key of unpriced) {
      if (pricedAgg.has(key)) continue;
      if (!state.priceRows[key]) {
        state.priceRows[key] = { price: '', pinned: true };
        addedEmpty++;
      }
    }
    return { priced, addedEmpty, keptPrices };
  }

  /* ------------------------------ probabilities ------------------------------ */

  /** Compute the drop probability table for the current pool: per-grade
   *  chance from the tier weights (custom `tiers` weight field, or the legacy
   *  `random` array) and per-item in-grade share from each item's `weight`
   *  (absent/empty = 1, `<= 0` = disabled entry with 0%).
   *  Pure function — the UI renders it. An empty grade has no items and is
   *  flagged for the UI to show the in-game fallback note. */
  function computeProbabilities(state) {
    const custom = Array.isArray(state.tiers) && state.tiers.length > 0;
    const count = custom ? state.tiers.length : 5;
    const weights = [];
    for (let gi = 0; gi < count; gi++) weights.push(tierWeightOf(state, gi));
    const totalWeight = weights.reduce((a, b) => a + b, 0);

    const grades = [];
    for (let gi = 0; gi < count; gi++) {
      const gw = weights[gi];
      const itemsRaw = state.grades && state.grades[gi] ? state.grades[gi] : [];
      const items = [];
      let sumW = 0;
      for (const it of itemsRaw) {
        const value = (it.value || '').trim();
        if (!value) continue;
        // weight: empty/missing = 1 (game fills uniform defaults); <= 0 = disabled
        const w = it.weight === '' || it.weight === null || it.weight === undefined
          ? 1 : Number(it.weight);
        const active = Number.isFinite(w) && w > 0;
        items.push({
          value,
          note: (it.note || '').trim(),
          disabled: !active,
          weight: Number.isFinite(w) ? w : 0,
          itemProb: 0,
          priceKey: priceKeyOf(it), // 'ns:id' | 'ns:id#variant' | null — EV column lookup
          countMode: it.countMode || 'single',
          count: it.count,
          countMin: it.countMin,
          countMax: it.countMax,
        });
        if (active) sumW += w;
      }
      if (sumW > 0) {
        for (const it of items) {
          if (!it.disabled) it.itemProb = it.weight / sumW;
        }
      }
      const t = custom ? (state.tiers[gi] || {}) : null;
      grades.push({
        gi,
        weight: gw,
        gradeProb: totalWeight > 0 ? gw / totalWeight : 0,
        items,
        empty: items.length === 0,
        // custom tier display metadata (null for the legacy five tiers)
        tierId: t ? (t.id || '') : null,
        tierName: t ? ((t.name || '').trim() || t.id || ('tier' + (gi + 1))) : null,
        tierColor: t ? ((t.color || '').trim()) : null,
      });
    }
    return { totalWeight, openable: totalWeight > 0, grades, custom };
  }

  /** Pure open-box simulator — samples n opens from the same probability
   *  model computeProbabilities exposes (grade by `random` weight, item by
   *  `weight`, then the count range). `rng` is injectable so tests can be
   *  deterministic; defaults to Math.random.
   *
   *  Empty grades (weight > 0, no items) are conditioned out: sampling is
   *  repeated until a non-empty grade hits, i.e. their mass redistributes
   *  proportionally over the non-empty grades (a roll must yield an item).
   *
   *  Returns cumulative counts plus the FINAL single roll (`last`, used by
   *  the UI roll animation when n === 1). */
  function simulateOpens(state, n, rng) {
    const rand = rng || Math.random;
    const { openable, grades } = computeProbabilities(state);
    const gradeCounts = grades.map(() => 0);
    const items = []; // aggregated per pool entry, in pool order
    const byKey = new Map();
    let last = null;
    if (!openable || n <= 0) {
      return { openable, opened: 0, gradeCounts, items, last };
    }
    const pick = (r, pairs) => {
      // pairs: [[value, prob], ...] cumulative; returns value or last
      for (const [v, c] of pairs) {
        if (r <= c) return v;
      }
      return pairs[pairs.length - 1][0];
    };
    const active = grades.filter((g) => !g.empty && g.gradeProb > 0);
    // condition on non-empty grades: normalize their gradeProb
    const mass = active.reduce((a, g) => a + g.gradeProb, 0);
    const gradeCum = [];
    let c = 0;
    for (const g of active) {
      c += mass > 0 ? g.gradeProb / mass : 0;
      gradeCum.push([g, c]);
    }
    for (let k = 0; k < n; k++) {
      let g = null;
      for (let guard = 0; guard < 100 && !g; guard++) {
        const gv = pick(rand(), gradeCum);
        if (gv && !gv.empty) g = gv;
      }
      if (!g) break;
      gradeCounts[g.gi]++;
      // item within grade
      const itemCum = [];
      let ic = 0;
      for (let idx = 0; idx < g.items.length; idx++) {
        const it = g.items[idx];
        if (it.disabled) continue;
        ic += it.itemProb;
        itemCum.push([idx, ic]);
      }
      if (!itemCum.length) continue; // defensive: should not happen for non-empty grades
      const idx = pick(rand(), itemCum);
      const it = g.items[idx];
      // count: single=1, fixed=N, range=random int in [min, max]
      let count = 1;
      if (it && it.countMode === 'range') {
        const lo = Math.max(1, Math.floor(Number(it.countMin) || 1));
        const hi = Math.max(lo, Math.floor(Number(it.countMax) || lo));
        count = lo + Math.floor(rand() * (hi - lo + 1));
      } else if (it && it.countMode === 'fixed') {
        count = Math.max(1, Math.floor(Number(it.count) || 1));
      }
      const key = g.gi + ':' + idx;
      let agg = byKey.get(key);
      if (!agg) {
        agg = { gi: g.gi, idx, value: it.value, note: it.note, hits: 0, itemTotal: 0, theory: g.gradeProb * it.itemProb };
        byKey.set(key, agg);
        items.push(agg);
      }
      agg.hits++;
      agg.itemTotal += count;
      last = { gi: g.gi, idx, value: it.value, note: it.note, count };
    }
    return { openable, opened: n, gradeCounts, items, last };
  }


  /* ------------------------------ economy & apply ------------------------------ */

  /** Normalizes a workspace payload into an editor state ({fileName, meta,
   *  grades, priceRows, versionKey, taczEnabled}) — same allocation path
   *  applyPayload uses, minus rendering. */
  function payloadState(p) {
    const s = p && p.state ? p.state : {};
    const st = Object.assign(NS.emptyState(), {
      fileName: s.fileName || 'my_box',
      meta: Object.assign(NS.emptyMeta(), s.meta),
      grades: (s.grades || []).map((arr) => (Array.isArray(arr) ? arr : [])),
      tiers: Array.isArray(s.tiers) && s.tiers.length
        ? s.tiers.map((t) => (t && typeof t === 'object' && !Array.isArray(t) ? t : {}))
        : null,
      priceRows: s.priceRows && typeof s.priceRows === 'object' ? s.priceRows : {},
    });
    const want = st.tiers ? st.tiers.length : 5;
    while (st.grades.length < want) st.grades.push([]);
    st.versionKey = p.versionKey || '1.21.1';
    st.taczEnabled = p.taczEnabled == null ? null : !!p.taczEnabled;
    return st;
  }

  /** Midpoint price of one `ns:id`/`ns:id#variant` key in the draft's price
   *  rows. Returns null when the key is unknown or unpriced; range prices
   *  sample at the midpoint (same convention as the EV column). */
  function priceMid(state, key) {
    const row = state.priceRows && state.priceRows[key];
    if (!row) return null;
    const p = parsePriceInput(row.price);
    if (!p) return null;
    return (p.min + p.max) / 2;
  }

  /** Per-item expected count (single = 1, fixed = N, range = [min,max] mid). */
  function countMid(it) {
    if (it.countMode === 'range') {
      const lo = Math.max(1, Math.floor(Number(it.countMin) || 1));
      const hi = Math.max(lo, Math.floor(Number(it.countMax) || lo));
      return (lo + hi) / 2;
    }
    if (it.countMode === 'fixed') {
      return Math.max(1, Math.floor(Number(it.count) || 1));
    }
    return 1;
  }

  /** Pure value/sanity model behind the prices-page "odds & value check":
   *  per-grade chance (raw random-weight share) and expected value per open
   *  (grade and item rolls, range prices at midpoint). Empty grades are
   *  conditioned out exactly like simulateOpens so EV answers "what is one
   *  open worth". `unpriced` items (no priceKey / no price) contribute 0 and
   *  are counted; the warnings list carries i18n keys for the UI. */
  function boxEconomy(state) {
    const { openable, grades } = computeProbabilities(state);
    const out = { openable, grades: [], totalEv: 0, recycleEv: 0, unpriced: 0, priced: 0, warnings: [] };
    if (!openable) {
      // All grade weights (meta.random) are empty/zero: in-game the box
      // refuses to open at all — distinct from "weights set, grades empty".
      out.warnings.push({ key: 'econ.warnNoWeight' });
      return out;
    }
    const active = grades.filter((g) => !g.empty && g.gradeProb > 0);
    if (!active.length) {
      out.warnings.push({ key: 'econ.warnEmpty' });
      return out;
    }
    const mass = active.reduce((a, g) => a + g.gradeProb, 0);
    for (const g of active) {
      let ev = 0;
      let rer = 0; // recycle per open within this grade (per-item ceil, in-game rule)
      let unpriced = 0;
      let itemsActive = 0;
      for (const it of g.items) {
        if (it.disabled) continue;
        itemsActive++;
        if (!it.priceKey) { unpriced++; continue; }
        const mid = priceMid(state, it.priceKey);
        if (mid == null) { unpriced++; continue; }
        out.priced++;
        ev += it.itemProb * mid * countMid(it);
        rer += it.itemProb * Math.ceil(mid * 0.9) * countMid(it);
      }
      out.unpriced += unpriced;
      out.grades.push({
        gi: g.gi,
        prob: g.gradeProb / mass,
        items: itemsActive,
        ev,
        recycle: rer,
        unpriced,
      });
      out.totalEv += (g.gradeProb / mass) * ev;
      out.recycleEv += (g.gradeProb / mass) * rer;
    }
    if (out.unpriced > 0 && out.priced === 0) {
      out.warnings.push({ key: 'econ.warnAllUnpriced' });
    } else if (out.unpriced > 0) {
      out.warnings.push({ key: 'econ.warnSomeUnpriced', vars: { n: out.unpriced } });
      // Per-grade detail only makes sense when the box has some pricing at all
      // (an all-unpriced box is already covered by the single box-level warn).
      for (const gr of out.grades) {
        if (gr.items > 0 && gr.unpriced === gr.items) {
          out.warnings.push({
            key: 'econ.warnGradeUnpriced',
            vars: { grade: String(gr.gi + 1) },
          });
        }
      }
    }
    return out;
  }

  /** Prepares the File System Access write-back ("apply to game"): one box
   *  JSON per draft plus the shared `_prices.json` merged over every draft
   *  (last draft wins per key, matching how /csbox reload reads the folder).
   *  Pure function — app.js handles the picker and the actual writes. File
   *  names come from each draft's fileName (sanitized, leading underscores
   *  stripped so `_prices.json` / ignored helpers stay untouched). */
  function applyManifest(payloads) {
    const files = [];
    const states = [];
    const usedNames = new Set();
    let conflicts = 0;
    for (const e of payloads || []) {
      const p = e && e.payload;
      if (!p || !p.state) continue;
      const st = payloadState(p);
      states.push(st);
      let base = String(st.fileName || 'box').trim().replace(/^_+/, '');
      if (!base) base = 'box';
      base = base.replace(/[\\/:*?"<>|]/g, '_');
      if (usedNames.has(base)) {
        conflicts++;
        let k = 2;
        while (usedNames.has(base + '_' + k)) k++;
        base = base + '_' + k;
      }
      usedNames.add(base);
      files.push({
        name: base + '.json',
        text: JSON.stringify(buildBox(st, false), null, 2) + '\n',
      });
    }
    if (!states.length) {
      return { files, prices: null, conflicts: 0 };
    }
    const prices = {};
    for (const st of states) {
      Object.assign(prices, buildPrices(st));
    }
    return {
      files,
      prices: Object.keys(prices).length ? prices : null,
      conflicts,
    };
  }

  NS.emptyMeta = emptyMeta;
  NS.emptyItem = emptyItem;
  NS.emptyState = emptyState;
  NS.tierCountOf = tierCountOf;
  NS.tierWeightOf = tierWeightOf;
  NS.priceKeyOf = priceKeyOf;
  NS.parsePriceInput = parsePriceInput;
  NS.extractVariant = extractVariant;
  NS.variantFromEntry = variantFromEntry;
  NS.canConvertItemForm = canConvertItemForm;
  NS.convertItemForm = convertItemForm;
  NS.legacyBaseName = legacyBaseName;
  NS.weightOrderIsReversed = weightOrderIsReversed;
  NS.collectPriceRows = collectPriceRows;
  NS.looksLikeId = looksLikeId;
  NS.buildItem = buildItem;
  NS.buildBox = buildBox;
  NS.buildPrices = buildPrices;
  NS.parseItem = parseItem;
  NS.boxToState = boxToState;
  NS.mergeMigrations = mergeMigrations;
  NS.mergePrices = mergePrices;
  NS.parseJsonDocuments = parseJsonDocuments;
  NS.collectCratePriceEntries = collectCratePriceEntries;
  NS.mergeCratePriceEntries = mergeCratePriceEntries;
  NS.computeProbabilities = computeProbabilities;
  NS.simulateOpens = simulateOpens;
  NS.boxEconomy = boxEconomy;
  NS.payloadState = payloadState;
  NS.applyManifest = applyManifest;
})(window.CSBoxEdit);