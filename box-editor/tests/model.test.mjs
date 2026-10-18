/* CS2-Box box-editor — pure model/validator unit tests (no browser needed).
 * Loads data.js + model.js into a stubbed `window` and tests the pure
 * functions that the Playwright smoke can't reach:
 *   - entity round-trips must be LOSSLESS (regression for the silent-drop bug)
 *   - price parsing must respect the Java int bound (overflow -> negative)
 *   - price-table range values must survive an export/parse cycle
 *   - validator flags misplaced entity rates
 *
 * Run:  node --test tests/model.test.mjs   (or npm test:model)
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const load = (p) => (0, eval)(readFileSync(path.join(root, p), 'utf8'));

globalThis.window = globalThis;
load('js/data.js');
load('js/model.js');
load('js/validator.js');
const NS = globalThis.CSBoxEdit;

function roundTrip(entityArr) {
  const res = NS.boxToState({ type: 'csbox', name: 'T', entity: entityArr }, 'test');
  return NS.buildBox(res.state).entity;
}

test('entity round-trip: alternating [id, rate] pairs are lossless', () => {
  const input = ['minecraft:zombie', 0.1, 'minecraft:skeleton'];
  assert.deepEqual(roundTrip(input), input);
});

test('entity round-trip: pure id list is lossless', () => {
  const input = ['minecraft:zombie', 'minecraft:skeleton'];
  assert.deepEqual(roundTrip(input), input);
});

test('entity round-trip: single id is lossless', () => {
  assert.deepEqual(roundTrip(['minecraft:zombie']), ['minecraft:zombie']);
});

test('entity round-trip: misplaced leading number is preserved, not dropped', () => {
  const input = [0.05, 'minecraft:zombie'];
  assert.deepEqual(roundTrip(input), input);
});

test('entity round-trip: dangling trailing number is preserved, not dropped', () => {
  const input = ['minecraft:zombie', 0.1, 0.2];
  assert.deepEqual(roundTrip(input), input);
});

test('parsePriceInput accepts fixed, range-string and range-array forms', () => {
  assert.deepEqual(NS.parsePriceInput('1500'), { min: 1500, max: 1500 });
  assert.deepEqual(NS.parsePriceInput('1500-3000'), { min: 1500, max: 3000 });
  assert.deepEqual(NS.parsePriceInput([1500, 3000]), { min: 1500, max: 3000 });
  assert.deepEqual(NS.parsePriceInput('0'), { min: 0, max: 0 });
  assert.equal(NS.parsePriceInput(''), null);
  assert.equal(NS.parsePriceInput('abc'), null);
  assert.equal(NS.parsePriceInput('3000-1000'), null); // min > max
});

test('parsePriceInput rejects values above the Java int bound', () => {
  assert.equal(NS.parsePriceInput('2147483648'), null); // MAX_INT + 1
  assert.equal(NS.parsePriceInput('999999999999'), null);
  assert.equal(NS.parsePriceInput([3000000000, 4000000000]), null);
  assert.equal(NS.parsePriceInput('3000000000-4000000000'), null);
  assert.deepEqual(NS.parsePriceInput('2147483647'), { min: 2147483647, max: 2147483647 });
});

test('price-table export/import cycle accepts range values', () => {
  const state = {
    priceRows: {
      'minecraft:diamond': { price: 1500, pinned: true },
      'minecraft:nautilus_shell': { price: [200, 400], pinned: true },
    },
  };
  // buildPrices must serialize ranges as [min, max] arrays, and every exported
  // value must parse back through parsePriceInput (the in-game PriceTable)
  const out = NS.buildPrices(state);
  assert.equal(out['minecraft:diamond'], 1500);
  assert.deepEqual(out['minecraft:nautilus_shell'], [200, 400]);
  for (const v of Object.values(out)) {
    assert.notEqual(NS.parsePriceInput(v), null, `exported value ${JSON.stringify(v)} must re-parse`);
  }
});

test('buildBox compact mode omits defaults; verbose mode writes them explicitly', () => {
  const it = NS.emptyItem();
  it.value = 'minecraft:diamond';
  const state = NS.emptyState();
  state.fileName = 'test';
  state.meta.name = 'T';
  state.grades[0] = [it];

  const compact = NS.buildBox(state, false); // 精简输出
  assert.equal(compact.type, undefined);          // csbox is the schema default
  assert.equal(compact.enabled, undefined);       // true is the default
  assert.equal(compact.grade1[0].count, undefined);
  assert.equal(compact.grade1[0].weight, undefined);

  const verbose = NS.buildBox(state, true);       // 完整输出
  assert.equal(verbose.type, 'csbox');
  assert.equal(verbose.enabled, true);
  assert.equal(verbose.grade1[0].count, 1);
  assert.equal(verbose.grade1[0].weight, 1);
});

test('buildBox verbose keeps explicit count ranges even when [1, 1]', () => {
  const it = NS.emptyItem();
  it.value = 'minecraft:arrow';
  it.countMode = 'range';
  it.countMin = 1;
  it.countMax = 1;
  const state = NS.emptyState();
  state.fileName = 'test';
  state.meta.name = 'T';
  state.grades[0] = [it];

  const compact = NS.buildBox(state, false);
  assert.equal(compact.grade1[0].count, undefined);
  const verbose = NS.buildBox(state, true);
  assert.deepEqual(verbose.grade1[0].count, [1, 1]);
});

test('buildItem fixed count 1: omitted under compact, explicit under verbose', () => {
  const it = NS.emptyItem();
  it.value = 'minecraft:arrow';
  it.countMode = 'fixed';
  it.count = 1;
  const compact = NS.buildItem(it, false);
  assert.equal(compact.count, undefined);
  const verbose = NS.buildItem(it, true);
  assert.equal(verbose.count, 1);
  // and a genuinely different count stays in both modes
  it.count = 64;
  assert.equal(NS.buildItem(it, false).count, 64);
  assert.equal(NS.buildItem(it, true).count, 64);
});

test('validator warns when an item-model icon is used on a non-26.x version only', () => {
  const mk = () => {
    const st = NS.emptyState();
    st.fileName = 'test';
    st.meta.name = 'T';
    st.meta.icon = 'minecraft:ender_chest'; // ns:path model-id form
    return st;
  };
  const on1211 = NS.validate(mk(), '1.21.1');
  assert.ok(on1211.some((i) => i.key === 'v.iconModelUnsupported'),
    '1.21.1 must warn about model-id icons');
  const on1201 = NS.validate(mk(), 'forge-1.20.1');
  assert.ok(on1201.some((i) => i.key === 'v.iconModelUnsupported'),
    'forge-1.20.1 must warn about model-id icons');
  const on262 = NS.validate(mk(), '26.2');
  assert.ok(!on262.some((i) => i.key === 'v.iconModelUnsupported'),
    '26.2 supports model-id icons and must not warn');
  // integer form never warns
  const intSt = mk();
  intSt.meta.icon = '75001';
  assert.ok(!NS.validate(intSt, '1.21.1').some((i) => i.key === 'v.iconModelUnsupported'));
});

/* ----- legacy import: pre-v2.0.1 boxes (inline price, no `type`) ----- */

