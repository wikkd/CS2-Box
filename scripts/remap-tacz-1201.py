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

METHOD_RE = re.compile(r'(L[^;]+;)(m_[0-9]+_?)\(([^)]*)\)(.)')
FIELD_RE = re.compile(r'(L[^;]+;)(f_[0-9]+_?|field_[0-9]+)(?=[^A-Za-z0-9_]|$)')


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
            if not line.startswith('\t'):
                # tsrg2 class line: "<leftClass> <rightClass>" (names often equal)
                parts = line.split()
                cur = parts[0] if parts else None
                continue
            # tsrg2 member line: "\t<leftName> <desc> <rightName>"
            parts = line.split()
            if len(parts) < 3:
                continue
            name, desc, srg = parts[0], parts[1], parts[2]
            if desc.startswith('('):
                args = desc[:desc.index(')') + 1]
                methods[(cur, srg, args)] = name
                methods_global[(srg, args)] = name
            else:
                fields[(cur, srg)] = name
                fields_global[srg] = name
    return methods, fields, methods_global, fields_global


def remap_descriptor(text, methods, fields, methods_global, fields_global):
    def repl_method(m):
        wrapped = m.group(1)
        cls = wrapped[1:-1]  # strip the leading 'L' and trailing ';'
        srg, args, ret = m.group(2), m.group(3), m.group(4)
        official = methods.get((cls, srg, '(' + args + ')'))
        if official is None:
            official = methods_global.get((srg, '(' + args + ')'))
        if official:
            return 'L' + cls + ';' + official + '(' + args + ')' + ret
        return m.group(0)

    def repl_field(m):
        wrapped = m.group(1)
        cls = wrapped[1:-1]
        srg = m.group(2)
        official = fields.get((cls, srg))
        if official is None:
            official = fields_global.get(srg)
        if official:
            return 'L' + cls + ';' + official
        return m.group(0)

    text = METHOD_RE.sub(repl_method, text)
    return FIELD_RE.sub(repl_field, text)


def rewrite_refmaps(jar, methods, fields, methods_global, fields_global):
    zin = zipfile.ZipFile(jar, 'r')
    tmp = jar + '.tmp.jar'
    with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith('.refmap.json'):
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
