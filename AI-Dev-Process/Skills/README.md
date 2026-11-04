# Companion Skills (in-repo)

Every skill the `ai-dev-process` workflow uses is committed here so the whole team — and any coding-agent the team picks — works from the **same revision** without manual setup. The canonical project rules for using them live in [`../devprocess.md`](../devprocess.md); this README is just the catalog.

**These skill files are frozen by design.** There is no routine re-sync with upstream — see [Re-syncing](#re-syncing-is-a-manual-maintainer-decision) below.

## Catalog

| Skill | Folder | Upstream source | Phase / use | Project status |
|---|---|---|---|---|
| **ai-dev-process** (orchestrator) | [`AI-Dev-Process (Main-Skill)/`](AI-Dev-Process%20%28Main-Skill%29/) | upstream `ai-dev-process` bundle | Drives all phases via `devprocess.md` | **Main skill** — `(Main-Skill)` suffix marks it as central |
| **grill-me** | [`grill-me/`](grill-me/) | [`mattpocock/skills`](https://github.com/mattpocock/skills/tree/main/skills/grill-me) | Phases 2 + 5 — stress-test ideas / architecture | As-is |
| **grill-with-docs** | [`grill-with-docs/`](grill-with-docs/) | [`mattpocock/skills`](https://github.com/mattpocock/skills/tree/main/skills/grill-with-docs) | Issue grilling (called by `triage`); also auxiliary grilling | **Adapted** — glossary maps to `../glossary.md` (no `CONTEXT.md`/`CONTEXT-MAP.md` at root); no `docs/adr/`; read-only when invoked via `triage` |
| **to-prd** | [`to-prd/`](to-prd/) | [`mattpocock/skills`](https://github.com/mattpocock/skills/tree/main/skills/to-prd) | Phase 2 — produce / refresh the PRD | **Adapted** — writes locally to `PRD.md` / `prds/`; uses [`../PRD-TEMPLATE.md`](../PRD-TEMPLATE.md), not the built-in template; no tracker push |
| **to-issues** | [`to-issues/`](to-issues/) | [`mattpocock/skills`](https://github.com/mattpocock/skills/tree/main/skills/to-issues) | (would-be) Phase 3 — derive issues from a plan | **Disabled** — agent does not create issues; committed only for completeness |
| **triage** | [`triage/`](triage/) | [`mattpocock/skills`](https://github.com/mattpocock/skills/tree/main/skills/triage) | Phase 6 — issue-flow state machine | **Read-only** — recommendations only; no comments, no labels, no closures; `.out-of-scope/` not used |
| **tdd** | [`tdd/`](tdd/) | [`mattpocock/skills`](https://github.com/mattpocock/skills/tree/main/skills/tdd) | Phase 6 — red/green/refactor per issue | As-is — ADR references are absent in this project (see `devprocess.md`) |
| **mermaid** | [`mermaid/`](mermaid/) | [`WH-2099/mermaid-skill`](https://github.com/WH-2099/mermaid-skill) | Phase 5 + issue-level SDD — diagrams | As-is; class-diagram regeneration writes to `../ai_class_diagramm.md` only, never to `../../spec/class_diagram.md` |

## Project Overrides — short version

Four skills deviate from their upstream definitions in this project. **The overrides win.** Each affected `SKILL.md` carries a `⚠ Project Override` blockquote at the top with the same content; the full canonical text lives in [`../devprocess.md`](../devprocess.md) under "Project Overrides".

- **`to-prd`** — no tracker push; uses `../PRD-TEMPLATE.md`.
- **`triage`** — read-only; outputs recommendations to the developer.
- **`to-issues`** — disabled entirely.
- **`grill-with-docs`** — no `CONTEXT.md` / `CONTEXT-MAP.md` at root (glossary lives at `../glossary.md`); no `docs/adr/`; read-only when invoked from `triage`.

**Cross-skill meta-pattern: ADRs.** A separate **["ADRs across skills"](../devprocess.md#adrs-across-skills)** entry in `../devprocess.md` covers `tdd`, `grill-with-docs`, and **every future skill that looks for ADRs** as one shared rule rather than as a per-skill override. The project keeps **no** formal ADRs (`docs/adr/`); ADR-references in any skill resolve to:

- `../design.md` for architecture decisions,
- the team wiki for team-level decisions,
- `../devprocess.md` for process / workflow decisions.

Treat that as a project-wide invariant, not as a skill-specific quirk.

[`AI-Dev-Process (Main-Skill)/REFERENCE.md`](AI-Dev-Process%20%28Main-Skill%29/REFERENCE.md) and [`AI-Dev-Process (Main-Skill)/DEVPROCESS-TEMPLATE.md`](AI-Dev-Process%20%28Main-Skill%29/DEVPROCESS-TEMPLATE.md) also carry override headers explaining that the layout / template they describe is the upstream generic and **not** what this project actually uses.

## How a coding-agent picks the skills up

- **Claude Code** — point the agent at `AI-Dev-Process/Skills/` (or symlink into `~/.claude/skills/`); or use the Anthropic `anthropic-skills` plugin (`anthropic-skills:*` namespace ships the same definitions).
- **Other coding-agents** — load each `SKILL.md` directly. Support files next to a `SKILL.md` (e.g. `tdd/refactoring.md`, `triage/AGENT-BRIEF.md`, `mermaid/references/`, `grill-with-docs/CONTEXT-FORMAT.md`) are picked up automatically.

## Re-syncing is a manual maintainer decision

The contents of this folder are **frozen on purpose**. The team works from this exact revision; routine re-sync with upstream is **not part of the workflow**.

When an update is genuinely needed — for example a critical upstream fix or a deliberate decision to absorb a new skill version — the **project maintainer** performs it manually:

1. **Replace** the files in the relevant `Skills/<skill>/` subfolder with the new revision pulled from the upstream source listed in the catalog above.
2. **Re-apply the `⚠ Project Override` header blocks** if upstream overwrote them. Currently overridden:
   - `to-prd/SKILL.md`
   - `to-issues/SKILL.md`
   - `triage/SKILL.md`
   - `triage/OUT-OF-SCOPE.md`
   - `grill-with-docs/SKILL.md`
   - `grill-with-docs/CONTEXT-FORMAT.md`
   - `grill-with-docs/ADR-FORMAT.md`
   - `AI-Dev-Process (Main-Skill)/REFERENCE.md`
   - `AI-Dev-Process (Main-Skill)/DEVPROCESS-TEMPLATE.md`
   - `AI-Dev-Process (Main-Skill)/EXAMPLES.md`
3. **Walk through `../devprocess.md` → Project Overrides** and confirm the override text still matches what each skill does after the bump.
4. **Add a `[skills-update]` entry to [`../CHANGELOG.md`](../CHANGELOG.md)** describing what changed, which upstream revisions you pulled, and any override re-edits. *(Skill updates are structural changes — see the `[blue-hygiene]` log split between [`../WORKFLOW-LOG.md`](../WORKFLOW-LOG.md) and [`../CHANGELOG.md`](../CHANGELOG.md).)*

**Do not** update individual files ad hoc as a developer — that's exactly the inconsistency the freeze is meant to prevent.

## `.gitignore` invariant

`AI-Dev-Process/Skills/` is intentionally **not** in [`../../.gitignore`](../../.gitignore) — the in-repo skill copies are committed on purpose, and exclusion would defeat the freeze. If `.gitignore` is ever restructured (e.g. someone adds a sweeping `Skills/` or `*.md` pattern), confirm that `AI-Dev-Process/Skills/**` still tracks correctly. A quick sanity check:

```bash
git check-ignore AI-Dev-Process/Skills/grill-me/SKILL.md
# expected: no output (file is tracked)
```

If that command prints a matching pattern, the freeze is broken — fix the `.gitignore` change before merging.

## File-tree at a glance

```
Skills/
├── README.md                                  (this file)
├── AI-Dev-Process (Main-Skill)/
│   ├── SKILL.md                               (orchestrator definition)
│   ├── REFERENCE.md                           (phases, completion criteria, SDD/Mermaid, AI vs. human diagrams)
│   ├── DEVPROCESS-TEMPLATE.md                 (generic devprocess.md seed)
│   └── EXAMPLES.md                            (example invocations)
├── grill-me/SKILL.md
├── grill-with-docs/
│   ├── SKILL.md
│   ├── ADR-FORMAT.md
│   └── CONTEXT-FORMAT.md
├── to-prd/SKILL.md                            (⚠ Project Override at top)
├── to-issues/SKILL.md                         (⚠ Project Override — disabled)
├── triage/
│   ├── SKILL.md                               (⚠ Project Override — read-only)
│   ├── AGENT-BRIEF.md
│   └── OUT-OF-SCOPE.md                        (⚠ Project Override — not used)
├── tdd/
│   ├── SKILL.md
│   ├── deep-modules.md
│   ├── interface-design.md
│   ├── mocking.md
│   ├── refactoring.md
│   └── tests.md
└── mermaid/
    ├── SKILL.md
    └── references/                            (30 diagram-type reference files)
```
