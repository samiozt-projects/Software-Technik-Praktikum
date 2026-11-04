# Reference

> ## ⚠ Project Override (STP-2026-Team-D)
>
> **This file documents the upstream / generic ai-dev-process defaults.** In this project the layout, the companion-skill behaviour, and the issue-tracking policy all deviate from the defaults below. The project's actual layout and overrides are recorded in [`../../devprocess.md`](../../devprocess.md) — read that file first. Specifically:
>
> - **Project Layout** is overridden: artefacts live under `AI-Dev-Process/` (not at repo root) and companion skills are committed in-repo under `AI-Dev-Process/Skills/`. See `devprocess.md` → "Project Layout".
> - **Issue tracking** is overridden: the agent does **not** create or modify issues. See `devprocess.md` → "Project Overrides" for the per-skill rules (`to-prd` adapted, `triage` read-only, `to-issues` disabled).
> - **PRD template** is overridden: the project-canonical shape lives in [`../../PRD-TEMPLATE.md`](../../PRD-TEMPLATE.md), not in the `to-prd` skill's built-in template.
>
> The remainder of this file is kept as upstream reference so the workflow stays readable without the skill installed and so future re-syncs from the source repository are straightforward.

## Default layout

- `devprocess.md` — process tracker
- `spec/spec.md` — prompt spec
- `spec/` — mocks and supporting artifacts
- `PRD.md` — requirements specification
- `design.md` — architecture/design
- `issues/needs-triage/` — local issue store when GitHub is not linked and project policy permits local staging

Ask once whether to accept or override this layout. If overridden, record the custom layout in `devprocess.md`.

## Default phases

1. `prompt-spec`
2. `requirements-specification`
3. `derive-issues-stories`
4. `select-core-stories`
5. `architecture-definition`
6. `incremental-implementation-workflow-management`

## Companion skills

This skill is inspired by Matt Pocock's skill system and is intended to orchestrate companion skills from:

- `https://github.com/mattpocock/skills/tree/main/skills`

It also uses the Mermaid skill by WH-2099 when diagramming is appropriate:

- `https://github.com/WH-2099/mermaid-skill/blob/main/.claude/skills/mermaid/SKILL.md`

When the required companion skill is available locally, explicitly switch into that workflow. Do not merely mention it.

Required phase behavior:

- `prompt-spec` -> create `spec/spec.md` and any needed files under `spec/`
- `requirements-specification` -> apply `grill-me`, then apply `to-prd`
- `derive-issues-stories` -> apply `to-issues` to derive issues/stories; create local or GitHub issues only when project policy permits it, otherwise record candidates for the responsible owner
- `architecture-definition` -> apply `grill-me`; when diagrams would materially help, recommend named diagram types with reasons, and if the user agrees, apply the Mermaid skill and update `design.md`
- `incremental-implementation-workflow-management` -> apply `triage` for issue flow and `tdd` for implementation work; before TDD, decide whether issue-level SDD with Mermaid diagrams would help

If a required companion skill is unavailable locally, say so explicitly, point the user to the expected source repository, and advise them to clone it:

- `https://github.com/mattpocock/skills/tree/main/skills`

Offer to help the user clone or install that repository locally. Explicitly state that the workflow cannot proceed as intended without those companion skills being available locally. Once the companion skills are available locally, load and apply the required skills. If the user does not want to do that, continue manually using the same workflow shape, while making clear that this is a fallback rather than the intended workflow.

For Mermaid specifically, strongly advise the user to clone the Mermaid skill repository so diagrams can be created with the intended workflow:

- `https://github.com/WH-2099/mermaid-skill/blob/main/.claude/skills/mermaid/SKILL.md`

## SDD and Mermaid guidance

Before TDD on a `ready-for-agent` issue, explicitly inspect `design.md`, cross-check the planned issue-level design against it, and decide whether issue-level SDD would add value. Do not create issue-level SDD notes or Mermaid diagrams without performing this consistency check first. Consider diagrams/specification especially when the issue:

- touches multiple components or modules
- introduces a new abstraction, protocol, or workflow
- changes architecture or integration boundaries
- has important runtime interaction worth a sequence diagram
- has domain structure worth a class, component, or state model
- is ambiguous enough that diagramming would reduce risk

If diagrams make sense:

1. Explicitly cross-check the planned issue-level design against `design.md` first and keep issue-level SDD and diagrams consistent with it.
2. Recommend them before TDD as a strong checkpoint.
3. Name the diagram type or types and explain why, e.g. sequence, class, component, or state.
4. Ask the user whether to proceed.
5. If the user agrees, use the Mermaid skill.
6. Put the SDD notes and Mermaid diagrams inline in the issue markdown file by default.
7. If the issue file becomes unwieldy, split to a sibling spec file.

If this explicit cross-check finds any inconsistency, omission, or likely architectural drift relative to `design.md`:

1. Raise the inconsistency explicitly before proceeding.
2. Explain how the planned issue-level design differs from `design.md`.
3. Ask whether to resolve it now or defer it.
4. If resolved now, update or refactor `design.md` accordingly.
5. If deferred, still create the issue-level diagrams, but mark them as provisional and diverging from the current `design.md`.
6. Note the divergence in the current issue.
7. Record or request a follow-up architecture/design issue or story, according to the project's issue-authorship policy, describing whether the expected resolution is to update `design.md` or to refactor the implementation/spec back toward the current design.
8. Record the process impact in `devprocess.md` if relevant.

If the user declines diagrams, proceed with TDD but record that diagrams were recommended and declined.

## AI vs. human-maintained class diagrams

Some projects keep both a human-maintained class diagram (typically in `spec/class_diagram.md` or similar) **and** an AI-maintained counterpart (e.g. `ai_class_diagramm.md` in the AI-Dev-Process folder). When this split exists:

- The AI must update **only** the AI-maintained file when producing or regenerating a class diagram during the architecture-definition phase or issue-level SDD.
- The human-maintained file is treated as read-only by the workflow; do not silently overwrite it.
- If the two diverge, raise the inconsistency to the user and decide collaboratively whether the human file, the AI file, or `design.md` is the one to update.
- Record the regeneration in `devprocess.md` (workflow log) and in the AI diagram's own change log.

## Completion criteria

Use these defaults unless the user specifies otherwise:

- `prompt-spec`: `spec/spec.md` exists and the intent/mocks are captured
- `requirements-specification`: `PRD.md` exists and reflects grilled requirements
- `derive-issues-stories`: issues exist locally or in GitHub and start in `needs-triage`, or candidate issues are captured for the responsible owner when the agent is not allowed to create issues
- `select-core-stories`: prototype stories are explicitly identified
- `architecture-definition`: `design.md` exists and matches current understanding
- `incremental-implementation-workflow-management`: at least one issue is actively flowing through triage/spec/TDD/review

## Reconciliation rules

- Treat `devprocess.md` as canonical, but reconcile it with repo artifacts and issue tracker state.
- Keep exactly one `NEXT_PHASE` and one `NEXT_ACTION`.
- If tracker and reality disagree, do not silently rewrite state. Raise the inconsistency, explain it, and resolve it with the user.
- Backtracking is allowed by changing `NEXT_*` and logging why.

## After each completed phase

1. Update `devprocess.md`
2. Summarize what finished
3. State the new `NEXT_PHASE`
4. State the new `NEXT_ACTION`
5. Propose the next collaborative step
6. Ask: **“Shall we proceed?”**
