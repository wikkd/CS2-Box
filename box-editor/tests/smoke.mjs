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
            random: ['', '', '', '', ''], discount: '', stock: '',
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

  const bgImage = await page.evaluate(() => getComputedStyle(document.body).backgroundImage);
  check('body background uses bundled cover image',
    bgImage.includes('bg.jpg') && bgImage.split(',').length >= 2,
    (bgImage || '').slice(0, 60));

  const glass = await page.locator('.card').first().evaluate((el) => {
    const s = getComputedStyle(el);
    return (s.backdropFilter || s.webkitBackdropFilter || '');
  });
  check('cards use glass backdrop blur', /blur\(/.test(glass), (glass || '').slice(0, 40));

  const metaInputs = await page.locator('#card-meta input, #card-meta select').count();
  check('meta card has real inputs', metaInputs >= 6, 'count=' + metaInputs);
  check('no escaped ghost inputs',
    !(await page.locator('#card-meta').innerHTML()).includes('&lt;input'));

  // view-switch tabs localize with the UI language; headless locale follows
  // the host system (zh here), so assert both languages explicitly and
  // restore whatever language the rest of the run expects.
  const initialLang = await page.evaluate(() => document.querySelector('#lang-select').value);
  const setLang = (l) => page.evaluate((lang) => {
    const sel = document.querySelector('#lang-select');
    sel.value = lang;
    sel.dispatchEvent(new Event('change', { bubbles: true }));
  }, l);
  await setLang('en');
  await page.waitForTimeout(200);
  const probTabEn = await page.locator('#view-prob').textContent();
  check('view tab label localized (en)', /probabilities/i.test(probTabEn || ''), probTabEn || '');
  await setLang('zh');
  await page.waitForTimeout(200);
  const probTabZh = await page.locator('#view-prob').textContent();
  check('view tab label localized (zh)', probTabZh === '概率表', probTabZh || '');
  await setLang(initialLang);
  await page.waitForTimeout(200);

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

  // top-bar "Advanced" toggle gates the custom-icon (icon) field
  const advToggle = page.locator('#advanced-toggle');
  check('top bar has Advanced toggle', (await advToggle.count()) === 1);
  await advToggle.uncheck(); // deterministic start
  check('icon field hidden by default (Advanced off)',
    (await page.locator('#card-meta [data-f="meta.icon"]').count()) === 0);
  await advToggle.check();
  check('icon field appears with Advanced on',
    (await page.locator('#card-meta [data-f="meta.icon"]').count()) === 1);
  const presetCount = await page.locator('#icon-preset option').count();
  check('icon preset dropdown offers presets', presetCount >= 10, 'options=' + presetCount);
  await page.selectOption('#icon-preset', 'minecraft:ender_chest');
  const iconVal = await page.locator('#card-meta [data-f="meta.icon"]').inputValue();
  check('preset pick fills the icon input', iconVal === 'minecraft:ender_chest', iconVal);
  await page.waitForTimeout(250); // preview rebuild is debounced (~90 ms)
  const previewHasIcon = (await page.locator('#json-preview').innerText())
    .includes('"icon": "minecraft:ender_chest"');
  check('preview JSON includes the picked icon', previewHasIcon);
  await advToggle.uncheck();
  check('icon field hidden again after Advanced off',
    (await page.locator('#card-meta [data-f="meta.icon"]').count()) === 0);
  check('configured icon still prompts a hint in basic mode',
    (await page.locator('#card-meta .icon-hidden-hint').count()) === 1);

  // TACZ card and per-item enchant fields are gated behind Advanced too
  check('TACZ card hidden in basic mode', (await page.locator('#card-tacz').count()) === 0);
  check('enchant selector hidden in basic mode',
    (await page.locator('#grade-items-1 [data-f$=".enchantMode"]').count()) === 0);
  await advToggle.check();
  check('TACZ card appears with Advanced on', (await page.locator('#card-tacz').count()) === 1);
  check('enchant selector appears with Advanced on',
    (await page.locator('#grade-items-1 [data-f$=".enchantMode"]').count()) >= 1);

  // entity id dropdown with every vanilla mob (independent of Advanced)
  const entityListAttr = await page.locator('[data-f="meta.entity.0.id"]').getAttribute('list');
  const entityOptionCount = await page.locator('#csbox-entity-list option').count();
  check('entity input offers vanilla mob dropdown',
    entityListAttr === 'csbox-entity-list' && entityOptionCount >= 70, 'options=' + entityOptionCount);

  // Entity row add / remove. Regression guard: both handlers used to call
  // rebuildMeta() instead of rebuildDrop(), so the entity row was mutated in
  // state but never re-rendered — the buttons looked dead, and an export then
  // carried rows the user could not see. The list must also be drainable to
  // zero (model.js omits `entity` entirely when no row is filled in).
  const entityRowCount = () => page.locator('#entity-rows .entity-row').count();
  const entityBase = await entityRowCount();
  check('entity list renders its rows', entityBase >= 1, 'rows=' + entityBase);
  await page.click('[data-action="add-entity"]');
  await page.waitForTimeout(220);
  await page.click('[data-action="add-entity"]');
  await page.waitForTimeout(220);
  check('add-entity renders the new rows', (await entityRowCount()) === entityBase + 2,
    'rows=' + await entityRowCount());
  await page.locator('#entity-rows [data-action="del-entity"]').nth(1).click();
  await page.waitForTimeout(220);
  check('del-entity removes exactly the clicked row', (await entityRowCount()) === entityBase + 1,
    'rows=' + await entityRowCount());
  let drainGuard = 0;
  while ((await entityRowCount()) > 0 && drainGuard++ < 10) {
    await page.locator('#entity-rows [data-action="del-entity"]').first().click();
    await page.waitForTimeout(220);
  }
  check('the last entity row is removable', (await entityRowCount()) === 0,
    'rows=' + await entityRowCount());
  check('empty entity list shows a hint instead of blank space',
    (await page.locator('#entity-rows .entity-empty').count()) === 1);
  await page.click('[data-action="add-entity"]');
  await page.waitForTimeout(220);
  check('add-entity still works from the empty state', (await entityRowCount()) === 1,
    'rows=' + await entityRowCount());

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

  // advanced data collapsible: filled items expand, empty items stay closed
  const advTotal = await page.locator('#grade-items-1 .item-adv').count();
  const advOpen = await page.locator('#grade-items-1 .item-adv[open]').count();
  check('advanced data block: filled expands, empty stays closed',
    advTotal >= 2 && advOpen >= 1 && advOpen < advTotal,
    'total=' + advTotal + ' open=' + advOpen);

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

  // advanced-fields badge in basic mode: set an enchant while Advanced is on,
  // then turn Advanced off and expect a ⚙ badge; clicking it re-enables.
  await advToggle.check();
  await page.waitForTimeout(150);
  const encFirst = page.locator('#grade-items-0 [data-f$=".enchantMode"]').first();
  await encFirst.selectOption('any');
  await page.waitForTimeout(250);
  await advToggle.uncheck();
  await page.waitForTimeout(150);
  const badgeCount = await page.locator('#grade-items-0 .hidden-adv-badge').count();
  check('basic mode shows advanced-fields badge', badgeCount >= 1, 'count=' + badgeCount);
  await page.click('#grade-items-0 .hidden-adv-badge');
  check('badge click re-enables Advanced', await page.locator('#advanced-toggle').isChecked());
  // cleanup: reset enchant to none and turn Advanced back off before continuing
  const encReset = page.locator('#grade-items-0 [data-f$=".enchantMode"]').first();
  if (await encReset.count()) { await encReset.selectOption('none'); }
  await advToggle.uncheck();
  await page.waitForTimeout(300);

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
  await fn.fill('weapon_dealer');
  await page.waitForTimeout(400);

  /* A: a target hidden in a collapsed grade card gets expanded + flashed */
  const wInput = page.locator('[data-f="grades.0.0.weight"]');
  await wInput.fill('-5');
  await page.waitForTimeout(550);
  const wIssue = page.locator('.issue[data-issue-path="grade1[1].weight"]');
  await page.evaluate(() => {
    const d = document.querySelector('details.grade-card[data-grade="0"]');
    if (d) { d.open = false; d.dispatchEvent(new Event('toggle')); }
  });
  await page.waitForTimeout(150);
  if ((await wIssue.count()) > 0) {
    await wIssue.click();
    await page.waitForTimeout(400);
    const cardOpen = await page.evaluate(() => {
      const d = document.querySelector('details.grade-card[data-grade="0"]');
      return !!(d && d.open);
    });
    const flashed = await wInput.evaluate((el) => el.classList.contains('flash-highlight'));
    check('issue click expands collapsed grade card and flashes field', cardOpen && flashed,
      'open=' + cardOpen + ' flash=' + flashed);
  } else {
    check('issue click expands collapsed grade card', false, 'no weight issue row');
  }
  await wInput.fill('1');
  await page.waitForTimeout(400);

  /* B: a target hidden in a collapsed section card gets revealed + flashed.
     card-drop (drop & grade weights) exists for every box type and is
     collapsed again by the toggle test above; the random weights live
     inside it. */
  const dropSecB = page.locator('#card-drop');
  await dropSecB.locator('.col-head').click(); // expand so the field is fillable
  await page.waitForTimeout(150);
  const dInput = page.locator('[data-f="meta.random.0"]');
  await dInput.fill('10001'); // out of the 0..10000 range -> v.badRandom
  await page.waitForTimeout(550);
  const dIssue = page.locator('.issue[data-issue-path="meta.random"]');
  await dropSecB.locator('.col-head').click(); // collapse again over the bad value
  await page.waitForTimeout(150);
  if ((await dIssue.count()) > 0) {
    await dIssue.click();
    await page.waitForTimeout(400);
    const revealed = await dropSecB.evaluate((el) => !el.classList.contains('collapsed'));
    const flashed = await dInput.evaluate((el) => el.classList.contains('flash-highlight'));
    check('issue click reveals collapsed section and flashes field', revealed && flashed,
      'revealed=' + revealed + ' flash=' + flashed);
  } else {
    check('issue click reveals collapsed section', false, 'no random issue row');
  }
  await dInput.fill('');
  await page.waitForTimeout(300);

  /* ================= probability table view ================= */
  await page.click('#view-prob');
  await page.waitForTimeout(250);
  const probPanelVisible = await page.locator('#prob-panel').isVisible();
  const jsonHidden = await page.locator('#json-preview').isHidden();
  const probRows = await page.locator('#prob-panel tr.prob-grade').count();
  const probItems = await page.locator('#prob-panel tr.prob-item').count();
  check('probability view shows the table and hides JSON preview',
    probPanelVisible && jsonHidden, 'panel=' + probPanelVisible + ' jsonHidden=' + jsonHidden);
  check('probability table lists grade rows', probRows === 5, 'grades=' + probRows);
  check('probability table lists pool items',
    probItems >= 1 && ((await page.locator('#prob-panel').textContent()) || '').includes('%'),
    'items=' + probItems);

  /* item note (备注): the probability table shows the note name over the raw id */
  const noteIn = page.locator('[data-f="grades.0.0.note"]');
  check('item row exposes a note input', (await noteIn.count()) === 1);
  await noteIn.fill('铁锭备注');
  await page.waitForTimeout(650);
  const probHtml = await page.locator('#prob-panel').innerHTML();
  check('probability table shows the note name with the dimmed source id',
    probHtml.includes('铁锭备注') && probHtml.includes('prob-src-id'),
    'note=' + probHtml.includes('铁锭备注') + ' srcId=' + probHtml.includes('prob-src-id'));
  await page.click('#view-json');
  await page.waitForTimeout(200);
  check('switching back to JSON preview restores it',
    await page.locator('#json-preview').isVisible() &&
    !(await page.locator('#prob-panel').isVisible()));
  check('JSON preview serializes the note',
    ((await page.locator('#json-preview').textContent()) || '').includes('"note": "铁锭备注"'));
  await noteIn.fill('');
  await page.waitForTimeout(300);

  /* ================= legacy import (pre-v2.0.1 box JSON) ================= */
  // No file-name field: "Choose file" carries the name, so a legacy
  // terminal.json (no `type`, leftover key, inline prices) is recognized as a
  // terminal, its key is dropped, prices migrate, and the export uses the new
  // format. Pasting without a file name keeps it a crate (no name to infer).
  await page.click('#btn-import-legacy');
  check('legacy import button opens its own dialog',
    await page.locator('#legacy-dialog').evaluate((el) => el.open));
  check('legacy dialog asks for no file name',
    (await page.locator('#legacy-filename').count()) === 0);
  check('legacy dialog explains migration',
    /price/i.test((await page.locator('#legacy-hint').textContent()) || ''));

  await page.setInputFiles('#legacy-file-input', {
    name: 'terminal.json',
    mimeType: 'application/json',
    buffer: Buffer.from(JSON.stringify({
      name: '#FF5555 旧终端机',
      key: 'minecraft:air',
      drop: 0.05,
      random: [10, 20, 40, 80, 160],
      grade5: [{ id: 'minecraft:netherite_sword', price: 4000 }],
      grade4: [{ id: 'minecraft:diamond_sword', price: 1500 }],
    }, null, 2)),
  });
  await page.waitForTimeout(250);
  check('picked legacy file fills the textarea',
    ((await page.locator('#legacy-textarea').inputValue()) || '').includes('netherite_sword'));
  await page.click('#legacy-do');
  await page.waitForTimeout(400);
  const legacyType = await page.locator('[data-f="meta.type"]').inputValue();
  const legacyPreview = (await page.locator('#json-preview').textContent()) || '';
  const legacyToast = (await page.locator('#toast').textContent()) || '';
  check('legacy terminal.json is inferred as terminal',
    legacyType === 'terminal', 'type=' + legacyType);
  check('legacy terminal export drops key and price',
    /"type": "terminal"/.test(legacyPreview) &&
    !/"key"/.test(legacyPreview) && !/"price"/.test(legacyPreview),
    'preview ok=' + /"type": "terminal"/.test(legacyPreview));
  check('legacy import migrates inline prices',
    /迁移|Migrated/.test(legacyToast) && /2|two/i.test(legacyToast),
    'toast=' + (legacyToast || '').slice(0, 50));

  // pasting (no file name) a legacy crate stays a crate
  await page.click('#btn-import-legacy');
  await page.fill('#legacy-textarea',
    JSON.stringify({ name: '旧宝箱', grade1: [{ id: 'minecraft:diamond' }] }, null, 2));
  await page.click('#legacy-do');
  await page.waitForTimeout(300);
  const crateType = await page.locator('[data-f="meta.type"]').inputValue();
  check('pasted legacy crate without a file name stays csbox', crateType === 'csbox', 'type=' + crateType);

  // ChloePrime-style reversed grade weights are auto-reversed on import
  await page.click('#btn-import-legacy');
  await page.setInputFiles('#legacy-file-input', {
    name: 'old_weights.json',
    mimeType: 'application/json',
    buffer: Buffer.from(JSON.stringify({
      name: '旧反序权重箱',
      random: [2, 5, 25, 125, 625],
      grade1: [{ id: 'minecraft:wooden_sword' }],
      grade5: [{ id: 'minecraft:netherite_sword' }],
    }, null, 2)),
  });
  await page.waitForTimeout(250);
  await page.click('#legacy-do');
  await page.waitForTimeout(400);
  const revPreview = (await page.locator('#json-preview').textContent()) || '';
  const revToast = (await page.locator('#toast').textContent()) || '';
  const revRandom = /"random":\s*\[\s*625,\s*125,\s*25,\s*5,\s*2\s*\]/.test(revPreview);
  check('legacy import auto-reverses ChloePrime ascending weights',
    revRandom, 'preview random reversed=' + revRandom);
  check('toast announces the auto-reversal', /反转|reversed/i.test(revToast),
    'toast=' + (revToast || '').slice(0, 60));
  await page.click('[data-action="undo"]');
  await page.waitForTimeout(250);

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

  await page.click('[data-action="paste-prices"]');
  await page.fill('#paste-textarea', 'minecraft:test_range\t1500-3000');
  await page.click('#paste-do');
  await page.waitForTimeout(350);
  const rangePreview = (await page.locator('#json-preview').textContent()) || '';
  const rangeObj = JSON.parse(rangePreview);
  check('price range imports and serializes as [min, max]',
    JSON.stringify(rangeObj['minecraft:test_range']) === '[1500,3000]' &&
    (await page.locator('tr[data-price-key="minecraft:test_range"] input.price-input').inputValue()) === '1500-3000',
    'serialized=' + JSON.stringify(rangeObj['minecraft:test_range']));

  /* ---- multi-crate JSON import (terminals + plain crates at once) ---- */
  // Run on a fresh draft: the example draft ships with its own price table and
  // mergeCratePriceEntries gives existing prices precedence ("already priced
  // wins"), which would swallow the average/keep assertions below.
  await page.goto(BASE, { waitUntil: 'load' });
  await page.waitForTimeout(200);
  await page.click('#file-add'); // new empty draft (file workspace)
  await page.waitForTimeout(300);
  await page.goto(BASE + 'prices.html', { waitUntil: 'load' }); // crate import lives on the prices page
  await page.waitForTimeout(200);
  const crateDoc1 = JSON.stringify({
    name: '矿物终端机', type: 'terminal',
    grade1: [{ id: 'minecraft:iron_ingot', price: 200 }, { id: 'minecraft:raw_iron' }],
    grade2: [{ id: 'minecraft:gold_ingot', price: 300 }],
  });
  const crateDoc2 = JSON.stringify({
    name: '重复价格箱', type: 'csbox',
    grade1: [{ id: 'minecraft:iron_ingot', price: 400 }, { id: 'minecraft:bow' }],
  });
  await page.click('[data-action="import-crate-prices"]');
  check('crate import dialog opens', await page.locator('#crate-import-dialog').isVisible());
  await page.fill('#crate-import-textarea', crateDoc1 + '\n' + crateDoc2);
  await page.click('#crate-import-do');
  await page.waitForTimeout(350);
  check('crate import dialog closes after import',
    !(await page.locator('#crate-import-dialog').isVisible()));
  const crateIron = await page.locator('tr[data-price-key="minecraft:iron_ingot"] input.price-input').inputValue();
  check('crate import averages duplicate inline prices (200/400 -> 300)', crateIron === '300', 'iron=' + crateIron);
  check('crate import adds priced key from second doc',
    (await page.locator('tr[data-price-key="minecraft:gold_ingot"] input.price-input').inputValue()) === '300');
  check('crate import adds unpriced keys',
    (await page.locator('tr[data-price-key="minecraft:raw_iron"]').count()) === 1 &&
    (await page.locator('tr[data-price-key="minecraft:bow"]').count()) === 1);
  const cratePreview = JSON.parse((await page.locator('#json-preview').textContent()) || '{}');
  check('crate import serializes into _prices.json',
    cratePreview['minecraft:iron_ingot'] === 300 && cratePreview['minecraft:gold_ingot'] === 300 &&
    cratePreview['minecraft:raw_iron'] === undefined,
    'iron=' + JSON.stringify(cratePreview['minecraft:iron_ingot']));
  const crateToast = await page.locator('#toast').textContent();
  check('crate import toast summarizes', /箱子文档|crate document/.test(crateToast || ''),
    'toast=' + (crateToast || '').slice(0, 70));

  // imported rows survive a round-trip to the box page
  await page.goto(BASE, { waitUntil: 'load' });
  const storedState = await page.evaluate(() => localStorage.getItem('cs2box-editor-files-v1') || '');
  check('imported prices persist across pages', storedState.includes('minecraft:test_shard'));

  /* ================= share link restore ================= */
  const shareUrl = BASE + '#state=' + makeShareState('shared_box_test');
  // Navigate via a different document first so the fragment-only URL is a full
  // cross-document load; a same-document goto followed by reload is flaky
  // (page detaches) in headless Chromium.
  await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
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

  /* ================= game pack link (#pack=, /csbox editor) ================= */
  {
    const { deflateSync } = await import('node:zlib');
    const mkBox = (nm) => ({
      name: nm, type: 'csbox', key: 'minecraft:iron_ingot',
      random: [625, 125, 25, 5, 2],
      grade1: [{ id: 'minecraft:diamond', weight: 1 }], grade2: [], grade3: [], grade4: [], grade5: [],
    });
    const manifest = JSON.stringify({
      v: 1, mc: '26.2',
      files: { 'pack_alpha.json': mkBox('Pack Alpha'), 'pack_beta.json': mkBox('Pack Beta') },
      prices: { 'minecraft:diamond': 1234 },
    });
    const enc = deflateSync(Buffer.from(manifest, 'utf8')).toString('base64')
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const packUrl = BASE + '#pack=' + enc;
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' }); // cross-document hop
    // Clear AFTER the hop: a fresh storage on prices.html would seed the
    // example draft, which the pack import then keeps as a third entry.
    await page.evaluate(() => localStorage.clear());
    await page.goto(packUrl, { waitUntil: 'load' });
    await page.waitForTimeout(400);
    const packDrafts = await page.locator('.file-item').count();
    check('pack link imports both box files as drafts', packDrafts === 2, 'items=' + packDrafts);
    const packActive = await page.locator('[data-f="meta.fileName"]').inputValue();
    check('pack import activates the first imported draft', packActive === 'pack_alpha', 'fn=' + packActive);
    const packSecond = await page.locator('.file-item').nth(1).locator('.file-name').textContent();
    check('pack import lists the second imported draft', (packSecond || '').trim() === 'pack_beta',
      'name=' + packSecond);
    const badgeTxt = ((await page.locator('.file-item .file-tag').first().textContent()) || '').trim();
    check('imported draft shows the pack version badge', badgeTxt === '26.2', 'badge=' + badgeTxt);
    const fileTip = await page.locator('.file-item').first().getAttribute('title');
    check('draft tooltip names the source file', (fileTip || '').includes('pack_alpha.json'),
      'tip=' + fileTip);
    check('pack link clears its fragment after import', await page.evaluate(() => location.hash === ''),
      'hash="' + (await page.evaluate(() => location.hash.slice(0, 12))) + '"');
    await page.reload({ waitUntil: 'load' });
    await page.waitForTimeout(400);
    check('plain reload does not duplicate imported drafts',
      (await page.locator('.file-item').count()) === 2,
      'items=' + (await page.locator('.file-item').count()));
    await page.goto(packUrl, { waitUntil: 'load' });
    await page.waitForTimeout(400);
    check('re-opening the same pack link does not re-import',
      (await page.locator('.file-item').count()) === 2,
      'items=' + (await page.locator('.file-item').count()));
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
    const packPrice = await page.locator('tr[data-price-key="minecraft:diamond"] input').first().inputValue();
    check('pack link merges _prices.json into the price table', packPrice === '1234', 'price=' + packPrice);
    check('prices page shows the odds & value panel',
      (await page.locator('details.econ-panel').count()) === 1);
    await page.locator('details.econ-panel summary').click();
    const econRows = await page.locator('details.econ-panel .econ-table tbody tr').count();
    check('econ panel lists one row per non-empty grade', econRows === 1, 'rows=' + econRows);
    const econEv = ((await page.locator('details.econ-panel .econ-ev').first().textContent()) || '').trim();
    check('econ panel computes the grade EV (diamond @1234)', econEv === '1234.0', 'ev=' + econEv);
    const econTotal = ((await page.locator('details.econ-panel p.help').first().textContent()) || '');
    check('econ panel shows total and recycle EV',
      /1234\.0/.test(econTotal) && /1111\.0/.test(econTotal), econTotal.trim().slice(0, 60));
    await page.evaluate(() => localStorage.clear());

    /* broken JSON file: opens pre-filled in the import dialog, fix -> new draft */
    const brokenPack = JSON.stringify({
      v: 1, mc: '26.2',
      files: { 'pack_alpha.json': mkBox('Pack Alpha') },
      broken: { 'broken_b.json': '{"name":"Half typed","grade1": [' },
    });
    const encBroken = deflateSync(Buffer.from(brokenPack, 'utf8')).toString('base64')
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' }); // cross-document hop
    await page.evaluate(() => localStorage.clear());
    await page.goto(BASE + '#pack=' + encBroken, { waitUntil: 'load' });
    await page.waitForTimeout(400);
    const dlgOpen = await page.locator('#import-dialog[open]').count();
    check('broken pack file opens the import dialog', dlgOpen === 1);
    const prefilled = await page.locator('#import-textarea').inputValue();
    check('broken pack file pre-fills the raw text',
      prefilled.startsWith('{"name":"Half typed"'), 'ta=' + prefilled.slice(0, 24));
    await page.fill('#import-textarea', JSON.stringify(mkBox('Pack Fixed')));
    await page.click('#import-do');
    await page.waitForTimeout(350);
    const dlgClosed = await page.locator('#import-dialog[open]').count();
    check('import dialog closes after the fixed import', dlgClosed === 0);
    const fixedDrafts = await page.locator('.file-item').count();
    check('fixed broken file lands as a second draft', fixedDrafts === 2, 'items=' + fixedDrafts);
    const fixedActive = await page.locator('[data-f="meta.fileName"]').inputValue();
    check('fixed draft keeps the source file name', fixedActive === 'broken_b', 'fn=' + fixedActive);
    await page.evaluate(() => localStorage.clear());

    /* game registry lists: items/entities feed the autocomplete + persist */
    const listsPack = JSON.stringify({
      v: 1, mc: '26.2',
      files: { 'pack_l.json': mkBox('Pack Lists') },
      items: 'zzmod:cogwheel\nzztacz:ak47\nminecraft:diamond',
      entities: 'zzmod:goblin\nminecraft:zombie',
    });
    const encLists = deflateSync(Buffer.from(listsPack, 'utf8')).toString('base64')
      .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' }); // cross-document hop
    await page.evaluate(() => localStorage.clear());
    await page.goto(BASE + '#pack=' + encLists, { waitUntil: 'load' });
    await page.waitForTimeout(500);
    const cogOpt = await page.locator('#csbox-item-list option[value="zzmod:cogwheel"]').count();
    check('pack registry items land in the item autocomplete', cogOpt === 1, 'opt=' + cogOpt);
    const gobOpt = await page.locator('#csbox-entity-list option[value="zzmod:goblin"]').count();
    check('pack registry entities land in the entity autocomplete', gobOpt === 1, 'opt=' + gobOpt);
    const storedItems = await page.evaluate(() =>
      (JSON.parse(localStorage.getItem('cs2box-editor-game-lists-v1') || '{}').items || []).length);
    check('registry lists persist to localStorage', storedItems === 3, 'n=' + storedItems);
    // Survives a plain reload (no pack link): the autocomplete stays complete.
    await page.goto(BASE, { waitUntil: 'load' });
    await page.waitForTimeout(450);
    const cogAfter = await page.locator('#csbox-item-list option[value="zzmod:cogwheel"]').count();
    check('synced items survive a plain reload', cogAfter === 1, 'opt=' + cogAfter);
    // unknown-id cross-check: after the reload the synced registry must still
    // feed the validator (gameLists -> NS.gameRegistry mirror).
    const regSize = await page.evaluate(() =>
      (window.CSBoxEdit.gameRegistry && window.CSBoxEdit.gameRegistry.items)
        ? window.CSBoxEdit.gameRegistry.items.size : 0);
    check('game registry mirror survives a plain reload', regSize === 3, 'n=' + regSize);
    await page.click('[data-action="paste-items"][data-grade="0"]');
    await page.fill('#paste-textarea', 'zzmod:missing_thing');
    await page.click('#paste-do');
    await page.waitForTimeout(600);
    const issueTxt = ((await page.locator('#issues-root').textContent()) || '');
    check('unknown item id raises a registry warning', /zzmod:missing_thing/.test(issueTxt),
      'issues=' + issueTxt.replace(/\s+/g, ' ').slice(0, 70));
    await page.evaluate(() => localStorage.clear());
  }

  /* ================= pack difference dialog (overwrite/keep/skip) ================= */
  {
    const { deflateSync } = await import('node:zlib');
    const diffBox = (itemId) => ({
      name: 'Pack Alpha', type: 'csbox', key: 'minecraft:iron_ingot',
      random: [625, 125, 25, 5, 2],
      grade1: [{ id: itemId, weight: 1 }], grade2: [], grade3: [], grade4: [], grade5: [],
    });
    const diffUrl = (itemId) => BASE + '#pack=' + deflateSync(Buffer.from(JSON.stringify({
      v: 1, mc: '26.2', files: { 'pack_alpha.json': diffBox(itemId) },
    }), 'utf8')).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
    await page.evaluate(() => localStorage.clear());
    await page.goto(diffUrl('minecraft:diamond'), { waitUntil: 'load' });
    await page.waitForTimeout(450);
    check('difference-dialog baseline import', (await page.locator('.file-item').count()) === 1,
      'items=' + (await page.locator('.file-item').count()));
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
    await page.goto(diffUrl('minecraft:diamond'), { waitUntil: 'load' });
    await page.waitForTimeout(450);
    check('identical re-import shows no difference dialog',
      (await page.locator('#swap-dialog[open]').count()) === 0);
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
    await page.goto(diffUrl('minecraft:emerald'), { waitUntil: 'load' });
    await page.waitForTimeout(500);
    check('changed pack file opens the difference dialog',
      (await page.locator('#swap-dialog[open]').count()) === 1);
    await page.click('#swap-overwrite');
    await page.waitForTimeout(450);
    check('overwrite keeps a single draft', (await page.locator('.file-item').count()) === 1,
      'items=' + (await page.locator('.file-item').count()));
    const ovItem = await page.locator('#grade-items-0 [data-f$=".value"]').first().inputValue();
    check('overwrite replaces the draft content', ovItem === 'minecraft:emerald', 'item=' + ovItem);
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
    await page.goto(diffUrl('minecraft:sand'), { waitUntil: 'load' });
    await page.waitForTimeout(500);
    check('difference dialog opens again for the next change',
      (await page.locator('#swap-dialog[open]').count()) === 1);
    await page.click('#swap-keep');
    await page.waitForTimeout(450);
    check('keep-both creates a second draft', (await page.locator('.file-item').count()) === 2,
      'items=' + (await page.locator('.file-item').count()));
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
    await page.goto(diffUrl('minecraft:stone'), { waitUntil: 'load' });
    await page.waitForTimeout(500);
    check('difference dialog opens for the third change',
      (await page.locator('#swap-dialog[open]').count()) === 1);
    await page.keyboard.press('Escape');
    await page.waitForTimeout(450);
    check('Escape skips the file (draft count stable)',
      (await page.locator('.file-item').count()) === 2,
      'items=' + (await page.locator('.file-item').count()));
    await page.evaluate(() => localStorage.clear());
  }

  /* ================= workspace share (#workspace=) ================= */
  {
    // Capture the share link instead of hitting the real clipboard.
    await page.addInitScript(() => {
      window.__copied = null;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText = (t) => { window.__copied = String(t); return Promise.resolve(); };
      }
    });
    await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
    await page.evaluate(() => localStorage.clear());
    await page.goto(BASE, { waitUntil: 'load' });
    await page.waitForTimeout(300);
    await page.click('#file-add');
    await page.waitForTimeout(300);
    check('workspace share setup has two drafts', (await page.locator('.file-item').count()) === 2,
      'items=' + (await page.locator('.file-item').count()));
    await page.click('#btn-share-ws');
    await page.waitForTimeout(450);
    const wsLink = await page.evaluate(() => window.__copied || '');
    check('workspace share copies a #workspace= link',
      wsLink.includes('#workspace=') && wsLink.length > 60, 'len=' + wsLink.length);
    if (wsLink.includes('#workspace=')) {
      await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
      await page.evaluate(() => localStorage.clear());
      await page.goto(wsLink, { waitUntil: 'load' });
      await page.waitForTimeout(550);
      check('workspace link imports its drafts', (await page.locator('.file-item').count()) === 2,
        'items=' + (await page.locator('.file-item').count()));
      check('workspace link clears its fragment',
        (await page.evaluate(() => location.hash)) === '');
      await page.goto(BASE + 'prices.html', { waitUntil: 'load' });
      await page.goto(wsLink, { waitUntil: 'load' });
      await page.waitForTimeout(550);
      check('re-opening the workspace link does not duplicate drafts',
        (await page.locator('.file-item').count()) === 2,
        'items=' + (await page.locator('.file-item').count()));
    }
    const applyState = await page.evaluate(() => {
      const btn = document.getElementById('btn-apply-game');
      return {
        api: typeof window.showDirectoryPicker === 'function',
        vis: !!btn && !btn.hidden,
      };
    });
    check('apply-to-game button matches File System Access availability',
      applyState.api === applyState.vis, JSON.stringify(applyState));
    await page.evaluate(() => localStorage.clear());
  }

  /* ================= file workspace (multi-draft) ================= */
  await page.goto(BASE, { waitUntil: 'load' });
  await page.evaluate(() => localStorage.clear());
  await page.reload({ waitUntil: 'load' });
  await page.waitForTimeout(300);
  check('filebar lists first draft', (await page.locator('.file-item').count()) >= 1,
    'items=' + (await page.locator('.file-item').count()));
  const firstDraftName = await page.locator('.file-item.active .file-name').textContent();
  check('filebar shows box id as draft name', !!firstDraftName && firstDraftName.trim().length > 0,
    'name=' + firstDraftName);
  await page.click('#file-add');
  await page.waitForTimeout(300);
  check('filebar create adds a draft', (await page.locator('.file-item').count()) === 2);
  check('new draft becomes active',
    (await page.locator('.file-item').nth(0).getAttribute('class')).includes('active'));
  await page.locator('.file-item').nth(0).hover(); // actions appear on hover
  await page.locator('.file-item').nth(0).locator('[data-file-action="rename"]').click();
  await page.fill('#file-dialog-input', 'renamed_box');
  await page.click('#file-dialog-do');
  await page.waitForTimeout(350);
  const fnAfterRename = await page.locator('[data-f="meta.fileName"]').inputValue();
  check('filebar rename updates box id', fnAfterRename === 'renamed_box', 'fn=' + fnAfterRename);
  await page.locator('.file-item').nth(1).click();
  await page.waitForTimeout(350);
  const fnAfterSwitch = await page.locator('[data-f="meta.fileName"]').inputValue();
  check('filebar switch restores the other draft', fnAfterSwitch === 'weapon_dealer', 'fn=' + fnAfterSwitch);
  check('filebar active highlight follows switch',
    (await page.locator('.file-item').nth(1).getAttribute('class')).includes('active'));
  // delete the INACTIVE renamed draft; keep the example draft (with pool +
  // prices) active for the EV / simulator checks that follow
  await page.locator('.file-item').nth(0).hover();
  await page.locator('.file-item').nth(0).locator('[data-file-action="del"]').click();
  await page.click('#file-dialog-do');
  await page.waitForTimeout(350);
  check('filebar delete removes the draft', (await page.locator('.file-item').count()) === 1);
  const fnAfterDelete = await page.locator('[data-f="meta.fileName"]').inputValue();
  check('filebar delete keeps the active draft loaded', fnAfterDelete === 'weapon_dealer', 'fn=' + fnAfterDelete);

  /* ================= dark theme toggle ================= */
  const themeBefore = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  await page.click('#btn-theme');
  const themeAfter = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  check('theme toggle flips data-theme', themeBefore !== themeAfter, themeBefore + '->' + themeAfter);
  await page.reload({ waitUntil: 'load' });
  await page.waitForTimeout(200);
  const themePersisted = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
  check('theme persists after reload', themePersisted === themeAfter, themePersisted);
  await page.click('#btn-theme'); // back to light for the visual checks below

  /* ================= probability table: EV column ================= */
  await page.click('#view-prob');
  await page.waitForTimeout(250);
  check('prob table gains EV column',
    (await page.locator('#prob-panel .prob-table thead th').count()) === 4);
  check('EV footer rows present',
    (await page.locator('#prob-panel tr.prob-footer').count()) === 2);
  const evCells = await page.locator('#prob-panel td.prob-ev').allTextContents();
  check('EV column shows numbers or em dashes',
    evCells.length > 0 && evCells.some((t) => /[0-9]/.test(t)), JSON.stringify(evCells.slice(0, 4)));
  await page.click('#view-json');
  await page.waitForTimeout(150);

  /* ================= open simulator ================= */
  await page.click('#view-sim');
  await page.waitForTimeout(250);
  check('simulator panel visible', await page.locator('#sim-panel').isVisible());
  await page.click('[data-sim-action="run100"]');
  await page.waitForTimeout(400);
  const simCount100 = parseInt(await page.locator('.sim-count b').first().textContent(), 10);
  check('simulator batch counts opens', simCount100 === 100, 'opened=' + simCount100);
  check('simulator stats table rendered',
    (await page.locator('#sim-panel .sim-table tbody tr').count()) >= 5);
  await page.click('[data-sim-action="run1"]');
  await page.waitForTimeout(500);
  check('simulator roll strip has 48 cells',
    (await page.locator('#sim-strip .sim-cell').count()) === 48);
  await page.waitForTimeout(3800);
  const simResult = (await page.locator('#sim-result').textContent()) || '';
  check('simulator single roll shows result', simResult.trim().length > 0,
    'result=' + simResult.slice(0, 40));
  const simCount101 = parseInt(await page.locator('.sim-count b').first().textContent(), 10);
  check('simulator single roll adds to stats', simCount101 === 101, 'opened=' + simCount101);

  /* ================= item id autocomplete ================= */
  const datalistCount = await page.evaluate(() =>
    document.getElementById('csbox-item-list').children.length);
  check('item datalist populated', datalistCount > 150, 'options=' + datalistCount);
  const hasTacz = await page.evaluate(() =>
    Array.from(document.getElementById('csbox-item-list').children).some((o) => o.value.startsWith('tacz:')));
  check('tacz suggestions follow version visibility', hasTacz === true, 'tacz=' + hasTacz);
  const listAttr = await page.locator('[data-f="grades.0.0.value"]').first().getAttribute('list');
  check('pool value inputs linked to datalist', listAttr === 'csbox-item-list', 'list=' + listAttr);

  /* ================= PWA: service worker + manifest ================= */
  const swRes = await page.request.get(BASE + 'sw.js');
  check('sw.js served', swRes.ok());
  const manRes = await page.request.get(BASE + 'manifest.webmanifest');
  check('manifest served with manifest type',
    manRes.ok() && (manRes.headers()['content-type'] || '').includes('manifest'),
    manRes.headers()['content-type']);
  await page.waitForTimeout(600);
  const swState = await page.evaluate(async () => {
    if (!('serviceWorker' in navigator)) return 'unsupported';
    const reg = await navigator.serviceWorker.getRegistration();
    return reg ? 'registered' : 'none';
  });
  // dev server serves raw source (literal __VER__ in HTML) — the SW must NOT
  // register there, or its cache-first strategy would serve stale assets
  // after every code edit; dist/Pages builds register for real.
  check('service worker skipped on raw dev source', swState === 'none' || swState === 'unsupported',
    swState);

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