# AI-Dev-Process

This folder holds the **AI-assisted development process** for the STP-2026-Team-D project: the persistent process tracker, the derived project artefacts (PRD, design, spec, AI-maintained class diagram), and in-repo copies of every companion skill the workflow uses.

The pre-existing project artefacts ([`../spec/Lastenheft.pdf`](../spec/Lastenheft.pdf) — canonical German requirements; [`../spec/class_diagram.md`](../spec/class_diagram.md) — human-maintained class diagram) stay **outside** this folder under `../spec/`. They are read by the workflow but never overwritten.

## What lives here

The "Maintainer" column uses five normalised categories:

- **Agent (phase X)** — derived artefacts that the workflow regenerates during a specific phase.
- **Agent + team** — collaborative editing; the agent proposes, the team accepts / corrects.
- **Team** — human-maintained, incrementally extended.
- **Maintainer-only (frozen)** — stable, only the project maintainer changes it.
- **Anyone restructuring** — written by whoever performs a structural change to `AI-Dev-Process/`.

| File / Folder | Purpose | Maintainer |
|---|---|---|
| [`devprocess.md`](devprocess.md) | **Canonical process tracker.** `NEXT_PHASE` / `NEXT_ACTION`, Workflow Log pointer, Project Layout, Project Overrides, OPEN_QUESTIONS. | Agent + team (canonical tracker) |
| [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md) | Full Workflow Log (project-state diary, chronological / oldest-first). Extracted from `devprocess.md` for compactness. | Agent + team (append-only) |
| [`spec.md`](spec.md) | Prompt spec — derived from Lastenheft + repo + wiki + GitHub-Issues. | Agent (phase 1 / reconciliation) |
| [`PRD.md`](PRD.md) | Product requirements (FR/NFR-numbered, Lastenheft-cross-referenced). | Agent (phase 2, using [`PRD-TEMPLATE.md`](PRD-TEMPLATE.md)) |
| [`PRD-TEMPLATE.md`](PRD-TEMPLATE.md) | The project-canonical PRD shape that `to-prd` must use. | Maintainer-only (frozen template) |
| [`design.md`](design.md) | Current architecture mapped against Lastenheft + wiki. | Agent (phase 5 / reconciliation) |
| [`ai_class_diagramm.md`](ai_class_diagramm.md) | **AI-maintained** Mermaid class diagram (counterpart to `../spec/class_diagram.md`). | Agent (on regeneration) |
| [`glossary.md`](glossary.md) | DE / EN domain vocabulary used by skills and humans. | Team (incrementally extended) |
| [`agent-prompt.md`](agent-prompt.md) | Reusable coding-agent prompt template (German, by exception — see "Documentation languages"): mandatory-context reading order, project rules / overrides, branching + HITL policy, coding conventions, TDD/SDD steps, and a fill-in ISSUE / USER STORY block. | Team (incrementally extended) |
| [`prds/`](prds/) | Per-feature PRDs (one per Lastenheft §3.3 item, starting Inkrement 3). | Agent (per-feature, using `PRD-TEMPLATE.md`) |
| [`Skills/`](Skills/) | In-repo copies of every companion skill — see [`Skills/README.md`](Skills/README.md). | Maintainer-only (frozen bundle) |
| [`CHANGELOG.md`](CHANGELOG.md) | History of structural changes to this folder (folder layout, skill bundling, override additions, …). | Anyone restructuring (post-change) |
| [`README.md`](README.md) | This file — entry point, onboarding checklist, post-restructure checklist. | Anyone restructuring (post-change) |

## Onboarding checklist (new team member)

1. **Read `devprocess.md` top-to-bottom.** It is the canonical entry point. Pay particular attention to:
   - `Project Layout` — where everything lives and why
   - `Project Overrides` — four companion skills (`to-prd`, `triage`, `to-issues`, `grill-with-docs`) behave **differently** in this project than their upstream definitions; the overrides win
   - `Current State` (`NEXT_PHASE` / `NEXT_ACTION`)
   - `Workflow Log` (skim the last 5–10 entries to catch up)
2. **Skim the source artefacts** in this order: `spec.md` → `PRD.md` → `design.md`. They reflect the current state of the repository at the latest `dev` merge (each carries a `Last synced` header).
3. **Keep [`glossary.md`](glossary.md) at hand** as the DE↔EN reference alongside the artefacts above. It maps Lastenheft / wiki terms (Quelle, Konstruktionsphase, Verstopfung, Inkrement, ...) to code identifiers and carries a `status` column (`gap`, `partial`, `optional`, path) that doubles as a quick capability map.
4. **Open `../spec/Lastenheft.pdf`** (canonical requirements) and the team wiki page `Gemeinsame-Grundlagen.md`. Both are authoritative.
5. **Pick a coding-agent setup**:
   - **Claude Code**: either point the agent at [`AI-Dev-Process/Skills/`](Skills/) as a skill directory or use the Anthropic `anthropic-skills` plugin (`anthropic-skills:*` namespace — same definitions).
   - **Other coding-agents**: load `AI-Dev-Process/Skills/<skill>/SKILL.md` directly. Supporting files next to each `SKILL.md` are picked up automatically.
