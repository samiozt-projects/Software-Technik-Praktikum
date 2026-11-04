# PRD Template (Project-specific)

> **Status:** Frozen template. Updated only when the team explicitly decides to change the project-canonical PRD shape (e.g. after a Lastenheft revision or a wiki convention change). Each such update is recorded as its own entry in [`CHANGELOG.md`](CHANGELOG.md), with an optional pointer in [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md).

> **Use this template instead of the `to-prd` skill's built-in template** when generating or refreshing PRDs in this project. See `devprocess.md` → "Project Overrides" for the rationale.
>
> The structure below is what the canonical [`PRD.md`](PRD.md) has grown into and what the team expects to see. It is Lastenheft- and team-wiki-driven (not generic). The same shape is used both for the project-level PRD (`PRD.md`) and for any per-feature PRD that may eventually land under [`prds/`](prds/).

---

# {Product Name} — Product Requirements Document

> Derived from `../spec/Lastenheft.pdf` (canonical), the current repository state at `{date}`, the **verified** GitHub-Issues / PR snapshot, and the **team wiki** (`https://github.com/sekassel/STP-2026-Team-D.wiki.git`, page `Gemeinsame-Grundlagen.md`) for team-level conventions. Requirements that restate Lastenheft sections are cross-referenced. Statements not directly supported by Lastenheft, wiki, or code are explicitly marked **Assumption** or **Unknown**.
>
> The Lastenheft remains the authoritative requirements document. The wiki is authoritative for process conventions. This PRD structures both for the ai-dev-process workflow; it does not override them.

## Product Summary

A short paragraph plus a bulleted list naming primary target audience, target platforms, inspiration / prior art (if any), and the implementation tech stack with a cross-reference to `build.gradle.kts` / equivalent.

## Backlog Structure

| Epic | Title | Concerns |
|---|---|---|
| #N | … | … |

(Table mirroring the GitHub-Issues `epic` labels. Each functional requirement below should map to one of these epics.)

## Team Conventions

> Authoritative source: the team wiki. Full text mirrored in `devprocess.md`; this section keeps only the summary needed to read the PRD.

Brief bullets for: **Branching** (incl. dev/main convention), **Definition of Done**, **Code comments**, **Meetings**.

## Problem Statement

The problem this product solves, in the user's perspective. Cross-reference Lastenheft §1 where applicable.

## Goals

Direct quotes / paraphrases from Lastenheft §1 + §3. Bullet list.

## Non-Goals

What is **explicitly out of scope**. Derived from Lastenheft + repository evidence. Mark **Assumption** where the Lastenheft is silent.

## Existing Features

A status digest, **not** the full inventory (that lives in `devprocess.md`). One subsection per Inkrement / release tier:

### Inkrement-N (released on `main` via PR #X)

A short paragraph identifying which Issues are closed and what the highlights are.

### Closed after Inkrement-N (on `dev` per wiki branching convention)

A bulleted summary of post-release work that lives on `dev`, with a one-line cross-reference per issue (`#N` → key PR, key outcome).

### Test set / CI

A short bulleted overview of the test suite locations and the CI gate.

## Missing or Incomplete Features

Numbers in `[#x]` are GitHub issue numbers. Each item is **either** a verified open issue, **or** a Lastenheft requirement not yet tracked, **or** a known repository defect. The full open-issue inventory lives in `devprocess.md`; this section keeps a thematic digest only.

### Currently in flight on `dev`

Subgroups by domain: UI / data, domain / simulation, detection, hardware / 3D-print, tooling. Per-issue one short line with state ("partial via PR #x; remaining work: …").

### Open issues without a branch

Same grouping, one line per issue.

### Lastenheft requirements **without** a tracking issue yet

Explicit Lastenheft §3.3 / §3.4 obligations that have no issue. List the would-be epic next to each.

### Repository defects not bound to a specific issue

Strikethrough resolved entries (with the closing PR), keep open ones as-is.

## Functional Requirements

Numbered for traceability. Each `[L §x]` references the Lastenheft; `[#y]` references a GitHub issue; `[code: …]` marks an existing implementation; `[gap]` marks something not yet implemented.

1. **{Requirement summary}.** `[L §…]` `[#… open|done]` `[code: …|gap]`
2. …

Cover at least: load/save, network state, construction vs. simulation phase, detection (straight + curves + live camera), modelling-error visualisation, vehicle types + spawn config, routing + kinematics, simulation rules, statistics & completion criteria, save/load networks, tutorial + level set, control / configuration menus, accessibility, scaling, optional config file, table-only control, multi-table coupling, promotional video, bridges. Keep numbering stable across revisions.

## Non-Functional Requirements

Numbered (NFR1…). Cover at least: visual quality, latency, robustness, maintainability/extensibility, hardware fit, performance budget, determinism, accessibility, language/runtime, CI quality gate, consistent scaling.

NFR1. **{NFR summary}.** `[L §…]`

NFR2. …

## Acceptance Criteria

### Delivery (Lastenheft §5)

Bulleted list of the contractually-required deliverables (working software, test report, physical road elements, promotional video).

### Final demo (Lastenheft §5)

Bulleted list of the demo bullet points.

### Sprint minima (Lastenheft §5.1) — verified status

| Inkrement | Lastenheft minimum | Status (verified) |
|---|---|---|
| 1 | … | … |
| 2 | … | … |
| … | … | … |

## Risks and Assumptions

Mix of:

- **Risk — {short title}.** Description, mitigation if any.
- **Assumption — {short title}.** What is assumed and why; how it could be invalidated.
- **Note — {short title}.** Recorded incidents / clarifications worth keeping in the test-report deliverable.

Strikethrough resolved risks (with the closing event), keep open ones as-is.

---

## Notes on use

- This template is read by `to-prd` when the skill runs in this project. The skill's default Problem/Solution/User-Stories shape is **not** used here (see "Project Overrides" in `devprocess.md`).
- The Lastenheft and wiki are canonical. This PRD restates and structures them; conflicts are resolved in favour of the source.
- For per-feature PRDs (one per Inkrement-3+ Lastenheft §3.3 item, for example), the same shape applies — drop sections that don't apply at the feature level (`Backlog Structure`, `Acceptance Criteria → Sprint minima`) and replace `Existing Features` with a short scope summary.
- Numbering of `FR{n}` / `NFR{n}` is stable across revisions; add new ones at the end rather than re-numbering.
