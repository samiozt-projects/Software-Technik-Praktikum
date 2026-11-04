# Prüfliste Lastenheft

Quelle: [`spec/Lastenheft.pdf`](../spec/Lastenheft.pdf), insbesondere §§ 2-5. Diese
Prüfliste ist die vollständige Nachweis-Matrix. „Grün“ bedeutet, dass der
verlinkte automatisierte Test im regulären JVM-Lauf bestanden hat. „Demo“ bezeichnet einen
reproduzierbaren Abnahmeschritt auf der angegebenen Hardware. „Offen“ bedeutet, dass der
Nachweis noch aussteht.

| Status | Bedeutung |
|---|---|
| Grün | Deterministischer JVM-Test; keine Kamera, Uhrzeit oder externe Serververbindung erforderlich. |
| Demo | Reproduzierbarer Abnahmeschritt mit Tischhardware bzw. echter Serverinstanz. |
| Offen | Noch nicht vollständig nachgewiesen. |

## Fachliche Anforderungen

| ID | Lastenheft-Anforderung | Prüfmethode und Nachweis | Status / Issue |
|---|---|---|---|
| F-01 | Straßennetz aus gerichteten Straßen, Quellen, Senken und Verbindungspunkten; Polylinien mit Anfangs-/Endpunkt. | [`DomainModelTest`](domaintests/DomainModelTest.kt), [`LevelTest`](domaintests/LevelTest.kt), [`LevelSerializerRoundtripTests`](persistencetests/LevelSerializerRoundtripTests.kt) | Grün |
| F-02 | Ebenen 0/1/2, Brücken und Rampen mit maximal einem Ebenensprung. | [`LevelValidationTest`](domaintests/LevelValidationTest.kt), [`MultiColorVehicleTest`](domaintests/MultiColorVehicleTest.kt), [`StreetDetectionTest`](imagedetectiontests/StreetDetectionTest.kt) | Grün |
| F-03 | Vorgegebene Quellen/Senken samt Richtung bleiben Teil des Levels; Quellen konfigurieren Typ, Farbe, Takt und Startgeschwindigkeit; Senken sind farblich eindeutig. Fahrzeuglängen: Auto 4 m, LKW 8 m, Motorrad 2 m. | [`SourceNodeTest`](domaintests/SourceNodeTest.kt), [`GameSimulationConfigTest`](domaintests/GameSimulationConfigTest.kt), [`ConfigurationSceneVehicleTypeTest`](scenestests/ConfigurationSceneVehicleTypeTest.kt), [`LevelValidationTest`](domaintests/LevelValidationTest.kt) | Grün |
| F-04 | Vollständiges Netz: gerichteter Pfad je Quellfarbe zur passenden Senke sowie mindestens eine ankommende und abgehende Straße an mehrarmigen Verbindungen. | [`LevelValidationTest`](domaintests/LevelValidationTest.kt), [`StreetPathFinderTest`](domaintests/StreetPathFinderTest.kt) | Grün |
| F-05 | In der Konstruktionsphase Straßen physisch ergänzen, verändern und entfernen; unvollständige Zwischenzustände zulassen und Interpretation anzeigen. | [`StreetDetectionIntegrationTest`](imagedetectiontests/StreetDetectionIntegrationTest.kt), [`StreetDetectionTest`](imagedetectiontests/StreetDetectionTest.kt), [`WorldEditorSessionPlacementTest`](worldeditortests/WorldEditorSessionPlacementTest.kt), [`SceneRenderSmokeTest`](scenestests/SceneRenderSmokeTest.kt) | Grün + Tischdemo TD-01 |
| F-06 | Erkennung von geraden/gebogenen Straßen, Kreuzungen, Orientierung und Ebenen aus Kameraaufnahmen. | [`StreetDetectionTest`](imagedetectiontests/StreetDetectionTest.kt), [`ArucoMarkerDetectionTest`](imagedetectiontests/ArucoMarkerDetectionTest.kt), [`ArucoTableRectifierTest`](imagedetectiontests/ArucoTableRectifierTest.kt) | Grün + Kamerademo |
| F-07 | Verbindungsfehler und Unvollständigkeiten verständlich und sichtbar anzeigen. | [`LevelValidationTest`](domaintests/LevelValidationTest.kt), [`ConstructionMarkerPositionTest`](renderingtests/ConstructionMarkerPositionTest.kt), [`SourceIndicatorColorsTest`](renderingtests/SourceIndicatorColorsTest.kt) | Grün + Tischdemo TD-02 |
| F-08 | Nur ein vollständiges, konsistentes Netz darf in die Simulation wechseln. | [`GameStateTest`](domaintests/GameStateTest.kt), [`LevelValidationTest`](domaintests/LevelValidationTest.kt), [`LevelSceneCountdownTest`](scenestests/LevelSceneCountdownTest.kt) | Grün |
| F-09 | Mehrere Fahrzeuge gemäß Quellenkonfiguration erzeugen, zur farbgleichen Senke bewegen, kürzesten euklidischen Weg wählen und bei Stau ausweichen. | [`VehicleGenerationFromMultipleSourcesTest`](domaintests/VehicleGenerationFromMultipleSourcesTest.kt), [`StreetPathFinderTest`](domaintests/StreetPathFinderTest.kt), [`VehicleTargetHandoverSimulationTest`](domaintests/VehicleTargetHandoverSimulationTest.kt), [`SimulationTest`](domaintests/SimulationTest.kt) | Grün |
| F-10 | Fahrzeugtypen Auto, Lastwagen und Motorrad unterscheiden. | [`MultiColorVehicleTest`](domaintests/MultiColorVehicleTest.kt), [`ConfigurationSceneVehicleTypeTest`](scenestests/ConfigurationSceneVehicleTypeTest.kt), [`AssetsTest`](uitests/AssetsTest.kt) | Grün |
| F-11 | Plausible Bewegung: Kurvengeschwindigkeit aus Radiusformel, rechtzeitiges Bremsen/Beschleunigen, maximale Beschleunigung/Geschwindigkeit, Mindestabstand 2 m und Kollisionsvermeidung. | [`CurveSpeedTest`](domaintests/CurveSpeedTest.kt), [`VehicleMovementManagerTest`](domaintests/VehicleMovementManagerTest.kt), [`VehicleAdvanceTest`](domaintests/VehicleAdvanceTest.kt), [`SimulationTest`](domaintests/SimulationTest.kt) | Grün |
| F-12 | Reißverschlussprinzip an Zusammenführungen. | [`SimulationTest`](domaintests/SimulationTest.kt) (Merge-/Round-Robin-Fälle) | Grün |
| F-13 | Verstopfung/Verklemmung erkennen; unzulässige Quellenkonfiguration beim Laden melden. | [`CongestionDetectorTest`](domaintests/CongestionDetectorTest.kt), [`SimulationTest`](domaintests/SimulationTest.kt), [`LevelValidationTest`](domaintests/LevelValidationTest.kt) | Grün |
| F-14 | Erfolg/Misserfolg, Fahrzeugbewegung, erzeugte Fahrzeuge, mittlere Geschwindigkeit und Statistikvergleich anzeigen. | [`SimulationTest`](domaintests/SimulationTest.kt), [`LevelSceneCountdownUnificationTest`](scenestests/LevelSceneCountdownUnificationTest.kt), [`SceneRenderSmokeTest`](scenestests/SceneRenderSmokeTest.kt) | Grün + Tischdemo TD-03 |
| F-15 | Simulation bei identischem Netz und gleichen Eingaben deterministisch wiederholen. | [`SimulationTest`](domaintests/SimulationTest.kt), Test `runPlayback_speedMultiplierKeepsDeterministicSimulationSequence` | Grün |
| F-16 | Einstellbare beschleunigte Simulation, mindestens 10x. | [`SimulationSpeedTest`](domaintests/SimulationSpeedTest.kt), [`GameSimulationConfigTest`](domaintests/GameSimulationConfigTest.kt) | Grün |
| F-17 | Straßennetze speichern und laden; vorbereitete Szenarien wiederverwenden. | [`LevelSerializerRoundtripTests`](persistencetests/LevelSerializerRoundtripTests.kt), [`LevelSerializerFileIOTest`](persistencetests/LevelSerializerFileIOTest.kt), [`SavedNetworksScene`](scenestests/LevelMenuSceneTest.kt) | Grün |
| F-18 | Bedienung am Projektionstisch ohne Maus/Tastatur; gespeicherte Netze auch am PC simulierbar. PC-Konstruktion ist nicht gefordert. | Szenen-/Steuerungstests: [`ControlButtonsTest`](scenestests/ControlButtonsTest.kt), [`MenuSceneTest`](scenestests/MenuSceneTest.kt), [`LevelMenuSceneTest`](scenestests/LevelMenuSceneTest.kt); vollständige Tischbedienung: TD-04 | Teilweise Grün; Tischdemo TD-04 |

