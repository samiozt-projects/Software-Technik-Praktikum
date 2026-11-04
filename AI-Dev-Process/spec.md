# Specification

> **Last synced** with `dev`/`main` @ commit `60e27e5` on 2026-07-08 (`[dev-merge-9]`, deep content sync). Refresh on every `[dev-merge]` reconciliation pass — see [`WORKFLOW-LOG.md`](WORKFLOW-LOG.md). The body below now covers the Inkrement-5 codebase restructure (new `src/simulation/`, `src/network/`, `src/audio/`, `src/config/`, `src/geometry/`, `src/worldeditor/` packages) and the Inkrement-6 backlog opening — see `design.md` for the full architectural detail.

> Bootstrapped from an existing repository. Sources used for this spec:
>
> 1. The canonical German requirements document [`../spec/Lastenheft.pdf`](../spec/Lastenheft.pdf) (read in full).
> 2. Current repository code, tests, and configuration at `dev`/`main` @ `60e27e5` (2026-07-06).
> 3. The **verified** GitHub-Issues and PR state — see `devprocess.md` for the up-to-date table. **Inkrement 5 was released to `main` via PR #265 "Inkrement 5" (`dev → main`, merged 2026-07-06T21:50:52Z, `main` HEAD `60e27e5`, git tag `Sprint5`; ⚠ the `Inkrement-5` tag itself is mistagged at the old `Inkrement-4` commit — see `devprocess.md`)** — following Inkrement 4 (PR #202, 2026-06-22), Inkrement 3 (PR #141, 2026-06-08), Inkrement 2 (PR #100, 2026-05-25) and Inkrement 1 (PR #38, 2026-05-11). The entire Inkrement-5 minimum set is closed: multi-table server coupling and vehicle transfer (#203–#212), server-side world management (#221–#224/#227/#230/#231/#241), multi-table UI (#211/#219/#232), ArUco cropping #213 + detection smoothing #214, sound #226, statistics rework #228, and the full video-finalisation chain #215–#218 (completing the Lastenheft §4 promotional-video line). Carried-over items #99/#182/#248/#249 also closed; only #229 (Teamscale findings) survived. **Inkrement 6 ("bug-free release") has just started**: a 23-issue backlog `#266`–`#288` opened 2026-07-07/08, almost entirely untriaged.
> 4. The **team wiki** at `https://github.com/sekassel/STP-2026-Team-D.wiki.git` — currently a single page `Gemeinsame-Grundlagen.md` documenting agreed conventions (meetings, branching strategy, Definition of Done, comment style). The wiki is treated as the source of truth for team-level conventions.
>
> Inferences and unconfirmed facts are explicitly labelled **Assumption** or **Unknown**. The Lastenheft is treated as the canonical product reference; this file restates and structures it for the ai-dev-process workflow without altering its substance.

## Intent

Develop an **interactive projection-table system** that lets users construct and simulate a road network. Per `Lastenheft.pdf` §1:

- A camera observes the table; the software interprets the layout as a digital road network and projects feedback back onto the table.
- The table is fitted with **ferromagnetic foil**; physical road elements are **3D-printed** and held in place by magnets.
- Two strictly separated phases: **Konstruktionsphase** (free editing, partial/illegal networks allowed) and **Simulationsphase** (a complete, consistent network is required, then traffic is simulated).
- The product targets **school visitors and technically interested visitors** at outreach events; UX must be haptic, low-threshold, intuitive, yet deep enough to teach traffic planning.
- Inspiration: the game *Freeways* and MIT Media Lab CityScope.
- Networking multiple tables via a server is an **explicit later-phase goal**, not part of the first usable release.

## Backlog Structure (Epics)

The GitHub Issues board is organised around nine **epic** labels that structure all child issues:

| Epic | Title | Scope |
|---|---|---|
| #1 | Datenspeicherung | Saving and loading levels |
| #3 | Straßennetz | Road-network model and rules |
| #4 | Domänenmodell | Streets, vehicles (types, speeds), colours, nodes (sources, targets, elevation) |
| #5 | Benutzeroberfläche (UI) | All on-table / on-screen UI |
| #6 | Spielsteuerung | User interaction with the running game |
| #7 | Produktmarketing | The 60–90 s promotional video |
| #8 | Simulationslogik | Routing, speed control, weather, distances, success/abort criteria |
| #9 | Objekterkennung | Camera-based recognition of road elements |
| #10 | 3D-Druck | Printing road elements |

All other issues are children of these epics.

## Existing Capabilities

State of the repository at bootstrap time, mapped to the Lastenheft Inkremente (§5.1) and **verified against GitHub Issues / PRs**.

### Implemented (Inkrement 1 — wrapped by PR #38 "Inkrement 1" merged 2026-05-11)

Closed Issues, all merged via the `dev` → `main` flow:

