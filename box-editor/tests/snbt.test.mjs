/* CS2-Box box-editor — SNBT parser/writer unit tests (no browser needed).
 * The tag field round-trips JSON objects and vanilla SNBT; js/snbt.js is the
 * SNBT side. Covers the grammar (compounds, typed numbers/arrays, quoted and
 * unquoted strings, escapes) and the known-mod conversion paths that SNBT
 * strings unlock (canConvertItemForm / convertItemForm on hand-written tags,
 * canonical SNBT display for imported object tags).
 *
 * Run:  node --test tests/snbt.test.mjs   (or npm run test:snbt)
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
load('js/snbt.js');
load('js/model.js');
load('js/validator.js');
const NS = globalThis.CSBoxEdit;

/* ------------------------------ parser ------------------------------ */

test('parse: plain compound with quoted string', () => {
  assert.deepEqual(NS.parseSnbt('{GunId:"tacz:ak47"}'), { GunId: 'tacz:ak47' });
});

test('parse: typed number suffixes collapse to JS numbers', () => {
  assert.deepEqual(
    NS.parseSnbt('{Count:1b,Damage:12s,Data:7,Age:-5L,Health:20.0f,Pi:3.14d,Half:0.5}'),
    { Count: 1, Damage: 12, Data: 7, Age: -5, Health: 20, Pi: 3.14, Half: 0.5 });
});

test('parse: true/false literals become booleans', () => {
  assert.deepEqual(NS.parseSnbt('{Invulnerable:true,Glowing:false}'),
    { Invulnerable: true, Glowing: false });
});

test('parse: single-quoted strings and escapes', () => {
  assert.deepEqual(NS.parseSnbt('{Name:\'A \\"quoted\\" \\\\ name\'}'),
    { Name: 'A "quoted" \\ name' });
});

test('parse: nested compounds and lists', () => {
  assert.deepEqual(
    NS.parseSnbt('{display:{Name:\'{"text":"Sword"}\'},ench:[{id:16s,lvl:5s}],mods:[a,b]}'),
    { display: { Name: '{"text":"Sword"}' }, ench: [{ id: 16, lvl: 5 }], mods: ['a', 'b'] });
});

test('parse: typed arrays including empty', () => {
  assert.deepEqual(NS.parseSnbt('[B;1b,2b]'), [1, 2]);
  assert.deepEqual(NS.parseSnbt('[I;1,2,3]'), [1, 2, 3]);
  assert.deepEqual(NS.parseSnbt('[L;]'), []);
});

test('parse: whitespace tolerance and bare colon ids', () => {
  assert.deepEqual(NS.parseSnbt('{ GunId : "tacz:ak47" , Count : 1b }'),
    { GunId: 'tacz:ak47', Count: 1 });
  assert.deepEqual(NS.parseSnbt('{Potion:minecraft:water}'),
    { Potion: 'minecraft:water' });
});

test('parse: quoted keys', () => {
  assert.deepEqual(NS.parseSnbt('{"weird key":1}'), { 'weird key': 1 });
});

test('parse errors: report a position', () => {
  const bad = ['{a:1', '{a:1}x', '{a 1}', '{a:}', '{a:1,,b:2}', '[B;x]', '"unterminated', '{a:1,}'];
  for (const s of bad) {
    assert.throws(() => NS.parseSnbt(s), /SNBT position/, s);
  }
});

/* ------------------------------ writer ------------------------------ */

test('stringify: canonical SNBT round-trips through parse', () => {
  const src = '{display:{Name:"Sword"},ench:[{id:16,lvl:5}],flags:[1,0]}';
  assert.equal(NS.stringifySnbt(NS.parseSnbt(src)), src);
});

test('stringify: booleans become byte literals, numbers stay bare', () => {
  assert.equal(NS.stringifySnbt({ Invulnerable: true, Count: 1, Pi: 3.14 }),
    '{Invulnerable:1b,Count:1,Pi:3.14}');
});

test('stringify: string values are always quoted (vanilla writer form)', () => {
  assert.equal(NS.stringifySnbt({ a: '5', b: 'true', c: '', d: 'with space', e: 'tacz:ak47' }),
    '{a:"5",b:"true",c:"",d:"with space",e:"tacz:ak47"}');
});

