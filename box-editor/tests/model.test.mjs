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