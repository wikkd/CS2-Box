// CS2-Box box editor — zero-dependency static server (Node >= 18).
// Usage: node server.mjs [--port 4173] [--dir ./dist] [--open|--no-open] [--host 127.0.0.1]
import { createServer } from 'node:http';
import { promises as fs } from 'node:fs';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import process from 'node:process';

const here = fileURLToPath(new URL('.', import.meta.url));
const args = process.argv.slice(2);

function arg(name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
}
const hasFlag = (name) => args.includes(name);

const PORT = Number(arg('--port', process.env.PORT || 4173));
const HOST = arg('--host', '127.0.0.1');
let root = resolve(here, arg('--dir', '.'));
const openBrowser = !hasFlag('--no-open');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.gif': 'image/gif',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.map': 'application/json',
};

function contentType(file) {
  return MIME[extname(file).toLowerCase()] || 'application/octet-stream';
}

function escapeHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

async function resolveTarget(urlPath) {
  let pathname;
  try {
    pathname = decodeURIComponent(urlPath.split('?')[0]);
  } catch {
    return null;
  }
  if (pathname.endsWith('/')) pathname += 'index.html';
  const target = normalize(join(root, pathname));
  if (!(target === root || target.startsWith(root + sep)) && !target.startsWith(root + '/')) {
    return null; // path traversal
  }
  return target;
}

const server = createServer(async (req, res) => {
  try {
    const target = await resolveTarget(req.url || '/');
    if (!target) {
      res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('403 Forbidden');
      return;
    }
    let content;
    try {
      content = await fs.readFile(target);
    } catch {
      const exists = await fs.stat(target).then(() => true, () => false);
      if (!exists && (req.url || '').split('?')[0].endsWith('/')) {
        // try index.html fallback for SPA-style deep links
        const idx = join(root, 'index.html');
        try {
          content = await fs.readFile(idx);
          res.writeHead(200, { 'Content-Type': MIME['.html'] });
          res.end(content);
          return;
        } catch { /* fall through to 404 */ }
      }
      res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end('<!doctype html><meta charset="utf-8"><h1>404 Not Found</h1><p>' +
        escapeHtml(req.url) + '</p><p>Server root: ' + escapeHtml(root) + '</p>');
      return;
    }
    res.writeHead(200, {
      'Content-Type': contentType(target),
      'Cache-Control': 'no-cache',
    });
    res.end(content);
  } catch (err) {
    res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('500 Internal Server Error\n' + err.message);
  }
});

server.listen(PORT, HOST, () => {
  const url = `http://${HOST}:${PORT}/`;
  console.log('');
  console.log('  ▣ CS2-Box box editor (local deployment)');
  console.log('  -> ' + url);
  console.log('  root: ' + root);
  console.log('  Ctrl+C to stop');
  console.log('');
  if (openBrowser) {
    let cmd;
    const platform = process.platform;
    if (platform === 'win32') cmd = ['cmd', ['/c', 'start', '', url]];
    else if (platform === 'darwin') cmd = ['open', [url]];
    else cmd = ['xdg-open', [url]];
    try {
      spawn(cmd[0], cmd[1], { stdio: 'ignore', detached: true }).unref();
    } catch { /* ignore */ }
  }
});

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    console.log('\nStopping box editor server…');
    server.close(() => process.exit(0));
  });
}