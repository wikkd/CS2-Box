/* CS2-Box box editor — lightweight validator mirroring the in-game checks.
 * Issues: { level: 'error'|'warn'|'info', path: string, key: i18n key, vars } */
window.CSBoxEdit = window.CSBoxEdit || {};
(function (NS) {
  'use strict';

  const DATA = window.CSBDATA;
  const ID_RE = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
  const KEY_RE = /^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/;
  const FILE_RE = /^[a-z0-9_.\/-]+$/;

  function add(issues, level, path, key, vars) {
    issues.push({ level, path, key, vars: vars || undefined });
  }

  function numOk(v, min, max) {
    if (v === '' || v === null || v === undefined) return true;
    const n = Number(v);
    if (!Number.isFinite(n)) return false;
    if (min !== undefined && n < min) return false;
    if (max !== undefined && n > max) return false;
    return true;
  }

  /** Java int upper bound — mirrors model.js MAX_INT; the in-game loader
   *  casts prices/ints with `(int)`, larger values overflow negative. */
  const MAX_INT = 2147483647;

  function intOk(v, min, max) {
    const hi = max === undefined ? MAX_INT : max;
    if (v === '' || v === null || v === undefined) return true;
    if (typeof v === 'number') return Number.isInteger(v) && (min === undefined || v >= min) && v <= hi;
    return /^-?\d+$/.test(String(v)) && (min === undefined || Number(v) >= min) && Number(v) <= hi;
  }

  function validate(state, versionKey) {
    const issues = [];
    const version = DATA.versions.find((v) => v.key === versionKey) || DATA.versions[0];
    const taczOn = state.taczEnabled == null ? version.taczVariants : !!state.taczEnabled;
    const meta = state.meta;

    /* ------- top-level ------- */
    const fileName = (state.fileName || '').trim();
    if (!fileName) add(issues, 'error', 'meta.fileName', 'v.fileNameRequired');
    else if (!FILE_RE.test(fileName)) add(issues, 'warn', 'meta.fileName', 'v.fileNameChars');

    if (!(meta.name || '').trim()) add(issues, 'error', 'meta.name', 'v.nameRequired');

    if (meta.type !== 'csbox' && meta.type !== 'terminal') {
      add(issues, 'error', 'meta.type', 'v.badType');
    } else if (meta.type === 'terminal' && (meta.key || '').trim()) {
      add(issues, 'error', 'meta.key', 'v.terminalNoKey');
    }

    if (!numOk(meta.drop, 0, 1)) add(issues, 'error', 'meta.drop', 'v.badDrop');

    const icon = String(meta.icon || '').trim();
    if (icon !== '' && /^-?\d+$/.test(icon) && (Number(icon) < 0 || Number(icon) > MAX_INT)) {
      // CustomModelData is a Java int — anything above MAX_INT overflows.
      add(issues, 'error', 'meta.icon', 'v.badIcon');
    } else if (icon !== '' && !/^-?\d+$/.test(icon) && !version.itemModel) {
      // ns:path model-id icons only render on 26.x (minecraft:item_model);
      // on 1.21.1 / 1.20.1 the runtime logs a warning and ignores them.
      add(issues, 'warn', 'meta.icon', 'v.iconModelUnsupported');
    }

    if (!numOk(meta.discount, 0, 1)) add(issues, 'error', 'meta.discount', 'v.badDiscount');
    if (!intOk(meta.stock, -1)) add(issues, 'error', 'meta.stock', 'v.badStock');    if (!intOk(meta.maxPerPlayer, -1)) add(issues, 'error', 'meta.maxPerPlayer', 'v.badMaxPer');
    if (!intOk(meta.cooldownSeconds, 0)) add(issues, 'error', 'meta.cooldownSeconds', 'v.badCooldown');

    /* ------- pity (v2.0.1) ------- */
    const pityGrade = (meta.pityGrade || '').trim();
    const pityEveryStr = String(meta.pityEvery ?? '').trim();
    if (pityGrade || pityEveryStr !== '') {
      const PITY_GRADES = ['consumer', 'industrial', 'mil_spec', 'restricted', 'classified'];
      const gradeOk = PITY_GRADES.includes(pityGrade);
      if (!gradeOk) add(issues, 'error', 'meta.pityGrade', 'v.badPityGrade');
      if (!intOk(meta.pityEvery, 2)) add(issues, 'error', 'meta.pityEvery', 'v.badPityEvery');
      // A half-filled pity is DROPPED silently by buildBox (both fields are
      // required for export) — warn so the author does not lose the config.
      if ((pityGrade && !pityEveryStr) || (!pityGrade && pityEveryStr)) {
        add(issues, 'warn', 'meta.pity', 'v.pityHalfFilled');
      }
    }

    /* ------- random ------- */
    const rn = meta.random.map((v) => (v === '' || v === null ? null : Number(v)));
    const anyFilled = rn.some((v) => v !== null);
    if (anyFilled) {
      let bad = false;
      if (rn.length !== 5) bad = true;
      else for (const v of rn) {
        if (v === null || !Number.isInteger(v) || v < 0 || v > 10000) { bad = true; break; }
      }
      if (bad) add(issues, 'error', 'meta.random', 'v.badRandom');
      else if (rn.every((v) => v !== null && v <= 0)) add(issues, 'info', 'meta.random', 'v.randomDefault');
    } else {
      add(issues, 'info', 'meta.random', 'v.randomDefault');
    }

    /* ------- requires ------- */
    const reqsText = (meta.requiresText || '').trim();
    const reqs = reqsText ? reqsText.split(',').map((s) => s.trim()) : [];
    if (reqs.length && reqs.some((s) => s === '')) add(issues, 'warn', 'meta.requires', 'v.requiresEmpty');

    /* ------- entity ------- */
    const entityRows = meta.entity || [];
    for (const r of entityRows) {
      const eid = (r.id || '').trim();
      const ra = (r.rate || '').trim();
      if (eid && !ID_RE.test(eid)) {
        add(issues, 'warn', 'meta.entity', 'v.badEntityId');
        break;
      }
      if (ra !== '' && !numOk(ra, 0, 1)) {
        add(issues, 'error', 'meta.entity', 'v.badEntity');
        break;
      }
      // A row with a rate but no id exports as a bare number — either a
      // misplaced rate (number before its id) or a dangling one, both of
      // which the in-game loader rejects or ignores (load error). Keep the
      // data visible but flag it so the round-trip is not silently wrong.
      if (ra !== '' && !(r.id || '').trim()) {
        add(issues, 'error', 'meta.entity', 'v.entityDanglingRate');
        break;
      }
    }

    /* ------- grades ------- */
    let gradeFilledCount = 0;
    for (let gi = 0; gi < 5; gi++) {
      const items = state.grades[gi] || [];
      const gradeKey = 'grade' + (gi + 1);
      const hasContent = items.some((it) => (it.value || '').trim());
      if (!hasContent) {
        add(issues, 'info', gradeKey, 'v.gradeEmpty', { grade: gradeKey });
        continue;
      }
      gradeFilledCount++;
      items.forEach((it, ii) => {
        const path = gradeKey + '[' + (ii + 1) + ']';
        const value = (it.value || '').trim();
        if (!value) {
          add(issues, 'error', path, 'v.itemSourceRequired', { n: ii + 1 });
        } else if (it.source === 'id') {
          if (!ID_RE.test(value)) add(issues, 'warn', path + '.id', 'v.badItemId');
        } else if (it.source === 'tag') {
          if (!value.startsWith('#')) add(issues, 'error', path + '.tag', 'v.tagNeedsHash');
        } else if (it.source === 'loot_table') {
          if (!ID_RE.test(value)) add(issues, 'warn', path + '.loot_table', 'v.badLootTable');
        }

        // count
        if (it.countMode === 'fixed' && !intOk(it.count, 1)) {
          add(issues, 'error', path + '.count', 'v.badCount');
        } else if (it.countMode === 'range') {
          const ok = intOk(it.countMin, 1) && intOk(it.countMax, 1);
          if (!ok || Number(it.countMax) < Number(it.countMin)) add(issues, 'error', path + '.count', 'v.badCount');
        }

        // weight
        if (!intOk(it.weight, 0)) add(issues, 'error', path + '.weight', 'v.badWeight');

        // enchant
        if (it.enchantMode === 'custom') {
          if (!(it.enchantId || '').trim()) add(issues, 'warn', path + '.enchant', 'v.badEnchant');
          else if (it.enchantLevelMode === 'fixed' && !intOk(it.enchantLevel, 1)) {
            add(issues, 'error', path + '.enchant', 'v.badEnchantLevel');
          } else if (it.enchantLevelMode === 'range') {
            const ok = intOk(it.enchantLevelMin, 1) && intOk(it.enchantLevelMax, 1);
            if (!ok || Number(it.enchantLevelMax) < Number(it.enchantLevelMin)) {
              add(issues, 'error', path + '.enchant', 'v.badEnchantLevel');
            }
          }
        }

        // variant / raw tag
        const variant = (it.variant || '').trim();
        const rawTag = (it.tagRaw || '').trim();
        if (variant && !ID_RE.test(variant)) add(issues, 'error', path + '.variant', 'v.badVariant');
        if (variant && rawTag) add(issues, 'error', path + '.tag', 'v.variantConflict');
        if (!variant && rawTag && !rawTag.startsWith('{')) add(issues, 'error', path + '.tag', 'v.badTag');

        // components
        const comp = (it.components || '').trim();
        if (comp) {
          let parsed = null;
          try { parsed = JSON.parse(comp); } catch (e) { parsed = null; }
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            add(issues, 'error', path + '.components', 'v.badComponents');
          } else if (!version.components) {
            add(issues, 'warn', path + '.components', 'v.componentsIgnored');
          }
        }

        if (!taczOn && variant) {
          add(issues, 'warn', path + '.variant', 'v.variantUnsupported');
        }
      });
    }
    if (gradeFilledCount === 0) {
      add(issues, 'info', 'grades', 'v.allGradesEmpty');
    }

    /* ------- price table ------- */
    const { rows } = NS.collectPriceRows(state, versionKey);
    for (const r of rows) {
      const path = 'prices.' + r.key;
      if (!KEY_RE.test(r.key)) add(issues, 'error', path, 'v.badPriceKey');
      const price = r.row.price;
      // Pool auto-keys (id entries) MUST be priced: the game refuses to load
      // a box containing an unpriced id item (no grade-default fallback).
      if (r.grades.size > 0 && !NS.parsePriceInput(price)) {
        add(issues, 'error', path, 'v.unpricedItem');
      } else if (price !== '' && price !== null && price !== undefined) {
        // Fixed non-negative int or a "min-max" range (parsed by model).
        if (!NS.parsePriceInput(price)) add(issues, 'error', path, 'v.badPriceValue');
      }
      if (r.key.includes('#') && !taczOn) {
        add(issues, 'warn', path, 'v.variantUnsupported');
      }
    }

    return issues;
  }

  NS.validate = validate;
})(window.CSBoxEdit);