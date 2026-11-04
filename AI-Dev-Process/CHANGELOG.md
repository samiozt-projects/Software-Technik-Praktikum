# AI-Dev-Process Structure Changelog

> History of **structural changes to the `AI-Dev-Process/` folder itself** — folder layout, skill bundling, project overrides, glossary, README scaffolding, etc.
>
> **Scope vs. Workflow Log.** Project-state changes (PR merges, phase transitions, reconciliation passes against `dev`, policy clarifications) live in [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md) — chronological / oldest-first; `devprocess.md` carries only a Workflow-Log pointer with the latest entries. OPEN_QUESTIONS continues to live in `devprocess.md` → "Current Summary". This file only records changes to **how the AI-Dev-Process workflow is organised in this repository**.
>
> Entries are reverse-chronological (newest first). Each entry references the matching `[tag]` from the Workflow Log where applicable.

---

## 2026-06-01 — `[agent-prompt-added]` — Reusable coding-agent prompt template registered

- Registered the new `AI-Dev-Process/agent-prompt.md` artefact (added by the developer) in the folder's self-documentation. The file is a reusable, German coding-agent prompt template: mandatory-context reading order (devprocess → spec → PRD → design → glossary → WORKFLOW-LOG → README), the project rules and Project Overrides, the branching + HITL policy, coding conventions, the TDD/SDD steps, and a fill-in ISSUE / USER STORY block.
- **`README.md`:** added an `agent-prompt.md` row to the "What lives here" table (Maintainer: *Team — incrementally extended*); added a **German exception** bullet to "Documentation languages" (the file is German by design because it is handed to an agent and agent ↔ developer chat is German), and added `WORKFLOW-LOG.md` to the English-files enumeration.
- **`devprocess.md` Project Layout** and **`design.md` folder structure** + **`spec.md` Supporting Artifacts:** added `agent-prompt.md` (and, where missing, `WORKFLOW-LOG.md` / `CHANGELOG.md` / `glossary.md`) to the artefact inventories so the listings match the folder.
- No skill or override change; the prompt mirrors the existing `devprocess.md → Project Overrides` rather than introducing new ones.

## 2026-06-01 — `[dev-merge-2-sync]` — Note: project-state reconciliation lives in WORKFLOW-LOG.md