## Nicht-funktionale Anforderungen und Rahmenbedingungen

| ID | Lastenheft-Anforderung | Prüfmethode und Nachweis | Status / Issue |
|---|---|---|---|
| N-01 | Kotlin, KorGE und JavaCV verwenden; Software auf dem Notebook mit Kamera und Projektor betreiben. | [`build.gradle.kts`](../build.gradle.kts), JVM-21-Workflow [`.github/workflows/gradle.yml`](../.github/workflows/gradle.yml) | Grün |
| N-02 | Anschauliche, ästhetische und intuitive Projektion; zusätzliche Landschaftsdetails möglich. | [`AssetsTest`](uitests/AssetsTest.kt), [`LevelViewRendererWeatherBackgroundTest`](renderingtests/LevelViewRendererWeatherBackgroundTest.kt), [`MenuSceneColorBlindnessTest`](scenestests/MenuSceneColorBlindnessTest.kt), TD-05 mit Zielgruppe | Demo |
| N-03 | Erkennung, Interpretation und Rückprojektion innerhalb von einer Sekunde. | Zeitstempel-Messung TD-06 (20 Wiederholungen, P95 ≤ 1 s) | Demo TD-06 |
| N-04 | Robust gegen leichte Verschiebung, Lichtwechsel, Perspektive und kurzzeitige Erkennungsfehler; verständliche Fehleranzeige. | [`ArucoTableRectifierTest`](imagedetectiontests/ArucoTableRectifierTest.kt), [`StreetDetectionTest`](imagedetectiontests/StreetDetectionTest.kt), TD-07 | Teilweise Grün + Demo TD-07 |
| N-05 | Wartbar/erweiterbar: Bildverarbeitung, Domäne, Simulation und Projektion trennen. | Paketgrenzen unter [`src/`](../src/), [`test/COVERAGE.md`](COVERAGE.md), CI-Testlauf | Dokumentiert |
| N-06 | Einheitlicher Maßstab 1:250 und Spielfeldverhältnis 9:5. | [`GameAreaScaleTest`](renderingtests/GameAreaScaleTest.kt), [`LevelSerializerRoundtripTests`](persistencetests/LevelSerializerRoundtripTests.kt) | Grün |
| N-07 | Barriereärmerer Farbmodus. | [`MenuSceneColorBlindnessTest`](scenestests/MenuSceneColorBlindnessTest.kt) | Grün |

