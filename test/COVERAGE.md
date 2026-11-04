# Test Coverage

Status of the JVM test-coverage work for issue #147 ("Code Coverage erhöhen").

## How coverage is measured

Coverage is produced by the JaCoCo report wired up in [`build.gradle.kts`](../build.gradle.kts):

```sh
# JDK 21 is required (the KorGE 6 / JVM-21 build rejects an older default JVM)
./gradlew jacocoTestReport
```

- `jacocoTestReport` depends on `jvmTest`, so one run executes the full suite and writes the report.
- HTML: `build/reports/jacoco/jacocoTestReport/html/index.html`
- XML:  `build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`
- The same report is uploaded to Teamscale by [`.github/workflows/teamscale.yml`](../.github/workflows/teamscale.yml) on every push/PR to `main`/`dev`.

Generated/synthetic classes (`KR`, `Typed*`, `*$serializer`, …) are excluded in the report config so
the numbers reflect hand-written production code only. Test classes are no longer part of the
reported class directories. Teamscale may report a slightly different percentage because it applies
its own scope and exclusion rules.

`jacocoCoverageVerification` protects the central packages against substantial regressions. The
initial line-coverage limits are 90 % for `domain` and `simulation`, 80 % for `persistence`, 70 % for
`network`, and 55 % for `scenes`. The Teamscale workflow runs this verification together with the
report; these conservative lower bounds should be raised as more targeted tests are added.

## Baseline vs. current (line coverage)

Baseline = before any issue-#147 work. Current = the IntelliJ coverage result shown in the project
snapshot from 2026-07-13 after the tests listed below and the `dev` test-stability changes.

| Package               | Baseline line | Class | Method | Line | Branch | Note |
|-----------------------|--------------:|------:|-------:|-----:|-------:|------|
| `audio`               | —             | 83 %  | 61 %   | 46 % | 21 %   | Startup and hardware-backed audio paths remain only partly testable. |
| `config`              | —             | 100 % | 96 %   | 96 % | 73 %   | Configuration parsing and defaults. |
| `domain`              | 95.9 %        | 100 % | 100 %  | 99 % | 85 %   | Central simulation/business logic. |
| `geometry`            | —             | 100 % | 100 %  | 100 % | 78 %  | Geometry and path simplification. |
| `imageprocessing`     | 89.7 %        | 93 %  | 95 %   | 90 % | 75 %   | Deterministic processing plus scene-driven capture paths. |
| `network`             | —             | 87 %  | 86 %   | 82 % | 51 %   | REST/WebSocket clients and server coordination. |
| `persistence`         | 87.8 %        | 72 %  | 86 %   | 91 % | 59 %   | Level (de)serialization and saved-world metadata. |
| `rendering`           | 53.8 %        | 100 % | 100 %  | 97 % | 85 %   | Exercised through renderer and scene tests. |
| `scenes`              | 3.0 %         | 51 %  | 58 %   | 60 % | 34 %   | See "Scene rendering" below. |
| `simulation`          | 0 %           | 100 % | 97 %   | 97 % | 81 %   | Simulation control and playback logic. |
| `ui`                  | 28.8 %        | 100 % | 94 %   | 94 % | 64 %   | Shared controls rendered via scenes. |
| `worldeditor`         | —             | 72 %  | 83 %   | 80 % | 59 %   | World selection and editor flows. |
| `(default)` `main.kt` | 0 %           | 0 %   | 0 %    | 0 %  | 0 %    | KorGE entry point; only an end-to-end smoke test would reach it. |
| **Total (`src`)**     | **50.7 %**    | **71 %** | **76 %** | **79 %** | **58 %** | |

The screenshot reports **79 % total line coverage**. The most recently documented complete test
outcome is still **818 of 818 tests passed**; the screenshot itself does not display a test count.
JaCoCo remains the authoritative CI/Teamscale report because IntelliJ and JaCoCo instrumentation can
produce slightly different package totals.

## Tests added for issue #147

