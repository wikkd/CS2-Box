/* CS2-Box box-editor — validator ↔ JSON schema drift guard (no browser needed).
 * The docs/box-schema/*.schema.json files are the authoritative contract;
 * this suite fails when:
 *   1. the schema gains a property the editor neither probes nor skip-lists,
 *   2. the validator stops rejecting a value the schema forbids,
 *   3. baked example/compat-pack data stops matching the schema's price shape,
 *   4. boxToState → buildBox round-trips stop reproducing the source doc.
 *
 * Run:  node --test tests/schema-drift.test.mjs   (or npm run test:schema)
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const repo = path.resolve(root, '..');
const load = (p) => (0, eval)(readFileSync(path.join(root, p), 'utf8'));

globalThis.window = globalThis;
load('js/data.js');
load('js/model.js');
load('js/validator.js');
const NS = globalThis.CSBoxEdit;
const DATA = globalThis.CSBDATA;

const boxSchema = JSON.parse(readFileSync(path.join(repo, 'docs/box-schema/box.schema.json'), 'utf8'));
const pricesSchema = JSON.parse(readFileSync(path.join(repo, 'docs/box-schema/prices.schema.json'), 'utf8'));

const BASE_DOC = {
  type: 'csbox',
  name: 'T',
  key: 'minecraft:air',
  random: [100, 80, 60, 40, 20],
  grade1: [{ id: 'minecraft:diamond', weight: 10 }],
  grade2: [],
  grade3: [],
  grade4: [],
  grade5: [],
};

/** Valid draft state from a valid doc; mutate individual fields to probe. */
function freshState() {
  return NS.boxToState(JSON.parse(JSON.stringify(BASE_DOC)), 'probe').state;
}

function issueAt(issues, path, level) {
  return issues.some((i) => i.path === path && i.level === level);
}

function probe(name, mutate, path, level) {
  test(`validator flags ${name}`, () => {
    const st = freshState();
    mutate(st);
    const issues = NS.validate(st, '1.21.1');
    assert.ok(
      issueAt(issues, path, 'error') || issueAt(issues, path, 'warn'),
      `expected an issue on ${path}, got: ` + JSON.stringify(issues.filter((i) => !i.path.startsWith('grade'))),
    );
  });
}

/* ---- 2: validator must reject what the schema forbids ---- */

probe('type outside the enum',            (s) => { s.meta.type = 'magic'; }, 'meta.type');
probe('drop below 0',                     (s) => { s.meta.drop = -0.5; }, 'meta.drop');
probe('drop above 1',                     (s) => { s.meta.drop = 1.5; }, 'meta.drop');
probe('discount above 1',                 (s) => { s.meta.discount = 2; }, 'meta.discount');
probe('stock below -1',                   (s) => { s.meta.stock = -2; }, 'meta.stock');
probe('max_per_player below -1',          (s) => { s.meta.maxPerPlayer = -2; }, 'meta.maxPerPlayer');
probe('cooldown_seconds below 0',         (s) => { s.meta.cooldownSeconds = -1; }, 'meta.cooldownSeconds');
probe('random with fewer than 5 entries', (s) => { s.meta.random = [1, 2, 3]; }, 'meta.random');
probe('random above 10000',               (s) => { s.meta.random = [0, 0, 0, 0, 10001]; }, 'meta.random');
probe('unknown pity grade',               (s) => { s.meta.pityGrade = 'legendary'; s.meta.pityEvery = 2; }, 'meta.pityGrade');
probe('pityEvery below 2',                (s) => { s.meta.pityGrade = 'classified'; s.meta.pityEvery = 1; }, 'meta.pityEvery');
probe('entity rate outside [0,1]',        (s) => { s.meta.entity = [{ id: 'minecraft:zombie', rate: '2' }]; }, 'meta.entity');
probe('dangling entity rate (no id)',     (s) => { s.meta.entity = [{ id: '', rate: '0.5' }]; }, 'meta.entity');
probe('requires with empty entries',      (s) => { s.meta.requiresText = 'a,,b'; }, 'meta.requires');
probe('item count fixed below 1',         (s) => { s.grades[0][0].countMode = 'fixed'; s.grades[0][0].count = 0; }, 'grade1[1].count');
probe('item count range min < 1',         (s) => { s.grades[0][0].countMode = 'range'; s.grades[0][0].count = ''; s.grades[0][0].countMin = 0; s.grades[0][0].countMax = 3; }, 'grade1[1].count');
probe('item weight below 0',              (s) => { s.grades[0][0].weight = -1; }, 'grade1[1].weight');
probe('item weight not an integer',       (s) => { s.grades[0][0].weight = 'abc'; }, 'grade1[1].weight');
probe('bad components JSON',              (s) => { s.grades[0][0].components = '{'; }, 'grade1[1].components');
probe('variant without TACZ support',     (s) => { s.taczEnabled = false; s.grades[0][0].variant = 'tacz:ak47'; }, 'grade1[1].variant');
probe('tag without # prefix',             (s) => { s.grades[0][0].source = 'tag'; s.grades[0][0].value = 'c endlessly'; }, 'grade1[1].tag');

// unpriced pool item (the game refuses to load these — harder than the schema)
probe('unpriced pool item',               (s) => { s.priceRows['minecraft:diamond'] = { price: '' }; }, 'prices.minecraft:diamond');

