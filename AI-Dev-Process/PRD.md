# Product Requirements Document

> **Last synced** with `dev`/`main` @ commit `60e27e5` on 2026-07-08 (`[dev-merge-9]`, deep content sync). Refresh on every `[dev-merge]` reconciliation pass — see [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md). This pass folds in the Inkrement-5 state (multi-table server coupling, worlds, sound, codebase restructure — see `design.md` for the architectural detail) and the Inkrement-6 backlog opening.

> Derived from [`../spec/Lastenheft.pdf`](../spec/Lastenheft.pdf) (canonical), the repository state at `dev`/`main` @ `60e27e5` (2026-07-06), the **verified** GitHub-Issues / PR snapshot (**Inkrement 5 released to `main` via PR #265 "Inkrement 5", 2026-07-06, tag `Sprint5`**; the entire I5 backlog closed except tooling item #229; **Inkrement 6 "bug-free release" just started** — the #266–#288 backlog opened 2026-07-07/08, almost entirely untriaged), and the **team wiki** (`https://github.com/sekassel/STP-2026-Team-D.wiki.git`, page `Gemeinsame-Grundlagen.md`) for team-level conventions. Requirements that restate Lastenheft sections are cross-referenced. Statements not directly supported by Lastenheft, wiki or code are explicitly marked **Assumption** or **Unknown**.
>
> The Lastenheft remains the authoritative requirements document. The wiki is authoritative for process conventions. This PRD structures both for the ai-dev-process workflow; it does not override them.

## Product Summary

An **interactive projection-table system** that lets users construct a road network from physical, 3D-printed road tiles on a ferromagnetic table, automatically digitises the layout with a camera, validates and visualises it via a projector, and runs a deterministic traffic simulation that gives statistical feedback on the network's quality.

- **Primary target audience**: school pupils on outreach visits and technically interested visitors (Lastenheft §1).
- **Target platforms**: laptop **or** Raspberry Pi 5B with 2 GB RAM (Lastenheft §2).
- **Inspiration**: the game *Freeways* and MIT Media Lab CityScope (Lastenheft §1).
- **Implementation**: Kotlin + KorGE + JavaCV (Lastenheft §2 technical constraints, honoured by `build.gradle.kts`).

## Backlog Structure

The GitHub Issues board is organised around nine **epics** that group the work; each functional requirement below maps to one of them:

| Epic | Title | Concerns |
|---|---|---|
| #1 | Datenspeicherung | Saving and loading levels |
| #3 | Straßennetz | Road-network domain rules |
| #4 | Domänenmodell | Streets, vehicles, colours, nodes incl. elevation |
| #5 | Benutzeroberfläche (UI) | All UI surfaces (menus, scenes, overlays) |
| #6 | Spielsteuerung | Touch / mouse / keyboard interaction |
| #7 | Produktmarketing | 60–90 s promotional video |
| #8 | Simulationslogik | Routing, kinematics, weather, distances, success/failure |
| #9 | Objekterkennung | Camera-based recognition |
| #10 | 3D-Druck | Printable road and bridge elements |

## Team Conventions

Authoritative source: team wiki at `https://github.com/sekassel/STP-2026-Team-D.wiki.git`, page `Gemeinsame-Grundlagen.md`. The full text is mirrored in [`devprocess.md`](devprocess.md) under "Lastenheft, Wiki and Issue Tracker Alignment".

Summary for PRD purposes:

- **Branching** — feature branches base on `dev` and merge into `dev`; `main` is updated by a release merge at least once per Inkrement. Any "landed on `dev`" annotation below counts as **done by team convention**, even if `main` does not yet reflect it.
- **Definition of Done** — test-first where practical (AI-assisted tests explicitly allowed), implement, tests pass, code review by anyone in the team, follow-up issues for new / known bugs.
- **Code comments** — KDoc doc-strings (interpretation discussed under "Risks and Assumptions").
- **Meetings** — Mo–Mi 12:00–14:00.

If the wiki changes, the **devprocess.md** mirror is updated first; this PRD's summary is then reconciled.

## Problem Statement

Traffic-planning concepts are abstract on paper or pure-software UI. The product's value proposition is that a **tangible, hands-on construction surface** with **automatic digital twin** plus **projected simulation feedback** makes traffic planning experienceable — and turns the table into a shared work and exhibition space (Lastenheft §1).

## Goals

Directly from Lastenheft §1 + §3:

- Let users physically extend a pre-defined level (with fixed sources and sinks) with road tiles.
- Continuously recognise the layout from a camera image.
- Project a clear, aesthetically appealing visualisation back onto the table, including feedback on which elements were recognised and which connections are illegal.
- Cleanly separate **Konstruktionsphase** (free editing, partial / illegal allowed) from **Simulationsphase** (network must be valid).
- Simulate **many vehicles concurrently**, with deterministic behaviour and configurable speed-up.
- Produce **statistics** that allow comparing alternative network designs.
- Run on the prescribed hardware (RPi 5B, 2 GB).

## Non-Goals

Derived from Lastenheft + repository evidence:

- **Constructing a network purely at the PC** is explicitly out of scope (Lastenheft §3.3).
- ~~**Multi-table coupling** is deferred to Inkrement 5~~ — **implemented and released** (PR #265, "Inkrement 5"). See Functional Requirement 40 and "Existing Features" → "Inkrement 5" below.
- **Audio output on Raspberry Pi** is explicitly excluded (Lastenheft §3.4) — the new Inkrement-5 sound feature (#226) is a laptop-mode-only enhancement, consistent with this constraint.
- **Non-JVM platforms.** `build.gradle.kts` activates only `targetJvm()` (**Assumption**: out of scope, consistent with the JVM-only Lastenheft constraint).
- **Realistic driver modelling beyond the required physics** (right-before-left, dynamic re-routing on traffic news) — Lastenheft §3.3 marks these as **optional**.

## Existing Features

Implemented in the repository today. **All issue states are verified via `gh`.** The full closed/open/PR tables live in [`devprocess.md`](devprocess.md) under "Lastenheft, Wiki and Issue Tracker Alignment"; this PRD keeps only a status digest so that requirements traceability stays compact.

### Inkrement-1 (released on `main` via PR #38 merged 2026-05-11)

13 issues closed: **#2, #11, #13, #14, #15, #16, #17, #18, #19, #20, #21, #22, #23**. Highlights: full domain model + JSON persistence + level validation + KorGE main / level / save menus + straight-road detection + I1 single-vehicle simulation. Issue #20 ("Abschluss eines Levels") currently materialises as the I1 first-arrival placeholder.

### Inkrement 2 (released to `main` via PR #100 "Inkrement/Sprint 2 PR", merged 2026-05-25)

The following landed on `dev` after Inkrement 1 and reached `main` with the **Inkrement-2 release merge PR #100** (`main` was `871b9c9` at release). The in-repo `AI-Dev-Process/` folder also reached `main` via PR #69. `main` has since advanced through the PR-template back-port (PR #124) and the **Inkrement-3 release (PR #141 "Sprint 3", 2026-06-08, `main` HEAD `5b3f20f`)**.

- **#46 Fahrzeugtypen um Grafikeigenschaft erweitern** — chain complete on `dev`. PR #60 → revert #63 → PR #65 restored the getter → **PR #68 "Final Fix" merged 2026-05-19 10:17 UTC** re-added `VehicleType.graphicRef`, DTO/serializer mapping, `Assets` preload/lookup, `DomainModelTest` coverage, and the KDoc on the getter.
- **#47 Vereinheitlichung des Verhaltens der Bedienelemente** — **PR #66 merged 2026-05-19 13:07 UTC** introduces the `ControlButtons` helper (grey/black Start, Pause, gear, close, icons under `res/images/`). **PR #74 merged 2026-05-20 14:38 UTC** re-applies the helper inside `LevelScene` and removes the duplicated control code.
- **#40 Projekt-Ordnerstruktur überarbeiten** — **PR #70 merged 2026-05-19 21:07 UTC**. `resources/` collapsed into `res/images/`; `res/savedlevel/` separated from `res/level/`; `LevelViewRenderer` moved to `src/rendering/`; `build.gradle.kts` source-set wiring adjusted.
- **#39 Erstellung und Einbindung von 2D-Grafiken** — **PR #73 merged**. Vehicle / source / target / end / pause / construction PNGs under `res/images/`.
- **#44 Tutorial-Level** — **PR #75 merged**. `TutorialScene`, `res/level/tutorial.json`, `TutorialSceneTest`.
- **#59 Modus für Farbenblinde** — **PR #76 merged 2026-05-21 16:34 UTC**. Toggle on `MenuScene`, palette switch in `GameState`, `MenuSceneColorBlindnessTest`.
- **#43 + #48** — **PR #77 merged 2026-05-21 16:53 UTC**. `PredefinedLevelCatalog` removes the old `// TODO: Load Level 1-8`; dedicated `SavedNetworksScene` with filter / sort / load / delete; eight `res/level/level{1..8}.json`.
- **#45 Mehrere Level erstellen** — **PR #78 merged 2026-05-24 16:42 UTC**. Eight progressively complex levels with rotation, plus `PredefinedLevelCatalog` extension.
- **#49 Anpassung des Pausenmenüs** — PR #61 scaffold, completed by the configuration-menu chain.
- **#50 Übersichtsmenü** — **PR #82 merged 2026-05-24**. Overview `ConfigurationScene`, `VehicleTypeDetailScene`, `SourceConfigDetailScene`.
- **#51 Konfigurationsmenü für Fahrzeugtypen** — **PR #87 merged 2026-05-25**. Full vehicle-type editing via the new `VehicleConfigurationScene` + `VehiclePreview` helper.
- **#52 Konfigurationsmenü für Quellkonfigurationen** — **PR #84 + #90 merged 2026-05-25**. Source-config editor (initial speed / frequency / colour, per-source vehicle-type selection); two-colour-vehicle fix.
- **#56 Simulation mit Fahrzeugen aus mehreren Quellen** — **PR #83 merged 2026-05-24**. Intermediate completion criterion wired into `Simulation` (`allSourceConfigurationsCompleted()`); colour-aware routing via `Level.findNextStreetTowardTarget`.
- **#54 Erkennung komplexer Straßennetze** — **PR #80 merged 2026-05-24**. Skeleton-based street-graph construction + driving-path finding; circumcircle-based roundabout-direction detection; node-identity fix in `LevelSerializer`.
- **#55 Erzeugung von Fahrzeugen aus allen konfigurierten Quellkonfigurationen** — closed earlier (PR #62 → reverted by #64 → PR #67 placed the work on `dev`).
- Misc: **PR #81 merged 2026-05-24** — scroll regression fix in `LevelMenuScene` / `SavedNetworksScene` with `LevelMenuSceneTest` coverage.

### Inkrement 3 (released to `main` via PR #141 "Sprint 3", merged 2026-06-08)

- **#107 Simulation-Neustart-Fix** — **PR #112 merged 2026-05-29**. `simulationState` reset on level load; `GameState.activeSimulation` introduced and cleared by the `currentLevel` setter.
- **#91 Kurvengeschwindigkeit anhand Kurvenradius (§3.3)** — **PR #113 merged 2026-05-30**. `src/domain/CurveSpeed.kt` + route-aware look-ahead in `Simulation.accelerateVehicles` (inner waypoints + connection-node corners).
- **#41 Einheitliche Skalierung (1:250, 9:5)** — **PR #88 merged 2026-06-01**. `GameAreaScale.kt` full coordinate/unit utility; rendering rewritten onto the in-game coordinate system. (Last Inkrement-2 residual, finished post-release.)
- **#106 Einheitliche UI** — **PR #116 merged 2026-06-01**. Unified headings, frames, fonts, vehicle-symbol aspect ratios across configuration / pause / load menus.
- **#94** path-completeness validation (PR #125), **#97** source-config error display (PR #133), **#98** success overlay (PR #126), **#102** scene transitions (PR #123), **#103** per-level success criteria (PR #129), **#108** challenge mode (PR #115, `src/domain/ChallengeMode.kt`) + **#117** tutorial (PR #128), **#110** save-only-on-change (PR #120), **#119** realistic junction transitions (PR #121), **#57** bridge models (PR #122/#127, `res/3dprinting/`) + **#12** road-element printing + **#58** physical-bridge print, **#71** LevelSerializer ARGB colour fix (PR #134), **#79** close-button nav, **#109** menu usability, **#101** promotional-video script, **#104** traffic volume / `Street.utilization` (PR #131), **#95** error highlighting, **#96** hint bar, **#132** `.exe` packaging (PR #136) — closed on `dev` 2026-06-02 … 2026-06-08.
- **§3.3 simulation rules completed just before the release** (all merged to `dev` 2026-06-08): **#92** right-of-way / zipper-merge (PR #135), **#105** 2 m minimum distance + collision avoidance, re-implemented (PR #137, `MIN_DISTANCE_M`/`speedCeilingForLeadingVehicle`), **#85** shortest-path routing (PR #139, `src/domain/StreetPathFinder.kt`), **#93** Verstopfung run-failure (PR #140, `Simulation.congestionDetected`); plus multi-level street detection **#114** (PR #138, `StreetDetection.kt` rewrite + `res/road/Road5.png`).
- Tooling: **PR #111 merged 2026-05-31** — `.github/pull_request_template.md`; **PR #124 merged 2026-06-03** back-ported it to `main`.

### Test set / CI

- All tests now live under subject subfolders (no test directly under `test/` anymore):
  - `test/domaintests/`: `CurveSpeedTest`, `DomainModelTest`, `LevelValidationTest`, `MultiColorVehicleTest`, `SimulationTest`, `StreetUtilizationTest` (#104), `StreetPathFinderTest` (#85), `SimulationSpeedTest` (#144), `VehicleAdvanceTest`, `VehicleGenerationFromMultipleSourcesTest`.
  - `test/persistencetests/`: `LevelSerializerTest` (+ `ExampleLevel.json`, with the #160 `weatherCondition` field), `PredefinedLevelsTest`, `TutorialLevelTest`, plus the persistence README (level-JSON schema, moved from `test/README.md` during the PR #70 restructure).
  - `test/imagedetectiontests/`: `StreetDetectionTest`, `StreetDetectionIntegrationTest`.
  - `test/renderingtests/`: `GameAreaScaleTest`, `StreetRendererTest`, `ViewSyncTest`.
  - `test/scenestests/`: `ConfigurationSceneScrollTest`, `ControlButtonsTest`, `LevelMenuSceneTest`, `MenuSceneColorBlindnessTest`, `MenuSceneTest`, `TutorialSceneTest`.
  - `test/uitests/`: `AssetsTest`.
- `./gradlew jvmTest` on push to `main` (JDK 21, Zulu); `.github/pull_request_template.md` added by PR #111.

## Missing or Incomplete Features

Each item below is **either** a verified open GitHub issue, **or** a Lastenheft requirement not yet tracked as an issue, **or** a known repository defect. Numbers in `[#x]` are GitHub issue numbers. The full open-issue inventory (with epics, branches and PRs) lives in [`devprocess.md`](devprocess.md); this section keeps a thematic digest only.

> **The Inkrement-3 §3.3 simulation-rule set is complete and released** (PR #141): curve-radius #91, right-of-way #92 (PR #135), 2 m distance #105 (PR #137), traffic volume #104 (PR #131), shortest path #85 (PR #139, `StreetPathFinder`), Verstopfung #93 (PR #140, `congestionDetected`), modelling-error highlighting #95. The items below are the **Inkrement-4 backlog** (#142–#166) plus carried-over items.

### Inkrement-4 core — detailed statistics (Lastenheft §5.1): COMPLETE

- **#142 — Detaillierte Simulationsstatistiken erfassen** (epic #8, PR #167). Collects per-run statistics (`src/domain/SimulationStatistics.kt`).
- **#143 — Detaillierte Simulationsstatistiken anzeigen** (epic #5, PR #188). Displays the collected statistics after a run. Together with #142 the §5.1 core deliverable is delivered.

### Inkrement 4 — released to `main` via PR #202 "Inkrement 4" (`dev → main`, 2026-06-22, tag `Inkrement-4`)

- **#144 — Einstellbare Simulationsgeschwindigkeit** (PR #164). Configurable speed factor in `GameState`, applied in `Simulation`, controlled via `src/ui/ControlButtons.kt`; `SimulationSpeedTest`, `ControlButtonsTest`.
- **#160 — Wetterbedingungen in Leveldateien** (PR #165). `src/domain/WeatherCondition.kt` enum (`DRY`/`WET`/`SNOWY`), `Level.weatherCondition`, persisted via `LevelDto`/`LevelSerializer`; weather value in every `res/level/*.json`.
- **#159 — Simulation automatisch nach Validierung starten** (PR #172). Auto-starts the simulation once the network validates.
- **#161 — Wetterbedingungen über die UI konfigurierbar machen** (PR #176). Per-level weather editable in the UI; completes the weather line #160 → #158 → #161.
- **#157 — Kamera integrieren** (PR #180/#171). Physical-camera integration.
- **#156 — Relevante Spiel- und Simulationskonstanten auslagern** (PR #186). Externalised game/simulation constants (overlaps the `optional` #42).
- **#53 — Erkennen der gelegten Straßen per Kamera** (closed). Live-camera street ingestion.
- **#162 — Fahrzeugparameter lassen sich nicht in allen Leveln anpassen** (PR #181, bug). Vehicle parameters editable in all levels.
- **#163 — Konfigurationsmenü zeigt geänderte Levelparameter nicht korrekt an** (bug, closed). The configuration menu now reflects changed level parameters.

### Inkrement-4 backlog: closed (released via PR #202 "Inkrement 4", `dev → main`, 2026-06-22, tag `Inkrement-4`; #153/#189 closed shortly after the release)

- Robustness: **#154** (PR #168), **#155** (PR #193), **#149** (PR #191) — closed and in the release; **#153** (PRs #240/#246) closed shortly **after** the release (on `dev` only). Weather line **#158/#161** closed (PRs #170/#176).
- Result flow / UI: **#151** (PR #194), **#166** (PR #174), **#150** (PR #198) — closed. **#99** (level reset without reload) is the only carried-over UI item.
- Marketing video: **#145 + #148** closed (PR #196 — rough cut done); the finalisation chain continues as the Inkrement-5 set **#215–#218**.
- Bugs: **#184** (PR #195) closed and in the release; **#189** (PR #238) closed after it (on `dev` only). **#182** (street-detection correction from camera images) is the only surviving bug — its fix **PR #200 was closed unmerged**.
- Detection / camera: **#146** closed (PRs #187 + #192), joining #53/#157/#114; in Inkrement 5 the ArUco rectification **#213** (PR #244) and detection caching/smoothing **#214** (PR #247) also closed.
- Tooling: **#147** closed (PR #199; Teamscale CI PR #175, self-hosted runner PR #190), **#42** closed (PR #197 — `src/config/ConfigManager.kt`).

### Inkrement 5 — released to `main` via PR #265 "Inkrement 5" (`dev → main`, merged 2026-07-06T21:50:52Z, tag `Sprint5`)

The **entire** Lastenheft §5.1 Inkrement-5 minimum set is closed:

- **Server coupling & vehicle transfer** (new `src/network/`): **#203** server interaction (PR #250) + **#204** network config (PR #239); **#205 + #207** transfer-message format + coupling-server connection (one PR, **#253** — payload `{color, type, speedMetersPerSecond}` via `VehicleWebSocketConverter`); **#206** sink→source mapping (PR #243, `TransitionAssignment`); **#208/#209** outgoing/incoming vehicle hand-over (PRs #257/#258, `VehicleTargetHandoverPort` + `CoupledSinkVehicleHandover`/`FixedTransitionHandoverHandler`); **#210** connection-loss robustness (PR #264, WebSocket heartbeat + reconnect); **#212** live server data in the UI (PR #263).
- **Worlds**: **#221** fetch worlds (PR #256), **#222** selection template (PR #235), **#223** create world (PR #255), **#224** default template (PR #252), **#227** world editor (PR #254, new `src/worldeditor/`) + **#230** world editing (PR #242), **#231** delete world (PR #261), **#241** tile selection.
- **Multi-table UI**: **#211** `ServerMenuScene` (PR #245), **#219** menu globe/text (PR #220), **#232** main-menu adaptation (PR #251).
- **Detection**: **#213** ArUco table rectification (PR #244, new `src/imageprocessing/ArucoTableRectifier.kt`), **#214** polyline smoothing/caching (PR #247, new `src/geometry/PolylineSimplifier.kt`).
- **Sound** (Zusatzfeature, epic #7-adjacent): **#226** (PR #234, new `src/audio/`).
- **Statistics rating rework**: **#228** (PR #237).
- **Marketing video finalisation** (Lastenheft §4): **#215/#216/#217** (no PR) → **#218** (PR #259), **completing the full video line** #101→#145+#148→#215/#216/#217→#218.
- Cross-team data format **#225** (closed, no PR). Carried-over **#153** congestion hardening (PRs #240/#246), **#189** (PR #238), **#99** (no PR), **#182** (PR #262, issue closed 2026-07-07). New UI bugs **#248/#249** also closed (no PR).
- **Codebase restructure**: `src/domain/Simulation.kt` split into `src/simulation/` (`Simulation` + 7 managers); new `src/config/ConfigManager.kt`. See `design.md` for the full breakdown.
- Only **#229** (Teamscale findings) survived from the Inkrement-5 era.

### Currently open — Inkrement-6 backlog ("Bug-free release", Lastenheft §5.1 Inkrement 6)

23 new issues **#266–#288**, filed 2026-07-07/08 immediately after the Inkrement-5 release, almost entirely without labels or triage state (only **#266** `bug`, **#267** `fix`). Themes: bug fixes (#266, #268, #269, #281), UI polish (#270, #273, #275, #278, #280, #282, #284, #288), hardware/detection follow-ups (#272, #283, #285, #286), a Raspberry Pi performance item (**#276** — directly serves NFR6), documentation (**#271** README), a sound follow-up (#287), and requirements/coverage sweeps (**#277**, **#279**). Carried over from Inkrement 5: **#229** (Teamscale). See `devprocess.md` for the full table.

### Lastenheft requirements **without** a tracking issue yet

Explicit Lastenheft §3.4 / §5.1 obligations still "not started, not on the board". When the PO opens issues, they belong under the listed epic. *(Multi-table server coupling is now complete; configurable speed-up #144 and detailed statistics #142/#143 were completed earlier; what remains untracked:)*

- Deterministic simulation (identical trace from identical inputs) → epic #8. More concrete a gap now given genuinely concurrent network/audio I/O alongside the simulation tick.
- Full table-only control (no mouse / keyboard at the table) → epic #6 (Inkrement 4, still open).
- Projector calibration / fullscreen / camera-projector homography → epic #5 / #9.
- 60 FPS / 1000 vehicles performance verification on RPi 5B 2 GB → epic #8. **Now tracked** as Inkrement-6 Issue **#276**.

### Repository defects not bound to a specific issue

- ~~`Level.vehicleTypes` is unimplemented~~ — closed (PR #65 / #68); released to `main` with Inkrement 2.
- ~~`// TODO: Load Level 1-8` at `src/scenes/LevelMenuScene.kt`~~ — resolved by **PR #77** via `PredefinedLevelCatalog`.
- ~~`build.gradle.kts` references a non-existent `src/commonMain/res`~~ — source-set wiring adjusted by **PR #70**.
- ~~I1-placeholder level completion~~ — replaced by the Issue-#56 intermediate criterion (`allSourceConfigurationsCompleted()`); the Lastenheft final criterion is now implemented by #93 (`congestionDetected`) and the per-level criteria #103, both closed and released.
- ~~Junction routing is **partial**~~ — resolved by #85 (PR #139): `src/domain/StreetPathFinder.kt` does Dijkstra shortest-path routing with congestion-aware diversion, superseding the old `Level.findNextStreetTowardTarget` heuristic.
- `.github/workflows/deploy-js.yml` references a JS target disabled in `build.gradle.kts`.
- KorGE template defaults: `id = "com.sample.demo"`, `rootProject.name = "korge-hello-world"`.

## Functional Requirements

Numbered for traceability. Each `[L §x]` references the Lastenheft; `[#y]` references a GitHub issue; `[code: …]` marks an existing implementation; `[gap]` marks something not yet implemented.

1. **Load a level from JSON** conforming to the documented schema. `[L §3.3]` `[#13 done]` `[code: LevelSerializer]`
2. **Save the current level to JSON** with deduplicated colour and vehicle-type tables. `[L §3.3]` `[#22 done]` `[code]`
3. **Separate user-saved levels from pre-defined ones** in the folder layout. `[#40 closed on dev — PR #70]` `[#48 closed on dev — PR #77]`
4. **Load up to 8 pre-defined levels** in the level menu. `[#43 closed on dev — PR #77]` `[code: PredefinedLevelCatalog]`
5. **Validate a level** against Lastenheft §3.2. `[L §3.2]` `[#19 done]` `[code: Level.validate()]`
6. **Network state** is one of EMPTY / INCOMPLETE / COMPLETE. `[L §3.1]` `[code: NetworkState]`
7. **Display the recognised network on the projection surface within ≤ 1 s** of a layout change. `[L §3.4]` `[#53 live-camera ingestion closed; ≤ 1 s projection latency still a gap — no issue]`
8. **Construction phase** allows arbitrary intermediate states; **simulation phase** requires a valid network. `[L §1 + §3]` `[code: SimulationState]`
9. **Detect straight roads on elevation 0** from an image. `[L §3 + §5.1 I1]` `[#15 done]`
10. **Detect curves and crossings** as polylines / minimal-count street splits. `[L §3 + §5.1 I2]` `[#54 closed on dev — PR #80, skeleton-graph segmentation + circumcircle roundabout detection]`
11. **Ingest the layout from a live camera.** `[L §3]` `[#53 closed; #157 camera integration closed — PR #180/#171; #146 wiring closed — PRs #187+#192; #213 ArUco rectification closed — PR #244; #182 detection-correction open (fix PR #200 closed unmerged)]`
12. **Surface modelling errors visibly** (e.g. coloured highlighting). `[L §3.3]` `[#95 closed on dev]`
13. **Configurable spawn rhythm per (VehicleType, Color)**: spacing (s), initial speed (km/h); per-type max speed (km/h), max acceleration (m/s²), length (m), adhesion coefficient μ, graphic reference. `[L §3.1]` `[#46 closed; full editing done — source configurations via PR #84/#90 (#52), vehicle types via PR #87 (#51, VehicleConfigurationScene + VehiclePreview)]`
14. **Mandatory vehicle types**: Auto (4 m), Lastwagen (8 m), Motorrad (2 m). `[L §3.1]` `[code: model supports it; concrete catalogue Unknown]`
15. **Each source colour has exactly one matching target colour.** `[L §3.1, §3.2]` `[code: enforced by Level.validate()]`
16. **Vehicles take the shortest Euclidean path and divert on congestion.** `[L §3.3]` `[#85 closed — PR #139, `src/domain/StreetPathFinder.kt` Dijkstra router; Simulation switches to a longer route only when the shortest is congested]`
17. **Curve-radius speed limit** at every inner polyline waypoint **and at every connection node along the route**: `r = abc / (4A)`, `V_max^i = min(V_max, √(μ·g·r))`, `g = 9.81 m/s²`. For inner waypoints the triangle uses (prev, curr, next) from the street's own polyline; for connection nodes it uses (last point of the current street, the node, first point of the next street along the deterministic route). Source/target endpoints use the global vehicle max. Vehicles brake in time before any upcoming curvature point with a lower `V_max_i` and re-accelerate afterwards. `[L §3.3]` `[Issue #91 closed on dev — `src/domain/CurveSpeed.kt` + look-ahead in `Simulation.accelerateVehicles`]`
18. **Acceleration and braking obey vehicle-type maxima**; speed never exceeds the vehicle-type max. `[L §3.3]` `[code: acceleration cap + pre-waypoint braking in `Simulation.accelerateVehicles` (Issue #91)]`
19. **Minimum distance ≥ 2 m**; collisions to be avoided. `[L §3.3]` `[#105 closed — PR #137, `MIN_DISTANCE_M = 2.0` + `speedCeilingForLeadingVehicle` + hard clamp; duplicate #86 closed]`
20. **Zipper-merge** at junctions; right-before-left, roundabouts and traffic lights are optional. `[L §3.3]` `[#92 closed — PR #135, merge stop-line / round-robin right-of-way in Simulation.kt]`
21. **Run-failure detection** on Verstopfung / Verklemmung. `[L §3.3]` `[#93 closed — PR #140, `Simulation.congestionDetected` (30 %-speed-drop + wait-time deadlock); hardening tracked by open #153]`
22. **Source-configuration validity check on load** (spawn rhythm must be satisfiable without collisions). `[L §3.3]` `[#97 closed on dev — PR #133]`
23. **Concurrent simulation of multiple sources / configurations.** `[L §3.3]` `[#55 + #56 done — PR #67 / #83]`
24. **Intermediate level-completion criterion (Inkrement 2)**: at least one vehicle from each source configuration reaches a target. `[#56 done — PR #83; Simulation.allSourceConfigurationsCompleted()]`
25. **Final level-completion criterion (Inkrement 3)**: per-level success criteria + Verstopfung / Verklemmung failure. `[L §3.3]` `[#103 closed — PR #129 (per-level success criteria); #93 closed — PR #140 (`congestionDetected`); together they supersede the #56 intermediate criterion]`
26. **Show results and statistics**: success/failure/Verstopfung in Inkrement 3; detailed statistics in Inkrement 4; comparison between alternative networks at the final delivery. `[L §3.3]` `[#98 success overlay closed — PR #126; #104 traffic volume / Street.utilization closed — PR #131; detailed statistics closed — #142 collect (PR #167) + #143 display (PR #188), §5.1 deliverable complete]`
27. **Deterministic simulation** — same network ⇒ same trajectory. `[L §3.3]` `[gap; no determinism test today]`
28. **Configurable simulation speed-up** (e.g. 10×, user-settable). `[L §3.3]` `[#144 closed — PR #164, speed factor in GameState + ControlButtons]`
29. **Save / load networks** for repeatable demos. `[L §3.3]` `[#22 done; #48 closed on dev — PR #77 separates user saves into SavedNetworksScene]`
30. **Tutorial level** auto-loaded at startup, with explanatory texts, simple 1-source/1-sink, level menu reachable after first arrival. `[#44 closed on dev — PR #75]`
31. **At least 4 increasingly complex levels.** `[#45 closed on dev — PR #78, 8 levels of increasing complexity]`
32. **Pause menu** with a gear icon entry point. `[#49 closed via PR #61 — completed in spirit by the later PR #82 chain]`
33. **Sub-menu for vehicle types and source configurations** (overview + per-element edit). `[#50 + #51 + #52 done — PR #82 (overview) + PR #87 (VehicleConfigurationScene) + PR #84/#90 (SourceConfigDetailScene)]`
34. **Sub-menu for user-saved networks** (list, delete, load). `[#48 closed on dev — PR #77, SavedNetworksScene + filter/sort/delete]`
35. **Colour-blind mode toggle** in the menu, with palette adjustments for the most common forms of colour blindness. `[#59 closed on dev — PR #76]`
36. **Consistent control-element behaviour**: grey-disabled vs black Start button depending on network completeness; Pause button while running; gear icon top-right; close button in every menu. `[#47 closed on dev — PR #66 (ControlButtons) + PR #74 (LevelScene refactor)]`
37. **Unified 1:250 scale + 9:5 aspect ratio** (width:height, per `GameAreaScale.ASPECT_RATIO_W/H = 9/5`) for the play area; virtual coordinates rescaled at runtime; save/load size-independent. `[#41 done — PR #88; GameAreaScale.kt full coordinate/unit utility, rendering on the in-game coordinate system]`
38. **Optional textual config file for global constants** (window size, scaling factors, paths, image-processing settings). `[#42 closed — PR #197, `src/config/ConfigManager.kt`; constants also externalised via #156, PR #186]`
39. **Full table-only control** (no mouse/keyboard at the table). PC-mode allows mouse/keyboard, but constructing networks at the PC is **not** required. `[L §3.3]` `[gap — Inkrement 4, still untracked]`
40. **Multi-table coupling via a customer-provided server.** `[L §2 + §3.3]` `[#203–#212 closed — Inkrement 5, PR #265; new src/network/ package: TrafficSimRestClient (REST) + TrafficSimWebSocketClient (WebSocket) against the coupling server; VehicleWebSocketConverter for the agreed {color, type, speedMetersPerSecond} payload (#205/#225); VehicleTargetHandoverPort + CoupledSinkVehicleHandover (outgoing, #208) / FixedTransitionHandoverHandler (incoming, #209); TransitionAssignment for sink→source mapping (#206); ServerConnectionManager for the connection state machine + per-world tile claiming (#210, #241)]`
41. **Promotional video** of 60–90 s. `[L §4]` `[fully closed — script #101 (Inkrement 3), footage #145 + rough cut #148 (PR #196, Inkrement 4), re-shoots/fine cut/voice-over #215/#216/#217 (Inkrement 5), final export #218 (PR #259, Inkrement 5); epic #7]`
42. **Bridges for elevation 1 and 2** — 3D models + ≥ 2 prints each. `[#57 + #58 closed — PR #122/#127, models in res/3dprinting/]`
43. **Detect and project elevations 1 and 2** (multi-level street networks). `[L §3 + §5.1]` `[#114 closed — PR #138, StreetDetection.kt multi-level rewrite + res/road/Road5.png]`
44. **Per-level weather condition** stored in the level file (dry / wet / snowy). `[#160 closed — PR #165, WeatherCondition enum + LevelSerializer; adhesion-coefficient effect #158 (PR #170) and UI editing #161 (PR #176) closed]`
45. **Server-hosted world management** (create / fetch / edit / delete a multi-table network definition). `[L §2 + §5.1]` `[#221/#223/#224/#227/#230/#231/#241 closed — Inkrement 5; new src/persistence/WorldTemplate.kt (blank-world generator, #224) + src/worldeditor/ (editing session, #227)]`
46. **Table rectification via ArUco markers** for consistent camera cropping regardless of table/camera placement. `[#213 closed — PR #244, Inkrement 5, ArucoTableRectifier]`
47. **Detected-network smoothing / caching** so re-detection doesn't reintroduce camera jitter. `[#214 closed — PR #247, Inkrement 5, PolylineSimplifier]`
48. **Optional: driving / weather / congestion sound** (laptop mode only — excluded on the Raspberry Pi per NFR5). `[optional]` `[#226 closed — PR #234, Inkrement 5, new src/audio/ package]`

## Non-Functional Requirements

Numbered for traceability.

NFR1. **High visual quality and attractiveness.** `[L §3.4]`

NFR2. **Latency ≤ 1 s** for recognition → interpretation → projection on a layout change. `[L §3.4]`

NFR3. **Robustness** to mild shifts, varying ambient light, perspective distortion, brief recognition errors; ideally tolerates hands or occluding objects. `[L §3.4]`

NFR4. **Maintainability / extensibility**: image processing, domain model, simulation and projection separable. `[L §3.4]`

NFR5. **Hardware fit**: laptop **or** Raspberry Pi 5B 2 GB; no audio on the Pi. `[L §3.4]`

NFR6. **Performance**: smooth 60 FPS at up to 1000 vehicles on Raspberry Pi 5B 2 GB. `[L §3.4]`

NFR7. **Determinism**: identical network + inputs ⇒ identical simulation trace. `[L §3.3]`

NFR8. **Accessibility**: colour-blind mode (`[#59 done — PR #76]`). Most-common forms prioritised.

NFR9. **Language / runtime**: Kotlin (current at project start), JVM 21 in CI. `[L §2 + repository CI]`

NFR10. **CI quality gate**: `./gradlew jvmTest` passes on every push to `main`. `[repository: .github/workflows/gradle.yml]`

NFR11. **Consistent 1:250 scaling and 9:5 aspect ratio** (width:height), independent of play-area size; objects in virtual coordinates rescaled at runtime. `[#41 done — PR #88, GameAreaScale.kt]`

## Acceptance Criteria

From Lastenheft §5 (Auslieferungsumfang and demo bullets) plus §5.1 (Inkrement minima). Concrete test procedures are owned by the PO.

### Delivery (Lastenheft §5)

- Working software fulfilling all requirements above.
- A **test report** documenting verification of functional and non-functional requirements.
- A **set of physical road elements** sufficient to model a road network — see issues #12, #57, #58.
- A **promotional video** — epic #7.

### Final demo (Lastenheft §5)

- Load a prepared level and display it understandably on the projection table.
- Recognise and interpret roads + junctions in the construction phase correctly (#54).
- Visibly highlight inconsistencies and illegal connections.
- Validate a complete network and transition to the simulation phase.
- Spawn vehicles per source configuration and route them to matching-colour targets (#55, #56).
- Demonstrate Verstopfung / Verklemmung on a sub-optimal network.
- Show results and statistical summaries of a simulation run.

### Sprint minima (Lastenheft §5.1) — verified status

| Inkrement | Lastenheft minimum | Status (verified) |
|---|---|---|
| 1 | Domain model + persistence format + load + visualise prepared level; straight-road detection on level 0; save initially recognised networks; simple real-time simulation of ≥1 vehicle. | **Done & released** (PR #38 "Inkrement 1" merged 2026-05-11). |
| 2 | Configurable source/vehicle properties; spawn per configuration; recognise + save full network (curves + junctions); multi-vehicle real-time simulation without complex rules. | **Done & released to `main` via PR #100 (`dev → main`, 2026-05-25).** Closed: #46 (PR #68), #47 (PR #66 + #74), #40 (PR #70), #39 (PR #73), #44 (PR #75), #59 (PR #76), #43 + #48 (PR #77), #45 (PR #78), #50 (PR #82), #51 (PR #87), #52 (PR #84 + #90), #54 (PR #80), #55 (PR #67), #56 multi-source completion criterion (PR #83), #49 pause menu; plus PR #81 (scroll fix). #41 uniform scaling was the last residual, finished post-release via PR #88. |
| 3 | All Lastenheft §3.3 simulation rules; visible modelling-error highlighting; simple result display; promotional-video script. | **Done & released to `main` via PR #141 "Sprint 3" (`dev → main`, 2026-06-08).** Full §3.3 rule set closed: curve-radius **#91**, right-of-way / zipper-merge **#92** (PR #135), 2 m distance + collision avoidance **#105** (PR #137), traffic volume **#104** (PR #131), shortest path **#85** (PR #139, `StreetPathFinder`), Verstopfung **#93** (PR #140, `congestionDetected`), modelling-error highlighting **#95**. Also closed & released: **#107**, **#106**, **#41**, **#94**, **#97**, success overlay **#98**, **#102**, **#103**, challenge mode **#108**/**#117**, **#110**, junction transitions **#119**, bridge models **#57** + physical-bridge print **#58**, ARGB fix **#71**, **#79**, **#109**, promotional-video script **#101**, hint bar **#96**, multi-level detection **#114** (PR #138), `.exe` packaging **#132** (PR #136). |
| 4 | Detailed statistics; configurable speed-up; full table-only control; ad rough cut. | **Done & released to `main` via PR #202 "Inkrement 4" (`dev → main`, 2026-06-22, tag `Inkrement-4`).** **§5.1 core complete:** detailed statistics **#142** collect (PR #167) + **#143** display (PR #188); configurable speed-up **#144** (PR #164). Also closed & released: weather **#160/#158/#161**, auto-start **#159**, the camera line **#53/#157/#146** (PRs #180/#171, #187+#192), constants **#156** + config file **#42** (PR #197), robustness **#154/#155/#149**, result-flow **#151/#166/#150**, video rough cut **#145+#148** (PR #196), coverage **#147** (PR #199), bugs **#162/#163/#184**, hardware **#177**, visual **#72**; CI on a self-hosted runner (PR #190). **#153/#189** closed shortly after the release (on `dev` only); **#99/#182** still open. Still untracked: deterministic simulation, full table-only control. |
| 5 | Multi-table server coupling; final cut + audio-mixed promotional video. | **Done & released to `main` via PR #265 "Inkrement 5" (`dev → main`, merged 2026-07-06T21:50:52Z, tag `Sprint5`; ⚠ the `Inkrement-5` git tag itself is mistagged at the old `Inkrement-4` commit).** Entire minimum set closed: server interaction/config **#203/#204**, transfer & coupling **#205–#210/#212**, worlds **#221/#222/#223/#224/#227/#230/#231/#241**, multi-table UI **#211/#219/#232**, ArUco **#213**, smoothing **#214**, sound **#226**, statistics rating **#228**, data format **#225**, video finalisation **#215–#218** (completes the full §4 line). Carried-over **#99/#182/#248/#249** closed too. Only **#229** survived. |
| 6 | Bug-free release fulfilling every requirement. | **Started, not released.** 23-issue backlog **#266–#288** opened 2026-07-07/08, almost entirely untriaged (see "Currently open — Inkrement-6 backlog" above). |

## Risks and Assumptions

- ~~**Risk — `Level.vehicleTypes` `TODO()`.**~~ Closed on `dev` (PR #65 / #68) and **released to `main`** with Inkrement 2 (PR #100).
- ~~**Risk — `#46` revert/rework chain in flight.**~~ Closed: chain ends with PR #68 "Final Fix" merged 2026-05-19 10:17 UTC. `VehicleType.graphicRef` is present on `dev` and wired through persistence and `Assets`.
- ~~**Risk — `dev / main` divergence.**~~ **Not a risk per wiki branching convention.** `main` is updated by Inkrement release merge only (at least once per Inkrement).
- **Note — Branch-convention excursion #62 / #64.** PR #62 briefly bypassed the convention by merging directly to `main`; PR #64 reverted it on the same day. End state honours the wiki convention again. Recorded incident for the test-report deliverable; no follow-up action required.
- **Risk — Performance on RPi 5B 2 GB.** 60 FPS / 1000 vehicles is ambitious. JavaCV ARM64 support and KorGE throughput on the Pi remain Unknown.
- **Risk — JavaCV ARM64 natives.** `org.bytedeco:javacv-platform:1.5.10` does not guarantee Pi support out of the box.
- **Risk — Determinism (NFR7) needs to be designed in early.** No issue tracks it; retrofitting after routing, RNG and threading land will be expensive.
- **Risk — Camera ingestion + projector calibration are unowned.** Issue #53 exists for the camera side; no issue exists for projector calibration / homography.
- ~~**Risk — Lastenheft §3.3 rules partly without issues.**~~ **Resolved:** the entire §3.3 rule set is now closed and released (curve-radius #91, right-of-way #92, 2 m distance #105, traffic volume #104, routing #85, Verstopfung #93, error highlighting #95). Configurable speed-up #144 and detailed statistics #142/#143 are now tracked too. Still **untracked**: deterministic simulation (NFR7), full table-only control, projector calibration. (The earlier #105 / #86 duplicate is resolved — #86 was closed as a duplicate of #105.)
- ⚠ **New — `Inkrement-5` git tag mistagged.** The tag resolves to the same commit as `Inkrement-4` (`e27c676`) rather than the actual release commit `60e27e5` (correctly marked by `Sprint5`). Flagged for the team; not corrected by the agent (git tag changes are a team decision).
- ⚠ **New — network robustness is unproven beyond Inkrement-5 acceptance testing.** The heartbeat/reconnect logic in `TrafficSimWebSocketClient`/`ServerConnectionManager` (Issue #210) is new; a coupling-server outage or flaky network during an Inkrement-6 demo is a plausible failure mode not yet explicitly rehearsed.
- ⚠ **New — two overlapping connection-state enums.** `src/domain/ConnectionState` and `src/network/NetworkConnectionState` track similar lifecycles in different packages; the relationship between them is undocumented.
- ⚠ **New — determinism (NFR7) is more concrete a risk post-Inkrement-5.** The simulation tick now coexists with genuinely concurrent network I/O and audio coroutines; retrofitting determinism will be more expensive the longer it's deferred.
- **Risk — Vendored `:deps` sub-project** is opaque; reproducibility depends on the checked-in binaries.
- **Risk — Disabled-but-present `deploy-js.yml`** may either fail or no-op on every push.
- **Note — Wiki comment-style interpretation.** The wiki rule "doc-strings statt `//`" is best read as targeting API / class / function documentation. Whether ad-hoc inline `//` markers inside function bodies are also covered is a matter of team interpretation and should be clarified if a stricter reading is intended.
- **Assumption — `spec/Lastenheft.pdf` is canonical** and overrides this PRD on any conflict.
- **Assumption — Default ai-dev-process layout accepted** with one explicit deviation: GitHub Issues remain canonical. Per `devprocess.md`: the PO is responsible for feature / story issues, while team members may file bug issues per the wiki Definition of Done; the agent does not create issues.
- **Assumption — Documentation languages stay mixed**: Lastenheft + README + GitHub issues + wiki in German; ai-dev-process artefacts + KDoc in English. Team-to-agent communication is German (`devprocess.md` "Communication Language").
- **Wiki is canonical for team conventions.** Any conflict between this PRD and the wiki on process matters (branching, DoD, meetings, comment style) is resolved in favour of the wiki.
