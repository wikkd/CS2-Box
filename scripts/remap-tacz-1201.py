#!/usr/bin/env python3
"""Rewrite SRG->official mixin refmaps inside a SpecialSource-remapped jar.

The ForgeGradle 7 userdev client runs with official-mapped vanilla names
(e.g. LivingEntity.tick()), but a production Forge 1.20.1 mod jar ships a mixin
refmap whose targets are SRG names (e.g. LivingEntity.m_8119_). SpecialSource
remaps the bytecode but leaves the refmap JSON untouched, so after remapping a
jar with SpecialSource (SRG->official) you must also rewrite every target
descriptor in each *.refmap.json from SRG names to official names. This script
does exactly that, using the official->srg TSRG mapping (reversed).

Usage:
    python3 remap-tacz-1201.py <official-1.20.1-srg.tsr.gz> <mod.jar>
The jar is rewritten in place (via a temp file + atomic rename).
"""
import gzip
import json
import os
import re
import sys
import zipfile

METHOD_RE = re.compile(r'(L[^;]+;)((?:[A-Za-z0-9_$]+\$)?(?:m_[0-9]+_?|method_[0-9]+))\(([^)]*)\)(.)')
FIELD_RE = re.compile(r'(L[^;]+;)((?:[A-Za-z0-9_$]+\$)?(?:f_[0-9]+_?|field_[0-9]+))(?=[^A-Za-z0-9_]|$)')
# Bare-name forms (no L<class>; wrapper): mixin @Accessor refmap values like
# "f_143509_:Ljava/lang/String;" and method targets "m_8119_()V...".
BARE_FIELD_RE = re.compile(r'\b((?:[A-Za-z0-9_$]+\$)?(?:f_[0-9]+_?|field_[0-9]+))(?=[:\s(]|$)')
BARE_METHOD_RE = re.compile(r'\b((?:[A-Za-z0-9_$]+\$)?(?:m_[0-9]+_?|method_[0-9]+))\(')


def load_srg_to_official(tsrg_gz):
    """Parse an official->srg TSRG (gzipped, tsrg2 layout) and return reverse
    lookups: methods[(class, srg_name, args_desc)] -> official_name and
    fields[(class, srg_name)] -> official_name.
    """
    methods = {}
    fields = {}
    # Global fallbacks keyed only by (srg_name, args): the mixin refmap often
    # names an inherited method on a subclass (e.g. ServerPlayer.setSprinting
    # declared on LivingEntity), where the exact-class entry is absent but the
    # SRG name maps to the same official name on the declaring superclass.
    methods_global = {}
    fields_global = {}
    with gzip.open(tsrg_gz, 'rt', encoding='utf-8') as fh:
        cur = None
        for raw in fh:
            line = raw.rstrip('\n').rstrip('\r')
            if not line.strip():
                continue
            if line.startswith('\t\t'):
                # tsrg2 parameter row: "\t\t<index> <paramObf> <paramSrg>" under a
                # method -- must be skipped, otherwise "4 surfaceRule f_188871_"
                # poisons fields_global with f_188871_ -> "4" (citadel refmap
                # corruption, 2026-11-20).
                continue
            if not line.startswith('\t'):
                # tsrg2 class line: "<leftClass> <rightClass>" (names often equal)
                parts = line.split()
                cur = parts[0] if parts else None
                continue
            # tsrg2 member line: "\t<leftName> <desc> <rightName>" for methods;
            # field lines may omit the descriptor: "\t<leftName> <rightName>".
            parts = line.split()
            if len(parts) == 3:
                name, desc, srg = parts
            elif len(parts) == 2:
                name, desc, srg = parts[0], "", parts[1]
            else:
                continue
            if desc.startswith('('):
                args = desc[:desc.index(')') + 1]
                methods[(cur, srg, args)] = name
                methods_global[(srg, args)] = name
            else:
                fields[(cur, srg)] = name
                fields_global[srg] = name
    return methods, fields, methods_global, fields_global


