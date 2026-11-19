/* CS2-Box box editor — SNBT (stringified NBT) parser + writer.
 *
 * The tag field round-trips two string forms: JSON objects and vanilla SNBT.
 * This module implements the SNBT side with vanilla TagParser semantics,
 * close enough for config work: compounds, lists, typed arrays ([B;[I;[L;),
 * quoted (single/double) and unquoted strings, byte/short/int/long/float/
 * double suffixes and true/false literals.
 *
 * Type mapping follows the runtime convention (BoxItemCodec.tagToJson):
 * every numeric tag lands as a plain JS number — the byte/short/long/float
 * distinctions collapse because a JSON number cannot carry them — and
 * true/false stay booleans (byte 0/1 downstream). The writer emits canonical
 * SNBT again: booleans as 1b/0b, numbers bare, strings bare only when they
 * are unambiguous vanilla unquoted strings.
 */
window.CSBoxEdit = window.CSBoxEdit || {};
(function (NS) {
  'use strict';

  const WS = ' \t\r\n';
  /** Valid unquoted-key shape (vanilla TagParser key grammar): no ':' — a
   *  bare key ends at ':' and must be quoted when it contains one. Values
   *  are read by terminator, not pattern (they may carry ':' so bare ids
   *  like tacz:ak47 parse). */
  const BARE_KEY_RE = /^[A-Za-z0-9_\-\+\.]+$/;
  /** Full number token with optional typed suffix (b/s/l/f/d). */
  const NUM_TOKEN_RE = /^[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?([bBsSlLfFdD])?$/;
  const LITERAL_RE = /^(?:true|false)$/i;

  function err(i, msg) {
    return new Error('SNBT position ' + i + ': ' + msg);
  }

  /* ------------------------------ parsing ------------------------------ */

  /** Parse one SNBT document (a "{...}" compound at the top level). Throws
   *  an Error carrying the offending position on any syntax problem. */
  function parseSnbt(text) {
    const s = String(text == null ? '' : text);
    const p = { s, i: 0 };
    skipWs(p);
    const v = parseValue(p);
    skipWs(p);
    if (p.i < s.length) throw err(p.i, 'unexpected trailing content');
    return v;
  }

  function skipWs(p) {
    while (p.i < p.s.length && WS.indexOf(p.s[p.i]) >= 0) p.i++;
  }

  function parseValue(p) {
    skipWs(p);
    const c = p.s[p.i];
    if (c === '{') return parseCompound(p);
    if (c === '[') return parseList(p);
    if (c === '"' || c === "'") return parseQuoted(p);
    return parseBare(p, false);
  }

  function parseCompound(p) {
    const out = {};
    p.i++; // '{'
    skipWs(p);
    if (p.s[p.i] === '}') { p.i++; return out; }
    for (;;) {
      skipWs(p);
      const c = p.s[p.i];
      const key = (c === '"' || c === "'") ? parseQuoted(p) : parseBare(p, true);
      skipWs(p);
      if (p.s[p.i] !== ':') throw err(p.i, "expected ':' after key '" + key + "'");
      p.i++;
      out[key] = parseValue(p);
      skipWs(p);
      const c2 = p.s[p.i];
      if (c2 === ',') { p.i++; continue; }
      if (c2 === '}') { p.i++; return out; }
      throw err(p.i, "expected ',' or '}' in compound");
    }
  }

  function parseList(p) {
    p.i++; // '['
    // Typed array header ([B; / [I; / [L;): the ';' after the letter makes
    // it a typed array, otherwise the letter is a plain first element.
    let elemType = null;
    const c = p.s[p.i];
    if ((c === 'B' || c === 'I' || c === 'L') && p.s[p.i + 1] === ';') {
      elemType = c;
      p.i += 2;
    }
    const out = [];
    skipWs(p);
    if (p.s[p.i] === ']') { p.i++; return out; }
    for (;;) {
      const v = parseValue(p);
      if (elemType && typeof v !== 'number') {
        throw err(p.i, elemType + '-array expects numeric elements');
      }
      out.push(v);
      skipWs(p);
      const c2 = p.s[p.i];
      if (c2 === ',') { p.i++; continue; }
      if (c2 === ']') { p.i++; return out; }
      throw err(p.i, "expected ',' or ']' in list");
    }
  }

  function parseQuoted(p) {
    const quote = p.s[p.i];
    p.i++;
    let out = '';
    for (;;) {
      if (p.i >= p.s.length) throw err(p.i, 'unterminated string');
      const c = p.s[p.i];
      if (c === quote) { p.i++; return out; }
      if (c === '\\') {
        p.i++;
        if (p.i >= p.s.length) throw err(p.i, 'unterminated escape');
        const e = p.s[p.i];
        if (e === '"' || e === "'" || e === '\\') out += e;
        else if (e === 'b') out += '\b';
        else if (e === 'f') out += '\f';
        else if (e === 'n') out += '\n';
        else if (e === 'r') out += '\r';
        else if (e === 't') out += '\t';
        else if (e === 'u') {
          const hex = p.s.slice(p.i + 1, p.i + 5);
          if (!/^[0-9a-fA-F]{4}$/.test(hex)) throw err(p.i, 'bad \\u escape');
          out += String.fromCharCode(parseInt(hex, 16));
          p.i += 4;
        } else out += e; // lenient: unknown escape keeps the raw char
        p.i++;
        continue;
      }
      out += c;
      p.i++;
    }
  }

  /** Bare token: keys stop at ':' (vanilla key grammar), values at the
   *  structural terminators. Value tokens are classified — number with an
   *  optional suffix, true/false, else an unquoted string. */
  function parseBare(p, isKey) {
    const start = p.i;
    while (p.i < p.s.length) {
      const c = p.s[p.i];
      if (WS.indexOf(c) >= 0 || c === ',' || c === '}' || (isKey && c === ':')) break;
      if (!isKey && c === ']') break;
      p.i++;
    }
    if (p.i === start) throw err(p.i, isKey ? 'expected a key' : 'expected a value');
    const tok = p.s.slice(start, p.i);
    if (isKey) return tok;
    if (tok === 'true') return true;
    if (tok === 'false') return false;
    const m = NUM_TOKEN_RE.exec(tok);
    if (m) {
      const n = Number(m[0].replace(/[bBsSlLfFdD]$/, ''));
      if (Number.isFinite(n)) return n;
    }
    return tok;
  }

  /* ------------------------------ writing ------------------------------ */

  /** Canonical SNBT for a JS value tree (JSON-object tag data or a parsed
   *  SNBT tree) — vanilla NBT-writer conventions: booleans become 1b/0b,
   *  numbers go bare (JSON cannot carry NBT type suffixes — the same
   *  approximation the runtime's tagToJson makes), string VALUES are always
   *  quoted, and keys stay bare when they are valid unquoted keys. */
  function stringifySnbt(v) {
    if (v === null || v === undefined) return 'null';
    if (typeof v === 'boolean') return v ? '1b' : '0b';
    if (typeof v === 'number') return String(v);
    if (typeof v === 'string') return escapeQuoted(v);
    if (Array.isArray(v)) {
      return '[' + v.map(stringifySnbt).join(',') + ']';
    }
    const keys = Object.keys(v);
    return '{' + keys.map((k) => quoteKey(k) + ':' + stringifySnbt(v[k])).join(',') + '}';
  }

  function escapeQuoted(s) {
    return '"' + String(s).replace(/[\\"]/g, (c) => '\\' + c) + '"';
  }

  function quoteKey(k) {
    if (BARE_KEY_RE.test(k) && !LITERAL_RE.test(k) && !NUM_TOKEN_RE.test(k)) return k;
    return escapeQuoted(k);
  }

  NS.parseSnbt = parseSnbt;
  NS.stringifySnbt = stringifySnbt;
})(window.CSBoxEdit);