test('legacyBaseName strips path and .json', () => {
  assert.equal(NS.legacyBaseName('terminal.json'), 'terminal');
  assert.equal(NS.legacyBaseName('terminal'), 'terminal');
  assert.equal(NS.legacyBaseName('config/csbox/terminal.json'), 'terminal');
  assert.equal(NS.legacyBaseName('C:/x/y/terminal.json'), 'terminal');
  assert.equal(NS.legacyBaseName('weapon_supply_box.json'), 'weapon_supply_box');
  assert.equal(NS.legacyBaseName(''), '');
});

test('legacy boxToState: terminal.json without type is inferred as terminal, key dropped', () => {
  const obj = {
    name: '旧终端',
    key: 'minecraft:air',
    grade5: [{ id: 'minecraft:diamond_sword', price: 1500 }],
  };
  const res = NS.boxToState(obj, 'terminal.json', { legacy: true });
  assert.equal(res.state.meta.type, 'terminal');
  assert.equal(res.state.meta.key, '');
  // inline price still migrated
  assert.equal(res.migrations.length, 1);
  assert.equal(res.migrations[0].key, 'minecraft:diamond_sword');
  assert.equal(res.migrations[0].price, 1500);
  // export is the current-version format: explicit type, no key, no price
  const out = NS.buildBox(res.state);
  assert.equal(out.type, 'terminal');
  assert.equal(out.key, undefined);
  assert.equal(out.grade5[0].price, undefined);
});

test('legacy boxToState: non-terminal file without type stays a crate', () => {
  const obj = { name: '普通旧箱', grade1: [{ id: 'minecraft:diamond' }] };
  const res = NS.boxToState(obj, 'weapon_supply_box.json', { legacy: true });
  assert.equal(res.state.meta.type, 'csbox');
  // crates keep a key
  const withKey = NS.boxToState({ name: 'T', key: 'minecraft:air', grade1: [{ id: 'minecraft:diamond' }] }, 'weapon_supply_box.json', { legacy: true });
  assert.equal(withKey.state.meta.key, 'minecraft:air');
});

test('legacy boxToState: explicit type always wins over filename', () => {
  const obj = { name: 'T', type: 'csbox', grade1: [{ id: 'minecraft:diamond' }] };
  const res = NS.boxToState(obj, 'terminal.json', { legacy: true });
  assert.equal(res.state.meta.type, 'csbox');
  const term = NS.boxToState({ name: 'T', type: 'terminal', grade1: [{ id: 'minecraft:diamond' }] }, 'my_box.json', { legacy: true });
  assert.equal(term.state.meta.type, 'terminal');
});

test('non-legacy boxToState keeps old behavior (no type -> csbox even for terminal.json)', () => {
  const obj = { name: 'T', grade1: [{ id: 'minecraft:diamond' }] };
  const res = NS.boxToState(obj, 'terminal.json');
  assert.equal(res.state.meta.type, 'csbox');
  const resNoOpts = NS.boxToState(obj, 'terminal.json', undefined);
  assert.equal(resNoOpts.state.meta.type, 'csbox');
});

/* ----- ChloePrime reversed grade weights (auto-detect on legacy import) ----- */