test('stringify: keys with colons or spaces are quoted', () => {
  assert.equal(NS.stringifySnbt({ 'minecraft:custom_data': { GunId: 'tacz:ak47' } }),
    '{"minecraft:custom_data":{GunId:"tacz:ak47"}}');
});

/* --------------------------- model integration --------------------------- */

test('variantFromEntry: single-quoted SNBT (regex alone would miss)', () => {
  assert.deepEqual(NS.variantFromEntry({ id: 'tacz:modern_kinetic_gun', tag: "{GunId:'tacz:ak47'}" }),
    { field: 'GunId', id: 'tacz:ak47' });
});

test('variantFromEntry: spaced SNBT', () => {
  assert.deepEqual(NS.variantFromEntry({ id: 'x', tag: '{ VariantId : tacz:foo }' }),
    { field: 'VariantId', id: 'tacz:foo' });
});

test('parseItem: JSON-object tag displays as canonical SNBT', () => {
  const it = NS.parseItem({ id: 'minecraft:stone', tag: { CustomModelData: 5, Flag: true } }, []);
  assert.equal(it.tagRaw, '{CustomModelData:5,Flag:1b}');
});

test('parseItem: SNBT tag with variant populates the variant fields', () => {
  const it = NS.parseItem({ id: 'tacz:modern_kinetic_gun', tag: '{GunId:"tacz:ak47"}' }, []);
  assert.equal(it.variant, 'tacz:ak47');
  assert.equal(it.variantField, 'GunId');
  assert.equal(it.tagRaw, '');
});

test('buildItem: legacy variant branch emits SNBT string', () => {
  const it = NS.emptyItem();
  it.source = 'id';
  it.value = 'tacz:modern_kinetic_gun';
  it.variant = 'tacz:ak47';
  it.variantField = 'GunId';
  const o = NS.buildItem(it, false, false);
  assert.equal(o.tag, '{GunId:"tacz:ak47"}');
});

test('convert: hand-written SNBT converts to the components era', () => {
  const it = NS.emptyItem();
  it.source = 'id';
  it.value = 'tacz:modern_kinetic_gun';
  it.tagRaw = '{GunId:"tacz:ak47",Count:1b}';
  assert.equal(NS.canConvertItemForm(it), true);
  const moves = NS.convertItemForm(it, true);
  assert.deepEqual(moves, ['GunId']);
  const comps = JSON.parse(it.components);
  assert.deepEqual(comps['minecraft:custom_data'], { GunId: 'tacz:ak47' });
  assert.equal(it.tagRaw, '{Count:1}');
});

test('convert: components era back to SNBT tag (block entity data)', () => {
  const it = NS.emptyItem();
  it.source = 'id';
  it.value = 'superbwarfare:container';
  it.components = JSON.stringify({ 'minecraft:block_entity_data': { EntityType: 'superbwarfare:truck' } });
  const moves = NS.convertItemForm(it, false);
  assert.deepEqual(moves, ['BlockEntityTag']);
  assert.equal(it.tagRaw, '{BlockEntityTag:{EntityType:"superbwarfare:truck"}}');
  assert.equal(it.components, '');
});

/* ------------------------------ validator ------------------------------ */

function stateWithTag(tag) {
  return {
    fileName: 't',
    meta: NS.emptyMeta(),
    grades: [[Object.assign(NS.emptyItem(), {
      source: 'id', value: 'minecraft:stone', tagRaw: tag,
    })], [], [], [], []],
    priceRows: {},
    tiers: null,
    taczEnabled: null,
  };
}

test('validator: malformed SNBT in tagRaw is an error carrying the position', () => {
  const issues = NS.validate(stateWithTag('{GunId:"tacz:ak47"'), '1.21.1');
  const hit = issues.find((i) => i.key === 'v.badTagSnbt');
  assert.ok(hit, 'expected a v.badTagSnbt issue');
  assert.match(hit.vars.msg, /SNBT position/);
});

test('validator: JSON-object and valid SNBT tags pass clean', () => {
  for (const tag of ['{"GunId":"tacz:ak47"}', '{GunId:"tacz:ak47"}', '{CustomModelData:5}']) {
    const issues = NS.validate(stateWithTag(tag), '1.21.1');
    assert.ok(!issues.some((i) => i.key === 'v.badTagSnbt' || i.key === 'v.badTag'), tag);
  }
});
