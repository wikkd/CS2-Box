// CS2-Box box editor — static build (zero dependency, Node >= 18).
// Copies the editor into dist/ so the folder can be hosted as-is on GitHub
// Pages (Settings -> Pages -> folder /box-editor/dist) or any static host.
import { promises as fs } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const here = fileURLToPath(new URL('.', import.meta.url));
const SRC = here;
const OUT = join(here, 'dist');

const FILES = [
  'index.html',
  'prices.html',
  'css/style.css',
  'js/i18n.js',
  'js/data.js',
  'js/model.js',
  'js/validator.js',
  'js/app.js',
  'data/schemas/shared/box.schema.json',
  'data/schemas/shared/prices.schema.json',
  'README.md',
];

async function ensureDir(p) {
  await fs.mkdir(p, { recursive: true });
}

const VERSION = (process.env.GITHUB_SHA || '').slice(0, 8) || Date.now().toString(36);

async function copy(rel) {
  const src = join(SRC, rel);
  const dst = join(OUT, rel);
  await ensureDir(join(dst, '..'));
  let buf = await fs.readFile(src);
  if (rel.endsWith('.html')) {
    // cache-bust static refs so CDN/browsers pick up new assets immediately
    buf = Buffer.from(buf.toString('utf8').replace(/__VER__/g, VERSION), 'utf8');
  }
  await fs.writeFile(dst, buf);
  return buf.length;
}

async function main() {
  await ensureDir(OUT);
  const copied = [];
  for (const f of FILES) {
    try {
      copied.push([f, await copy(f)]);
    } catch (e) {
      console.error(`[build] missing source: ${f} (${e.message})`);
      process.exitCode = 1;
    }
  }
  // sanity: data.js must be the generated embedded bundle
  try {
    const dataJs = await fs.readFile(join(OUT, 'js/data.js'), 'utf8');
    if (!dataJs.includes('window.CSBDATA')) {
      console.error('[build] js/data.js does not look like the generated bundle — run `npm run sync` first.');
      process.exitCode = 1;
    }
  } catch (e) {
    console.error('[build] js/data.js missing — run `npm run sync` first.');
    process.exitCode = 1;
  }

  const total = copied.reduce((a, [, n]) => a + n, 0);
  console.log(`[build] wrote ${copied.length} files to ${relative(process.cwd(), OUT)} (${total} bytes)`);
  console.log('[build] serve locally:  npm start  (root = box-editor/)');
  console.log('[build] serve dist:     node server.mjs --dir ./dist');
  if (process.exitCode) process.exit(process.exitCode);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});