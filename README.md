# Interaktiver Projektionstisch – Verkehrssimulation (Team D)

Dieses Repository enthält die Software eines interaktiven Projektionstisch-Systems zur Konstruktion und Simulation von Straßennetzen. Auf einem physischen Tisch werden Straßenelemente aufgelegt, per Kamera erkannt, als digitales Straßennetz interpretiert und zurück auf den Tisch projiziert. Anschließend kann eine Verkehrssimulation gestartet werden.

Diese Anleitung richtet sich an Personen, die das fertige Programm bedienen möchten – Programmierkenntnisse sind nicht erforderlich.

## Voraussetzungen

- **Java**: JDK 21 (z. B. Amazon Corretto oder Temurin). Gradle und Kotlin müssen nicht installiert werden – der mitgelieferte Gradle-Wrapper lädt alles Weitere automatisch.
- **Hardware**: USB-Kamera (Standard: „1080P FHD Camera“) und ein Projektor über dem Tisch
- **Netzwerk** (nur für den Multi-Tisch-Modus): Zugang zum Kopplungsserver. Server-Adresse und Port stehen in `config/app-config.properties` (Abschnitt `network.*`); mit `network.active=true` wird die Kopplung aktiviert.
- **Ohne Tisch-Hardware**: Gespeicherte Netze können auch am normalen PC mit Maus und Tastatur geladen und simuliert werden.

## Programm starten

Im Projektverzeichnis ein Terminal öffnen und ausführen:

```
Windows:      .exe Starten
Linux/macOS:  java -jar game.jar
```

Das Programm öffnet sich in einem Fenster und startet mit dem der Kalibrierung. Hierfür mit **F11** den Vollbildmodus einschalten für eine korrekte Kalibrierung, wenn ohne Kamera getestet wird dies überspringen

**Ersteinrichtung:** Vor der ersten Verwendung sollte in `config/app-config.properties` geprüft werden, welche Kamera verwendet wird und wo im Kamerabild sich das Spielfeld befindet. Am besten zunächst mit der Standardkonfiguration starten und nur bei Bedarf anpassen. Änderungen wirken erst nach einem Neustart. Details: [config/README.md](config/README.md).

## Bedienung

Das Spiel funktioniert, wenn keine Anpassungen nötig sind und alleinig auf einem Tisch gespielt wird auch ohne Maus und Tastatur. Der **Zahnrad-Button** öffnet das Pausenmenü mit den Punkten *Level speichern*, *Levelauswahl*, *Konfiguration* und *Multi-Tisch-Modus*.

### Level laden

Im Pausenmenü **Levelauswahl** wählen. Dort stehen die mitgelieferten Level sowie unter **Gespeicherte Netze** die eigenen Speicherstände zur Auswahl. Das gewählte Level wird mit seinen Quellen (Startpunkte der Fahrzeuge) und Senken (Ziele) auf den Tisch projiziert.

### Netz erkennen

In der Konstruktionsphase werden physische Straßenelemente (Geraden, Kurven, Kreuzungen, Brücken) auf den Tisch gelegt. Die Kamera erkennt die Elemente automatisch, und die Interpretation wird direkt zurückprojiziert – auch unvollständige Zwischenstände.

### Validieren

Das Netz wird laufend geprüft. Fehler – etwa fehlende Verbindungen oder ein fehlender Weg von einer Quelle zur farbgleichen Senke – werden sichtbar auf dem Tisch markiert. Solange das Netz nicht vollständig und gültig ist, bleibt der Simulationsstart gesperrt.

### Simulation starten

Der **Start-Button** (oben links) startet die Simulation, sobald das Netz gültig ist. Fahrzeuge fahren dann von ihren Quellen zu den farblich passenden Senken. Derselbe Button pausiert die laufende Simulation. Am Ende zeigt das Programm Erfolg („Level geschafft“) oder Misserfolg („Netz verstopft“) sowie Statistiken wie erzeugte Fahrzeuge, mittlere Geschwindigkeit und Straßennetzlänge an.

### Speichern und Laden

