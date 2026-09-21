/* CS2-Box box-editor — escaping gate for dynamic HTML.
 * Every line that assigns to .innerHTML (and each continuation line of such a
 * statement) must reference esc( — dynamic content is interpolated into HTML
 * strings by hand, and one missed escape is an XSS (share links let anyone
 * send a config into someone's editor).
 *
 * Run:  node --test tests/esc.test.mjs
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');

/** Remove string literals, template literals, regex-ish slashes and line
 *  comments so bracket counting only sees code structure. */
function stripNonCode(line) {
  let out = '';
  let quote = null;
  let escNext = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quote) {
      if (escNext) escNext = false;
      else if (c === '\\') escNext = true;
      else if (c === quote) quote = null;
      continue;
    }
    if (c === '"' || c === "'" || c === '`') {
      quote = c;
      continue;
    }
    if (c === '/' && line[i + 1] === '/') break; // line comment
    out += c;
  }
  return out;
}

function netBrackets(line) {
  const code = stripNonCode(line);
  let depth = 0;
  for (const c of code) {
    if (c === '(' || c === '[' || c === '{') depth++;
    else if (c === ')' || c === ']' || c === '}') depth--;
  }
  return depth;
}

/** Statement start line -> full statement text (joined continuation lines).
 *  A statement ends on a line ending with `;` once bracket depth is back to
 *  the level where the assignment started — map callbacks with inner `;`
 *  lines don't truncate the scan. */
function innerHTMLStatements(src) {
  const lines = src.split('\n');
  const stmts = [];
  let open = null;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (open == null) {
      if (/\.innerHTML\s*=/.test(line)) {
        open = { start: i + 1, buf: line, depth: netBrackets(line) };
        if (open.depth <= 0 && /;\s*(\/\/.*)?$/.test(line)) {
          stmts.push({ start: open.start, text: open.buf });
          open = null;
        }
      }
    } else {
      open.buf += '\n' + line;
      open.depth += netBrackets(line);
      if (open.depth <= 0 && /;\s*(\/\/.*)?$/.test(line)) {
        stmts.push({ start: open.start, text: open.buf });
        open = null;
      }
    }
  }
  if (open != null) stmts.push({ start: open.start, text: open.buf }); // unterminated (safety)
  return stmts;
}

test('every innerHTML assignment escapes with esc()', () => {
  const offenders = [];
  for (const js of ['js/app.js']) {
    const src = readFileSync(path.join(root, js), 'utf8');
    for (const stmt of innerHTMLStatements(src)) {
      if (/\besc\(/.test(stmt.text)) continue;
      // Explicit opt-out for trusted sources (i18n text that is intentional
      // markup, or helpers that escape internally). Keep the marker on the
      // statement line so the audit stays grep-able.
      if (/esc-exempt\s*:/.test(stmt.text)) continue;
      offenders.push(js + ':' + stmt.start);
    }
  }
  assert.deepEqual(offenders, [],
    'innerHTML assignments without esc() found (escape dynamic parts, or add an esc-exempt: comment if the source is trusted):\n  ' +
    offenders.join('\n  '));
});