test('weightOrderIsReversed: ascending array is detected as reversed legacy weights', () => {
  assert.equal(NS.weightOrderIsReversed([2, 5, 25, 125, 625]), true); // mirror of the default
  assert.equal(NS.weightOrderIsReversed([10, 20, 40, 80, 160]), true);
  assert.equal(NS.weightOrderIsReversed([1, 2, 3, 4, 5]), true);
});

test('weightOrderIsReversed: descending or non-monotonic arrays are left alone', () => {
  assert.equal(NS.weightOrderIsReversed([625, 125, 25, 5, 2]), false); // default order
  assert.equal(NS.weightOrderIsReversed([50, 100, 30, 90, 10]), false); // non-monotonic
  assert.equal(NS.weightOrderIsReversed([10, 10, 10, 10, 10]), false);  // equal values
  assert.equal(NS.weightOrderIsReversed([0, 0, 0, 0, 0]), false);
});

test('weightOrderIsReversed: malformed input is never reversed', () => {
  assert.equal(NS.weightOrderIsReversed([1, 2, 3]), false);           // short
  assert.equal(NS.weightOrderIsReversed([1, 2, 3, 4, 5, 6]), false);  // long
  assert.equal(NS.weightOrderIsReversed(['a', 'b', 'c', 'd', 'e']), false);
  assert.equal(NS.weightOrderIsReversed(null), false);
  assert.equal(NS.weightOrderIsReversed(undefined), false);
  assert.equal(NS.weightOrderIsReversed('625,125,25,5,2'), false);
});

/* ----- validator guards: pity half-filled, icon int bound, entity ids ----- */

test('validator warns when pity is half-filled (silently dropped on export)', () => {
  const mk = () => {
    const st = NS.emptyState();
    st.fileName = 'test';
    st.meta.name = 'T';
    return st;
  };
  // grade only
  const st = mk();
  st.meta.pityGrade = 'classified';
  const issues = NS.validate(st, '1.21.1');
  assert.ok(issues.some((i) => i.key === 'v.pityHalfFilled'),
    'grade-only pity must warn about the dropped config');
  // every only
  const st2 = mk();
  st2.meta.pityEvery = '20';
  const issues2 = NS.validate(st2, '1.21.1');
  assert.ok(issues2.some((i) => i.key === 'v.pityHalfFilled'),
    'every-only pity must warn too');
  // both filled and valid -> no half-filled warning
  const st3 = mk();
  st3.meta.pityGrade = 'classified';
  st3.meta.pityEvery = '20';
  const issues3 = NS.validate(st3, '1.21.1');
  assert.ok(!issues3.some((i) => i.key === 'v.pityHalfFilled'),
    'complete pity must not warn');
});

test('validator rejects icon integers outside the Java int bound', () => {
  const mk = (icon) => {
    const st = NS.emptyState();
    st.fileName = 'test';
    st.meta.name = 'T';
    st.meta.icon = icon;
    return st;
  };
  assert.ok(NS.validate(mk('-1'), '26.2').some((i) => i.key === 'v.badIcon'));
  assert.ok(NS.validate(mk('2147483648'), '26.2').some((i) => i.key === 'v.badIcon'));
  assert.ok(!NS.validate(mk('2147483647'), '26.2').some((i) => i.key === 'v.badIcon'));
  assert.ok(!NS.validate(mk('0'), '26.2').some((i) => i.key === 'v.badIcon'));
  assert.ok(!NS.validate(mk('minecraft:ender_chest'), '26.2').some((i) => i.key === 'v.badIcon'));
});

test('validator warns on malformed entity ids', () => {
  const mk = (id) => {
    const st = NS.emptyState();
    st.fileName = 'test';
    st.meta.name = 'T';
    st.meta.entity = [{ id, rate: '' }];
    return st;
  };
  assert.ok(NS.validate(mk('zombie'), '1.21.1').some((i) => i.key === 'v.badEntityId'),
    'bare id (no namespace) must warn');
  assert.ok(NS.validate(mk('minecraft:Zombie'), '1.21.1').some((i) => i.key === 'v.badEntityId'),
    'uppercase id must warn');
  assert.ok(!NS.validate(mk('minecraft:zombie'), '1.21.1').some((i) => i.key === 'v.badEntityId'));
  assert.ok(!NS.validate(mk(''), '1.21.1').some((i) => i.key === 'v.badEntityId'));
});

/* ----- drop probability table ----- */

function probState(random, grades) {
  const st = NS.emptyState();
  st.fileName = 'test';
  st.meta.name = 'T';
  st.meta.random = random.map(String);
  grades.forEach((items, gi) => { st.grades[gi] = items; });
  return st;
}

function mkItem(value, weight) {
  const it = NS.emptyItem();
  it.value = value;
  if (weight !== undefined) it.weight = weight;
  return it;
}

test('computeProbabilities: grade chances follow the random weights', () => {
  const st = probState([625, 125, 25, 5, 2], [[mkItem('minecraft:diamond')], [], [], [], []]);
  const p = NS.computeProbabilities(st);
  assert.equal(p.openable, true);
  assert.equal(p.grades.length, 5);
  // 625 / 782
  const expected = 625 / (625 + 125 + 25 + 5 + 2);
  assert.ok(Math.abs(p.grades[0].gradeProb - expected) < 1e-9);
  assert.ok(Math.abs(p.grades[4].gradeProb - 2 / 782) < 1e-9);
  // grade probabilities sum to 1
  const sum = p.grades.reduce((a, g) => a + g.gradeProb, 0);
  assert.ok(Math.abs(sum - 1) < 1e-9);
});

