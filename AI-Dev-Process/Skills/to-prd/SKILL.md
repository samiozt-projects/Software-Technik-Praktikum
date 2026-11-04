---
name: to-prd
description: Turn the current conversation context into a PRD and publish it to the project issue tracker. Use when user wants to create a PRD from the current context.
---

> ## ⚠ Project Override (STP-2026-Team-D)
>
> This skill carries two behaviours that are **overridden** in this project. The overrides win. See [`../../devprocess.md`](../../devprocess.md) → "Project Overrides" → "Skill `to-prd` — adapted" for the full text. Summary:
>
> 1. **Do NOT publish to the GitHub issue tracker** and do NOT apply any triage label. The agent does not create or label issues in this project (PO / team own issue authorship per the wiki Definition of Done). Write the PRD to a local Markdown file only — by default `AI-Dev-Process/PRD.md` for project-level PRDs, or `AI-Dev-Process/prds/<feature>.md` for per-feature PRDs.
> 2. **Use [`../../PRD-TEMPLATE.md`](../../PRD-TEMPLATE.md), not the `<prd-template>` block below.** The template in this file (`Problem / Solution / User Stories / Implementation Decisions / Testing Decisions / Out of Scope / Further Notes`) does NOT match the project-canonical shape; the project template (Product Summary / Backlog Structure / Team Conventions / Problem Statement / Goals / Non-Goals / Existing Features / Missing Features / FR{n} / NFR{n} / Acceptance Criteria / Risks) is authoritative.
>
> The setup data that the line below expects from `/setup-matt-pocock-skills` is documented in `devprocess.md` instead — see the "Setup data normally provided by `/setup-matt-pocock-skills`" subsection of "Project Overrides".

---

This skill takes the current conversation context and codebase understanding and produces a PRD. Do NOT interview the user — just synthesize what you already know.

The issue tracker and triage label vocabulary should have been provided to you — run `/setup-matt-pocock-skills` if not. *(In this project: that command is not committed; the equivalent setup data lives in [`../../devprocess.md`](../../devprocess.md) → "Project Overrides" → "Setup data normally provided by `/setup-matt-pocock-skills`". Read that instead.)*

## Process

1. Explore the repo to understand the current state of the codebase, if you haven't already. Use the project's domain glossary vocabulary throughout the PRD, and respect any ADRs in the area you're touching.

2. Sketch out the major modules you will need to build or modify to complete the implementation. Actively look for opportunities to extract deep modules that can be tested in isolation.

A deep module (as opposed to a shallow module) is one which encapsulates a lot of functionality in a simple, testable interface which rarely changes.

Check with the user that these modules match their expectations. Check with the user which modules they want tests written for.

3. Write the PRD using the template below, then publish it to the project issue tracker. Apply the `ready-for-agent` triage label - no need for additional triage.

<prd-template>

## Problem Statement

The problem that the user is facing, from the user's perspective.

## Solution

The solution to the problem, from the user's perspective.

## User Stories

A LONG, numbered list of user stories. Each user story should be in the format of:

1. As an <actor>, I want a <feature>, so that <benefit>

<user-story-example>
1. As a mobile bank customer, I want to see balance on my accounts, so that I can make better informed decisions about my spending
</user-story-example>

This list of user stories should be extremely extensive and cover all aspects of the feature.

## Implementation Decisions

A list of implementation decisions that were made. This can include:

- The modules that will be built/modified
- The interfaces of those modules that will be modified
- Technical clarifications from the developer
- Architectural decisions
- Schema changes
- API contracts
- Specific interactions

Do NOT include specific file paths or code snippets. They may end up being outdated very quickly.

Exception: if a prototype produced a snippet that encodes a decision more precisely than prose can (state machine, reducer, schema, type shape), inline it within the relevant decision and note briefly that it came from a prototype. Trim to the decision-rich parts — not a working demo, just the important bits.

## Testing Decisions

A list of testing decisions that were made. Include:

- A description of what makes a good test (only test external behavior, not implementation details)
- Which modules will be tested
- Prior art for the tests (i.e. similar types of tests in the codebase)

## Out of Scope

A description of the things that are out of scope for this PRD.

## Further Notes

Any further notes about the feature.

</prd-template>
