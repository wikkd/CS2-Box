#!/usr/bin/env python3
"""Lightweight wiki consistency lint (substitute for the missing generate-site.py).

Checks:
  1. every [[wikilink]] resolves to an existing page slug
  2. every content page has >=1 inbound link (no orphans)
  3. frontmatter `type` is one of the allowed schema values
"""
import os
import re
import sys

WIKI = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "wiki"))

ALLOWED_TYPES = {"source", "concept", "entity", "comparison", "index", "overview", "log", "schema"}
NAV = {"CLAUDE", "index", "overview", "log"}

link_re = re.compile(r"\[\[([^\]\|]+)(?:\|[^\]]+)?\]\]")
fm_re = re.compile(r"^---\n(.*?)\n---\n", re.DOTALL)


def collect():
    slugs = set()
    pages = {}  # slug -> {type, links:set, path}
    for root, _, files in os.walk(WIKI):
        if "archived" in root.split(os.sep):
            continue  # archived/ 不参与 lint 与索引
        for f in files:
            if not f.endswith(".md"):
                continue
            slug = f[:-3]
            path = os.path.join(root, f)
            slugs.add(slug)
            text = open(path, encoding="utf-8").read()
            # strip fenced + inline code spans so literal `[[wikilinks]]` prose isn't parsed as a link
            text = re.sub(r"```.*?```", " ", text, flags=re.DOTALL)
            text = re.sub(r"`[^`\n]*`", " ", text)
            m = fm_re.match(text)
            ftype = None
            if m:
                for line in m.group(1).splitlines():
                    if line.startswith("type:"):
                        ftype = line.split(":", 1)[1].strip()
            links = set(link_re.findall(text))
            pages[slug] = {"type": ftype, "links": links, "path": path}
    return slugs, pages


def main():
    slugs, pages = collect()
    errors = []
    # 1. broken links
    for slug, p in pages.items():
        for t in p["links"]:
            if t not in slugs:
                errors.append(f"[BROKEN] {slug} -> [[{t}]]")
    # 2. orphans (content pages with no inbound link)
    inbound = {s: 0 for s in slugs}
    for slug, p in pages.items():
        for t in p["links"]:
            if t in inbound:
                inbound[t] += 1
    for s, c in inbound.items():
        if s not in NAV and c == 0:
            errors.append(f"[ORPHAN] {s} (0 inbound links)")
    # 3. frontmatter type
    for slug, p in pages.items():
        if p["type"] is None:
            errors.append(f"[NO-TYPE] {slug}")
        elif p["type"] not in ALLOWED_TYPES:
            errors.append(f"[BAD-TYPE] {slug} type={p['type']}")

    print(f"Pages scanned : {len(pages)}")
    print(f"Slugs         : {len(slugs)}")
    print(f"Link refs     : {sum(len(p['links']) for p in pages.values())}")
    broken = [e for e in errors if e.startswith("[BROKEN]")]
    orphans = [e for e in errors if e.startswith("[ORPHAN]")]
    other = [e for e in errors if not e.startswith(("[BROKEN]", "[ORPHAN]"))]
    print(f"Broken links  : {len(broken)}")
    print(f"Orphans       : {len(orphans)}")
    print(f"Other issues  : {len(other)}")
    for e in errors:
        print("  " + e)
    if errors:
        print("\nRESULT: FAIL")
        sys.exit(1)
    print("\nRESULT: PASS (no broken links, no orphans, all typed)")


if __name__ == "__main__":
    main()