test('computeProbabilities: per-item in-grade share uses weights, 0 disables', () => {
  const g1 = [mkItem('minecraft:wooden_sword', 1), mkItem('minecraft:stone_axe', 3), mkItem('minecraft:disabled', 0)];
  const st = probState([10, 20, 30, 40, 50], [g1, [], [], [], []]);
  const p = NS.computeProbabilities(st);
  const items = p.grades[0].items;
  assert.equal(items.length, 3);
  const stone = items.find((i) => i.value === 'minecraft:stone_axe');
  const wood = items.find((i) => i.value === 'minecraft:wooden_sword');
  const off = items.find((i) => i.value === 'minecraft:disabled');
  assert.equal(off.disabled, true);
  assert.equal(off.itemProb, 0);
  assert.ok(Math.abs(stone.itemProb - 3 / 4) < 1e-9);
  assert.ok(Math.abs(wood.itemProb - 1 / 4) < 1e-9);
  // overall = grade share × in-grade share
  assert.ok(Math.abs(p.grades[0].gradeProb * stone.itemProb - (10 / 150) * (3 / 4)) < 1e-9);
});

test('computeProbabilities: missing/empty weights default to 1', () => {
  const g1 = [mkItem('minecraft:a'), mkItem('minecraft:b', '')];
  const st = probState(['', '20', '', '', ''], [g1, [], [], [], []]);
  const p = NS.computeProbabilities(st);
  assert.equal(p.grades[0].weight, 0);
  // both items active with weight 1 → uniform
  assert.ok(Math.abs(p.grades[0].items[0].itemProb - 0.5) < 1e-9);
  assert.ok(Math.abs(p.grades[0].items[1].itemProb - 0.5) < 1e-9);
  // only grade2 has weight → its gradeProb is 1
  assert.ok(Math.abs(p.grades[1].gradeProb - 1) < 1e-9);
});

test('computeProbabilities: all-zero weights -> unopenable, empty grade flagged', () => {
  const st = probState(['0', '0', '0', '0', '0'], [[mkItem('minecraft:a')], [], [], [], []]);
  const p = NS.computeProbabilities(st);
  assert.equal(p.openable, false);
  assert.ok(p.grades.every((g) => g.gradeProb === 0));
  assert.equal(p.grades[1].empty, true);
  assert.equal(p.grades[1].items.length, 0);
  assert.equal(p.grades[0].empty, false);
});
/* ---------------- crate JSON -> price table (multi-document import) ---------------- */

function emptyPriceState() {
  const st = NS.emptyState();
  return st;
}

test('parseJsonDocuments: whole text is one JSON object', () => {
  const r = NS.parseJsonDocuments('{"name":"A","grade1":[]}');
  assert.equal(r.docs.length, 1);
  assert.equal(r.bad, 0);
  assert.equal(r.docs[0].name, 'A');
});

test('parseJsonDocuments: array of boxes', () => {
  const r = NS.parseJsonDocuments('[{"grade1":[]},{"grade2":[]}, 42]');
  assert.equal(r.docs.length, 2);
  assert.equal(r.bad, 1);
});

test('parseJsonDocuments: concatenated pretty-printed documents', () => {
  const text = '{\n  "name": "A",\n  "grade1": []\n}\n{\n  "name": "B",\n  "grade2": []\n}\n';
  const r = NS.parseJsonDocuments(text);
  assert.equal(r.docs.length, 2);
  assert.equal(r.docs[0].name, 'A');
  assert.equal(r.docs[1].name, 'B');
  assert.equal(r.bad, 0);
});

test('parseJsonDocuments: braces inside strings do not derail the scan', () => {
  const a = JSON.stringify({ name: 'A', grade1: [{ id: 'minecraft:x', tag: '{"GunId:\\"tacz:ak47\\""}' }] });
  const b = JSON.stringify({ name: 'B', grade1: [] });
  const r = NS.parseJsonDocuments(a + '\n' + b);
  assert.equal(r.docs.length, 2);
});

test('parseJsonDocuments: counts unparseable fragments as bad', () => {
  const r = NS.parseJsonDocuments('{"name":"A","grade1":[]} {"broken":');
  assert.equal(r.docs.length, 1);
  assert.equal(r.bad, 1);
});

test('collectCratePriceEntries: ids, variants, inline prices; tag/loot skipped', () => {
  const doc = {
    name: 'T', type: 'terminal',
    grade1: [
      { id: 'minecraft:iron_ingot', price: 200 },
      { id: 'minecraft:bow' },
      { tag: '#minecraft:swords' },
      { loot_table: 'minecraft:chests/simple_dungeon' },
      { id: 'tacz:ammo', tag: '{AmmoId:"tacz:9mm"}', price: 15 },
    ],
  };
  const r = NS.collectCratePriceEntries(doc);
  assert.equal(r.skipped, 2); // tag + loot_table cannot carry a price key
  const byKey = new Map(r.keys.map((e) => [e.key, e.price]));
  assert.equal(r.keys.length, 3);
  assert.equal(byKey.get('minecraft:iron_ingot'), 200);
  assert.equal(byKey.get('minecraft:bow'), null);
  assert.equal(byKey.get('tacz:ammo#tacz:9mm'), 15);
});