## Liefer- und Abnahmeanforderungen

| ID | Lastenheft-Anforderung | Prüfmethode und Nachweis | Status / Issue |
|---|---|---|---|
| A-01 | Lauffähige Software und Testbericht. | JVM-Tests gemäß [`LASTENHEFT-TESTBERICHT.md`](LASTENHEFT-TESTBERICHT.md) | Grün nach dokumentiertem Gesamtlauf |
| A-02 | Satz physischer Straßenelemente einschließlich Brücken. | [`res/3dprinting/README_3D_printing.md`](../res/3dprinting/README_3D_printing.md), TD-01 | Demo |
| A-03 | Werbevideo von 60 bis 90 Sekunden. | [Videolink](../Werbevideo/finalesWerbevideo/finalesWerbevideoLink), TD-08 (Laufzeit/Abnahme prüfen) | Demo |
| A-04 | Abnahmedemo: Level laden/projizieren, Erkennung, sichtbare Fehler, Validierung/Start, Quellen/Senken, Verstopfung und Statistik. | TD-01 bis TD-04 und TD-08; fachliche Teilnachweise F-05 bis F-14 | Demo |
| A-05 | Sprint-Minima I1 bis I6. I1-I4 sind durch die obigen Tests rückverfolgbar; I5 (Kopplung/Video) und I6 (vollständige Fehlerfreiheit) stehen noch aus. | [`AI-Dev-Process/PRD.md`](../AI-Dev-Process/PRD.md) §§ 5.1 / Zeilen 280-287 | I1-I4 dokumentiert; I5-I6 teilweise offen |

## Reproduzierbare Tisch- und Hardware-Demos

| ID | Ablauf | Bestehensregel |
|---|---|---|
| TD-01 | Vorbereitetes Level laden, auf den kalibrierten Tisch projizieren, gerade Straße, Kurve, Kreuzung und Brücke auflegen/entfernen. | Jedes Element wird richtig markiert; Quellen/Senken bleiben unverändert. |
| TD-02 | Ungültige Verbindung und fehlenden Farbpfad erzeugen, danach korrigieren. | Hinweis/Markierung erscheint, verschwindet nach Korrektur, Start bleibt vorher gesperrt. |
| TD-03 | Vollständiges Netz starten; ein zweites absichtlich verstopfendes Netz starten. | Erfolg bzw. Verstopfung und Statistiken sind sichtbar. |
| TD-04 | Alle Spielaktionen ausschließlich über die Projektionsfläche ausführen; gespeichertes Netz zusätzlich mit Maus/Tastatur starten. | Kein Tischschritt benötigt Maus/Tastatur; PC-Modus kann laden/starten. |
| TD-05 | Drei Vertreter der Zielgruppe erledigen TD-01 ohne Einweisung. | Beobachtungen und missverstandene Bedienelemente im Abnahmeprotokoll festhalten. |
| TD-06 | 20 Tischänderungen filmen; Zeit zwischen stabiler Hand-entfernter Aufnahme und projizierter Aktualisierung messen. | P95 liegt bei höchstens 1.000 ms. |
| TD-07 | TD-01 bei drei Beleuchtungen, leichter Elementverschiebung und teilweiser Handverdeckung wiederholen. | Keine unverständliche Anzeige; Fehler ist verständlich markiert. |
| TD-08 | Finales Werbevideo abspielen und Dauer messen. | 60 bis 90 Sekunden, Produktmerkmale erkennbar. |