No `src` production code was changed (per the issue's out-of-scope rule).

### 1. Central-logic unit tests (`domain` / `persistence`)

| Test file | New tests | Targets |
|-----------|-----------|---------|
| `persistencetests/LevelSerializerTest` | unknown street **start** node id; source-config unknown **color** | error branches the existing tests missed (they only hit the end-node / vehicle-type branches) |
| `persistencetests/LevelSerializerFileIOTest` *(new)* | `saveToFile` ↔ `loadFromAppVfs` round-trip; `loadFromAppVfs` missing file; `loadFromResources` missing resource | the suspend VFS entry points (previously only the string pipeline was tested) |
| `persistencetests/WorldLevelSelectionTest` *(new)* | standard template; predefined catalog level; saved app-VFS level | all three level-selection branches, including application of the chosen world name |
| `domaintests/LevelValidationTest` | connection node with outgoing but **no incoming** streets | the mirror of the existing "no outgoing" pass-through check |
| `domaintests/LevelTest` *(new)* | `removeStreet` removes dangling `ConnectionNode` endpoints; keeps still-connected nodes | `Level.removeStreet` auto-cleanup |
| `domaintests/GameStateTest` *(new)* | loading a non-Tutorial level clears `tutorialActivated`; loading the Tutorial level keeps it | `GameState.currentLevel` setter side effect |
| `domaintests/StreetPathFinderTest` | `findDrivingPath` returns empty when the target is unreachable | the no-path return |

### 2. Scene rendering (`scenes` / `ui` / `rendering`)

The dominant gap was `scenes` (~1779 lines at 3 %): the existing per-scene tests only verify extracted
layout/logic helpers (e.g. `MenuScene.MenuLayout`), never the `sceneMain` rendering bodies.

`scenestests/SceneRenderSmokeTest` drives selected scenes through KorGE's view-test harness
(`korlibs.korge.tests.ViewsForTesting`): `Assets.loadAll()`, a prepared `GameState`, then
`sceneContainer().changeTo { ... }`. This executes the full construction/render path of
`MenuScene`, `LevelScene`, `LevelMenuScene`, `SavedNetworksScene`, `ConfigurationScene`,
`WeatherConfigurationScene`, `TutorialScene`, `WorldSelectionScene`, `WorldEditorScene`,
`WorldPlaceholderScene` and `SourceConfigDetailScene` and, transitively, `LevelViewRenderer`,
`ControlButtons`, `Scrollbar` and `CameraManager.initialize`.

These are **smoke tests**: each asserts only that the scene builds and renders without throwing. That
is deliberate: the goal is to cover the load/render path and catch regressions that break a scene
entirely. Behavioural assertions stay in the dedicated per-scene tests.

## Remaining low-coverage areas (follow-up candidates)

- **`LevelScene` (~85.5 %)**: flaky interaction-driven scene tests were removed because they depended
  on non-deterministic background coroutines. Remaining gaps include start/pause, running-playback,
  completion/congestion UI paths, camera-capture edge paths, retry/next-level navigation callbacks,
  and challenge/weather popup click branches.
- **`VehicleConfigurationScene`, `WorldEditScene` and `NewWorldCreationScene`**: render smoke tests
  that instantiate KorGE text inputs were removed because they sporadically failed in the external
  KorGE font/text-input renderer.
- **`scenes/SceneRecovery` and `main.kt`**: the recovery navigation helper and the KorGE `main`
  bootstrap; reachable only via specific recovery flows / an end-to-end smoke test.
- **Hardware and external systems** — real camera drivers, operating-system window/audio startup,
  and availability/TLS behaviour of a real coupling server require hardware, system, or optional
  integration tests. Deterministic image processing, network conversion, and navigation decisions
  remain in the normal unit/integration test scope.

## Notes for the team / PO

- **Dead code: `Level.hasPathToTarget` (private, ~14 lines).** It has no remaining callers; the
  `findNextStreetTowardTarget` routing it supported was superseded by `StreetPathFinder` (issue #85).
  It cannot be reached through the public API and should be removed (separate change, since #147 is
  test-only). `design.md`/`glossary.md` still mention `Level.findNextStreetTowardTarget`, which no
  longer exists — those references are stale.
- **Verification on 2026-07-13:** after the `dev` test-stability changes were merged, the full
  IntelliJ test run completed successfully with 818 of 818 passing tests. The current supplied
  coverage snapshot reports 79 % line coverage for `src [commonMain]`. A fresh JaCoCo CI report is
  still required for the report-scope change.