test('collectCratePriceEntries: negative/invalid inline price yields unpriced key', () => {
  const doc = {
    grade1: [
      { id: 'minecraft:x', price: -5 },
      { id: 'minecraft:y', price: 1.5 },
    ],
  };
  const r = NS.collectCratePriceEntries(doc);
  assert.equal(r.keys.length, 2);
  assert.ok(r.keys.every((e) => e.price === null));
});

test('mergeCratePriceEntries: new keys priced, duplicates averaged (round half up)', () => {
  const st = emptyPriceState();
  const res = NS.mergeCratePriceEntries(st, [
    { key: 'minecraft:a', price: 100 },
    { key: 'minecraft:a', price: 101 },
    { key: 'minecraft:b', price: 7 },
  ]);
  assert.equal(res.priced, 2);
  assert.equal(st.priceRows['minecraft:a'].price, 101); // (100+101)/2 = 100.5 -> 101
  assert.equal(st.priceRows['minecraft:b'].price, 7);
  assert.equal(st.priceRows['minecraft:a'].pinned, true);
});

test('mergeCratePriceEntries: existing priced row wins', () => {
  const st = emptyPriceState();
  st.priceRows['minecraft:a'] = { price: '999', pinned: true };
  const res = NS.mergeCratePriceEntries(st, [{ key: 'minecraft:a', price: 1 }]);
  assert.equal(res.priced, 0);
  assert.equal(res.keptPrices, 1);
  assert.equal(st.priceRows['minecraft:a'].price, '999');
});

test('mergeCratePriceEntries: price 0 is a real price, not blank', () => {
  const st = emptyPriceState();
  st.priceRows['minecraft:a'] = { price: 0, pinned: true };
  const res = NS.mergeCratePriceEntries(st, [{ key: 'minecraft:a', price: 50 }]);
  assert.equal(st.priceRows['minecraft:a'].price, 0);
});

test('mergeCratePriceEntries: unpriced keys become pinned empty rows once', () => {
  const st = emptyPriceState();
  const res = NS.mergeCratePriceEntries(st, [
    { key: 'minecraft:a', price: null },
    { key: 'minecraft:a', price: null },
    { key: 'minecraft:b', price: null },
  ]);
  assert.equal(res.priced, 0);
  assert.equal(res.addedEmpty, 2);
  assert.ok(st.priceRows['minecraft:a']);
  assert.equal(st.priceRows['minecraft:a'].price, '');
  assert.equal(st.priceRows['minecraft:b'].price, '');
  // re-import is a no-op for unpriced keys already present
  const res2 = NS.mergeCratePriceEntries(st, [{ key: 'minecraft:a', price: null }]);
  assert.equal(res2.addedEmpty, 0);
});

test('mergeCratePriceEntries: invalid keys are refused like buildPrices', () => {
  const st = emptyPriceState();
  const r1 = NS.collectCratePriceEntries({ grade1: [{ id: 'Not A Valid Id', price: 10 }] });
  // 'Not A Valid Id' is a non-empty id-source value; the merge must not add it
  NS.mergeCratePriceEntries(st, r1.keys);
  assert.equal(Object.keys(st.priceRows).length, 0);
});

test('item note: parsed from box JSON, emitted on export, empty note omitted', () => {
  const res = NS.boxToState({
    type: 'csbox', name: 'N',
    grade1: [{ id: 'minecraft:iron_ingot', weight: 3, note: '铁锭 备注', count: 2 }],
  }, 'test');
  assert.equal(res.state.grades[0][0].note, '铁锭 备注');

  const out = NS.buildBox(res.state, false);
  assert.equal(out.grade1[0].note, '铁锭 备注');
  assert.equal(out.grade1[0].weight, 3);

  // whitespace-only note is dropped on export
  const res2 = NS.boxToState({
    type: 'csbox',
    grade1: [{ id: 'minecraft:gold_ingot', note: '   ' }],
  }, 'test');
  assert.equal(NS.buildBox(res2.state, false).grade1[0].note, undefined);
});

test('computeProbabilities carries the note through', () => {
  const st = NS.emptyState();
  const it = NS.emptyItem();
  it.value = 'minecraft:iron_ingot';
  it.note = '铁锭';
  st.grades[0] = [it];
  const p = NS.computeProbabilities(st);
  assert.equal(p.grades[0].items[0].note, '铁锭');
  assert.equal(p.grades[0].items[0].value, 'minecraft:iron_ingot');
});

/* ---------------- simulator (simulateOpens) ---------------- */

/** Deterministic mulberry32 RNG so distribution tests are stable. */
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function simState() {
  const st = NS.emptyState();
  st.meta.random = [10, 30, 0, 0, 0];
  st.grades[0] = [
    Object.assign(NS.emptyItem(), { value: 'minecraft:stone', weight: 1 }),
    Object.assign(NS.emptyItem(), { value: 'minecraft:dirt', weight: 3 }),
  ];
  st.grades[1] = [
    Object.assign(NS.emptyItem(), { value: 'minecraft:iron_ingot', weight: 1, countMode: 'range', countMin: 2, countMax: 5 }),
  ];
  return st;
}

