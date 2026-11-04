# Glossary

> **Last synced** with `dev`/`main` @ commit `60e27e5` on 2026-07-08 (`[dev-merge-9]`, deep content sync). Inkrement-5 vocabulary (server coupling, worlds/tiles, ArUco, sound) is now covered — see the new "Server coupling & multi-table" and "Sound" sections below, plus the ArUco entry in "Recognition pipeline". Refresh triggers: every PR touching §3.3 simulation rules; every Inkrement release merge; every `[dev-merge]` reconciliation pass. See "How to extend and maintain this glossary" at the bottom of this file for the full status-column maintenance rules.

> Domain vocabulary for the STP-2026-Team-D interactive projection-table system. Mappings between **Lastenheft / wiki German**, **codebase / KDoc English**, and where each term lives in the repository.
>
> **Authority:** the [`Lastenheft`](../spec/Lastenheft.pdf) defines the canonical product vocabulary; the [`class_diagram.md`](../spec/class_diagram.md) defines the canonical code vocabulary. When this glossary and either source diverge, the source wins — please update this glossary in the same PR.
>
> **Audience:** humans on the team, and skills (`to-prd`, `tdd`, `triage`, `to-issues`, the orchestrator) which reference *"the project's domain glossary vocabulary"*. Use these terms exactly as written when naming tests, modules, scenes, or prose.

## Core concepts