6. **When the in-repo skill instructions and `devprocess.md → Project Overrides` disagree, the overrides win.** Every affected `SKILL.md` carries a `⚠ Project Override` block at the top with a pointer to the central rules.

## Who pulls which lever

- **Product Owner (PO)** — drives feature / story issues on the GitHub tracker, owns the Lastenheft sequencing, ratifies PRD changes.
- **Team members** — file bug issues per the wiki Definition of Done; review PRs; perform the Inkrement release merges `dev → main`; pull labels and apply triage decisions surfaced by the agent.
- **Agent** — runs the phases of `devprocess.md`, updates the derived artefacts, surfaces recommendations and drafts. **Does not** create / label / close GitHub issues. **Does not** silently overwrite `../spec/class_diagram.md`.
- **Skills** — invoked explicitly by the agent during the matching phase. Project Overrides apply.

## After a structural change in this folder

When anyone (typically the maintainer) moves files around, adds a new skill, restructures an override, or renames anything inside `AI-Dev-Process/`:

1. **Walk the Markdown links** in the affected files to confirm none point to a renamed / moved target. A quick sanity sweep: open each touched `*.md`, follow every `[…](…)` link, and verify the target opens. For a thorough pass, a tool like `markdown-link-check` against the folder root catches dangling references mechanically.
2. **Confirm the `⚠ Project Override` blockquotes are still in place** at the top of every file listed under "Re-syncing" in [`Skills/README.md`](Skills/README.md).
3. **Re-check the `.gitignore` invariant** — see [`Skills/README.md`](Skills/README.md) → ".gitignore invariant" for the one-line check.
4. **Add an entry to [`CHANGELOG.md`](CHANGELOG.md)** following the "How to add a new entry" footer of that file.

For ordinary `dev` merges that only change source code (not this folder's structure), **none of this checklist applies** — these steps only fire when something in `AI-Dev-Process/` itself moved or changed.

## Companion skills are frozen by design

The skills under [`Skills/`](Skills/) are **intentionally committed at a specific revision** so the whole team (and every coding-agent) works from the same baseline. There is **no routine re-sync** with the upstream skill repositories — that's the whole point of committing them.

**Do not update individual skill files ad hoc.** If a skill update is genuinely needed:

- The update is performed **manually by the project maintainer**, not by individual developers.
- The maintainer pulls the new revision from upstream ([`mattpocock/skills`](https://github.com/mattpocock/skills/tree/main/skills), [`WH-2099/mermaid-skill`](https://github.com/WH-2099/mermaid-skill)), replaces the affected files, re-applies the `⚠ Project Override` header blocks (currently on `to-prd/SKILL.md`, `triage/SKILL.md`, `to-issues/SKILL.md`, `triage/OUT-OF-SCOPE.md`, `grill-with-docs/SKILL.md`, `grill-with-docs/CONTEXT-FORMAT.md`, `grill-with-docs/ADR-FORMAT.md`, `AI-Dev-Process (Main-Skill)/REFERENCE.md`, `AI-Dev-Process (Main-Skill)/DEVPROCESS-TEMPLATE.md`, `AI-Dev-Process (Main-Skill)/EXAMPLES.md`), walks through `devprocess.md → Project Overrides` to confirm the overrides still match the new revision, and records a `[skills-update]` **CHANGELOG entry** (skill updates are structural changes — see the `[blue-hygiene]` log split).
- Until that happens, treat the current `Skills/` content as authoritative — even if upstream has moved on.

## Documentation languages

- Process artefacts (everything in this folder — including `devprocess.md`, `PRD.md`, `design.md`, `spec.md`, `glossary.md`, `WORKFLOW-LOG.md`, `ai_class_diagramm.md`, `CHANGELOG.md`, `PRD-TEMPLATE.md`, this `README.md`, `Skills/README.md`, and any `prds/<feature>.md`), companion-skill outputs, KDoc in source code → **English**.
- **Exception:** [`agent-prompt.md`](agent-prompt.md) is intentionally **German**, because it is handed to an agent and developer ↔ agent chat is German. It carries a header documenting the exception; the process artefacts, skill outputs and KDoc it references stay English.
- Lastenheft, top-level repo-root `README.md`, GitHub issues, team wiki → **German**.
- Developer ↔ agent chat → **German**.

## Useful entry points

- [Skills overview](Skills/README.md)
- [Project Overrides in devprocess.md](devprocess.md#project-overrides)
- [PRD template](PRD-TEMPLATE.md)
- [Glossary](glossary.md)
- [Structural changelog](CHANGELOG.md)
