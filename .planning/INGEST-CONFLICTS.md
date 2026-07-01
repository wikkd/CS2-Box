# Conflict Detection Report

Mode: merge
Run date: 2026-07-01
Total classifications: 19 (1 SPEC, 18 DOC, 0 ADR, 0 PRD, 0 UNKNOWN-low)

---

## BLOCKERS (0)

None.

No LOCKED-vs-LOCKED ADR contradictions (no ADRs ingested).
No ingest-vs-existing-LOCKED decisions (no existing PROJECT.md/REQUIREMENTS.md/ROADMAP.md/CONTEXT.md with `<decisions>` blocks).
No UNKNOWN-low entries (all 19 docs classified at high or medium confidence).
No cycle-detection blockers (cross-ref graph has no directed cycles — the spec↔plan back-reference is an undirected SCC, not a directed cycle, and does not block synthesis).

---

## WARNINGS (0)

None.

No competing PRD acceptance variants (no PRDs ingested).
No SPEC-vs-higher-precedence contradictions (SPEC is the highest-precedence source in this run, no ADRs above it).
No lower-precedence-overrides-higher events.

---

## INFO (1)

[INFO] Auto-resolved: SPEC > DOC on module dependency direction
  Source A: /Users/shuangyuexingxun/Desktop/CS2-Box/.planning/multiloader-execution-spec.md (SPEC) declares `common` MUST NOT import `net.minecraft.*` or `net.neoforged.*` at compile time.
  Source B: /Users/shuangyuexingxun/Desktop/CS2-Box/.planning/multiloader-refactor-plan.md (DOC, "关键技术决策" 4th item) encodes the same rule.
  Note: Both sources agree. SPEC wins by precedence and the constraint is recorded as CONSTRAINT-001 in intel/constraints.md. DOC's embedded statement is consistent and not contradictory; the lower-precedence DOC merely restates the SPEC rule.

---

## Notes for next ingest

- If `AGENTS.md` invariants (CONFIG is final, no Cloth Config, recipe path singular) are intended to be LOCKED decisions, the user should convert `AGENTS.md` to a formal ADR with `Status: Accepted` and re-run with `--manifest precedence=0`.
- If acceptance criteria for the refactor phases should be tracked as requirements, the user should author a PRD document with explicit acceptance criteria per phase and re-run.