# Examples

> ## ⚠ Project Override (STP-2026-Team-D)
>
> **The examples below are upstream / generic.** In this project, several phases are project-overridden — when an example here and the overrides in [`../../devprocess.md`](../../devprocess.md) → "Project Overrides" disagree, **the overrides win**. Most relevant for the examples below:
>
> - **"Requirements phase" example** (`apply 1. grill-me 2. to-prd`): `to-prd` is **adapted** — it writes locally to `AI-Dev-Process/PRD.md` (or `AI-Dev-Process/prds/<feature>.md`) using [`../../PRD-TEMPLATE.md`](../../PRD-TEMPLATE.md). It does **not** push to the GitHub issue tracker.
> - **Any implementation example involving `triage` or `to-issues`**: `triage` is **read-only** (recommendations in chat, no tracker comments / labels / closures); `to-issues` is **disabled** (issue authorship is owned by the PO / team per the wiki Definition of Done).
> - **Any example involving `grill-with-docs`**: glossary lives at [`../../glossary.md`](../../glossary.md), not at `CONTEXT.md` in the repo root; no `docs/adr/` folder.
>
> Read [`../../devprocess.md`](../../devprocess.md) → "Project Overrides" once before treating any example below as the literal workflow.

---

## Bootstrap a new project

User asks to start a structured AI-assisted workflow for a fresh idea.

Agent should:

1. Check for `devprocess.md`
2. Create it from `DEVPROCESS-TEMPLATE.md` if missing
3. Propose the default layout
4. Report the initial `NEXT_PHASE` and `NEXT_ACTION`
5. Ask: **"Shall we proceed?"**

## Resume an existing project

User asks where the project is in the process.

Agent should:

1. Read `devprocess.md`
2. Reconcile it with the repo and issue state
3. Report what is complete
4. Report the current `NEXT_PHASE` and `NEXT_ACTION`
5. Raise inconsistencies before proceeding

## Requirements phase

If `NEXT_PHASE` is `requirements-specification`, the agent should explicitly apply:

1. `grill-me`
2. `to-prd`

It should not merely recommend those skills.

## Implementation issue with diagrams

Before TDD on a `ready-for-agent` issue, the agent should:

1. Inspect `design.md`
2. Cross-check the planned issue-level design against it
3. Decide whether SDD and Mermaid diagrams would help
4. If yes, recommend specific diagram types and ask to proceed
5. Use the Mermaid skill if available locally — **write any new or regenerated class diagram to `AI-Dev-Process/ai_class_diagramm.md`, never to `../spec/class_diagram.md`** (see Project Override at top of this file; the latter is human-maintained and read-only for the workflow)
6. Only then proceed to `tdd`