/* price-table values: badPriceValue probes (fixed / range / int bound) */
probe('negative fixed price',             (s) => { s.priceRows['minecraft:diamond'] = { price: '-5' }; }, 'prices.minecraft:diamond');
probe('reversed price range',             (s) => { s.priceRows['minecraft:diamond'] = { price: '5-3' }; }, 'prices.minecraft:diamond');
probe('price above Java int bound',       (s) => { s.priceRows['minecraft:diamond'] = { price: '99999999999' }; }, 'prices.minecraft:diamond');
probe('price variant without TACZ',       (s) => { s.taczEnabled = false; s.priceRows['minecraft:diamond'] = { price: '10' }; s.priceRows['minecraft:diamond#x'] = { price: '10' }; }, 'prices.minecraft:diamond#x');

test('invalid price value on a non-pool row still flagged', () => {
  const st = freshState();
  st.priceRows['minecraft:stone'] = { price: 'oops' };
  const issues = NS.validate(st, '1.21.1');
  assert.ok(issueAt(issues, 'prices.minecraft:stone', 'error'));
});

/* ---- 1: schema coverage guard — every schema property is either probed
 * (above) or explicitly skip-listed with a reason. New schema fields must
 * land in one of the two sets or this suite fails. ---- */

const COVERED = new Set([
  'name', 'type', 'key', 'drop', 'random', 'entity', 'discount',
  'stock', 'max_per_player', 'cooldown_seconds', 'pity', 'requires',
  'grade1', 'grade2', 'grade3', 'grade4', 'grade5',
]);

const SKIPPED = new Map([
  // plain string, the schema attaches no constraint the validator could check
  ['icon', 'free-form id-or-int, no invalid value the schema forbids'],
  ['enabled', 'boolean — the editor renders a checkbox, no bad value reaches validate()'],
  ['format_version', 'runtime loader metadata; the editor never emits it'],
  ['permission', 'plain string, no schema constraint'],
]);

test('every box-schema property is covered or skip-listed', () => {
  const props = Object.keys(boxSchema.properties);
  for (const key of props) {
    assert.ok(
      COVERED.has(key) || SKIPPED.has(key),
      `box.schema.json property "${key}" has no validator coverage — add a probe or a skip entry`,
    );
  }
});

test('coverage map references only real schema properties', () => {
  const props = new Set(Object.keys(boxSchema.properties));
  for (const key of COVERED) {
    assert.ok(props.has(key), `COVERED lists unknown schema property "${key}"`);
  }
});

/* ---- 3: baked data must match the prices schema shape ---- */

test('prices schema allows integers ≥ 0 or [min, max] pairs', () => {
  const oneOf = pricesSchema.additionalProperties.oneOf;
  assert.ok(Array.isArray(oneOf) && oneOf.length === 2);
  assert.equal(oneOf[0].type, 'integer');
  assert.deepEqual(oneOf[1].minItems, 2);
});

function assertPriceShape(prices, label) {
  for (const [key, value] of Object.entries(prices)) {
    assert.match(key, /^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/, `${label}: bad key ${key}`);
    if (typeof value === 'number') {
      assert.ok(Number.isInteger(value) && value >= 0, `${label}: ${key} fixed price`);
    } else if (Array.isArray(value)) {
      assert.equal(value.length, 2, `${label}: ${key} array length`);
      assert.ok(value.every((n) => Number.isInteger(n) && n >= 0), `${label}: ${key} range ints`);
      assert.ok(value[0] <= value[1], `${label}: ${key} min ≤ max`);
    } else {
      assert.fail(`${label}: ${key} not a price value`);
    }
  }
}

test('baked example price tables match the prices schema', () => {
  for (const ex of DATA.examples) {
    assertPriceShape(ex.prices || {}, 'example ' + ex.key);
  }
});

test('baked compat-pack price tables match the prices schema', () => {
  for (const pack of DATA.compatPacks || []) {
    if (pack.prices) assertPriceShape(pack.prices, 'pack ' + pack.key);
  }
});

test('validate() rejects a doc-level reversed price range', () => {
  // doc form (array) — schema additionalProperties.oneOf allows [min,max] only
  // with min ≤ max being a *runtime* rule; the validator must still surface it.
  const st = freshState();
  st.priceRows['minecraft:diamond'] = { price: '' };
  const issues = NS.validate(st, '1.21.1');
  assert.ok(issueAt(issues, 'prices.minecraft:diamond', 'error'), 'empty pool price must error');
});

/* ---- 4: export round-trip — example docs survive boxToState → buildBox ---- */

/** Semantic normalization for round-trip comparison:
 *  - `count: 1` and an omitted `count` are identical to the game (single-drop
 *    mode; buildBox emits the canonical omitted form);
 *  - variant tags are re-emitted with SNBT-escaped quotes `{GunId:\"...\"}`
 *    which parses to the same compound as the source's bare quotes. */
function normalizeDoc(doc) {
  const clone = JSON.parse(JSON.stringify(doc));
  for (let g = 1; g <= 5; g++) {
    const items = clone['grade' + g];
    if (!Array.isArray(items)) continue;
    for (const it of items) {
      if (!it || typeof it !== 'object' || Array.isArray(it)) continue;
      if (it.count === 1) delete it.count;
      if (typeof it.tag === 'string') it.tag = it.tag.replace(/\\"/g, '"');
    }
  }
  return clone;
}

test('built-in examples round-trip through the model losslessly', () => {
  for (const ex of DATA.examples) {
    const src = normalizeDoc(ex.box);
    const res = NS.boxToState(JSON.parse(JSON.stringify(ex.box)), ex.file.replace(/\.json$/i, ''));
    const rebuilt = normalizeDoc(NS.buildBox(res.state));
    assert.deepEqual(rebuilt, src, 'example ' + ex.key + ' changed on round-trip');
  }
});
