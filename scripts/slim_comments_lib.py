"""Helpers for comment trimming passes (see docs/SIZE-SLIM.md)."""


def comment_blocks(lines):
    """Yield (start, end, kind) for contiguous comment blocks.
    kind: 'line' (//...), 'block' (/*...*/ or /**...*/ or bare * lines)."""
    i = 0
    n = len(lines)
    while i < n:
        s = lines[i].strip()
        if s.startswith("//"):
            j = i
            while j < n and lines[j].strip().startswith("//"):
                j += 1
            yield i, j - 1, "line"
            i = j
        elif s.startswith("/*") or s.startswith("*"):
            if "*/" in lines[i]:
                yield i, i, "block"          # single-line block
                i += 1
                continue
            j = i
            while j < n and "*/" not in lines[j]:
                j += 1
            if j < n:
                yield i, j, "block"
                i = j + 1
            else:
                yield i, n - 1, "block"
                i = n
        else:
            i += 1


def find_block(lines, marker, start=0):
    """Return (start, end, kind) of the first comment block containing marker."""
    for a, b, k in comment_blocks(lines):
        if a >= start and marker in "".join(lines[a:b + 1]):
            return a, b, k
    raise LookupError(f"block containing {marker!r} not found")


def replace_block(lines, marker, new_lines, start=0):
    a, b, _ = find_block(lines, marker, start)
    return lines[:a] + new_lines + lines[b + 1:], b + len(new_lines)
