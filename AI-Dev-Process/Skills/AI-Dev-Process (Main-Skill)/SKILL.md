---
name: ai-dev-process
description: Track and orchestrate an AI-assisted development workflow through a persistent devprocess.md tracker. Use when the user wants to bootstrap or resume a structured dev workflow, track progress in devprocess.md, or coordinate grill-me, to-prd, to-issues, triage, tdd, and Mermaid-based SDD.
---

# AI Dev Process

Track and steer a project's AI-assisted development process through a persistent `devprocess.md` file.

## Quick start

Minimal flow:

1. Check for `devprocess.md`.
2. If missing, create it from [DEVPROCESS-TEMPLATE.md](DEVPROCESS-TEMPLATE.md).
3. Propose the default layout from [REFERENCE.md](REFERENCE.md).
4. Reconcile `devprocess.md` with repo reality.
5. Report:
   - what is complete
   - `NEXT_PHASE`
   - `NEXT_ACTION`
   - the proposed next collaborative step
6. Ask: **“Shall we proceed?”**

See [EXAMPLES.md](EXAMPLES.md) for concrete invocations.

## Workflows

### Resume or steer the process

- Treat `devprocess.md` as canonical.
- Keep exactly one `NEXT_PHASE` and one `NEXT_ACTION`.
- Record transitions, blockers, corrections, and key decisions in the workflow log.
- If tracker and reality disagree, raise the inconsistency before changing direction.
- After each completed phase, update `devprocess.md`, summarize what finished, state what is next, and ask for confirmation.

### Apply required companion skills

Follow the phase mapping in [REFERENCE.md](REFERENCE.md).

- When a phase requires a companion skill, explicitly switch into that workflow.
- Do not merely mention `grill-me`, `to-prd`, `to-issues`, `triage`, `tdd`, or the Mermaid skill.
- If required skills are missing locally, tell the user the workflow cannot proceed as intended, advise cloning the companion repositories, and offer to help.
- For issue-level SDD and architecture/design diagrams, follow the Mermaid guidance in [REFERENCE.md](REFERENCE.md).

## Advanced features

See [REFERENCE.md](REFERENCE.md) for:

- default layout and phases
- companion skill mapping
- SDD and Mermaid guidance
- AI vs. human-maintained class diagrams (regenerate the AI-maintained file only; never silently overwrite a human-maintained class diagram; raise inconsistencies)
- completion criteria
- reconciliation rules
- post-phase behavior