test('simulateOpens: openable=false yields zero counts', () => {
  const st = simState();
  st.meta.random = [0, 0, 0, 0, 0];
  const res = NS.simulateOpens(st, 10, mulberry32(1));
  assert.equal(res.openable, false);
  assert.equal(res.opened, 0);
  assert.equal(res.last, null);
});

test('simulateOpens: grade distribution matches theory within tolerance', () => {
  const st = simState();
  const N = 20000;
  const res = NS.simulateOpens(st, N, mulberry32(42));
  // grade1 mass 10/40, grade2 30/40
  assert.ok(Math.abs(res.gradeCounts[0] / N - 0.25) < 0.02, 'grade1 ' + res.gradeCounts[0] / N);
  assert.ok(Math.abs(res.gradeCounts[1] / N - 0.75) < 0.02, 'grade2 ' + res.gradeCounts[1] / N);
  assert.equal(res.gradeCounts[2] + res.gradeCounts[3] + res.gradeCounts[4], 0);
});

test('simulateOpens: item distribution matches weights (1:3) within tolerance', () => {
  const st = simState();
  const N = 20000;
  const res = NS.simulateOpens(st, N, mulberry32(7));
  const stone = res.items.find((i) => i.value === 'minecraft:stone');
  const dirt = res.items.find((i) => i.value === 'minecraft:dirt');
  const total = stone.hits + dirt.hits;
  assert.ok(Math.abs(stone.hits / total - 0.25) < 0.02, 'stone ' + stone.hits / total);
  assert.ok(Math.abs(dirt.hits / total - 0.75) < 0.02, 'dirt ' + dirt.hits / total);
});

test('simulateOpens: count range stays inside [min, max] and sums items', () => {
  const st = simState();
  const N = 5000;
  const res = NS.simulateOpens(st, N, mulberry32(123));
  const iron = res.items.find((i) => i.value === 'minecraft:iron_ingot');
  assert.equal(iron.hits, res.gradeCounts[1]);
  assert.ok(iron.itemTotal >= iron.hits * 2, 'itemTotal >= hits*2');
  assert.ok(iron.itemTotal <= iron.hits * 5, 'itemTotal <= hits*5');
});

test('simulateOpens: empty grades are conditioned out (mass redistributes)', () => {
  const st = simState();
  // grade2 has weight but we add an EMPTY grade3 with weight to test conditioning
  st.meta.random = [10, 30, 0, 40, 0];
  st.grades[3] = []; // empty despite weight 40
  const N = 20000;
  const res = NS.simulateOpens(st, N, mulberry32(99));
  // only grade1+grade2 have items -> their relative split must stay 10:30 = 1:3
  const total = res.gradeCounts[0] + res.gradeCounts[1];
  assert.ok(Math.abs(res.gradeCounts[0] / total - 0.25) < 0.02, 'grade1 share ' + res.gradeCounts[0] / total);
  assert.equal(res.gradeCounts[3], 0, 'empty grade never counted');
});

test('simulateOpens: rng injectable -> fully deterministic output', () => {
  const st = simState();
  const a = NS.simulateOpens(st, 500, mulberry32(2026));
  const b = NS.simulateOpens(st, 500, mulberry32(2026));
  assert.deepEqual(a.gradeCounts, b.gradeCounts);
  assert.deepEqual(a.items.map((i) => i.hits), b.items.map((i) => i.hits));
  assert.deepEqual(a.last, b.last);
});

/* ---------------- economy (boxEconomy) ---------------- */

test('boxEconomy: no grade weights -> warnNoWeight, un-openable', () => {
  const st = simState();
  st.meta.random = ['', '', '', '', ''];
  const en = NS.boxEconomy(st);
  assert.equal(en.openable, false);
  assert.deepEqual(en.warnings, [{ key: 'econ.warnNoWeight' }]);
  assert.equal(en.totalEv, 0);
});

test('boxEconomy: weights set but every grade empty -> warnEmpty', () => {
  const st = NS.emptyState();
  st.meta.random = [10, 0, 0, 0, 0];
  const en = NS.boxEconomy(st);
  assert.equal(en.openable, true);
  assert.deepEqual(en.warnings, [{ key: 'econ.warnEmpty' }]);
});

test('boxEconomy: EV = grade-share x item-share x price x count (range midpoint)', () => {
  const st = NS.emptyState();
  st.meta.random = [60, 40, 0, 0, 0];
  st.grades[0] = [Object.assign(NS.emptyItem(), { value: 'minecraft:stone', weight: 1 })];
  st.grades[1] = [Object.assign(NS.emptyItem(), {
    value: 'minecraft:dirt', weight: 1, countMode: 'range', countMin: 2, countMax: 4,
  })];
  st.priceRows = { 'minecraft:stone': { price: '100' }, 'minecraft:dirt': { price: '10-20' } };
  const en = NS.boxEconomy(st);
  assert.deepEqual(en.warnings, []);
  assert.equal(en.grades[0].prob, 0.6);
  assert.equal(en.grades[0].ev, 100);
  assert.equal(en.grades[1].prob, 0.4);
  assert.equal(en.grades[1].ev, 15 * 3); // mid price 15 x mid count 3
  assert.equal(en.totalEv, 0.6 * 100 + 0.4 * 45);
  // recycle = per-item ceil(price x 0.9): ceil(90) x1 and ceil(13.5)=14 x3
  assert.equal(en.grades[0].recycle, 90);
  assert.equal(en.grades[1].recycle, 42);
  assert.ok(Math.abs(en.recycleEv - (0.6 * 90 + 0.4 * 42)) < 1e-9, 'recycleEv=' + en.recycleEv);
  assert.equal(en.priced, 2);
  assert.equal(en.unpriced, 0);
});