| German (Lastenheft / Wiki) | English (Code / KDoc) | Where | Notes |
|---|---|---|---|
| Straßennetz | road network / level | `src/domain/Level.kt` | The aggregate root of one playable scenario. |
| Verkehrssimulation | traffic simulation | `src/domain/Simulation.kt` | The tick loop that advances vehicles. |
| Konstruktionsphase | construction phase | `SimulationState.CONSTRUCTION` | Free editing; partial / illegal networks allowed. |
| Simulationsphase | simulation phase | `SimulationState.SIMULATION` | Network must be valid; traffic runs. |
| Pause | paused | `SimulationState.PAUSED` | Simulation paused, can be resumed. |
| Kein Level geladen | no level loaded | `SimulationState.NO_LEVEL` | Startup / between-level state. |
| Inkrement | Increment / release | Lastenheft §5.1 | Sprint-aligned delivery unit (1–6). Reaches `main` via the Inkrement release merge. |
| Abnahme | Acceptance | Lastenheft §5 | The final delivery: working software + test report + physical road elements + promotional video. |
| Werbevideo | promotional video | Lastenheft §4 | 60–90 s; script closed in Inkrement 3 (Issue #101), shot in 4, final cut in 5. |
| Level-Abschlusskriterium | level-completion criterion | `Simulation.levelCompleted` / `allSourceConfigurationsCompleted()` | Implemented **intermediate** criterion (Issue #56): the level completes once at least one vehicle from **each** source configuration reaches a target. The Lastenheft final criterion (no Verstopfung) is implemented by #93 (`congestionDetected`, PR #140) and the per-level success criteria #103 (PR #129), both closed. |
| Challenge-Modus | challenge mode | `src/domain/ChallengeMode.kt` (Issue #108, PR #115; tutorial explanation #117, PR #128) | A challenge game mode; **closed on `dev`** with `test/ChallengeModeTest.kt` and a `MenuScene` entry point. |

## Network topology

| German | English | Where | Notes |
|---|---|---|---|
| Knoten | node | `src/domain/Node.kt` | Abstract base. Carries `position: Point` and `elevation: Int ∈ {0, 1, 2}`. |
| Quelle / Quellknoten | source / `SourceNode` | `src/domain/SourceNode.kt` | Spawns vehicles per its `configurations`. Has an `orientation: Angle` (0° = active side up). |
| Senke / Zielknoten | target / sink / `TargetNode` | `src/domain/TargetNode.kt` | Accepts vehicles of a matching `Color`. Has an `orientation`. |
| Verbindungsknoten | connection / `ConnectionNode` | `src/domain/ConnectionNode.kt` | Routing-only node; no spawn / sink semantics. |
| Straße | street | `src/domain/Street.kt` | Directed edge with a `path: List<Point>` (waypoints). |
| Polyline / Wegpunkte | waypoints | `Street.path` | Discrete points the vehicle follows. Curve-radius limits apply at inner waypoints (Lastenheft §3.3). |
| Routenwahl | routing | `Level.findNextStreetTowardTarget(node, color)` | At a `ConnectionNode`, picks an outgoing street that has a path to a matching-colour target (fallback: first outgoing). Colour-aware target-seeking; not yet shortest-path (Issue #85). |
| Ebene | elevation | `Node.elevation` | Integer 0, 1, or 2. Used for overpasses / bridges. Recognition + projection of elevations 1/2 is Issue #114. |
| Brücke | bridge | `res/3dprinting/` (Issues #57, #58 closed — PR #122/#127); #114 closed (PR #138) | 3D-printed model for elevation 1 / 2. STL models (`bridge_level1.stl`, `bridge_level2.stl`) + gcode `.3mf` print files landed on `dev`; physical-bridge print (#58) closed; elevation-1/2 recognition+projection (#114) closed via PR #138; bridge driving/rendering fixes #149/#150 closed via PRs #191/#198. |
| Netzwerk-Zustand | network state | `NetworkState` | One of `EMPTY`, `INCOMPLETE`, `COMPLETE`. |

## Vehicles & spawn configuration

| German | English | Where | Notes |
|---|---|---|---|
| Fahrzeug | vehicle | `src/domain/Vehicle.kt` | One simulated car / truck / motorbike instance. |
| Fahrzeugtyp | vehicle type | `src/domain/VehicleType.kt` | `(name, maxSpeed, length, adhesionCoefficient, acceleration, graphicRef)`. Mandatory: Auto (4 m), Lastwagen (8 m), Motorrad (2 m). |
| Quellkonfiguration | source configuration / `SourceNodeConfiguration` | `src/domain/SourceNodeConfiguration.kt` | Spawn rhythm per `(VehicleType, Color)`: `generationSpeed`, `initialVehicleSpeed`. |
| Maximalgeschwindigkeit | max speed | `VehicleType.maxSpeed` | km/h externally, m/s internally. |
| Beschleunigung | acceleration | `VehicleType.acceleration` | m/s². |
| Haftungskoeffizient | adhesion coefficient (μ) | `VehicleType.adhesionCoefficient` + `Level.adhesionCoefficientModifier` / `Level.effectiveAdhesionCoefficientModifier()` | Used in `V_max^i = min(V_max, √(μ · g · r))`. The effective modifier now folds in the level's `WeatherCondition` via `WeatherCondition.adhesionMultiplier` (DRY 1.0 / WET 0.7 / SNOWY 0.4) — **#158 closed (PR #170)**; weather affects braking + acceleration too, not just curve limits. |
| Grafikreferenz | graphic reference | `VehicleType.graphicRef` | Filename in `res/images/` (e.g. `car.png`, `truck.png`, `motorcycle.png`). Restored by PR #68. |
| Farbe | colour | `src/domain/Color.kt` | `name + RGBA`. A source spawns vehicles of one colour; a target accepts the matching colour. |

## Simulation rules (Lastenheft §3.3)

| German | English | Status | Notes |
|---|---|---|---|
| Kurvenradius / Geschwindigkeitsbegrenzung | curve-radius speed limit | `src/domain/CurveSpeed.kt` + look-ahead in `Simulation.accelerateVehicles` | `r = abc / (4A)` (circumcircle), `V_max^i = min(V_max, √(μ · g · r))`, `g = 9.81 m/s²`. Applied at every inner polyline waypoint **and** at every connection node along the deterministic route (triangle: last point of current street, node, first point of next street). Look-ahead is bounded by the `V_max² / (2·a)` braking distance and a visited-street cycle guard. |
| Mindestabstand (2 m) | minimum distance | **released** — Issue #105 (PR #137) | A follower keeps ≥ 2 m clear distance (bumper-to-tail) to the vehicle ahead on the **same** street and brakes to the leader's speed once inside 2 m. Since the Inkrement-5 restructure this lives in `src/simulation/VehicleAccelerationManager.kt` (`speedCeilingForLeadingVehicle`) and `VehicleMovementManager.kt` (`distanceAllowedByLeadingVehicle`, `vehiclesInMovementOrder`), with `clearDistanceToLeaderM` in `RouteUtils`/`VehicleUtils`. Canonical constants now live in `GameSimulationConfig`: `MIN_DISTANCE_M = 2.0` (unchanged), `LEADING_VEHICLE_LOOKAHEAD_M = 10.0` (⚠ changed from 20.0 during Inkrement 5), `LEADING_VEHICLE_BRAKE_MULTIPLIER = 5.0` (unchanged). The duplicate #86 was closed as `DUPLICATE`. |
| Kürzester Weg | shortest path | **released** — Issue #85 (PR #139) | `src/domain/StreetPathFinder.kt` performs Dijkstra over the street graph (`findDrivingPath`, `findDrivingRoutes` returning `DrivingRoute(target, streets, length)` via a `PriorityQueue`). Since Inkrement 5, `src/simulation/RouteManager.kt` additionally reroutes a vehicle to a less-congested alternative once its current route exceeds `GameSimulationConfig.ROUTE_SWITCH_CONGESTION_THRESHOLD = 0.5` (bounded by `MAX_ROUTE_OPTIONS = 10`). Tests: `StreetPathFinderTest`. |
| Verkehrsaufkommen / Auslastung | traffic volume / utilization | **released** — Issue #104 (PR #131) | Runtime `Street.utilization` (`0.0` free … `1.0` blocked), now computed by `src/simulation/StreetUtilizationManager.kt`; combines a vehicle-density factor and a speed-deficit factor weighted by `GameSimulationConfig.UTILIZATION_DENSITY_WEIGHT`/`UTILIZATION_SPEED_WEIGHT` (0.5 each, unchanged), clamped to `[0,1]`. Tests: `StreetUtilizationTest`. |
| Reißverschlussprinzip | zipper-merge | **released** — Issue #92 (PR #135) | At a merge `ConnectionNode` (≥ 2 incoming streets) only the `mergeOccupant` may cross; waiting approaches are served in round-robin order via `mergeRightOfWay`, granting at most one vehicle onto the outgoing street per tick. Since Inkrement 5 this lives in `src/simulation/MergeManager.kt` (`updateMergeGrants`) and `VehicleAccelerationManager.kt` (`speedCeilingForMerge`). Tests: `tick_mergeNode_*` in `SimulationTest`. |
| Vorfahrtsregeln / rechts-vor-links | right-of-way / right-before-left | **released** — Issue #92 (PR #135) | Round-robin right-of-way at merge nodes (`mergeRightOfWay`/`mergeOccupant`/`mergeGrant`, `mustYieldAtMerge`/`mayEnterMerge`/`enterMerge`/`hasClearedMerge`); yielding vehicles hold at a fixed front-bumper stop line (`STOP_LINE_FRONT_OFFSET_M`, translated to centre position by half vehicle length), and the next approach is released only after the previous occupant's rear bumper has cleared the node plus `MERGE_CLEARANCE_M`. `MERGE_START_SPEED_KMH` keeps granted restarts responsive. **Constants retuned during the Inkrement-5 congestion-hardening pass (Issue #153)**: `STOP_LINE_FRONT_OFFSET_M` 0.7→**3.0** m, `MERGE_START_SPEED_KMH` 7.0→**10.0** km/h (`MERGE_CLEARANCE_M = 0.5` unchanged); canonical values now in `GameSimulationConfig`. Lastenheft marks geometric right-before-left as optional; #92 implements turn-taking right-of-way at junctions. |
| Verstopfung | gridlock / congestion | **released** — Issue #93 (PR #140), **hardened** — Issue #153 (PRs #240/#246) | Run-failure / simulation-abort criterion, now `src/simulation/CongestionDetector.kt`. Three criteria set `Simulation.congestionDetected`: (1) running-average speed drop, (2) a vehicle waiting longer than `GameSimulationConfig.WAIT_TIMEOUT_S = 10.0` s at a merge stop line (**new** in the #153 hardening), (3) a source-configuration spawn backing up. Surfaced via `GameState` + an abort message in `LevelScene`, and via the Inkrement-5 congestion horn (`SimulationSoundController`). |
| Verklemmung | deadlock | **released** — Issue #93 (PR #140) | Run-failure criterion; covered by the same `congestionDetected` wait-time path. |
| Erfolgskriterien | success criteria | **released** — Issue #103 (PR #129) | Per-level success criteria; relates to the implemented #56 intermediate criterion. |
| Determinismus | determinism | **gap** (no seeded RNG yet) | NFR7: identical inputs ⇒ identical trajectories. No tracking issue. |
| Simulationsgeschwindigkeit / -beschleunigung | simulation speed / speed-up | **released** — Issue #144 (PR #164) | Configurable simulation speed factor in `src/domain/GameState.kt` (`simulationSpeed: SimulationSpeed`), applied in `Simulation`, controlled via `src/ui/ControlButtons.kt`. Since Inkrement 5 the fixed multiplier set lives in `GameSimulationConfig.SIMULATION_SPEED_X1/X2/X5/X10_MULTIPLIER`. Tests: `SimulationSpeedTest`, `ControlButtonsTest`. |
| Statistiken | statistics | **done** — collect #142 (PR #167) + display #143 (PR #188), **rating reworked** — #228 (PR #237) | Detailed simulation statistics, the Inkrement-4 §5.1 core, now **complete** and since Inkrement 5 collected by `src/simulation/StatisticsCollector.kt` (`SimulationStatistics` data class + `TrafficFlowRating` enum `EFFICIENT`/`STABLE`/`OVERLOADED`, thresholds in `GameSimulationConfig.TRAFFIC_FLOW_*`). Display half #143 renders them after a run (PR #188); the rating logic itself was reworked in Inkrement 5 (#228). Earlier steps: success overlay #98 (PR #126), traffic volume / utilization #104 (PR #131). |
| Wetterbedingung | weather condition | **done** — persistence #160 + adhesion #158 + UI #161 closed | `src/domain/WeatherCondition.kt` enum `DRY` / `WET` / `SNOWY` (`DEFAULT = DRY`, `fromJsonValue`, `adhesionMultiplier`); stored per level as `Level.weatherCondition`, persisted via `LevelDto` / `LevelSerializer` (`weatherCondition` JSON key — PR #165). It drives the effective adhesion coefficient and overall driving behaviour (**#158**, PR #170), and the per-level weather is editable in the UI (**#161**, PR #176). |

## Recognition pipeline

| German | English | Where | Notes |
|---|---|---|---|
| Bilderkennung / Objekterkennung | image processing / detection | `src/imageprocessing/` | OpenCV / JavaCV pipeline. |
| Skelett-Graph | skeleton graph | `StreetDetection.kt` (PR #80) | Zhang–Suen-thinned road skeleton → graph segmentation → driving paths. |
| Kreisverkehr | roundabout | `StreetDetection.kt` (PR #80) | Direction detected via circumcircle fitting; `GameSimulationConfig.MIN_ROUNDABOUT_JUNCTIONS = 3`, `ROUNDABOUT_RADIUS_TOLERANCE = 80.0`. |
| Kamera-Ingestion | live camera ingestion | **closed** — #53 ingestion + #157 integration (PR #180/#171) + #146 wiring (PRs #187+#192) | Camera street ingestion #53, physical-camera integration #157 and the camera→detection wiring #146 are closed. Static images under `res/images/Road.png` + `res/road/Road{2..5}.png` remain as test fixtures. |
| Tisch-Rektifizierung / Marker-Erkennung | table rectification / marker-based rectification | **released** — Issue #213 (PR #244) | `src/imageprocessing/ArucoTableRectifier.kt` — detects the four ArUco corner markers (4×4/50 dictionary, one per table corner) and perspective-rectifies the raw camera frame to a top-down `GameAreaScale.DESK_W_PX × DESK_H_PX` rectangle, **before** `StreetDetection` runs. Replaced the earlier ad-hoc contour-based crop and closed most of the previously-untracked camera-calibration gap. |
| Polylinien-Glättung / Zwischenspeicherung | polyline smoothing / caching | **released** — Issue #214 (PR #247) | `src/geometry/PolylineSimplifier.simplifyPath(points, epsilon)` — Ramer–Douglas–Peucker simplification of the raw detected polyline before it becomes a `Street.path`; `epsilon` defaults to `GameSimulationConfig.PATH_SIMPLIFICATION_EPSILON = 3.0`. Invoked from `StreetDetectionIntegration`. |
| Straßenerkennungs-Korrektur | detection-correction bug | **released** — Issue #182 (PR #262, closed 2026-07-07) | A fresh detection-refinement attempt after the earlier fix (PR #200) was closed unmerged. |
| Projektion / Beamer | projection | (planned) | HDMI mini-beamer ≈ 500 lm. NFR2 latency budget: ≤ 1 s. |
| Projektionskalibrierung / Homographie | projector calibration / camera–projector homography | **gap** (no issue) | Needed before NFR2 can be honoured on real hardware. |

## Server coupling & multi-table (Lastenheft §2 / §3.3 / §5.1 Inkrement 5)

Inkrement 5's core deliverable — released via PR #265 "Inkrement 5" (tag `Sprint5`). All items below **released**.

| German | English | Where | Notes |
|---|---|---|---|
| Tisch-Kopplung | table / multi-table coupling | `src/network/` | The overall feature: several physical tables share one traffic network via a customer-provided coupling server. |
| Kopplungs-Server | coupling server | `traficsim.se.uni-kassel.de` (external, team-provided) | REST (`TrafficSimRestClient`) + WebSocket (`TrafficSimWebSocketClient`) endpoints; `ConfigManager.NetworkConfig` holds host/port/active flag. |
| Feld / Tisch | field / table | `FieldPropertiesDTO`, `TableTile` | A field is the server's notion of one table/tile within a world; `TableTile(id, occupiedByOtherTable)` is the client-facing view backing `ServerConnectionManager.tilesFor(worldId)`. |
| Kachel / Kachel-Belegung | tile / tile claiming | `ServerConnectionManager.claimTile/releaseTile`, `WorldPlaceholderScene` (Issue #241) | A team claims one grid tile of a world before playing on it; claim state is per-world, tracked locally and reflected on the server. |
| Welt | world | `WorldPropertiesDTO`, `WorldTemplate`, `WorldSelectionScene`/`WorldEditorScene` | A server-hosted multi-table network definition (columns × rows of tiles) that one or more tables jointly play. `WorldTemplate.createBlankWorld()` seeds a new world with a fixed border layout of source/target pairs and no streets (Issue #224). |
| Welt-Editor | world editor | `src/worldeditor/`, `WorldEditorScene`/`WorldEditScene` (Issue #227) | Session/state layer (`WorldEditorSession`, `WorldEditorRepository`, `EditorNodeRecord`, placement/validation helpers) behind the world-editing UI. |
| Quelle (Server-seitig) / neutrale Quelle | server-side source / neutral source | `SourceNode.kind: SourceNodeKind` (`LOCAL` / `SERVER_NEUTRAL`) | Distinguishes a player-placed source (`LOCAL`) from a server-provided neutral source at a coupled table edge (`SERVER_NEUTRAL`), plus the server-side `QuelleNode` DTO. |
| Übergang / Übergangs-Zuordnung | transition / transition assignment | `TransitionNode` (DTO), `TransitionAssignment` (domain), Issue #206 | Maps a coupled sink on one table to the partner table's source, so an outgoing vehicle knows where it re-enters the network. |
| Fahrzeugübergabe | vehicle hand-over | `VehicleTargetHandoverPort`, `CoupledSinkVehicleHandover` (outgoing, Issue #208), `FixedTransitionHandoverHandler` (incoming, Issue #209) | The mechanism by which a vehicle reaching a coupled sink is converted and sent to the server (`VEHICLE_DEPARTURE`), and an incoming vehicle from the server is injected at the mapped local source (`VEHICLE_ARRIVAL`/`VEHICLE_SOURCE`). |
| Transfer-Nachrichtenformat | vehicle transfer message format | `VehicleWebSocketConverter`, `HandoverVehicleProperties` (Issue #205, agreed cross-team via #225) | Agreed payload: `{ color: {r,g,b,a}, type, speedMetersPerSecond }`. Strict parsing — a malformed payload fails loudly rather than silently defaulting. |
| Verbindungszustand | connection state | `src/network/NetworkConnectionState` (`DISCONNECTED`/`CONNECTING`/`CONNECTED`/…), `src/domain/ConnectionState` | ⚠ Two similarly-named connection-state enums exist in different packages; the mapping between them is not yet documented — see `design.md` "Known Design Issues". |
| Robustheit bei Verbindungsabbruch | connection-loss robustness | `TrafficSimWebSocketClient` heartbeat (10 s keepalive, 30 s silence timeout), `ServerConnectionManager` reconnect, `ConnectionLossNavigation` scene (Issue #210) | Detects a silently-dropped WebSocket connection and offers a reconnect path in the UI. |

## Sound (Lastenheft §3.4-adjacent — explicitly excluded on Raspberry Pi)

| German | English | Where | Notes |
|---|---|---|---|
| Fahrgeräusche | driving sounds | `src/audio/SimulationSoundController`, `DrivingSoundType` (`CAR`/`TRUCK`/`MOTORCYCLE`) | Issue #226. Per-vehicle-type looped sound; volume scales with moving-vehicle count/intensity via `SimulationSoundPolicy`. |
| Wettergeräusche | weather ambience | `WeatherSoundType` (`SUNNY`/`RAIN`/`WINTER`), keyed off `WeatherCondition` | Issue #226. Ambient loop matching the level's current weather. |
| Hupe (bei Stau) | congestion horn | `SimulationSoundController` + `SimulationSoundPolicy` | Issue #226. Triggers on the *rising edge* of `Simulation.congestionDetected`, not continuously. |
| Audio auf Raspberry Pi | audio on Raspberry Pi | Lastenheft §3.4 (NFR5) | **Explicitly excluded** — sound is a laptop-mode enhancement only. |



## Scale & geometry

| German | English | Value | Notes |
|---|---|---|---|
| Maßstab 1:250 | scale 1 : 250 | `GameAreaScale.M_TO_PX_MULTIPLIER = 8.0` (≡ 20 px/cm); `Simulation.PX_PER_METER` derives from it | Issue #41 (closed via PR #88). |
| Skalierungs-Utility | coordinate / unit utility | `src/rendering/GameAreaScale.kt` | Single source of truth for the three spatial domains (virtual metres ↔ screen px ↔ physical cm). Conversions: `virtualMetersToPixels`, `pixelToVirtualMeters`, `virtualMetersToRealCM`, `pixelToRealCM`, `translateVirtualPointToInGamePoint`. |
| Tischformat | table format | 90 cm × 50 cm | Real-world (`DESK_W_CM` / `DESK_H_CM`). |
| Spielfeldformat | game-area format | 1800 × 1000 px ≙ 225 × 125 m | `Level.WIDTH` / `Level.HEIGHT`, `GameAreaScale.GAME_AREA_W/H_METER`. |
| Seitenverhältnis | aspect ratio | **9 : 5** width:height (`GameAreaScale.ASPECT_RATIO_W = 9`, `ASPECT_RATIO_H = 5`; 1800 : 1000) | Issue #41. Convention settled on **9 : 5 (width : height)** to match the code; the process artefacts were updated from the earlier "5 : 9" wording in the `[dev-merge-2]` pass. |

## Process & workflow

| German | English | Where | Notes |
|---|---|---|---|
| Lastenheft | requirements document | [`../spec/Lastenheft.pdf`](../spec/Lastenheft.pdf) | Canonical. Written in German. |
| Wiki | team wiki | `https://github.com/sekassel/STP-2026-Team-D.wiki.git`, page `Gemeinsame-Grundlagen.md` | Canonical for team conventions (branching, DoD, meetings, comment style). |
| Definition of Done | Definition of Done (DoD) | wiki | Test-first where practical → implement → tests pass → review → file follow-up bug issues. |
| Branch-Konvention | branching convention | wiki | Feature branches on `dev`, merged to `dev`; `main` updated by Inkrement release merge only. |
| Inkrement-Release-Merge | Inkrement release merge | wiki | The `dev → main` merge that promotes one Inkrement. Analogue of PR #38 for Inkrement 1. |
| Vorgehensmodell | development process | [`devprocess.md`](devprocess.md) | The AI-assisted process this folder implements. |
| Issue-Triage | issue triage | `Skills/triage/` | Project Override: read-only. Canonical states: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. Categories: `bug`, `enhancement`. |
| Epic | epic | GitHub label | Nine epics organise the backlog: #1 Datenspeicherung, #3 Straßennetz, #4 Domänenmodell, #5 UI, #6 Spielsteuerung, #7 Produktmarketing, #8 Simulationslogik, #9 Objekterkennung, #10 3D-Druck. |
| Optionale Anforderung | optional requirement | GitHub label `optional` | E.g. Issue #42. |

## AI-Dev-Process meta-vocabulary

Terms that describe the AI-assisted development workflow itself — not domain terms, but they appear so often in the process artefacts and skill documents that a new reader needs an index.

| Term | Meaning | Where |
|---|---|---|
| **Project Override** | A project-specific rule that supersedes a companion skill's upstream behaviour. Authoritative collection of all overrides. | [`devprocess.md`](devprocess.md) → "Project Overrides". Mirrored in each affected `SKILL.md` as a `⚠ Project Override` blockquote at the top. |
| **Override blockquote** | The `> ## ⚠ Project Override (STP-2026-Team-D)` Markdown block at the top of every override-affected file. Pointer to the central rules in `devprocess.md`. | `Skills/to-prd/SKILL.md`, `Skills/triage/SKILL.md`, `Skills/to-issues/SKILL.md`, `Skills/triage/OUT-OF-SCOPE.md`, `Skills/grill-with-docs/SKILL.md`, `Skills/grill-with-docs/CONTEXT-FORMAT.md`, `Skills/grill-with-docs/ADR-FORMAT.md`, `Skills/AI-Dev-Process (Main-Skill)/{REFERENCE,DEVPROCESS-TEMPLATE,EXAMPLES}.md`. |
| **Workflow Log** | **Chronological** (oldest-first) **project-state** history (PR / branch / issue movements, phase transitions, reconciliation passes against `dev`). Reads as a project diary. Full log lives in `WORKFLOW-LOG.md` since `[workflow-log-extracted]`; `devprocess.md` keeps only a scope description and the latest 3-4 pointer entries. | [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md); pointer in [`devprocess.md`](devprocess.md) → "Workflow Log". |
| **CHANGELOG** | **Reverse-chronological** (newest-first) **structural-change** history of the `AI-Dev-Process/` folder itself (folder layout, skill bundling, override additions, README scaffolding). Release-style log. Split from Workflow Log via `[blue-hygiene]`. | [`CHANGELOG.md`](CHANGELOG.md). |
| **Skill freeze** | The convention that in-repo skill files under `Skills/` are committed at a specific revision and are **not** routinely re-synced from upstream. Updates are manual maintainer decisions. | [`Skills/README.md`](Skills/README.md) → "Re-syncing is a manual maintainer decision"; [`README.md`](README.md) → "Companion skills are frozen by design". |
| **AI-vs-human class diagrams** | Convention that `ai_class_diagramm.md` is AI-maintained and `../spec/class_diagram.md` is human-maintained; the AI never silently overwrites the human file. | [`Skills/AI-Dev-Process (Main-Skill)/REFERENCE.md`](Skills/AI-Dev-Process%20(Main-Skill)/REFERENCE.md) → "AI vs. human-maintained class diagrams"; `ai_class_diagramm.md` header. |
| **Last synced** | Header block at the top of `spec.md`, `PRD.md`, `design.md` indicating the `dev` commit / date the file was last reconciled against. Refresh trigger: every `[dev-merge]` Workflow Log pass. | Header blocks in [`spec.md`](spec.md), [`PRD.md`](PRD.md), [`design.md`](design.md). |
| **`[dev-merge]`** | Workflow Log tag for a reconciliation pass after `dev` was merged into the working branch. Triggers a status refresh of `spec.md` / `PRD.md` / `design.md` and the `glossary.md` status column. | [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md). |
| **`[skills-update]`** | CHANGELOG tag for a manual maintainer-driven update of one or more `Skills/<skill>/` bundles. Different from `[skills-folder]` (initial in-repo commit) and from `[blue-hygiene]` (log split). | [`CHANGELOG.md`](CHANGELOG.md). |
| **`NEXT_PHASE` / `NEXT_ACTION`** | The two canonical pointers in `devprocess.md` that say what comes next. Always exactly one of each. | [`devprocess.md`](devprocess.md) → "Current State". |
| **ADRs across skills** | Meta-rule: this project keeps **no** formal ADRs (`docs/adr/`); architecture decisions live in `design.md`, team-level decisions in the wiki, process decisions in `devprocess.md`. Several skills (`tdd`, `grill-with-docs`) reference ADRs — those references all resolve to this substitution. | [`devprocess.md`](devprocess.md) → "Project Overrides" → "ADRs across skills". |

## Tooling & runtime

| German | English | Notes |
|---|---|---|
| Kotlin (JVM) | Kotlin / JVM 21 | `build.gradle.kts`, CI runs on JDK 21 (Zulu). |
| KorGE | KorGE | Game / rendering framework. |
| kotlinx.serialization | kotlinx.serialization | Used by `LevelSerializer` for JSON. |
| JavaCV / OpenCV | JavaCV / OpenCV | `org.bytedeco:javacv-platform:1.5.10`. JVM source set only. |
| Gradle | Gradle | 8.x via `gradlew`. |

## How to extend and maintain this glossary

### When to add a new entry

- Add an entry **whenever a Lastenheft term enters the codebase** (typically during Inkrement 3 when §3.3 simulation rules start landing).
- Add an entry **whenever the agent or a developer first uses a new domain term** during grilling, PRD writing, or design discussions (the `grill-with-docs` Project Override mandates inline updates here, not in a `CONTEXT.md` at repo root).
- Keep the German column verbatim from the Lastenheft / wiki — do not paraphrase the source.
- Keep the English column matching the code identifier or KDoc term.
- Note **status** (`gap`, `optional`, `partial`, file path) so the glossary doubles as a quick capability map.
- Do not invent terms not present in either Lastenheft or code.

### When to refresh the `status` column

The status markers (especially in the **"Simulation rules"** and **"Recognition pipeline"** tables, which today carry many `gap` entries) go stale as Inkrement 3+ work lands. Refresh triggers:

- **Whenever a PR that touches §3.3 simulation rules is merged to `dev`** — update the affected `gap` → `partial` → `<path>` markers in the same PR or a follow-up commit.
- **At every Inkrement release merge (`dev → main`)** — walk the §3.3 + Recognition tables top-to-bottom and confirm every status reflects what's actually on `main` now.
- **At every `[dev-merge]` Workflow Log pass in `devprocess.md`** — re-check the markers as part of the reconciliation.

Out-of-sync status entries are not just cosmetic: skills like `to-prd` and `tdd` read this file as the project's domain glossary, and stale `gap` markers will mislead them about what already exists in code.
