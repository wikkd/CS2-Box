/* CS2-Box box-editor smoke test (Playwright).
 * Two pages: index.html (box config) and prices.html (price table), sharing
 * one localStorage state. Starts the static server, drives headless Chromium
 * through the critical UI paths, exits 1 on any failure.
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
    browser = await chromium.launch({ channel: 'chrome' });
  }
  const page = await browser.newPage();

  /* ================= box config page (index.html) ================= */
  await page.goto(BASE, { waitUntil: 'load' });
  check('title contains CS2-Box', /CS2-Box/.test(await page.title()));

  const metaInputs = await page.locator('#card-meta input, #card-meta select').count();
  check('meta card has real inputs', metaInputs >= 6, 'count=' + metaInputs);
  check('no escaped ghost inputs',
    !(await page.locator('#card-meta').innerHTML()).includes('&lt;input'));

  const helpCount = await page.locator('[data-help]').count();
  check('data-help covers >= 100 widgets', helpCount >= 100, 'count=' + helpCount);

  // box page: no price card
  check('box page hides price card', (await page.locator('#card-prices').count()) === 0);
  check('nav highlights Box Config',
    await page.locator('#nav-box').evaluate((el) => el.classList.contains('active')));

  // built-in key suggestions
  await page.evaluate(() => {
    const sel = document.querySelector('select[data-f="meta.type"]');
    if (sel && sel.value === 'terminal') { sel.value = 'csbox'; sel.dispatchEvent(new Event('change', { bubbles: true })); }
  });
  const keyListAttr = await page.locator('[data-f="meta.key"]').getAttribute('list');
  const keyOptionCount = await page.locator('#csbox-key-list option').count();
  check('key input offers built-in key dropdown',
    keyListAttr === 'csbox-key-list' && keyOptionCount >= 5, 'options=' + keyOptionCount);

  // name color picker: picking a color inserts the #RRGGBB prefix
  const colorInput = page.locator('#name-color');
  check('name color picker present', (await colorInput.count()) === 1);
  await colorInput.fill('#00aa66');
  const nameAfterPick = await page.locator('[data-f="meta.name"]').inputValue();
  check('color picker writes #RRGGBB prefix',
    /^#00aa66\s/.test(nameAfterPick), nameAfterPick.slice(0, 24));

  // entity id dropdown with every vanilla mob
  const entityListAttr = await page.locator('[data-f="meta.entity.0.id"]').getAttribute('list');
  const entityOptionCount = await page.locator('#csbox-entity-list option').count();
  check('entity input offers vanilla mob dropdown',
    entityListAttr === 'csbox-entity-list' && entityOptionCount >= 70, 'options=' + entityOptionCount);

  // TACZ import tutorial dialog
  await page.click('[data-action="tacz-tutorial"]');
  const tutorialOpen = await page.locator('#tutorial-dialog').evaluate((el) => el.open);
  const tutorialLis = await page.locator('#tutorial-body li').count();
  const tutorialHasReload = ((await page.locator('#tutorial-body').textContent()) || '').includes('/csbox reload');
  check('TACZ tutorial dialog opens with import steps',
    tutorialOpen && tutorialLis >= 8 && tutorialHasReload, 'li=' + tutorialLis);
  await page.click('#tutorial-close');

  await page.locator('[data-f="meta.fileName"]').hover();
  await page.waitForTimeout(350);
  const tipVisible = await page.locator('.field-tooltip').evaluate((el) => el.classList.contains('show'));
  const tipText = (await page.locator('.field-tooltip').textContent()).trim();
  check('hover tooltip shows', tipVisible && tipText.length > 0, tipText.slice(0, 40));

  await page.selectOption('#version-select', '26.2');
  await page.waitForTimeout(250);
  const variant0 = await page.locator('[data-f$=".variant"]').count();
  check('26.2 auto hides TACZ fields', variant0 === 0, 'count=' + variant0);
  await page.locator('#tacz-toggle').click();
  await page.waitForTimeout(250);
  const variantOn = await page.locator('[data-f$=".variant"]').count();
  check('switch forces TACZ fields on for 26.2', variantOn > 0, 'count=' + variantOn);
  await page.locator('#tacz-toggle').click();
  await page.waitForTimeout(200);
  const variantOff = await page.locator('[data-f$=".variant"]').count();
  check('switch forces TACZ fields off', variantOff === 0, 'count=' + variantOff);
  await page.locator('#tacz-toggle').click();
  await page.waitForTimeout(150);
  await page.selectOption('#version-select', '1.21.1');

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

  await page.click('[data-action="paste-items"][data-grade="1"]');
  await page.fill('#paste-textarea', 'minecraft:stone\nminecraft:andesite');
  await page.click('#paste-do');
  await page.waitForTimeout(350);
  const grade1 = await page.locator('#grade-items-1 [data-f$=".weight"]').count();
  check('item paste adds entries', grade1 >= 2, 'grade1 count=' + grade1);
  await page.click('[data-action="undo"]');
  await page.waitForTimeout(250);

  // /csbox nbt hand chat output: whole chat block (header + JSON + copy button)
  const baseG1 = await page.locator('#grade-items-1 [data-f$=".weight"]').count();
  await page.click('[data-action="paste-items"][data-grade="1"]');
  await page.fill('#paste-textarea',
    '手中物品 石头 的 JSON（可直接粘贴到箱子 items 条目）:\n' +
    '{"id":"minecraft:stone","count":1,"components":{}}\n' +
    '[ 点击复制完整 JSON ]');
  await page.click('#paste-do');
  await page.waitForTimeout(350);
  const afterHandPaste = await page.locator('#grade-items-1 [data-f$=".weight"]').count();
  check('paste recognizes /csbox nbt hand chat output', afterHandPaste === baseG1 + 1,
    'count=' + afterHandPaste);

  // multiple JSON blocks pasted at once
  await page.click('[data-action="paste-items"][data-grade="1"]');
  await page.fill('#paste-textarea',
    '{"id":"minecraft:andesite","count":2,"components":{}}\n' +
    '{"id":"minecraft:diorite","count":1}');
  await page.click('#paste-do');
  await page.waitForTimeout(350);
  const afterMulti = await page.locator('#grade-items-1 [data-f$=".weight"]').count();
  check('paste extracts multiple /csbox nbt hand lines', afterMulti === afterHandPaste + 2,
    'count=' + afterMulti);

  // truncated chat output: adds nothing and shows the copy-button hint
  const beforeTrunc = await page.locator('#grade-items-1 [data-f$=".weight"]').count();
  await page.click('[data-action="paste-items"][data-grade="1"]');
  await page.fill('#paste-textarea',
    '手中物品 深板岩 的 JSON（可直接粘贴到箱子 items 条目）:\n' +
    '{"id":"minecraft:deepslate","count":1,"components":{"mine\n' +
    '（输出过长已截断，完整 12345 字符）\n' +
    '[ 点击复制完整 JSON ]');
  await page.click('#paste-do');
  await page.waitForTimeout(180);
  const truncToast = await page.locator('#toast').textContent();
  const afterTrunc = await page.locator('#grade-items-1 [data-f$=".weight"]').count();
  check('truncated /csbox nbt hand output hints copy button and adds nothing',
    afterTrunc === beforeTrunc && /截断|truncated/i.test(truncToast || ''),
    'toast=' + (truncToast || '').slice(0, 40));
  await page.click('[data-action="undo"]');
  await page.waitForTimeout(250);

  const dropSec = page.locator('#card-drop');
  check('drop section starts collapsed', await dropSec.evaluate((el) => el.classList.contains('collapsed')));
  await dropSec.locator('.col-head').click();
  await page.waitForTimeout(150);
  check('drop header click expands section',
    !(await dropSec.evaluate((el) => el.classList.contains('collapsed'))));
  await dropSec.locator('.col-head').click();
  await page.waitForTimeout(150);
  check('drop header click collapses again',
    await dropSec.evaluate((el) => el.classList.contains('collapsed')));
  const gradesSec = page.locator('#card-grades');
  check('grade action button does not collapse card',
    !(await gradesSec.evaluate((el) => el.classList.contains('collapsed'))));

  const fn = page.locator('[data-f="meta.fileName"]');
  await fn.fill('');
  await page.waitForTimeout(550);
  const issue = page.locator('.issue[data-issue-path="meta.fileName"]');
  if ((await issue.count()) > 0) {
    await issue.click();
    await page.waitForTimeout(400);
    check('issue click highlights field',
      await fn.evaluate((el) => el.classList.contains('flash-highlight')));
  } else {
    check('issue click highlights field', false, 'no issue row found');
  }

  /* ================= price table page (prices.html) ================= */
  await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
  check('prices page title set', /CS2-Box/.test(await page.title()) && (await page.title()).length > 10);
  check('prices page shows only the price card',
    (await page.locator('#card-prices').count()) === 1 && (await page.locator('#card-meta').count()) === 0);
  check('nav highlights Prices',
    await page.locator('#nav-prices').evaluate((el) => el.classList.contains('active')));
  const autoRows = await page.locator('#card-prices tr[data-price-key]').count();
  check('prices page lists auto keys from box state', autoRows >= 1, 'rows=' + autoRows);
  check('prices preview shows JSON',
    ((await page.locator('#json-preview').textContent()) || '').trim().startsWith('{'));

  await page.click('[data-action="paste-prices"]');
  await page.fill('#paste-textarea', 'minecraft:test_shard\t42\ntacz:mgun#tacz:x 7');
  await page.click('#paste-do');
  await page.waitForTimeout(350);
  check('price batch import',
    (await page.locator('tr[data-price-key="minecraft:test_shard"]').count()) === 1 &&
    (await page.locator('tr[data-price-key="tacz:mgun#tacz:x"]').count()) === 1);
  check('prices preview includes imported key',
    ((await page.locator('#json-preview').textContent()) || '').includes('test_shard'));

  // imported rows survive a round-trip to the box page
  await page.goto(BASE, { waitUntil: 'load' });
  const storedState = await page.evaluate(() => localStorage.getItem('cs2box-editor-state-v1') || '');
  check('imported prices persist across pages', storedState.includes('minecraft:test_shard'));

  /* ================= share link restore ================= */
  const shareUrl = BASE + '#state=' + makeShareState('shared_box_test');
  await page.goto(shareUrl, { waitUntil: 'load' });
  await page.waitForTimeout(200);
  await page.reload({ waitUntil: 'load' });
  await page.waitForTimeout(300);
  const sharedFn = await page.locator('[data-f="meta.fileName"]').inputValue();
  const sharedVer = await page.locator('#version-select').inputValue();
  check('share link restores box state', sharedFn === 'shared_box_test' && sharedVer === '26.2',
    sharedFn + '/' + sharedVer);
  await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
  check('share link restores prices',
    (await page.locator('tr[data-price-key="minecraft:emerald"]').count()) === 1);

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