/* CS2-Box box-editor smoke test (Playwright).
 * Starts the zero-dependency static server, drives a headless Chromium through
 * the critical UI paths, and fails the process with exit 1 on any failure.
 *
 * Run locally:  cd box-editor && npx playwright install chromium && node tests/smoke.mjs
 * CI:          .github/workflows/box-editor-smoke.yml
 */
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { chromium } from 'playwright';

const PORT = 4317;
const BASE = 'http://127.0.0.1:' + PORT + '/';
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const results = [];
function check(name, ok, detail) {
  results.push({ name, ok: !!ok, detail: detail || '' });
  console.log((ok ? 'PASS' : 'FAIL') + '  ' + name + (detail ? '  [' + detail + ']' : ''));
}

function makeShareState(fn) {
  const json = JSON.stringify({
    fileName: fn,
    meta: { name: 'Shared Box', type: 'csbox', key: 'minecraft:iron_ingot', drop: '', icon: '',
            enabled: true, requiresText: 'tacz', entity: [{ id: 'minecraft:zombie', rate: '' }],
            random: ['', '', '', '', ''], discount: '', stock: '', restockMinutes: '',
            maxPerPlayer: '', cooldownSeconds: '', permission: '' },
    grades: [[{ source: 'id', value: 'minecraft:diamond', countMode: 'single', count: 1, countMin: 1, countMax: 1, weight: 1, enchantMode: 'none', enchantId: '', enchantLevel: 1, enchantLevelMode: 'fixed', enchantLevelMin: 1, enchantLevelMax: 1, variant: 'tacz:ak47', variantField: 'GunId', tagRaw: '', components: '' }], [], [], [], []],
    priceRows: { 'minecraft:emerald': { price: '99', pinned: true } },
    versionKey: '26.2',
    taczEnabled: true
  });
  return btoa(unescape(encodeURIComponent(json))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function waitForServer() {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(BASE);
      if (res.ok) return true;
    } catch (e) { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 300));
  }
  return false;
}

