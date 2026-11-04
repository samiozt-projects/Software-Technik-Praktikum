# Performance-Benchmark mit Java Flight Recorder (JFR)

Diese Anleitung beschreibt, wie der Benchmark der Verkehrssimulation auf dem
Raspberry Pi 5 durchgeführt wird: JFR-Aufnahme starten, Benchmark-Level
laufen lassen, Aufnahme stoppen und mit dem Python-Skript auswerten.

---

## Voraussetzungen

- Volles JDK (21) (enthält `jcmd`, `jps` und `jfr`)
- Python 3
- `auswertung.py` (liegt in diesem Ordner)

Kurz prüfen, ob alles da ist (übers Terminal):

```bash
jcmd -version
jfr --version
python3 --version
```

> **Wichtig:** Ein reines JRE reicht nicht – `jcmd` und `jfr` sind nur im
> vollen JDK enthalten.

---

## 1. Anwendung starten
-> über .exe Datei

## 2. Prozess-ID (PID) der Anwendung finden

In einem Terminal:

```bash
jps -l
```

Die Ausgabe zeigt alle laufenden Java-Prozesse. Die Zeile mit der
Simulation heraussuchen und die Nummer am Zeilenanfang merken (= PID).

-> Nun die richtige Zeile heraussuchen (meist app.jar oder game.jar) und die linke ID merken, welches die PID ist

## 3. JFR-Aufnahme starten

```bash
jcmd <PID> JFR.start name=bench settings=profile filename=bench.jfr
```

- `name=bench` – frei wählbares Label, wird zum Stoppen wieder gebraucht
- `settings=profile` – detaillierte Aufzeichnung (Method-Sampling etc.)
- `filename=bench.jfr` – Zieldatei (wird erst beim **Stoppen** geschrieben)

Prüfen, ob die Aufnahme läuft:

```bash
jcmd <PID> JFR.check
```

## 4. Benchmark-Level starten

In der Anwendung das Benchmark-Level starten:

> Zur Auswahl stehen 100/200/500/1000 Fahrzeuge

Wichtig dabei:

- Die Simulation muss **aktiv laufen** (Zustand `SIMULATION`). Das
  FPS-Event wird nur in diesem Zustand aufgezeichnet – Frames im Editor
  oder in der Pause fließen nicht in die Auswertung ein.
- Das Level für die gewünschte Messdauer laufen lassen, empfohlen sind
  **mindestens 60–90 Sekunden**.
- Während der Messung nichts anderes auf dem Pi laufen lassen (kein
  Browser, keine Updates), sonst wird das Ergebnis verfälscht.

## 5. JFR-Aufnahme stoppen

```bash
jcmd <PID> JFR.stop name=bench
```

Erst jetzt wird `bench.jfr` vollständig geschrieben. Kurz prüfen, dass die
Datei nicht leer ist

Eine Datei mit **0 Bytes** bedeutet: Die Aufnahme wurde nicht sauber
gestoppt (z. B. App vorher beendet). In dem Fall die Messung wiederholen.

## 6. Auswertung starten

```bash
python3 auswertung.py bench.jfr
```

Beispielausgabe:

```
Auswertung von: bench.jfr
----------------------------------------
Speicher (RSS) (Ø)  : 2549 MB
CPU            (Ø)  : 2.4 %
FPS            (Ø)  : 60.3
FPS            (min): 10.0
FPS            (max): 250.0
Frames              : 5002
```

Bedeutung der Werte:

- **Speicher (RSS)** – tatsächlicher Speicherverbrauch des gesamten
  Prozesses (inkl. JVM-Overhead). Fehlt das RSS-Event (z. B. bei Aufnahmen
  unter Windows), weicht das Skript automatisch auf den Java-Heap aus.
- **CPU** – durchschnittliche CPU-Last der Anwendung (JVM-User-Anteil).
- **FPS** – nur Frames im Zustand `SIMULATION`; Menü- und Pause-Frames
  werden herausgefiltert.

---

## Häufige Probleme

| Problem | Ursache / Lösung |
|---|---|
| `bench.jfr` ist 0 Bytes groß | Aufnahme wurde nie gestoppt oder App vorher beendet → Messung wiederholen, `JFR.stop` nicht vergessen |
| `jcmd: command not found` | Nur JRE installiert → volles JDK installieren (siehe Voraussetzungen) |
| Prozess taucht in `jps` nicht auf | App läuft unter anderem Benutzer → `sudo -u <user> jps -l` |
| `CPU: keine Daten` in der Auswertung | Aufnahme stammt von Windows – dort liefert JFR kein `jdk.CPULoad`/`jdk.ResidentSetSize`. Für den Testbericht auf dem **Raspberry Pi** messen. |
| FPS: keine Simulations-Frames | Das Level wurde während der Aufnahme nicht gestartet oder lief nur im Editor/Pause-Modus |

---
