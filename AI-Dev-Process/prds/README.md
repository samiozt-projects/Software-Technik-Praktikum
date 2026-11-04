# Per-Feature PRDs

This folder is **reserved for per-feature PRDs** that may be authored starting in **Inkrement 3** (Lastenheft §5.1) — when the §3.3 simulation rules begin to land as individual workstreams.

## When to add a file here

Add a Markdown file here when a single Lastenheft requirement is **substantial enough** to merit its own PRD instead of a bullet under `../PRD.md`. Candidates the team has flagged so far (per `../PRD.md` "Lastenheft requirements without a tracking issue yet"):

- Curve-radius speed limit (`r = abc / (4A)`, `V_max^i = min(V_max, √(μ·g·r))`) — epic #8
- 2 m minimum distance + collision avoidance — epic #8
- Shortest-path-by-Euclidean-distance routing with congestion diversion — epic #8
- Zipper-merge ("Reißverschlussprinzip") at junctions — epic #8
- Verstopfung / Verklemmung detection — epic #8
- Deterministic simulation (NFR7) — epic #8
- Configurable simulation speed-up — epic #6
- Detailed simulation statistics (Inkrement 4) — epic #5 / #8
- Projector calibration / camera-projector homography — epic #5 / #9
- Multi-table server coupling (Inkrement 5) — epic #6
- Promotional video (Inkrement 3 script → 4 shoot → 5 final) — epic #7

The project-level [`../PRD.md`](../PRD.md) keeps a thematic digest of all of these; this folder is for the *deep dives* once a feature is being implemented.

## Template

Use [`../PRD-TEMPLATE.md`](../PRD-TEMPLATE.md). For a feature-level PRD, drop the sections that don't apply at this scope:

| Section | Project-level `PRD.md` | Per-feature PRD here |
|---|---|---|
| Product Summary | ✅ | ❌ — replace with one paragraph "Feature scope" |
| Backlog Structure (epic table) | ✅ | ❌ — name the parent epic instead |
| Team Conventions | ✅ | ❌ — covered by `../PRD.md` |
| Problem Statement / Goals / Non-Goals | ✅ | ✅ |
| Existing Features / Missing Features | ✅ | ❌ — replace with "Current state" referencing relevant code paths |
| Functional Requirements (FR{n}) | ✅ | ✅ — re-number locally (FR1, FR2, … per file) |
| Non-Functional Requirements (NFR{n}) | ✅ | ✅ — only the NFRs that bind this feature |
| Acceptance Criteria — Sprint minima table | ✅ | ❌ |
| Acceptance Criteria — Delivery / Final demo | ✅ | ✅ — narrowed to this feature |
| Risks and Assumptions | ✅ | ✅ |

## Naming convention

`<lastenheft-section>-<short-kebab-name>.md`, e.g.:

- `3.3-curve-radius-speed-limit.md`
- `3.3-minimum-distance.md`
- `3.3-zipper-merge.md`
- `3.3-verstopfung-detection.md`
- `3.3-deterministic-simulation.md`
- `5-promotional-video.md`

So a sorted directory listing groups by Lastenheft section.

## Workflow

1. Identify the feature (e.g. "curve-radius speed limit").
2. Copy `../PRD-TEMPLATE.md` into this folder with the naming convention above.
3. Drop / adapt sections per the table.
4. Apply the `to-prd` skill **with the Project Override**: write here locally, do **not** push to the GitHub issue tracker. See `../devprocess.md` → "Project Overrides" → "Skill `to-prd` — adapted".
5. Once ratified by the PO, link the file from `../PRD.md` under the matching epic.
6. The Product Owner files the corresponding GitHub Issue(s) themselves — the agent surfaces candidates but does not create issues.

## Why the folder is empty today

Inkrement 2 finished without needing this level of granularity — its issues were tracked one-to-one on GitHub. The §3.3 backlog (curve radius, zipper-merge, Verstopfung, etc.) has not started yet, so no feature-level PRD has been authored. This folder waits.