let server = null;
let browser = null;
try {
  server = spawn(process.execPath, ['server.mjs', '--no-open', '--port', String(PORT)], {
    cwd: ROOT, stdio: 'ignore', windowsHide: true,
  });
  if (!(await waitForServer())) throw new Error('server did not start on ' + PORT);

  try {
    browser = await chromium.launch();
  } catch (e) {
    // Fall back to a system Chrome when the Playwright bundle is not installed.
    browser = await chromium.launch({ channel: 'chrome' });
  }
  const page = await browser.newPage();

  // ---- load ----
  await page.goto(BASE, { waitUntil: 'load' });
  check('title contains CS2-Box', /CS2-Box/.test(await page.title()));

  // field() regression: meta card must render real widgets (was escaped text)
  const metaInputs = await page.locator('#card-meta input, #card-meta select').count();
  check('meta card has real inputs', metaInputs >= 6, 'count=' + metaInputs);
  const noGhost = !(await page.locator('#card-meta').innerHTML()).includes('&lt;input');
  check('no escaped ghost inputs', noGhost);

  const helpCount = await page.locator('[data-help]').count();
  check('data-help covers >= 100 widgets', helpCount >= 100, 'count=' + helpCount);

  // ---- hover tooltip ----
  await page.locator('[data-f="meta.fileName"]').hover();
  await page.waitForTimeout(350);
  const tipVisible = await page.locator('.field-tooltip').evaluate((el) => el.classList.contains('show'));
  const tipText = (await page.locator('.field-tooltip').textContent()).trim();
  check('hover tooltip shows', tipVisible && tipText.length > 0, tipText.slice(0, 40));

  // ---- TACZ switch + version gating ----
  await page.selectOption('#version-select', '26.2');
  await page.waitForTimeout(250);
  const variant0 = await page.locator('[data-f$=".variant"]').count();
  check('26.2 auto hides TACZ fields', variant0 === 0, 'count=' + variant0);
  await page.locator('#tacz-toggle').click(); // auto -> enabled
  await page.waitForTimeout(250);
  const variantOn = await page.locator('[data-f$=".variant"]').count();
  check('switch forces TACZ fields on for 26.2', variantOn > 0, 'count=' + variantOn);
  await page.locator('#tacz-toggle').click(); // enabled -> disabled
  await page.waitForTimeout(200);
  const variantOff = await page.locator('[data-f$=".variant"]').count();
  check('switch forces TACZ fields off', variantOff === 0, 'count=' + variantOff);
  await page.locator('#tacz-toggle').click(); // disabled -> auto
  await page.waitForTimeout(150);
  await page.selectOption('#version-select', '1.21.1');

  // ---- undo / redo ----
  const before = await page.locator('[data-f$=".weight"]').count();
  await page.click('[data-action="add-item"][data-grade="0"]');
  await page.waitForTimeout(250);
  const afterAdd = await page.locator('[data-f$=".weight"]').count();
  await page.click('[data-action="undo"]');
  await page.waitForTimeout(250);
  const afterUndo = await page.locator('[data-f$=".weight"]').count();
  await page.click('[data-action="redo"]');
  await page.waitForTimeout(250);
  const afterRedo = await page.locator('[data-f$=".weight"]').count();
  check('undo/redo roundtrip', before === afterUndo && afterAdd === afterRedo && afterAdd === before + 1,
    before + '->' + afterAdd + '->' + afterUndo + '->' + afterRedo);

  // ---- item paste ----
  await page.click('[data-action="paste-items"][data-grade="1"]');
  await page.fill('#paste-textarea', 'minecraft:stone\nminecraft:andesite');
  await page.click('#paste-do');
  await page.waitForTimeout(350);
  const grade1 = await page.locator('#grade-items-1 [data-f$=".weight"]').count();
  check('item paste adds entries', grade1 >= 2, 'grade1 count=' + grade1);
  await page.click('[data-action="undo"]'); // revert paste to keep later counts predictable
  await page.waitForTimeout(250);

  // ---- price batch import ----
  await page.click('[data-action="paste-prices"]');
  await page.fill('#paste-textarea', 'minecraft:test_shard\t42\ntacz:mgun#tacz:x 7');
  await page.click('#paste-do');
  await page.waitForTimeout(350);
  const hasShard = await page.locator('tr[data-price-key="minecraft:test_shard"]').count();
  const hasVariantPrice = await page.locator('tr[data-price-key="tacz:mgun#tacz:x"]').count();
  check('price batch import', hasShard === 1 && hasVariantPrice === 1);

  // ---- prices tab preview ----
  await page.click('#tab-prices');
  await page.waitForTimeout(150);
  const pricesText = (await page.locator('#json-preview').textContent()) || '';
  check('prices tab shows JSON', pricesText.trim().startsWith('{') && pricesText.includes('test_shard'));
  await page.click('#tab-box');
  await page.waitForTimeout(150);

  // ---- collapsible sections ----
  const dropSec = page.locator('#card-drop');
  check('drop section starts collapsed', await dropSec.evaluate((el) => el.classList.contains('collapsed')));
  await dropSec.locator('.col-head').click();
  await page.waitForTimeout(150);
  check('drop header click expands section', !(await dropSec.evaluate((el) => el.classList.contains('collapsed'))));
  await dropSec.locator('.col-head').click();
  await page.waitForTimeout(150);
  check('drop header click collapses again', await dropSec.evaluate((el) => el.classList.contains('collapsed')));
  const gradesSec = page.locator('#card-grades');
  check('grade action button does not collapse card', !(await gradesSec.evaluate((el) => el.classList.contains('collapsed'))));

  // ---- issue click jump ----
  const fn = page.locator('[data-f="meta.fileName"]');
  await fn.fill('');
  await page.waitForTimeout(550);
  const issue = page.locator('.issue[data-issue-path="meta.fileName"]');
  if ((await issue.count()) > 0) {
    await issue.click();
    await page.waitForTimeout(400);
    const flashed = await fn.evaluate((el) => el.classList.contains('flash-highlight'));
    check('issue click highlights field', flashed);
  } else {
    check('issue click highlights field', false, 'no issue row found');
  }

  // ---- share link restore ----
  const shareUrl = BASE + '#state=' + makeShareState('shared_box_test');
  await page.goto(shareUrl, { waitUntil: 'load' });
  // goto to the same document with a different hash only fires hashchange;
  // reload simulates opening the shared link in a fresh tab.
  await page.waitForTimeout(200);
  await page.reload({ waitUntil: 'load' });
  await page.waitForTimeout(300);
  const sharedFn = await page.locator('[data-f="meta.fileName"]').inputValue();
  const sharedVer = await page.locator('#version-select').inputValue();
  const emeraldRow = await page.locator('tr[data-price-key="minecraft:emerald"]').count();
  check('share link restores state', sharedFn === 'shared_box_test' && sharedVer === '26.2' && emeraldRow === 1,
    sharedFn + '/' + sharedVer + '/rows=' + emeraldRow);

  const failed = results.filter((r) => !r.ok);
  console.log('\n' + (results.length - failed.length) + '/' + results.length + ' checks passed');
  process.exitCode = failed.length ? 1 : 0;
} catch (e) {
  console.error('SMOKE ERROR:', e && e.message ? e.message : e);
  process.exitCode = 1;
} finally {
  if (browser) await browser.close();
  if (server) { server.kill(); await new Promise((r) => setTimeout(r, 200)); }
}