- **#11 Vollständiges Domänenmodell implementieren** — PR #24 merged. `src/domain/` covering `Level`, `Node` hierarchy, `Street`, `Vehicle`, `VehicleType`, `Color`, `SourceNodeConfiguration`, `GameState`, `SimulationState`, `NetworkState`.
- **#13 Speicherformat definieren & Laden eines vorgegebenen Levels** — PR #25 merged. `LevelSerializer` + `LevelDto`, JSON schema documented in [`test/persistencetests/README.md`](../test/persistencetests/README.md) (moved from `test/README.md` during the PR #70 restructure).
- **#14 Vorgegebenes Level visualisieren** — PR #28 merged. KorGE `LevelScene` showing sources, targets, orientations.
- **#15 Einfache Straßen auf Ebene 0 erkennen** — PR #27 merged. `imageprocessing/StreetDetection` via JavaCV / OpenCV (HSV thresholds, morphology, contour detection, Zhang–Suen thinning).
- **#16 Erkanntes Verkehrsnetz speichern** — closed.
- **#17 Einfache Echtzeitsimulation starten** — PR #35 merged. `Simulation.tick`: spawn → accelerate → advance.
- **#18 Definieren eines Level 0** — PR #26 merged. `res/level/level0.json`.
- **#19 Überprüfung vollständiges & korrektes Straßennetz** — PR #29 merged. `Level.validate()` with the six structural rules.
- **#20 Abschluss eines Levels** — closed. Currently implemented as the I1-placeholder "first arrival" (`Simulation.levelCompleted`); the final Lastenheft criterion is **not yet** addressed.
- **#21 Hauptmenü Implementierung** — PR #31 merged.
- **#22 Speichern eines Straßennetzes** — PR #33 merged.
- **#23 Levelauswahl Menü** — PR #34 merged (still carries `// TODO: Load Level 1-8` at `src/scenes/LevelMenuScene.kt:194`, addressed by open issue **#43**).
- **#2 Visualisierung/Projektion** — closed (likely a structural / planning issue).

### Implemented in Inkrement 2 (released to `main` via PR #100 "Inkrement/Sprint 2 PR", merged 2026-05-25)

All of the following landed on `dev` after Inkrement 1 and reached `main` with the **Inkrement-2 release merge PR #100** (the analogue of PR #38). `main` has since advanced through the back-ported PR template (PR #124) and the **Inkrement-3 release merge PR #141 "Sprint 3" (`dev → main`, 2026-06-08, `main` HEAD `5b3f20f`)**. The PR #62 → #64 branch-convention excursion was fully reverted earlier (see #55 below). The in-repo `AI-Dev-Process/` folder also reached `main` as part of the Inkrement-2 release via PR #69.

- **#46 Fahrzeugtypen um Grafikeigenschaft erweitern** — chain complete on `dev`. PR #60 added field + getter (merged 2026-05-17), reverted entirely by PR #63 (2026-05-18 16:12 UTC), getter restored by PR #65 (2026-05-18 18:38 UTC), and finally `VehicleType.graphicRef` re-added with DTO/serializer mapping, `Assets` preload/lookup, `DomainModelTest` coverage and KDoc on the getter by **PR #68 "Final Fix" merged 2026-05-19 10:17 UTC**.
- **#47 Vereinheitlichung des Verhaltens der Bedienelemente** — **PR #66 merged 2026-05-19 13:07 UTC** introduced the `ControlButtons` helper plus icons under `res/images/{start_simulation,menu,gear,close}.png`; **PR #74 merged 2026-05-20 14:38 UTC** refactored `LevelScene` onto the helper.
- **#40 Projekt-Ordnerstruktur überarbeiten** — **PR #70 merged 2026-05-19 21:07 UTC**. `resources/` collapsed into `res/images/`; `res/savedlevel/` separated from `res/level/`; `LevelViewRenderer` moved from `src/ui/` to `src/rendering/`; `build.gradle.kts` source-set wiring adjusted.
- **#39 Erstellung und Einbindung von 2D-Grafiken** — **PR #73 merged**. Vehicle / source / target / end / pause / construction PNGs in `res/images/`.
- **#44 Tutorial-Level** — **PR #75 merged**. `TutorialScene`, `res/level/tutorial.json`, `TutorialSceneTest`.
- **#59 Modus für Farbenblinde** — **PR #76 merged 2026-05-21 16:34 UTC**. Toggle on `MenuScene`, palette switch in `GameState`, `MenuSceneColorBlindnessTest`.
- **#43 + #48** — **PR #77 merged 2026-05-21 16:53 UTC**. `PredefinedLevelCatalog` resolves the old `// TODO: Load Level 1-8`; dedicated `SavedNetworksScene` with filter / sort / load / delete; eight `res/level/level{1..8}.json`.
- **#45 Mehrere Level erstellen (≥ 4 of increasing complexity)** — **PR #78 merged 2026-05-24 16:42 UTC**. Eight progressively complex levels with rotation.
- **#49 Anpassung des Pausenmenüs** — PR #61 scaffold, completed by the configuration-menu chain (PR #82 / #84 / #87 / #90).
- **#50 Übersichtsmenü Fahrzeugtypen / Quellkonfigurationen** — **PR #82 merged 2026-05-24**. Overview `ConfigurationScene`, `VehicleTypeDetailScene`, `SourceConfigDetailScene`.
- **#51 Konfigurationsmenü für Fahrzeugtypen** — **PR #87 merged 2026-05-25**. Full vehicle-type editing via the new `VehicleConfigurationScene` + the `VehiclePreview` tinted-bitmap helper.
- **#52 Konfigurationsmenü für Quellkonfigurationen** — **PR #84 + #90 merged 2026-05-25**. Source-configuration editor (initial speed / frequency / colour, per-source vehicle-type selection); two-colour-vehicle handling fixed.
- **#54 Erkennung komplexer Straßennetze (Kurven, Kreuzungen)** — **PR #80 merged 2026-05-24**. Skeleton-based street-graph construction + driving-path finding, circumcircle-based roundabout-direction detection, node-identity fix in `LevelSerializer`.
- **#55 Erzeugung von Fahrzeugen aus allen konfigurierten Quellkonfigurationen** — closed. PR #62 wrongly merged to `main` ("Fehlerhafter Merge" per PR #64), reverted on `main` by PR #64, real work placed on `dev` by PR #67.
- **#56 Simulation mit Fahrzeugen aus mehreren Quellen und Quellkonfigurationen** — **PR #83 merged 2026-05-24**. The intermediate completion criterion is now wired into `Simulation`: `completedSourceConfigurations` records each `(source, configuration)` whose vehicle has reached a target, and `levelCompleted` flips only once `allSourceConfigurationsCompleted()` is true (≥ 1 arrival from **every** source configuration). Vehicles at a `ConnectionNode` now route via `Level.findNextStreetTowardTarget(node, colour)` toward a matching-colour target.
- Misc: **PR #81 merged 2026-05-24** — scroll regression fix in `LevelMenuScene` / `SavedNetworksScene` plus `LevelMenuSceneTest` coverage.