def _map_srg_token(name, args, methods, fields, methods_global, fields_global):
    """Map one (possibly prefixed) SRG token to official; returns None if no hit."""
    prefix = ""
    target = name
    idx = name.rfind("$")
    if idx != -1:
        prefix, target = name[:idx + 1], name[idx + 1:]
    if target.startswith(("m_", "method_")):
        official = methods_global.get((target, "(" + (args or "") + ")"))
        if official is None and args is None:
            # args unknown: try any descriptor with this SRG name
            for (srg, _a), off in methods_global.items():
                if srg == target:
                    return prefix + off
        if official:
            return prefix + official
    elif target.startswith(("f_", "field_")):
        official = fields_global.get(target)
        if official:
            return prefix + official
    return None


def remap_descriptor(text, methods, fields, methods_global, fields_global):
    def repl_method(m):
        wrapped = m.group(1)
        cls = wrapped[1:-1]  # strip the leading 'L' and trailing ';'
        token, ret = m.group(2), m.group(4)
        prefix = ""
        target = token
        idx = token.rfind("$")
        if idx != -1:
            prefix, target = token[:idx + 1], token[idx + 1:]
        official = methods.get((cls, target, '(' + m.group(3) + ')'))
        if official is None:
            official = methods_global.get((target, '(' + m.group(3) + ')'))
        if official:
            return 'L' + cls + ';' + prefix + official + '(' + m.group(3) + ')' + ret
        return m.group(0)

    def repl_field(m):
        wrapped = m.group(1)
        cls = wrapped[1:-1]
        token = m.group(2)
        prefix = ""
        target = token
        idx = token.rfind("$")
        if idx != -1:
            prefix, target = token[:idx + 1], token[idx + 1:]
        official = fields.get((cls, target))
        if official is None:
            official = fields_global.get(target)
        if official:
            return 'L' + cls + ';' + prefix + official
        return m.group(0)

    def repl_bare_method(m):
        mapped = _map_srg_token(m.group(1), None, methods, fields, methods_global, fields_global)
        if mapped:
            return mapped + '('
        return m.group(0)

    def repl_bare_field(m):
        mapped = _map_srg_token(m.group(1), None, methods, fields, methods_global, fields_global)
        if mapped:
            return mapped
        return m.group(0)

    text = METHOD_RE.sub(repl_method, text)
    text = FIELD_RE.sub(repl_field, text)
    text = BARE_METHOD_RE.sub(repl_bare_method, text)
    text = BARE_FIELD_RE.sub(repl_bare_field, text)
    return text


def rewrite_refmaps(jar, methods, fields, methods_global, fields_global):
    zin = zipfile.ZipFile(jar, 'r')
    tmp = jar + '.tmp.jar'
    with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            # refmap naming varies by mod: "x.refmap.json" (dot) and
            # "x-forge-refmap.json" (hyphen, e.g. REI / architectury) both exist;
            # endswith('refmap.json') covers both. The dot-only filter silently
            # skipped hyphen-named refmaps (2026-11-16 pipeline first run).
            if item.filename.endswith('refmap.json'):
                try:
                    obj = json.loads(data.decode('utf-8'))
                except Exception:
                    zout.writestr(item, data)
                    continue
                for sec in ('mappings',):
                    section = obj.get(sec)
                    if isinstance(section, dict):
                        for mp in section.values():
                            if isinstance(mp, dict):
                                for k, v in list(mp.items()):
                                    if isinstance(v, str):
                                        mp[k] = remap_descriptor(v, methods, fields, methods_global, fields_global)
                d = obj.get('data')
                if isinstance(d, dict):
                    for sub in d.values():
                        if isinstance(sub, dict):
                            for mp in sub.values():
                                if isinstance(mp, dict):
                                    for k, v in list(mp.items()):
                                        if isinstance(v, str):
                                            mp[k] = remap_descriptor(v, methods, fields, methods_global, fields_global)
                data = json.dumps(obj, indent=2).encode('utf-8')
            zout.writestr(item, data)
    zin.close()
    os.replace(tmp, jar)


def main():
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 1
    tsrg_gz, jar = sys.argv[1], sys.argv[2]
    methods, fields, methods_global, fields_global = load_srg_to_official(tsrg_gz)
    print(f'rewriting refmaps in {jar} (methods={len(methods)}, fields={len(fields)})')
    rewrite_refmaps(jar, methods, fields, methods_global, fields_global)
    return 0


if __name__ == '__main__':
    sys.exit(main())