Im Pausenmenü **Level speichern** wählen – das aktuelle Straßennetz wird als Datei im Ordner `savedlevel/` abgelegt. Gespeicherte Netze lassen sich später über *Levelauswahl → Gespeicherte Netze* wieder laden, auch am PC ohne Tisch.

## Konfiguration

Im Pausenmenü **Konfiguration** wählen (im Challenge-Modus gesperrt). Dort gibt es:

- **Quellen**: Für jede Quelle lassen sich Farbe, Erzeugungshäufigkeit (in Sekunden) und initiale Geschwindigkeit der Fahrzeuge einstellen.
- **Fahrzeugtypen**: Auto, Lastwagen und Motorrad mit ihren Eigenschaften (z. B. Maximalgeschwindigkeit).
- **Wetter**: Auswahl der Wetterbedingung, die Darstellung und Simulation beeinflusst.

Die **Simulationsgeschwindigkeit** wird direkt auf der Spielfläche neben dem Start-Button gewählt (mehrere Stufen bis mindestens 10-fach). Ein Standardwert kann zusätzlich in `config/app-config.properties` (`simulation.speed`) hinterlegt werden.

## Multi-Tisch-/Server-Modus

Mehrere Tische können über einen Kopplungsserver zu einer gemeinsamen Welt verbunden werden; Fahrzeuge fahren dann über Tischgrenzen hinweg.

1. In `config/app-config.properties` den Server eintragen (`network.host`, `network.port`, `network.base_url`) und `network.active=true` setzen, dann das Programm neu starten.
2. Im Pausenmenü **Multi-Tisch-Modus** wählen – hier wird die Verbindung zum Server aufgebaut und angezeigt.
3. Über die **Weltauswahl** einer bestehenden Welt beitreten oder mit **Neue Welt erstellen** eine eigene Welt anlegen.
4. Anschließend die eigene **Tisch-Kachel** in der Welt wählen – diese Position übernimmt der Tisch in der gemeinsamen Welt.

Im Multi-Tisch-Modus stehen außerdem *Welt speichern*, ein *Welt-Editor* zum Bearbeiten der Welt und die Fahrzeugkonfiguration zur Verfügung. Bricht die Verbindung ab, wechselt das Programm automatisch zurück ins Multi-Tisch-Menü und versucht, die Verbindung wiederherzustellen („Automatischer Reconnect läuft…“).

## Bekannte Einschränkungen und Fehlerbehebung

- **Kamera wird nicht gefunden**: In `config/app-config.properties` den Kameranamen (`camera.model.name`) oder den Index (`camera.default.index`) anpassen und neu starten.
- **Erkennung ungenau**: Für gleichmäßige Beleuchtung sorgen und den Spielfeldausschnitt (`imageprocessing.pre_crop.*`) prüfen. Die Hand nach dem Auflegen eines Elements kurz vom Tisch nehmen.
- **Konfigurationsänderungen wirken nicht**: Änderungen an `config/app-config.properties` erfordern immer einen Neustart des Programms.
- **Challenge-Modus**: Im Challenge-Modus ist das Konfigurationsmenü absichtlich gesperrt und die Netzgröße begrenzt.
- **Multi-Tisch ohne Server**: Ohne erreichbaren Kopplungsserver (oder mit `network.active=false`) steht der Multi-Tisch-Modus nicht zur Verfügung; der Einzeltisch-Betrieb funktioniert unabhängig davon.

## Technische Details und weitere Dokumentation

- Sprache: Kotlin, Visualisierung: KorGE, Bilderkennung: JavaCV
- Zielplattform: Notebook bzw. Raspberry Pi mit Kamera und Projektor
- Konfigurationsreferenz: [config/README.md](config/README.md)
- 3D-Druck der Straßenelemente: [res/3dprinting/README_3D_printing.md](res/3dprinting/README_3D_printing.md)

### AI-assistierte Entwicklung

Dieses Projekt nutzt einen strukturierten, dokumentierten AI-Entwicklungsprozess. Alle Prozess-Artefakte sowie eine eingefrorene Kopie der verwendeten Skill-Definitionen liegen unter [`AI-Dev-Process/`](AI-Dev-Process/). Einstiegspunkt für neue Teammitglieder: [`AI-Dev-Process/README.md`](AI-Dev-Process/README.md).