### Implemented in Inkrement 3 (released to `main` via PR #141 "Sprint 3", 2026-06-08)

`main` carries Inkrement 2; the following Inkrement-3 work is on `dev` only and awaits the next Inkrement-3 release merge:

- **#107 Simulation lässt sich nicht erneut starten sobald ein Level abgeschlossen wurde** — **PR #112 merged 2026-05-29**. `simulationState` resets on level load; `GameState.activeSimulation` introduced and cleared by the `GameState.currentLevel` setter.
- **#91 Kurvengeschwindigkeit anhand Kurvenradius (Lastenheft §3.3)** — **PR #113 merged 2026-05-30**. New `src/domain/CurveSpeed.kt` (`r = abc/(4A)`, `V_max^i = min(V_max, √(μ·g·r))`, `g = 9.81 m/s²`) and a route-aware look-ahead in `Simulation.accelerateVehicles` covering inner polyline waypoints and connection-node corners. First Inkrement-3 §3.3 rule.
- **#41 Einheitliche Skalierung aller Objekte (1:250, 9:5)** — **PR #88 merged 2026-06-01**. `src/rendering/GameAreaScale.kt` is now a full coordinate/unit utility (virtual metres ↔ screen px ↔ physical cm); rendering and `StreetDetectionIntegration` rewritten onto the in-game coordinate system; `Simulation.PX_PER_METER` derives from `GameAreaScale.M_TO_PX_MULTIPLIER`. (Last Inkrement-2 residual, finished post-release.)
- **#106 Einheitliche UI** — **PR #116 merged 2026-06-01**. Unified headings, frames, font sizes, and vehicle-symbol aspect ratios across the configuration / pause / load menus.
- **#94 Netzvalidierung – Pfad-Vollständigkeit** — **PR #125 merged 2026-06-05**. Validation that every source can reach a matching-colour target; extended `LevelValidationTest` + `Level`.
- **#97 Quellkonfigurationsfehler beim Laden** — **PR #133 merged 2026-06-07**. Detect + display source-config errors on load; extra "speed too high" check; numeric-only input.
- **#98 Erfolgs-Overlay nach Simulationsende** — **PR #126 merged 2026-06-04**. Success overlay shown when a run completes (first result-display step).
- **#102 Scenen – Wechsel anpassen** — **PR #123 merged 2026-06-06**. Adjusted scene transitions. **#79** (close-button navigation) also closed (`COMPLETED`) in this window.
- **#103 Erfolgskriterien für alle Level** — **PR #129 merged 2026-06-07**. Per-level success criteria.
- **#108 Challenge-Modus** + **#117 Tutorial-Erklärung** — **PR #115 merged 2026-06-02** (new `src/domain/ChallengeMode.kt` + `test/ChallengeModeTest.kt`, `MenuScene` entry point) and **PR #128 merged 2026-06-06**.
- **#110 Speichern von Leveln nur bei Veränderung** — **PR #120 merged 2026-06-02**. Saves skipped when the level is unchanged.
- **#119 Realistischer Übergang von Straßen an Kreuzungen** — **PR #121 merged 2026-06-02**. Smoother road-to-junction transition rendering (new issue, opened + closed inside Inkrement 3).
- **#57 Modellierung von Brücken für Ebene 1 und 2** — **PR #122 merged 2026-06-03 + #127 merged 2026-06-04**. `res/3dprinting/` bridge STL models + gcode `.3mf` files + magnet-hole bores + `README_3D_printing.md`; **#12** (road-element printing) closed alongside.
- **#71 LevelSerializer übersetzt Color-Attribut falsch** — **PR #134 merged 2026-06-07**. ARGB byte-order fix (channel-wise conversion, DTO `rgba → argb` rename, canonical colour values unified, 4 regression tests).
- **#109 Benutzerfreundlichkeit der Einstellungen/des Menüs**, **#101 Werbevideo-Skript** — closed (usability polish; promotional-video script as a document deliverable).
- Tooling: **PR #111 merged 2026-05-31** — `.github/pull_request_template.md`. **PR #124 merged 2026-06-03** back-ported the PR template to `main`.

