# SYNTHESIS — Ingest Docs Summary

## Mode
`merge` (defaulted because `.planning/` existed at ingest time)

## Doc counts by type
| Type | Count | Notes |
|---|---|---|
| ADR | 0 | None discovered |
| PRD | 0 | None discovered |
| SPEC | 1 | multiloader-execution-spec.md |
| DOC | 18 | Mixed: guides, release notes, reviews, runbooks, architecture |
| UNKNOWN | 0 | — |
| **Total** | **19** | under v1 cap of 50 |

## Decisions locked
0 (no ADRs ingested)

## Requirements extracted
0 (no PRDs ingested)

## Constraints
1 SPEC → 6 constraints in `intel/constraints.md`:
- CONSTRAINT-001: module dependency direction (common ∉ net.minecraft)
- CONSTRAINT-002: resource layering
- CONSTRAINT-003: build artifact naming
- CONSTRAINT-004: Java toolchain divergence (21 vs 25)
- CONSTRAINT-005: phase acceptance gate
- CONSTRAINT-006: non-negotiable runtime invariants

## Context topics
18 DOC entries organized into 5 topics:
1. Refactor baseline (2 entries)
2. GUI fix guide (2 entries)
3. v1.0.5 review & changelog (5 entries)
4. Architecture overview (2 entries)
5. Development & build documentation (7 entries)

## Conflicts
0 BLOCKERS, 0 WARNINGS, 1 INFO. Detail in `INGEST-CONFLICTS.md`.

## Files produced
- `.planning/intel/constraints.md` — 6 constraints from SPEC
- `.planning/intel/context.md` — 18 context entries from DOC
- `.planning/intel/SYNTHESIS.md` — this file
- `.planning/INGEST-CONFLICTS.md` — conflict report (3 buckets)

## Files NOT produced (no inputs)
- `.planning/intel/decisions.md` — no ADRs ingested (skipped)
- `.planning/intel/requirements.md` — no PRDs ingested (skipped)

## Pointers
- Read `intel/context.md` for what the project IS, HAS, and HAS DONE
- Read `intel/constraints.md` for the architectural boundaries
- Read `INGEST-CONFLICTS.md` for conflict details

## Status
READY — safe to route. No blockers, no competing variants, single precedence layer (SPEC > DOC) applies cleanly.