test('boxEconomy: empty grades with weight are conditioned out of the shares', () => {
  const st = NS.emptyState();
  st.meta.random = [10, 30, 40, 0, 0];
  st.grades[0] = [Object.assign(NS.emptyItem(), { value: 'minecraft:stone' })];
  st.grades[1] = [Object.assign(NS.emptyItem(), { value: 'minecraft:dirt' })];
  // grade3 has weight 40 but no items -> mass redistributes 1:3 over g1/g2
  st.priceRows = { 'minecraft:stone': { price: '10' }, 'minecraft:dirt': { price: '10' } };
  const en = NS.boxEconomy(st);
  assert.equal(en.grades.length, 2);
  assert.ok(Math.abs(en.grades[0].prob - 0.25) < 1e-9);
  assert.ok(Math.abs(en.grades[1].prob - 0.75) < 1e-9);
  assert.ok(Math.abs(en.totalEv - 10) < 1e-9);
  // both items price 10 -> recycle ceil(9)=9 everywhere
  assert.ok(Math.abs(en.recycleEv - 9) < 1e-9, 'recycleEv=' + en.recycleEv);
});

test('boxEconomy: unpriced items count as 0 and produce warnings', () => {
  const st = NS.emptyState();
  st.meta.random = [100, 0, 0, 0, 0];
  st.grades[0] = [
    Object.assign(NS.emptyItem(), { value: 'minecraft:stone' }),
    Object.assign(NS.emptyItem(), { value: 'minecraft:dirt' }),
  ];
  st.priceRows = { 'minecraft:stone': { price: '100' } };
  const en = NS.boxEconomy(st);
  assert.equal(en.priced, 1);
  assert.equal(en.unpriced, 1);
  assert.equal(en.grades[0].ev, 50); // 1/2 x 100, dirt counts 0
  assert.deepEqual(en.warnings, [{ key: 'econ.warnSomeUnpriced', vars: { n: 1 } }]);
});

test('boxEconomy: an all-unpriced box warns once at the box level', () => {
  const st = NS.emptyState();
  st.meta.random = [100, 0, 0, 0, 0];
  st.grades[0] = [Object.assign(NS.emptyItem(), { value: 'minecraft:stone' })];
  const en = NS.boxEconomy(st);
  assert.equal(en.priced, 0);
  assert.deepEqual(en.warnings, [{ key: 'econ.warnAllUnpriced' }]);
});

test('boxEconomy: a fully unpriced grade is flagged with its 1-based number', () => {
  const st = NS.emptyState();
  st.meta.random = [50, 50, 0, 0, 0];
  st.grades[0] = [Object.assign(NS.emptyItem(), { value: 'minecraft:stone' })];
  st.grades[1] = [Object.assign(NS.emptyItem(), { value: 'minecraft:dirt' })];
  st.priceRows = { 'minecraft:stone': { price: '100' } };
  const en = NS.boxEconomy(st);
  assert.deepEqual(en.warnings, [
    { key: 'econ.warnSomeUnpriced', vars: { n: 1 } },
    { key: 'econ.warnGradeUnpriced', vars: { grade: '2' } },
  ]);
});

test('boxEconomy: weight-0 items are disabled and excluded', () => {
  const st = NS.emptyState();
  st.meta.random = [100, 0, 0, 0, 0];
  st.grades[0] = [
    Object.assign(NS.emptyItem(), { value: 'minecraft:stone', weight: 1 }),
    Object.assign(NS.emptyItem(), { value: 'minecraft:dirt', weight: 0 }),
  ];
  st.priceRows = { 'minecraft:stone': { price: '100' } };
  const en = NS.boxEconomy(st);
  assert.equal(en.grades[0].items, 1);
  assert.equal(en.grades[0].ev, 100);
  assert.deepEqual(en.warnings, []);
});

/* ---------------- apply (applyManifest) ---------------- */

function manifestState(fileName, itemId, price) {
  const st = NS.emptyState();
  st.fileName = fileName;
  st.meta.name = fileName;
  st.meta.random = [100, 0, 0, 0, 0];
  st.grades[0] = [Object.assign(NS.emptyItem(), { value: itemId })];
  st.priceRows = {};
  st.priceRows[itemId] = { price };
  return st;
}

