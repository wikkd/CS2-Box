/* CS2-Box box editor — lightweight validator mirroring the in-game checks.
 * Issues: { level: 'error'|'warn'|'info', path: string, key: i18n key, vars } */
window.CSBoxEdit = window.CSBoxEdit || {};
(function (NS) {
  'use strict';

  const DATA = window.CSBDATA;
  const ID_RE = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;
  const KEY_RE = /^[a-z0-9_.-]+:[a-z0-9_./-]+(#.+)?$/;
  const FILE_RE = /^[a-z0-9_.\/-]+$/;
  /** Data-component key shape: a plain ns:path id (no #variant suffix). */
  const COMPONENT_KEY_RE = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;

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

  /** Cross-checks against the game registry lists the /csbox editor pack
   *  carried (NS.gameRegistry, optional). Warn-only: the game is the final
   *  authority (missing mods → different id space), so unknown ids stay
   *  visible but never block export. false = no list / id known. */
  function unknownInRegistry(list, id) {
    const reg = NS.gameRegistry;
    if (!reg || !reg[list] || !reg[list].size) return false;
    return !reg[list].has(id);
  }

  /** Per-key validation of a components object (1.21+/26.x): keys must be
   *  ns:path ids; the entity-payload components hard-require their string
   *  "id" member in vanilla's component codecs — missing it drops the whole
   *  component (and with it the preview/summon behavior) with only a chat
   *  warning in-game, so the editor surfaces it up front; a custom_data
   *  variant marker (GunId/AmmoId/VariantId) hints at the matching
   *  id#variant price key. */
  function validateComponents(comps, path, issues) {
    for (const key of Object.keys(comps)) {
      if (!COMPONENT_KEY_RE.test(key)) {
        add(issues, 'error', path + '.components.' + key, 'v.badComponentKey', { key });
        continue;
      }
      const payload = comps[key];
      if (key === 'minecraft:block_entity_data' || key === 'minecraft:entity_data') {
        const hasId = payload && typeof payload === 'object' && !Array.isArray(payload)
          && typeof payload.id === 'string' && payload.id.trim();
        if (!hasId) add(issues, 'error', path + '.components.' + key, 'v.entityDataMissingId');
      }
      if (key === 'minecraft:custom_data' && payload
          && typeof payload === 'object' && !Array.isArray(payload)) {
        const marker = ['GunId', 'AmmoId', 'VariantId'].find(
          (f) => typeof payload[f] === 'string' && payload[f].trim());
        if (marker) {
          add(issues, 'info', path + '.components.' + key, 'v.customDataVariantHint',
            { field: marker, value: payload[marker].trim() });
        }
      }
    }
  }

  function validate(state, versionKey) {
    const issues = [];
    const version = DATA.versions.find((v) => v.key === versionKey) || DATA.versions[0];
    const taczOn = state.taczEnabled == null ? version.taczVariants : !!state.taczEnabled;
    const meta = state.meta;
    const customTiers = Array.isArray(state.tiers) && state.tiers.length > 0;
    const tierCount = customTiers ? state.tiers.length : 5;
    const tierIds = customTiers
      ? state.tiers.map((t) => ((t || {}).id || '') + '').map((s) => s.trim()).filter(Boolean)
      : null;
    const PITY_GRADES = ['consumer', 'industrial', 'mil_spec', 'restricted', 'classified'];

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
      // Custom tiers resolve the pity grade against their own tier ids; the
      // legacy format keeps the canonical five-id mapping.
      const gradeOk = customTiers ? tierIds.includes(pityGrade) : PITY_GRADES.includes(pityGrade);
      if (!gradeOk) add(issues, 'error', 'meta.pityGrade', 'v.badPityGrade');
      if (!intOk(meta.pityEvery, 2)) add(issues, 'error', 'meta.pityEvery', 'v.badPityEvery');
      // A half-filled pity is DROPPED silently by buildBox (both fields are
      // required for export) — warn so the author does not lose the config.
      if ((pityGrade && !pityEveryStr) || (!pityGrade && pityEveryStr)) {
        add(issues, 'warn', 'meta.pity', 'v.pityHalfFilled');
      }
    }

    /* ------- v2.x gameplay fields (2.0.4 baseline) ------- */
    const keys = (meta.keysText || '').split(',').map((s) => s.trim()).filter(Boolean);
    if (keys.length > 8) add(issues, 'warn', 'meta.keys', 'v.tooManyKeys', { n: keys.length });
    if (keys.some((k) => !ID_RE.test(k))) add(issues, 'error', 'meta.keys', 'v.badKeyId');

    if (meta.wearMin !== '' && meta.wearMin != null && !numOk(meta.wearMin, 0, 1)) {
      add(issues, 'error', 'meta.wear', 'v.badWear');
    } else if (meta.wearMax !== '' && meta.wearMax != null && !numOk(meta.wearMax, 0, 1)) {
      add(issues, 'error', 'meta.wear', 'v.badWear');
    } else if (meta.wearMin !== '' && meta.wearMin != null && meta.wearMax !== '' && meta.wearMax != null
        && Number(meta.wearMax) < Number(meta.wearMin)) {
      // Export collapses max onto min (runtime rule) — flag it so the author
      // does not discover a narrowed range only in-game.
      add(issues, 'error', 'meta.wear', 'v.badWearRange');
    }

    if (meta.bonusChance !== '' && meta.bonusChance != null && !numOk(meta.bonusChance, 0, 1)) {
      add(issues, 'error', 'meta.bonus', 'v.badBonus');
    }

    const ann = (meta.announceText || '').trim();
    if (ann !== '') {
      if (/^-?\d+$/.test(ann)) {
        if (Number(ann) < 0) add(issues, 'error', 'meta.announce_above', 'v.badAnnounce');
        else if (Number(ann) === 0) add(issues, 'info', 'meta.announce_above', 'v.announceOff');
        else if (customTiers && Number(ann) > tierCount) {
          add(issues, 'warn', 'meta.announce_above', 'v.announceLevelHigh', { level: ann, n: tierCount });
        }
      } else if (!(customTiers ? tierIds.includes(ann) : PITY_GRADES.includes(ann))) {
        add(issues, 'error', 'meta.announce_above', 'v.badAnnounceId', { id: ann });
      }
    }

    // The runtime parses ISO-8601 via OffsetDateTime.parse then Instant.parse —
    // both REQUIRE a zone designator (Z or ±HH:MM). A local datetime without
    // one is a load error and the field is ignored.
    const ISO_RE = /^\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}(:\d{2}(\.\d{1,9})?)?(Z|z|[+-]\d{2}:\d{2})$/;
    const iso = (v) => {
      const s = String(v || '').trim();
      return s ? s : null;
    };
    const fromIso = iso(meta.availableFrom);
    const untilIso = iso(meta.availableUntil);
    if ((fromIso && !ISO_RE.test(fromIso)) || (untilIso && !ISO_RE.test(untilIso))) {
      add(issues, 'error', 'meta.available', 'v.badIso');
    } else if (fromIso && untilIso && Date.parse(fromIso) > Date.parse(untilIso)) {
      add(issues, 'warn', 'meta.available', 'v.windowReversed');
    }

    if (!intOk(meta.openCost, 0)) add(issues, 'error', 'meta.open_cost', 'v.badOpenCost');

    const soundId = (meta.sound || '').trim();
    if (soundId && !ID_RE.test(soundId)) add(issues, 'warn', 'meta.sound', 'v.badSoundId');

    const upTarget = (meta.upTarget || '').trim();
    if (upTarget) {
      if (!FILE_RE.test(upTarget)) add(issues, 'warn', 'meta.upgrade', 'v.badUpgradeTarget', { id: upTarget });
      if (meta.upConsume !== '' && meta.upConsume != null && !intOk(meta.upConsume, 1)) {
        add(issues, 'error', 'meta.upgrade', 'v.badUpgradeConsume');
      }
    }

    if (meta.negRounds !== '' && meta.negRounds != null) {
      if (!intOk(meta.negRounds, 0)) add(issues, 'error', 'meta.negotiation', 'v.badNegRounds');
      else if (Number(meta.negRounds) > 5) add(issues, 'warn', 'meta.negotiation', 'v.negRoundsClamped');
    }
    if (!intOk(meta.negTimeout, 0)) add(issues, 'error', 'meta.negotiation', 'v.badNegTimeout');
    if (meta.negBias !== '' && meta.negBias != null && !numOk(meta.negBias, -0.9, 9)) {
      add(issues, 'error', 'meta.negotiation', 'v.badNegBias');
    }

    /* ------- custom tiers (v2.x "grades" array) ------- */
    if (customTiers) {
      if (state.tiers.length > 64) add(issues, 'error', 'tiers', 'v.tooManyTiers', { n: state.tiers.length });
      const seen = new Set();
      state.tiers.forEach((t, gi) => {
        const base = 'tiers.' + gi;
        const id = ((t || {}).id || '') + '';
        const idTrim = id.trim();
        if (!idTrim) add(issues, 'error', base + '.id', 'v.tierIdRequired', { n: gi + 1 });
        else if (seen.has(idTrim)) add(issues, 'error', base + '.id', 'v.tierDup', { id: idTrim });
        else seen.add(idTrim);
        if (!intOk(t.weight, 0, 10000)) add(issues, 'error', base + '.weight', 'v.tierWeight');
        const color = String((t || {}).color || '').trim();
        if (color && !/^#[0-9a-fA-F]{6}$/.test(color)) add(issues, 'error', base + '.color', 'v.tierColor');
        if (t.stock !== '' && t.stock !== null && t.stock !== undefined && !intOk(t.stock, 0)) {
          add(issues, 'error', base + '.stock', 'v.tierStock');
        }
      });
    }

    /* ------- random (legacy five-slot weights only) ------- */
    if (customTiers) {
      // The custom tier format replaces random/grade1..5 entirely — leftover
      // legacy weights are ignored by the loader.
      if (meta.random.some((v) => v !== '' && v !== null && Number(v) > 0)) {
        add(issues, 'info', 'meta.random', 'v.randomIgnoredCustom');
      }
    } else {
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
      } else if (eid && unknownInRegistry('entities', eid)) {
        add(issues, 'warn', 'meta.entity', 'v.unknownEntity', { id: eid });
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

    /* ------- grades (items; custom tiers iterate their own count) ------- */
    let gradeFilledCount = 0;
    for (let gi = 0; gi < tierCount; gi++) {
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
          else if (unknownInRegistry('items', value)) add(issues, 'warn', path + '.id', 'v.unknownItem', { id: value });
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

        // enchant — custom mode with an empty id is legal: id-less {level}
        // means a random applicable enchantment in the level window (runtime
        // applyRandomEnchant). Only the level fields need validating.
        if (it.enchantMode === 'custom') {
          if (it.enchantLevelMode === 'fixed' && !intOk(it.enchantLevel, 1)) {
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
        if (!variant && rawTag && rawTag.startsWith('{')) {
          // JSON-object or SNBT string — must parse as one of the two. The
          // in-game loaders silently drop unparseable tags, so surface the
          // syntax error (with the parser's position) up front.
          let tagOk = false;
          try {
            const p = JSON.parse(rawTag);
            tagOk = !!(p && typeof p === 'object' && !Array.isArray(p));
          } catch (e) { /* SNBT below */ }
          if (!tagOk) {
            try { NS.parseSnbt(rawTag); tagOk = true; }
            catch (e2) {
              add(issues, 'error', path + '.tag', 'v.badTagSnbt',
                { msg: String((e2 && e2.message) || e2) });
            }
          }
        }

        // components
        const comp = (it.components || '').trim();
        if (comp) {
          let parsed = null;
          try { parsed = JSON.parse(comp); } catch (e) { parsed = null; }
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            add(issues, 'error', path + '.components', 'v.badComponents');
          } else if (!version.components) {
            add(issues, 'warn', path + '.components', 'v.componentsIgnored');
          } else {
            validateComponents(parsed, path, issues);
          }
        }

        if (!taczOn && variant) {
          add(issues, 'warn', path + '.variant', 'v.variantUnsupported');
        }
      });
    }
    if (gradeFilledCount === 0) {
      add(issues, 'info', customTiers ? 'tiers' : 'grades', 'v.allGradesEmpty');
    }

    /* ------- price table ------- */
    const { rows } = NS.collectPriceRows(state, versionKey);
    for (const r of rows) {
      const path = 'prices.' + r.key;
      if (!KEY_RE.test(r.key)) add(issues, 'error', path, 'v.badPriceKey');
      else {
        // Registry cross-check ignores the #variant suffix (NBT pricing keys).
        const base = r.key.split('#')[0];
        if (unknownInRegistry('items', base)) add(issues, 'warn', path, 'v.unknownPriceKey', { id: base });
      }
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