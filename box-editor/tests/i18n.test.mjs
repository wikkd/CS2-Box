/* CS2-Box box-editor — i18n dictionary regression tests.
 * Guards the zh/en dictionaries: both must define exactly the same keys, and
 * every key referenced from app.js / validator.js must exist (otherwise the UI
 * silently falls back to the raw key string).
 *
 * Run:  node --test tests/i18n.test.mjs
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const read = (p) => readFileSync(path.join(root, p), 'utf8');

/** Extract the top-level keys of one language block from i18n.js. */
function keysOfLang(src, lang) {
  const re = new RegExp('\\b' + lang + '\\s*:\\s*\\{');
  const m = re.exec(src);
  assert.ok(m, `dictionary "${lang}" not found`);
  const start = src.indexOf('{', m.index);
  let depth = 0, end = -1, quote = null, esc = false;
  for (let i = start; i < src.length; i++) {
    const c = src[i];
    if (quote) {
      if (esc) esc = false;
      else if (c === '\\') esc = true;
      else if (c === quote) quote = null;
      continue;
    }
    if (c === "'" || c === '"' || c === '`') { quote = c; continue; }
    if (c === '{') depth++;
    else if (c === '}') { depth--; if (depth === 0) { end = i; break; } }
  }
  assert.ok(end > start, `unterminated "${lang}" block`);
  const body = src.slice(start, end + 1);
  const keys = new Set();
  const keyRe = /^\s*'?([A-Za-z0-9_.\-]+)'?\s*:/gm;
  let km;
  while ((km = keyRe.exec(body))) keys.add(km[1]);
  return keys;
}

const i18nSrc = read('js/i18n.js');
const zh = keysOfLang(i18nSrc, 'zh');
const en = keysOfLang(i18nSrc, 'en');

test('zh and en dictionaries define the same keys', () => {
  const onlyZh = [...zh].filter((k) => !en.has(k)).sort();
  const onlyEn = [...en].filter((k) => !zh.has(k)).sort();
  assert.deepEqual(onlyZh, [], 'keys missing from the en dictionary');
  assert.deepEqual(onlyEn, [], 'keys missing from the zh dictionary');
  assert.ok(zh.size > 150, `expected a substantial dictionary, got ${zh.size} keys`);
});

test('every t("...") key referenced by the UI exists in both dictionaries', () => {
  const sources = ['js/app.js', 'js/validator.js'];
  const used = new Set();
  for (const file of sources) {
    const src = read(file);
    // t('key') / t("key") — skip dynamic calls like t(i.key, ...)
    for (const m of src.matchAll(/\bt\(\s*'([A-Za-z0-9_.\-]+)'/g)) used.add(m[1]);
    for (const m of src.matchAll(/\bt\(\s*"([A-Za-z0-9_.\-]+)"/g)) used.add(m[1]);
    // data-help="key" tooltips are resolved through t() as well
    for (const m of src.matchAll(/data-help="([A-Za-z0-9_.\-]+)"/g)) used.add(m[1]);
  }
  const missingZh = [...used].filter((k) => !zh.has(k)).sort();
  const missingEn = [...used].filter((k) => !en.has(k)).sort();
  assert.deepEqual(missingZh, [], 'keys used by the UI but missing from zh');
  assert.deepEqual(missingEn, [], 'keys used by the UI but missing from en');
});

test('version metadata exposes the itemModel capability flag', () => {
  globalThis.window = globalThis;
  const dataSrc = read('js/data.js');
  const data = (0, eval)(dataSrc);
  assert.ok(Array.isArray(data.versions) && data.versions.length >= 6);
  for (const v of data.versions) {
    assert.equal(typeof v.itemModel, 'boolean', `version ${v.key} must declare itemModel`);
  }
  const byKey = Object.fromEntries(data.versions.map((v) => [v.key, v]));
  assert.equal(byKey['1.21.1'].itemModel, false);
  assert.equal(byKey['forge-1.20.1'].itemModel, false);
  assert.equal(byKey['26.2'].itemModel, true);
});