#### §3.3 simulation rules completed just before the release (all merged to `dev` 2026-06-08)

- **#92 Vorfahrtsregeln** — **PR #135 merged**. Right-of-way / zipper-merge at junctions: a merge stop-line / round-robin right-of-way system in `src/domain/Simulation.kt` (`updateMergeGrants`, `mergeOccupant`/`mergeGrant`/`mergeRightOfWay`, `speedCeilingForMerge`, `distanceAllowedByMergeStopLine`) with ~10 merge tests in `test/domaintests/SimulationTest.kt`.
- **#105 Fahrzeugabstand und Kollisionsvermeidung** — **PR #137 merged**. The 2 m minimum distance was re-implemented after the earlier removal: `MIN_DISTANCE_M = 2.0`, `speedCeilingForLeadingVehicle`, `clearDistanceToLeaderM` + a hard movement clamp; also fixed a scaling bug. Duplicate #86 closed as `DUPLICATE`.
- **#85 Kürzester-Weg-Berechnung** — **PR #139 merged**. New `src/domain/StreetPathFinder.kt` (Dijkstra over the street graph, `findDrivingPath`/`findDrivingRoutes` via a `PriorityQueue`); `Simulation` switches to a longer route only when the shortest is congested. Tests: `StreetPathFinderTest`.
- **#93 Simulationsabbruch bei Verstopfung** — **PR #140 merged**. `Simulation.congestionDetected` set each tick via two criteria (running-average speed below 30 % of the initial average; per-vehicle wait-time deadlock), surfaced via `GameState` + an abort message in `LevelScene`.
- **#114 Erkennung & Projektion der Ebenen 1 und 2** — **PR #138 merged**. Large `src/imageprocessing/StreetDetection.kt` rewrite for multi-level detection + new `res/road/Road5.png`.

### Inkrement 4 — released to `main` via PR #202 "Inkrement 4" (`dev → main`, 2026-06-22, tag `Inkrement-4`)