test('applyManifest: one pretty JSON file per draft + merged prices', () => {
  const plan = NS.applyManifest([
    { payload: { versionKey: '1.21.1', state: manifestState('alpha', 'minecraft:stone', '100') } },
    { payload: { versionKey: '1.21.1', state: manifestState('beta', 'minecraft:dirt', '5-9') } },
  ]);
  assert.deepEqual(plan.files.map((f) => f.name), ['alpha.json', 'beta.json']);
  assert.equal(plan.conflicts, 0);
  const alpha = JSON.parse(plan.files[0].text);
  assert.equal(alpha.name, 'alpha');
  assert.ok(plan.files[0].text.endsWith('\n'), 'trailing newline');
  assert.deepEqual(plan.prices, { 'minecraft:stone': 100, 'minecraft:dirt': [5, 9] });
});

test('applyManifest: file names sanitize forbidden chars and leading underscores', () => {
  const plan = NS.applyManifest([
    { payload: { versionKey: '1.21.1', state: manifestState('_prices', 'minecraft:stone', '1') } },
    { payload: { versionKey: '1.21.1', state: manifestState('a/b:c*d?', 'minecraft:dirt', '1') } },
  ]);
  assert.deepEqual(plan.files.map((f) => f.name), ['prices.json', 'a_b_c_d_.json']);
});

test('applyManifest: colliding sanitized names get numbered, not overwritten', () => {
  const plan = NS.applyManifest([
    { payload: { versionKey: '1.21.1', state: manifestState('dup', 'minecraft:stone', '1') } },
    { payload: { versionKey: '1.21.1', state: manifestState('dup', 'minecraft:dirt', '1') } },
    { payload: { versionKey: '1.21.1', state: manifestState('dup', 'minecraft:sand', '1') } },
  ]);
  assert.deepEqual(plan.files.map((f) => f.name), ['dup.json', 'dup_2.json', 'dup_3.json']);
  assert.equal(plan.conflicts, 2);
});

test('applyManifest: later drafts win on shared price keys (last-wins merge)', () => {
  const plan = NS.applyManifest([
    { payload: { versionKey: '1.21.1', state: manifestState('one', 'minecraft:stone', '10') } },
    { payload: { versionKey: '1.21.1', state: manifestState('two', 'minecraft:stone', '20') } },
  ]);
  assert.deepEqual(plan.prices, { 'minecraft:stone': 20 });
});

test('applyManifest: empty workspace yields no files and null prices', () => {
  const plan = NS.applyManifest([]);
  assert.deepEqual(plan.files, []);
  assert.equal(plan.prices, null);
  assert.equal(plan.conflicts, 0);
});

test('applyManifest: skips entries without a payload state', () => {
  const plan = NS.applyManifest([
    { payload: null },
    { payload: { versionKey: '1.21.1', state: manifestState('ok', 'minecraft:stone', '1') } },
    null,
  ]);
  assert.deepEqual(plan.files.map((f) => f.name), ['ok.json']);
  assert.equal(plan.prices['minecraft:stone'], 1);
});

/* ---------------- validator registry cross-checks ---------------- */

function registryState(itemId, entityId) {
  const st = NS.emptyState();
  st.meta.name = 'x';
  st.meta.random = [100, 0, 0, 0, 0];
  if (entityId) st.meta.entity = [{ id: entityId, rate: '' }];
  st.grades[0] = [Object.assign(NS.emptyItem(), { value: itemId, source: 'id' })];
  st.priceRows = {};
  return st;
}

test('validator: no registry -> no unknown-id warnings', () => {
  delete NS.gameRegistry;
  const st = registryState('zzmod:test', 'zzmod:goblin');
  st.priceRows = { 'zzmod:test': { price: '1' } };
  const issues = NS.validate(st, '1.21.1');
  assert.equal(issues.filter((i) => i.key.startsWith('v.unknown')).length, 0);
});

test('validator: unknown item / entity / price key warn against the synced registry', () => {
  NS.gameRegistry = { items: new Set(['minecraft:stone']), entities: new Set(['minecraft:zombie']) };
  const st = registryState('zzmod:test', 'zzmod:goblin');
  st.priceRows = { 'zzmod:test': { price: '1' } };
  const issues = NS.validate(st, '1.21.1');
  const keys = issues.map((i) => i.key);
  assert.ok(keys.includes('v.unknownItem'), keys.join(','));
  assert.ok(keys.includes('v.unknownEntity'), keys.join(','));
  assert.ok(keys.includes('v.unknownPriceKey'), keys.join(','));
  delete NS.gameRegistry;
});

test('validator: known ids stay silent', () => {
  NS.gameRegistry = { items: new Set(['minecraft:stone']), entities: new Set(['minecraft:zombie']) };
  const st = registryState('minecraft:stone', 'minecraft:zombie');
  st.priceRows = { 'minecraft:stone': { price: '1' } };
  const issues = NS.validate(st, '1.21.1');
  assert.equal(issues.filter((i) => i.key.startsWith('v.unknown')).length, 0);
  delete NS.gameRegistry;
});

test('validator: variant price key checks the base id only', () => {
  NS.gameRegistry = { items: new Set(['tacz:ak47']), entities: new Set() };
  const st = registryState('tacz:ak47', null);
  st.taczEnabled = true;
  st.priceRows = { 'tacz:ak47#GunId': { price: '1' } };
  const issues = NS.validate(st, '1.21.1');
  assert.equal(issues.filter((i) => i.key.startsWith('v.unknown')).length, 0);
  delete NS.gameRegistry;
});