- The `[dev-merge-2]` reconciliation against `dev` @ `1b7a60b` (Inkrement 2 released via PR #100, Inkrement-3 in progress, large new backlog, test-tree reorganisation, new source files) is a **project-state** change, not a structural one — its full record is the `[inkrement-2-released]` / `[dev-merge-2]` entries in [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md). Logged here only as a pointer because it bumped the `Last synced` headers across `spec.md` / `PRD.md` / `design.md` / `glossary.md`. No `AI-Dev-Process/` layout change.

## 2026-05-25 — `[yellow-stale-refs]` — Stale "Workflow Log in devprocess.md" references re-routed to WORKFLOW-LOG.md

Eight instruction-text references that still pointed at `devprocess.md` Workflow Log (instead of the actual home `WORKFLOW-LOG.md` since `[workflow-log-extracted]`) were updated:

- **`CHANGELOG.md`** scope header: "Project-state changes live in `devprocess.md` → 'Workflow Log'" → "live in `WORKFLOW-LOG.md` ... `devprocess.md` carries only a pointer with the latest entries". OPEN_QUESTIONS clarification added (still in `devprocess.md` → "Current Summary").
- **`PRD-TEMPLATE.md`** Status block: "recorded as its own entry in CHANGELOG.md and in the `devprocess.md` Workflow Log" → "in CHANGELOG.md, with an optional pointer in WORKFLOW-LOG.md".
- **`Skills/README.md`** re-sync footer: "log split between `devprocess.md` Workflow Log and `CHANGELOG.md`" → "between `WORKFLOW-LOG.md` and `CHANGELOG.md`".
- **`Skills/triage/SKILL.md`** override block (point 4 of `triage` override): "record in `devprocess.md` under 'Workflow Log' or 'OPEN_QUESTIONS'" → split: WORKFLOW-LOG.md for log entries, `devprocess.md` → OPEN_QUESTIONS for still-being-decided items.
- **`Skills/triage/OUT-OF-SCOPE.md`** override header: same split as triage SKILL.md.
- **`devprocess.md` `triage` override** (point 4): same split.
- **`devprocess.md` "ADRs across skills"** subsection: "Process / workflow decisions are recorded in this `devprocess.md` (Workflow Log + Project Overrides)" → "in `WORKFLOW-LOG.md` (project-state diary) and in this `devprocess.md` → 'Project Overrides' (when they create a new rule)"; "the decision goes into a Workflow Log entry" → "goes into a `WORKFLOW-LOG.md` entry (and optionally a `CHANGELOG.md` entry if `docs/adr/` itself is created as a structural change)".
- **`Skills/AI-Dev-Process (Main-Skill)/DEVPROCESS-TEMPLATE.md`** project-override blockquote: clarified that the "extensive Workflow Log" was extracted to `../../WORKFLOW-LOG.md` since `[workflow-log-extracted]`; also extended the override list from three to four skills (`grill-with-docs` was missing).
- **Last-synced headers** in `spec.md`, `PRD.md`, `design.md`: "see `devprocess.md` Workflow Log" → "see [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md)".

Historical log entries that describe the past extraction ("the Workflow Log was extracted from `devprocess.md`") were preserved verbatim — they accurately record what happened at that point in time.

## 2026-05-25 — `[red-howto-glossary-fix]` — How-to-add-entry rewritten, sort-order corrected in glossary

- **`CHANGELOG.md` "How to add a new entry":** completely rewritten. The previous version (a) pointed at `devprocess.md` Workflow Log even though the full log lives in `WORKFLOW-LOG.md` since `[workflow-log-extracted]`, and (b) contradicted the `[blue-hygiene]` rule by framing structural changes as Workflow Log entries first. New version makes the CHANGELOG entry the **primary record** and the WORKFLOW-LOG.md pointer **optional**, exactly matching what `[yellow-followups]`, `[green-followups]`, `[blue-followups]`, `[red-backfill-fix]`, `[green-consistency]`, `[blue-polish]` already did in practice. Also adds explicit guidance for pure project-state changes (skip the CHANGELOG, write directly to WORKFLOW-LOG.md).
- **`glossary.md` AI-Dev-Process meta-vocabulary:** corrected the **"Workflow Log"** entry to say **chronological (oldest-first)** instead of the wrong "reverse-chronological"; clarified that the full log lives in `WORKFLOW-LOG.md` since `[workflow-log-extracted]` with only a pointer in `devprocess.md`. Symmetrically corrected the **"CHANGELOG"** entry to explicitly state its **reverse-chronological (newest-first)** order (it previously didn't say either way). Also updated the **"`[dev-merge]`"** entry's "where" pointer from `devprocess.md` Workflow Log to `WORKFLOW-LOG.md`.
- **`devprocess.md` Workflow Log latest-entries pointer list:** the "Tracker stays compact" phrasing in the `[workflow-log-extracted]` summary was softened to "Tracker stays focused" — the extraction reduced the file from 340 to 316 lines, which is "more focused" but not strictly "compact". Cosmetic.

## 2026-05-25 — `[blue-polish]` — EXAMPLES diagram-target explicit, PRD-TEMPLATE freeze marker, Workflow Log extracted

- **`Skills/AI-Dev-Process (Main-Skill)/EXAMPLES.md`:** "Implementation issue with diagrams" example step 5 now spells out that any new or regenerated class diagram is written to `AI-Dev-Process/ai_class_diagramm.md`, never to `../spec/class_diagram.md`. Pulls the AI-vs-human invariant down from the override blockquote at the top of the file into the concrete instruction in the workflow step.
- **`PRD-TEMPLATE.md`:** added a `Status: Frozen template` block at the top documenting that updates are explicit team decisions and recorded as their own `CHANGELOG.md` entries. Matches the analogous "frozen by design" framing already in place for `Skills/`.
- **`WORKFLOW-LOG.md`:** new file. Holds the full Workflow Log (~35 entries, chronological / oldest-first) extracted from `devprocess.md`. The `devprocess.md` "Workflow Log" section is now a compact scope + latest-3 pointer that links here. Symmetric to the earlier `[blue-hygiene]` split between Workflow Log and CHANGELOG: now devprocess.md is a focused tracker, CHANGELOG.md holds structural history, WORKFLOW-LOG.md holds project-state history. `devprocess.md` size dropped from ~340 to ~210 lines.
- **`README.md` + `devprocess.md` Project Layout:** both extended with a row / bullet for the new `WORKFLOW-LOG.md`. The `devprocess.md` entry in the file table now reads "Workflow Log pointer" instead of "Workflow Log" to reflect the split.
- **`devprocess.md` Workflow Log section** retains a 4-pointer list as a navigation aid (`[next]`, `[workflow-log-extracted]`, `[green-consistency]`, `[red-backfill-fix]` — newest first), with the rest in `WORKFLOW-LOG.md`.
- The duplication of the "skills frozen by design" message between `AI-Dev-Process/README.md` and `Skills/README.md` was intentionally **kept** as redundant-for-discoverability — no change there.

## 2026-05-25 — `[green-consistency]` — Workflow-Log-header completeness, Maintainer-column modifier consistency

- **`devprocess.md` Workflow Log header annotation:** the parenthetical list of mixed-concern historical entries was rewritten from a 4-example abbreviation + `…` to the **full chronological list** of all eleven entries: `[folder-consolidation]`, `[path-rewrite]`, `[skills-folder]`, `[grill-with-docs-added]`, `[skills-path-rewrite]`, `[project-overrides]`, `[yellow-cleanup]`, `[green-onboarding]`, `[red-stale-text]`, `[yellow-grill-with-docs]`, `[green-polish]`. Unambiguous and robust against later back-fills.
- **`README.md` file table Maintainer column:** modifier usage normalised so every row carries a parenthetical clarifier. Updated rows: `devprocess.md` → `Agent + team (canonical tracker)`; `PRD-TEMPLATE.md` → `Maintainer-only (frozen template)`; `Skills/` → `Maintainer-only (frozen bundle)`; `CHANGELOG.md` and `README.md` → `Anyone restructuring (post-change)`. Rows that already had natural modifiers (`Agent (phase X)`, `Team (incrementally extended)`, etc.) stay as-is. The legend above the table still defines the five base categories; modifiers just narrow them.

## 2026-05-25 — `[red-backfill-fix]` — Missing back-fill entries restored

- Inserted the previously missing `[path-rewrite]` (2026-05-24) entry between `[skills-folder]` and `[folder-consolidation]`. It captures the cross-reference rewrites in the four core process artefacts (`devprocess.md`, `spec.md`, `PRD.md`, `design.md`) that immediately followed the folder move into `AI-Dev-Process/`.
- Inserted the previously missing `[grill-with-docs-added]` (2026-05-25) entry between `[skills-path-rewrite]` and `[skills-folder]`. It captures the manual addition of `Skills/grill-with-docs/{SKILL.md, ADR-FORMAT.md, CONTEXT-FORMAT.md}` that closed the dangling `/grill-with-docs` reference from `triage/SKILL.md`.
- Both entries existed in the `devprocess.md` Workflow Log but were missed when `[blue-hygiene]` back-filled structural entries into this CHANGELOG. No content was lost — the structural change had been performed at the time of each respective Workflow Log entry; only the CHANGELOG mirror was incomplete.

## 2026-05-25 — `[blue-followups]` — Last sync-marker harmonisation, transition-entry annotation, Maintainer-column normalisation

- **`glossary.md`:** added a `Last synced with dev @ commit <sha> on <date>` header block, with refresh triggers calibrated to the file's actual lifecycle ("every PR touching §3.3 simulation rules; every Inkrement release merge; every `[dev-merge]` reconciliation pass") and a cross-reference to the existing "How to extend and maintain this glossary" footer that documents the status-column maintenance rules.
- **`ai_class_diagramm.md`:** added a `Last regenerated: never (initial scaffold only)` header — semantically different from "Last synced" because the file's lifecycle is regenerate-on-demand (during architecture-definition or issue-level SDD), not sync-driven. The header points at the file's own "Change Log" section as the per-regeneration record.
- **`devprocess.md` Workflow Log:** added a parenthetical annotation to the `[blue-hygiene]` entry making explicit that it is the **transition entry** — it stays in the Workflow Log because it is the moment of the log split; all structural-change entries from `[yellow-followups]` onward live in `CHANGELOG.md` only, with a one-line pointer in the Workflow Log.
- **`README.md` file table:** normalised the Maintainer column onto five standard categories — *Agent (phase X)*, *Agent + team*, *Team*, *Maintainer-only (frozen)*, *Anyone restructuring* — defined in a short legend above the table. Each row now uses one of these labels (or a labelled variant like "Agent (on regeneration)" / "Agent (per-feature)"); the previous mix of *"Stable; updated only when team conventions change"* / *"Frozen; manual maintainer updates only"* / etc. collapses into the same category. Added a missing row for `README.md` itself.

## 2026-05-25 — `[green-followups]` — Glossary meta-vocabulary, language coverage, ADRs cross-link

- **`glossary.md`:** added a new **"AI-Dev-Process meta-vocabulary"** section (between "Process & workflow" and "Tooling & runtime") indexing the workflow-internal terms a new reader keeps stumbling over: *Project Override*, *Override blockquote*, *Workflow Log*, *CHANGELOG*, *Skill freeze*, *AI-vs-human class diagrams*, *Last synced*, *`[dev-merge]`*, *`[skills-update]`*, *`NEXT_PHASE` / `NEXT_ACTION`*, *ADRs across skills*. Each entry carries a one-line definition and a "where" pointer to the canonical file.
- **`README.md` "Documentation languages":** rewrote the first bullet to explicitly enumerate every file in `AI-Dev-Process/` that is English (`devprocess.md`, `PRD.md`, `design.md`, `spec.md`, `glossary.md`, `ai_class_diagramm.md`, `CHANGELOG.md`, `PRD-TEMPLATE.md`, this `README.md`, `Skills/README.md`, any `prds/<feature>.md`) — so the question "what language is CHANGELOG?" no longer needs interpretation.
- **`Skills/README.md` ADRs-across-skills cross-link:** strengthened the existing one-line mention into a labeled **"Cross-skill meta-pattern: ADRs"** paragraph that explicitly names `tdd`, `grill-with-docs` and "every future skill that looks for ADRs" as covered by a single shared invariant rather than per-skill overrides; lists the three substitution targets (`../design.md`, wiki, `../devprocess.md`) and links the canonical "ADRs across skills" section in `devprocess.md`.

## 2026-05-25 — `[yellow-followups]` — Loose ends after the log split

- **`AI-Dev-Process/README.md`:** rewrote the confusing "ordinary `dev` merges" sentence to a clear negative ("none of this checklist applies — these steps only fire when something in `AI-Dev-Process/` itself moved or changed"); changed the skill-update bullet from "records a `[skills-update]` Workflow Log entry" to "records a `[skills-update]` **CHANGELOG entry**" with a cross-reference to the `[blue-hygiene]` log split; extended the re-applied-header-blocks list to include the three newly overridden companion files (`EXAMPLES.md`, `CONTEXT-FORMAT.md`, `ADR-FORMAT.md`); added `CHANGELOG.md` to the "Useful entry points" section.
- **`Skills/README.md`:** mirrored the `[skills-update]` Workflow Log → CHANGELOG correction; extended the override-header-list in "Re-syncing is a manual maintainer decision" to include `EXAMPLES.md`, `CONTEXT-FORMAT.md`, `ADR-FORMAT.md`.
- **`Skills/AI-Dev-Process (Main-Skill)/EXAMPLES.md`:** added an `⚠ Project Override` blockquote at the top, calling out that the "Requirements phase" example's `to-prd` invocation is project-adapted (no tracker push, writes to local `PRD.md` / `prds/` via `PRD-TEMPLATE.md`), and that any `triage` / `to-issues` / `grill-with-docs` example needs to be read through the Project Overrides first.
- **`Skills/grill-with-docs/CONTEXT-FORMAT.md`:** added an `⚠ Project Override — Not used` header stating that no `CONTEXT.md` / `CONTEXT-MAP.md` is created in this project, with cross-reference to `glossary.md` and the override.
- **`Skills/grill-with-docs/ADR-FORMAT.md`:** added an `⚠ Project Override — Not used` header stating that no `docs/adr/` is kept, with cross-reference to the "ADRs across skills" subsection.

## 2026-05-25 — `[blue-hygiene]` — Changelog split, gitignore invariant, restructure checklist

- Introduced this `CHANGELOG.md` to host **structural-change history** going forward. Existing folder-restructure / override / scaffolding entries were back-filled (see below). The `devprocess.md` Workflow Log now states the scope split at its header and routes new structural entries here.
- Added a `.gitignore` invariant section to [`Skills/README.md`](Skills/README.md): explicitly documents that `AI-Dev-Process/Skills/` must not be excluded by future `.gitignore` edits, with a one-line `git check-ignore` sanity check.
- Added a **post-structural-change checklist** to [`README.md`](README.md) (four steps: link sweep, override-header check, `.gitignore` invariant check, `CHANGELOG.md` entry).
- Listed `CHANGELOG.md` in the file table of [`README.md`](README.md) and in the Project Layout of [`devprocess.md`](devprocess.md). The same edit added `README.md` itself to the Project Layout listing for completeness.

## 2026-05-25 — `[green-polish]` — Discoverability & sync hygiene

- Added top-level `README.md` (Repo-Root) section **"AI-assistierte Entwicklung"** pointing at `AI-Dev-Process/README.md`.
- Extended the onboarding checklist in `AI-Dev-Process/README.md` with a dedicated step for `glossary.md` as the DE↔EN reference.
- Added uniform `Last synced with dev @ <sha> on <date>` header blocks to `spec.md`, `PRD.md`, `design.md` for visible drift indication.
- Added a "When to refresh the `status` column" maintenance trigger section to `glossary.md` with concrete events (PR merges touching §3.3, Inkrement release merges, `[dev-merge]` reconciliation passes).
- **Reframed the skill update story** in `README.md` + `Skills/README.md`: in-repo skills are **frozen by design**, no routine re-sync; updates are manual maintainer decisions recorded as `[skills-update]` (not `[skills-resync]`).

## 2026-05-25 — `[yellow-grill-with-docs]` — `grill-with-docs` overrides

- New "Skill `grill-with-docs` — adapted" subsection in `devprocess.md` → "Project Overrides": no `CONTEXT.md` at repo root, no `CONTEXT-MAP.md`, no `docs/adr/`; glossary references map to `AI-Dev-Process/glossary.md`; read-only when invoked via `triage`.
- Renamed the prior "Skill `tdd` — note on ADRs" to **"ADRs across skills"** to centrally cover `tdd`, `grill-with-docs`, and any future skill that looks for ADRs.
- Added `⚠ Project Override` blockquote at the top of `Skills/grill-with-docs/SKILL.md`.
- Closed the OPEN_QUESTION "Companion-skill scope for future phases" via strikethrough + resolved marker.
- Updated `Skills/README.md` and `AI-Dev-Process/README.md` to list four override skills (was three) and to extend the maintainer-update file list with `grill-with-docs/SKILL.md` and the two Main-Skill bundle files.

## 2026-05-25 — `[red-stale-text]` — Cleanup of stale statements

- Rewrote the `to-prd` override's "Until a dedicated `glossary.md` is added" line to actively point at the now-existing `glossary.md`.
- Rewrote the two `spec.md` mentions of `prds/` as "purpose still Unknown" to describe the documented per-feature-PRD purpose; removed the orphaned `prds/` entry from `spec.md` "Unclear or Missing Information".
- Extended `design.md` top-level folder listing so `prds/` mentions its documented purpose, and added the previously missing entries for `PRD-TEMPLATE.md`, `glossary.md`, `README.md`.

## 2026-05-25 — `[green-onboarding]` — README scaffolding, glossary, prds purpose, ADR note

- Added `AI-Dev-Process/README.md` (entry point: file table, onboarding checklist, role responsibilities).
- Added `AI-Dev-Process/Skills/README.md` (skill catalog with sources, override status, file-tree).
- Added `AI-Dev-Process/glossary.md` (DE↔EN domain vocabulary structured by topic area).
- Added `AI-Dev-Process/prds/README.md` documenting per-feature-PRD purpose, naming convention, template-adaptation table.
- Added an "ADRs" subsection to "Project Overrides" (initially named "Skill `tdd` — note on ADRs") clarifying that no formal ADRs are kept; `design.md` + wiki substitute.

## 2026-05-25 — `[project-overrides]` — Per-skill override system

- Created `AI-Dev-Process/PRD-TEMPLATE.md` as the project-canonical PRD shape.
- Added "Project Overrides" section to `devprocess.md` documenting per-skill adapted behaviour for `to-prd`, `triage`, `to-issues` (later joined by `grill-with-docs`).
- Added matching `⚠ Project Override` header blocks to `Skills/to-prd/SKILL.md`, `Skills/triage/SKILL.md`, `Skills/to-issues/SKILL.md`.

## 2026-05-25 — `[yellow-cleanup]` — Inconsistencies + missing bits

- Added `Skills/grill-with-docs/{SKILL.md, ADR-FORMAT.md, CONTEXT-FORMAT.md}` (closes the dangling reference from `triage/SKILL.md`).
- Added project-override blockquotes at the top of `Skills/AI-Dev-Process (Main-Skill)/REFERENCE.md` and `Skills/AI-Dev-Process (Main-Skill)/DEVPROCESS-TEMPLATE.md` so readers entering through the generic skill bundle are routed to `devprocess.md` for the actual layout.
- Listed the "AI vs. human-maintained class diagrams" rule under "Advanced features" in `Skills/AI-Dev-Process (Main-Skill)/SKILL.md`.
- Annotated the three `run /setup-matt-pocock-skills if not` lines in `to-prd`, `to-issues`, `triage` with an inline pointer to the `devprocess.md` setup data section.
- Added an override header to `Skills/triage/OUT-OF-SCOPE.md` ("`.out-of-scope/` knowledge base is not used").

## 2026-05-25 — `[skills-path-rewrite]` — Cross-reference updates

- Rewrote all references in `devprocess.md`, `design.md`, `spec.md`, `ai_class_diagramm.md` from plain filenames (`REFERENCE.md`, etc.) to the new `Skills/AI-Dev-Process (Main-Skill)/...` paths. The orchestrator's own `SKILL.md` keeps plain filenames since its siblings live in the same folder.

## 2026-05-25 — `[grill-with-docs-added]` — Closes dangling reference from `triage`

- Manually added `Skills/grill-with-docs/{SKILL.md, ADR-FORMAT.md, CONTEXT-FORMAT.md}` to close the missing reference from `triage/SKILL.md` (which calls `/grill-with-docs` during issue grilling). The skill runs read-only per the `triage` project override.

## 2026-05-25 — `[skills-folder]` — In-repo skills

- Created `AI-Dev-Process/Skills/` and added in-repo copies of every companion skill so the team works from a deterministic baseline:
  - `Skills/AI-Dev-Process (Main-Skill)/` — orchestrator bundle (`SKILL.md` renamed from `ai-dev-process-SKILL.md`, plus `REFERENCE.md`, `DEVPROCESS-TEMPLATE.md`, `EXAMPLES.md`).
  - `Skills/{grill-me, to-prd, triage, tdd, mermaid, to-issues}/` — six tool skills with `SKILL.md` plus support files (`tdd/{deep-modules,interface-design,mocking,refactoring,tests}.md`, `triage/{AGENT-BRIEF,OUT-OF-SCOPE}.md`, `mermaid/references/`).
  - `to-issues` committed for completeness only — issue authorship is owned by PO / team per the wiki Definition of Done.

## 2026-05-24 — `[path-rewrite]` — Internal cross-references after folder consolidation

- Rewrote all internal cross-references in the four core process artefacts (`devprocess.md`, `spec.md`, `PRD.md`, `design.md`) to fit the new folder layout introduced by `[folder-consolidation]`: references to `spec/Lastenheft.pdf`, `src/main.kt`, `test/persistencetests/README.md`, `build.gradle.kts`, and other repo-root targets now use `../` from inside `AI-Dev-Process/`; cross-references between AI-Dev-Process artefacts use plain filenames. Counterpart to the later `[skills-path-rewrite]`, which extended the same pattern to the `Skills/AI-Dev-Process (Main-Skill)/...` paths after the skills were committed in-repo.

## 2026-05-24 — `[folder-consolidation]` — Move into `AI-Dev-Process/`

- Manually consolidated all `ai-dev-process` artefacts into a dedicated `AI-Dev-Process/` folder: `devprocess.md`, `PRD.md`, `design.md`, `REFERENCE.md`, `DEVPROCESS-TEMPLATE.md`, `EXAMPLES.md`, `ai-dev-process-SKILL.md` moved from repo root; `spec/spec.md` moved to `AI-Dev-Process/spec.md`; `spec/prds/` moved to `AI-Dev-Process/prds/`.
- Pre-existing project artefacts (`spec/Lastenheft.pdf`, `spec/class_diagram.md`) stayed in `spec/` as human-maintained sources of truth.
- Introduced `AI-Dev-Process/ai_class_diagramm.md` as the AI-maintained counterpart to `spec/class_diagram.md`, with a regeneration / divergence protocol added to `REFERENCE.md`.

---

## How to add a new entry

When making a **structural change** to `AI-Dev-Process/` (folder layout, skill bundle, override addition, README scaffolding, glossary, …):

1. **Make the change.**
2. **Pick a stable tag** (e.g. `[skills-update]`, `[layout-revision]`, `[override-added]`). Use kebab-case in square brackets.
3. **Add a CHANGELOG entry here as the primary record** — prepend a new section using the format **`## <date> — `[tag]` — <short title>`** with a bulleted summary of what changed in the folder. Per the `[blue-hygiene]` rule, structural changes belong in this file, **not** in the Workflow Log.
4. **Optionally add a one-line pointer to [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md)** if the change is worth surfacing in the project-state diary. The pointer pattern is: `[tag] <one-line summary>. Details in CHANGELOG.md. No project-state change.` All entries from `[yellow-followups]` onward use this two-file pattern; follow the same convention.
5. **Keep the entries reverse-chronological** in this file (newest first). Do not edit historical entries to reflect later state — append a new entry instead.

When making a **pure project-state change** (PR / branch / issue movement, phase transition, policy clarification), skip steps 3-5 and write directly to `WORKFLOW-LOG.md` instead. See its header for the convention there.