The PO opened a large Inkrement-4 backlog (#142–#189, mostly without `epic` labels; the epic mapping below was the agent's recommendation). It is now closed — see the closed/open breakdown below and in `devprocess.md`.

#### Already landed on `dev`

- **#144 Einstellbare Simulationsgeschwindigkeit** — **PR #164 merged 2026-06-11**. Configurable speed factor in `src/domain/GameState.kt`, applied in `Simulation`, controlled via `src/ui/ControlButtons.kt`; `SimulationSpeedTest`, `ControlButtonsTest`.
- **#160 Wetterbedingungen in Leveldateien** — **PR #165 merged 2026-06-11**. New `src/domain/WeatherCondition.kt` enum (`DRY`/`WET`/`SNOWY`), `Level.weatherCondition`, persisted via `LevelDto`/`LevelSerializer`; weather value in every `res/level/*.json`.

#### Inkrement-4 core (Lastenheft §5.1): COMPLETE

- **#142 Detaillierte Simulationsstatistiken erfassen** (epic #8, PR #167) + **#143 Detaillierte Simulationsstatistiken anzeigen** (epic #5, PR #188) — both closed; the §5.1 detailed-statistics deliverable is delivered.

#### Inkrement-4 backlog: closed (released via PR #202 "Inkrement 4", `dev → main`, 2026-06-22, tag `Inkrement-4`; #153/#189 closed shortly after the release)

- Robustness **#154** (PR #168) / **#155** (PR #193) / **#149** (PR #191), weather **#158/#161** (PRs #170/#176), result flow **#151** (PR #194) / **#166** (PR #174) / **#150** (PR #198), camera wiring **#146** (PRs #187+#192), video rough cut **#145+#148** (PR #196), coverage **#147** (PR #199), config file **#42** (PR #197 — `src/config/ConfigManager.kt`), bug **#184** (PR #195) — all closed and in the release. **#153** (PRs #240/#246) and **#189** (PR #238) closed shortly **after** the release (on `dev` only). Only **#182** (street-detection correction; fix PR #200 closed unmerged) and the UI item **#99** survived the increment.

#### Inkrement 5 — released to `main` via PR #265 "Inkrement 5" (`dev → main`, 2026-07-06T21:50:52Z, tag `Sprint5`)

The full Lastenheft §5.1 Inkrement-5 minimum set (multi-table server coupling + final video) is **closed**:

- **Server coupling & vehicle transfer** — new `src/network/` package: server interaction **#203** (PR #250, `TrafficSimRestClient`/`TrafficSimWebSocketClient`), network config **#204** (PR #239), transfer-message format **#205** + coupling-server connection **#207** (one PR, **#253** — payload `{color, type, speedMetersPerSecond}` via `VehicleWebSocketConverter`; `SourceNode` gained a `LOCAL`/`SERVER_NEUTRAL` `kind`), sink→source mapping **#206** (PR #243, `TransitionAssignment`), outgoing hand-over **#208** (PR #257, `Simulation` gained an optional `VehicleTargetHandoverPort`; `CoupledSinkVehicleHandover` sends `VEHICLE_DEPARTURE`), incoming hand-over **#209** (PR #258, `FixedTransitionHandoverHandler`), connection-loss robustness **#210** (PR #264, WebSocket heartbeat + `ServerConnectionManager` reconnect), live server data in the UI **#212** (PR #263).
- **Worlds** — new `src/persistence/WorldTemplate.kt` + `src/worldeditor/`: selection template **#222** (PR #235, `WorldSelectionScene`), fetch worlds **#221** (PR #256), create world **#223** (PR #255), default template **#224** (PR #252), world editing **#230** (PR #242, `WorldEditScene`) + editor **#227** (PR #254, `src/worldeditor/`), tile selection **#241** (closed with PR #245), delete world **#231** (PR #261).
- **Multi-table UI** — `ServerMenuScene` **#211** (PR #245), menu globe/text **#219** (PR #220), main-menu adaptation **#232** (PR #251).
- **Detection & smoothing** — ArUco-marker table rectification **#213** (PR #244, new `src/imageprocessing/ArucoTableRectifier.kt`, replacing the earlier contour-based table crop), polyline smoothing/caching **#214** (PR #247, new `src/geometry/PolylineSimplifier.kt`).
- **Sound** (Zusatzfeature) — **#226** (PR #234, new `src/audio/` package: `SimulationSoundController`, driving loops, weather ambience, congestion horn).
- **Statistics rework** — **#228** (PR #237, `TrafficFlowRating` rating logic).
- **Marketing video** (Lastenheft §4) — **#215/#216/#217** (re-shoots, fine cut, voice-over; closed 2026-07-03, no dedicated PR) → **#218** (PR #259, final export), **completing the entire video line** #101 → #145+#148 → #215/#216/#217 → #218.
- **Cross-team data format** — **#225** (closed, no PR — the agreed format that #205 implements).
- Carried-over: **#153** congestion hardening (PRs #240/#246 — `src/simulation/CongestionDetector.kt`, new `WAIT_TIMEOUT_S` criterion), **#189** (PR #238), **#99** (closed, no PR), **#182** detection-correction bug (PR #262, issue closed 2026-07-07 — another auto-close-gap instance). Two new UI bugs also closed: **#248** fullscreen inconsistency, **#249** challenge-button UI.
- **Codebase restructure**: `src/domain/Simulation.kt` split into the new `src/simulation/` package (`Simulation` + 7 managers: `MergeManager`, `RouteManager`, `CongestionDetector`, `StatisticsCollector`, `VehicleSpawnManager`, `VehicleMovementManager`, `VehicleAccelerationManager`, `StreetUtilizationManager`); new `src/config/ConfigManager.kt` centralising camera/detection/simulation/network settings. See [`design.md`](design.md) for the full architectural breakdown, including which physics constants were re-tuned during the #153 hardening pass.
- Only **#229** (Teamscale findings) survived from the Inkrement-5 era.

### Inkrement 6 — started, not released ("Bug-free release" per Lastenheft §5.1)

A 23-issue backlog **#266–#288** was opened 2026-07-07/08, immediately after the Inkrement-5 release, almost entirely without labels or triage state (only **#266** `bug`, **#267** `fix`). Spans bug fixes (#266, #268, #269, #281), UI polish (#270, #273, #275, #278, #280, #282, #284, #288), hardware/detection follow-ups (#272, #283, #285, #286), a Raspberry Pi performance item (**#276**, directly ties to Lastenheft NFR6), documentation (**#271**, end-user README), a sound follow-up (#287), and requirements/coverage sweeps (**#277** code coverage, **#279** explicit Lastenheft-requirements test coverage). See `devprocess.md` for the full table. The local working tree picked up **#283** Brückenerkennung verbessern first (branch `283-brückenerkennung-verbessern`, one commit pushed, no PR yet).

### Tests and CI

- `test/` is now **fully organised into subject subfolders** (the earlier top-level test files were moved during the Inkrement-3 work; no test lives directly under `test/` anymore):
  - `test/domaintests/`: `CurveSpeedTest` (#91), `DomainModelTest`, `LevelValidationTest`, `MultiColorVehicleTest`, `SimulationTest`, `StreetUtilizationTest` (#104), `VehicleAdvanceTest`, `VehicleGenerationFromMultipleSourcesTest` (#55).
  - `test/persistencetests/`: `LevelSerializerTest` (+ golden master `ExampleLevel.json`), `PredefinedLevelsTest`, `TutorialLevelTest`, `README.md` (level-JSON schema, moved from `test/README.md`).
  - `test/imagedetectiontests/`: `StreetDetectionTest`, `StreetDetectionIntegrationTest`.
  - `test/renderingtests/`: `GameAreaScaleTest` (#41), `StreetRendererTest`, `ViewSyncTest`.
  - `test/scenestests/`: `ConfigurationSceneScrollTest`, `ControlButtonsTest`, `LevelMenuSceneTest`, `MenuSceneColorBlindnessTest`, `MenuSceneTest`, `TutorialSceneTest`.
  - `test/uitests/`: `AssetsTest`.
- CI: `.github/workflows/gradle.yml` runs `./gradlew jvmTest` on push to `main` (JDK 21, Zulu). A `.github/pull_request_template.md` was added by PR #111.
- `.github/workflows/deploy-js.yml` exists but `build.gradle.kts` only enables `targetJvm()`.

### Not yet implemented (per Lastenheft §3.3 / §5.1)

The Inkrement-3 §3.3 simulation-rule set (released, PR #141) and the **entire Inkrement-5 minimum set** (released, PR #265) are now **complete**. What remains open is the fresh **Inkrement-6 backlog** (see "Inkrement 6" above) plus two Lastenheft items still without a tracking issue:

**Lastenheft items still without a tracking issue:**

- Deterministic simulation (repeating a scenario yields identical trajectories) — NFR7, no seeded RNG today. Now more pressing given genuinely concurrent I/O (network client, audio coroutines) alongside the simulation tick.
- Full touch-only / table-only control on the table (Inkrement 4 requirement, still untracked — worth re-checking now that the multi-table UI has matured).
- 60 FPS / 1000 vehicles performance verification on Raspberry Pi 5B 2 GB — **now tracked** as Inkrement-6 Issue **#276**.

**Fully implemented Lastenheft §3.3/§5.1 lines** (for reference; see `devprocess.md` for the exhaustive per-issue table): the full §3.3 simulation-rule set (curve-radius #91, right-of-way #92, 2 m distance #105, traffic volume #104, shortest path #85, Verstopfung #93, hardened further by #153); detailed statistics #142/#143 (reworked #228); configurable speed-up #144; the weather line #160/#158/#161; the camera/detection line #53/#157/#146/#213/#214/#182; multi-table server coupling and vehicle transfer #203–#212; server-side world management #221–#224/#227/#230/#231/#241; sound #226; the full promotional-video line #101→#145+#148→#215/#216/#217→#218.

## Primary Users or Use Cases

Per Lastenheft §1:

- **Pupils at outreach / school-visit events** — primary target group; "Informatik erleben".
- **General technically interested visitors.**
- **Operator / lecturer** running the demonstration on the table.
- **Developer** running the simulation in PC/notebook mode (Lastenheft §3.3: "Simulation eines gespeicherten Netzes … auch ohne Tisch an einem PC oder Notebook"). Constructing networks at the PC alone is explicitly **not required**.
- **Colour-blind users** (Issue #59): a dedicated pause-menu toggle adjusts colours so red/green/blue cars, sources and targets remain distinguishable.

## Inputs and Outputs

### Inputs

- **Physical road tiles** placed on a 90 × 50 cm ferromagnetic table, observed by an **HD USB webcam**. Live-camera ingestion, camera integration, camera→detection wiring, ArUco-marker table rectification (`ArucoTableRectifier`, replacing the earlier contour-based crop) and detection-polyline smoothing/caching (`PolylineSimplifier`) are all closed and released. Static images under `res/images/Road.png` + `res/road/Road{2..5}.png` remain as test fixtures.
- **Level JSON files** under `res/level/` (pre-defined) and `res/savedlevel/` (user saves), conforming to `test/persistencetests/README.md`.
- **Server-hosted worlds** (new, Inkrement 5): a world's editor state and network configuration are fetched from / pushed to the coupling server via `TrafficSimRestClient`, rather than only local JSON.
- **Coupled-neighbour vehicle hand-overs** (new, Inkrement 5): a `VEHICLE_ARRIVAL`/`VEHICLE_SOURCE` WebSocket message injects a vehicle at a locally-mapped source.
- **Touch input on the projected surface** (target end-state, Inkrement 4, still untracked as full table-only control); mouse/keyboard otherwise.

### Outputs

- **Projected visualisation** of the recognised network and live simulation, via a ~500 lm HDMI mini-beamer.
- **Persisted level JSON** via `LevelSerializer.saveToFile`; user-saves separated from pre-defined levels (Issue #40, #48).
- **Vehicle statistics** — success/failure + counts in Inkrement 3, detailed statistics (collect + display + rating rework #228) in Inkrement 4/5.
- **Validation errors** as a typed `List<ValidationError>` from `LevelValidator` (extracted from `Level` during the Inkrement-5 restructure).
- **Accessibility-adjusted palette** when colour-blind mode is active (#59).
- **Outgoing vehicle hand-overs** (new, Inkrement 5): a `VEHICLE_DEPARTURE` WebSocket message sent to the coupling server when a vehicle reaches a coupled sink.
- **Driving / weather / congestion sound** (new, Inkrement 5, Issue #226): explicitly a laptop-mode-only enhancement — audio stays excluded on the Raspberry Pi per NFR5.

## Team Conventions

Authoritative source: team wiki at `https://github.com/sekassel/STP-2026-Team-D.wiki.git`, page `Gemeinsame-Grundlagen.md`. The full text is mirrored in [`devprocess.md`](devprocess.md) under "Lastenheft, Wiki and Issue Tracker Alignment"; only the summary needed for this spec is reproduced here.

- **Branching**: feature branches on `dev`, merged into `dev`; `main` is updated by a release merge at least once per Inkrement (both branches protected).
- **Definition of Done**: test-first where practical (AI-assisted tests explicitly allowed), implement, tests pass, code review, follow-up issues for bugs.
- **Comments**: KDoc-style doc-strings (interpretation of `//` in function bodies — see `devprocess.md` and `PRD.md`).
- **Meetings**: weekly Mo–Mi 12:00–14:00.

**Implication for this spec**: any "dev vs. main divergence" between Inkrement releases is **expected by design**, not a defect. The Inkrement-1 release reached `main` via PR #38, and the Inkrement-2 release reached `main` via PR #100 (2026-05-25). Inkrement-3 work now accumulates on `dev` (40 commits ahead of `main` at this sync) and will reach `main` with the next Inkrement release merge.

## Hardware and Technical Constraints

Per Lastenheft §2:

- **Compute**: laptop **or Raspberry Pi 5B with 2 GB RAM** (no audio on the Pi).
- **Projection surface**: 90 cm × 50 cm at scale **1:250** (Issue #41 makes this explicit and adds a 9:5 width:height aspect-ratio constraint — `GameAreaScale.ASPECT_RATIO_W/H = 9/5`).
- **Beamer**: HDMI, ≈ 500 lm.
- **Camera**: HD USB webcam.
- **Table**: ferromagnetic foil; road elements 3D-printed and magnet-equipped (epic #10; child issues #12, #57, #58).
- **Language**: Kotlin (latest at project start).
- **Visualisation framework**: KorGE.
- **Computer vision**: JavaCV.

All four technology constraints are honoured by the current `build.gradle.kts`.

## Supporting Artifacts

Pre-existing project artefacts (still under `spec/`):

- [`../spec/Lastenheft.pdf`](../spec/Lastenheft.pdf) — canonical German requirements document (11 pages, read in full for this spec).
- [`../spec/class_diagram.md`](../spec/class_diagram.md) — **human-maintained** Mermaid class diagram of the current domain model; closely matches `src/domain/`.

AI-Dev-Process artefacts (this folder, after the folder-consolidation pass):

- [`PRD.md`](PRD.md) — structured product requirements derived from the Lastenheft and the verified GitHub-Issues state.
- [`design.md`](design.md) — current architecture.
- [`devprocess.md`](devprocess.md) — process tracker.
- [`glossary.md`](glossary.md) — DE↔EN domain vocabulary linking Lastenheft / wiki terms to code identifiers.
- [`agent-prompt.md`](agent-prompt.md) — reusable coding-agent prompt template (German) encoding the mandatory-context reading order, project rules / overrides, branching + HITL policy, and the TDD/SDD steps.
- [`ai_class_diagramm.md`](ai_class_diagramm.md) — **AI-maintained** counterpart to `../spec/class_diagram.md`; populated by the agent during architecture-definition or issue-level SDD.
- [`REFERENCE.md`](Skills/AI-Dev-Process%20(Main-Skill)/REFERENCE.md), [`DEVPROCESS-TEMPLATE.md`](Skills/AI-Dev-Process%20(Main-Skill)/DEVPROCESS-TEMPLATE.md), [`EXAMPLES.md`](Skills/AI-Dev-Process%20(Main-Skill)/EXAMPLES.md), [`ai-dev-process-SKILL.md`](Skills/AI-Dev-Process%20(Main-Skill)/SKILL.md) — local ai-dev-process workflow reference files.
- [`prds/`](prds/) — reserved for **per-feature PRDs** starting Inkrement 3 (one Markdown file per Lastenheft §3.3 item, authored with `to-prd` using `PRD-TEMPLATE.md`); currently empty. See [`prds/README.md`](prds/README.md) for the naming convention and template-adaptation rules.

## Unclear or Missing Information

- **Final level-completion criterion.** The I1 placeholder ("first arrival") has been superseded by the **Issue #56 intermediate criterion**, now implemented in `Simulation` (`allSourceConfigurationsCompleted()`: at least one vehicle from each source configuration reaches a target). The Lastenheft §3.3 **final** criterion (no Verstopfung / Verklemmung) is split across **#93** (Verstopfungs-Abbruch, closed — PR #140, hardened by #153) and **#103** (Erfolgskriterien für alle Level, closed — PR #129). How #103 / #93 relate to the #56 criterion per level still needs PO ratification.
- ⚠ **`Inkrement-5` git tag mistagged.** The tag points at the same commit as `Inkrement-4` (`e27c676`) instead of the real Inkrement-5 release commit (`60e27e5`, correctly marked by the `Sprint5` tag). Flagged for the team to fix; not corrected by the agent.
- **Two connection-state enums.** `src/domain/ConnectionState` and `src/network/NetworkConnectionState` appear to track overlapping lifecycles in different packages — unclear whether one delegates to the other. See `design.md` "Known Design Issues".
- **Inkrement-6 backlog has no triage state yet.** #266–#288 were filed without labels; the thematic grouping used in this spec (and in `devprocess.md`) is the agent's mapping, for the PO/team to confirm and triage.
- **Projection calibration.** No issue, no code. Needed before NFR2 (≤1 s latency on real hardware) can be honoured.
- **Determinism enforcement.** Lastenheft requires deterministic simulation; no issue tracks it explicitly, and it is now more concrete a gap given genuinely concurrent network/audio I/O alongside the simulation tick.
- **`../spec/Lastenheft.pdf` vs `PRD.md`** — both now describe requirements. **Assumption**: Lastenheft is canonical; `PRD.md` is a derived English/structured companion. To be confirmed by the PO.

## Desired Improvements

Drawn from observable repo signals (TODO markers, comments, reverted PRs, Lastenheft Inkremente not yet started) and from open GitHub issues — *not* invented requirements. **Issue creation is owned by the team per the wiki conventions** (PO drives feature / story issues; team members file bug issues per the Definition of Done). **The agent does not create issues during this workflow.**

- **Drive the Inkrement-6 plan.** With Inkrement 5 fully released (PR #265), confirm with the PO/team which of the new **#266–#288** backlog issues form Inkrement 6's ("bug-free release") minimum set, get them triaged/labelled, then pick the next `ready-for-agent` issue and run it through per-issue SDD/TDD. **#283** (Brückenerkennung verbessern) is already picked up locally.
- **Correct the `Inkrement-5` git tag** so it points at the real release commit `60e27e5` rather than duplicating `Inkrement-4`'s `e27c676`.
- **Reconcile the two connection-state enums** (`domain.ConnectionState` vs. `network.NetworkConnectionState`) or document why both exist.
- Anchor the remaining **untracked** Lastenheft items (deterministic simulation NFR7, full table-only control) in dedicated issues — performance verification is now tracked as Inkrement-6 Issue **#276**.
- Reconcile `deploy-js.yml` with `targetJvm()`-only Gradle config.
- Customise the KorGE template defaults (`id = "com.sample.demo"`, `rootProject.name = "korge-hello-world"`).
- Performance-test the Inkrement-5 `src/simulation/` manager split on representative hardware (RPi 5B 2 GB) — directly serves Issue #276.
- When the agent next regenerates a class diagram (architecture-definition or issue-level SDD), keep `ai_class_diagramm.md` (populated for the first time in the `[dev-merge-9]` pass) consistent with `../spec/class_diagram.md`, per the rule in [`Skills/AI-Dev-Process (Main-Skill)/REFERENCE.md`](Skills/AI-Dev-Process%20(Main-Skill)/REFERENCE.md).

*Note: GitHub Issues are the canonical store. Feature / story issues are driven by the Product Owner; bug issues may be filed by any team member per the wiki Definition of Done. The agent does not create issues during this workflow.*
