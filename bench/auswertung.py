#!/usr/bin/env python3
"""
jfr_simple.py - Liest eine JFR-Datei und gibt Speicher, CPU und FPS aus.

Aufruf:  python3 jfr_simple.py bench.jfr

Hinweis: Nicht jede Plattform liefert alle Events. Unter Windows fehlen
z. B. jdk.ResidentSetSize und jdk.CPULoad - dann werden diese Werte
uebersprungen bzw. der Java-Heap als Speicherwert benutzt.
"""

import json
import subprocess
import sys


# 1. Dateiname von der Kommandozeile holen (Pflichtangabe)
if len(sys.argv) != 2:
    print("Aufruf: python3 jfr_simple.py <datei.jfr>")
    sys.exit(1)

datei = sys.argv[1]


# 2. Hilfsfunktion: liest alle Events eines Typs aus der JFR-Datei.
#    Dazu rufen wir das JDK-Werkzeug "jfr" auf, das die Events als JSON
#    ausgibt. Dieses JSON lesen wir dann mit Python ein.
def lies_events(typ):
    befehl = ["jfr", "print", "--json", "--events", typ, datei]
    ausgabe = subprocess.run(befehl, capture_output=True, text=True).stdout
    events = json.loads(ausgabe)["recording"]["events"]
    return [e["values"] for e in events]


# 3. Hilfsfunktion: Durchschnitt einer Liste (0, wenn die Liste leer ist)
def durchschnitt(zahlen):
    if len(zahlen) == 0:
        return 0
    return sum(zahlen) / len(zahlen)


# 4. FPS einsammeln - nur Frames, in denen die Simulation wirklich laeuft
frames = lies_events("simulation.Frame")
fps_werte = []
for f in frames:
    if f["simulationState"] == "SIMULATION":
        fps_werte.append(f["fps"])


# 5. CPU-Auslastung einsammeln (Werte gehen von 0.0 bis 1.0, daher * 100)
cpu_events = lies_events("jdk.CPULoad")
cpu_werte = []
for c in cpu_events:
    cpu_werte.append(c["jvmUser"] * 100)


# 6. Speicher einsammeln (Werte sind Bytes, daher / 1024 / 1024 fuer MB).
#    Bevorzugt RSS (ganzer Prozess). Fehlt das Event (Windows), nehmen wir
#    ersatzweise den Java-Heap.
speicher_werte = []
speicher_name = "Speicher (RSS)"

for s in lies_events("jdk.ResidentSetSize"):
    speicher_werte.append(s["size"] / 1024 / 1024)

if len(speicher_werte) == 0:
    speicher_name = "Speicher (Heap)"
    for h in lies_events("jdk.GCHeapSummary"):
        speicher_werte.append(h["heapUsed"] / 1024 / 1024)


# 7. Ergebnisse ausgeben
print()
print("Auswertung von:", datei)
print("-" * 40)

if len(speicher_werte) > 0:
    print(speicher_name, "(Ø)  :", round(durchschnitt(speicher_werte)), "MB")
else:
    print("Speicher       : keine Daten in der Aufnahme")

if len(cpu_werte) > 0:
    print("CPU            (Ø)  :", round(durchschnitt(cpu_werte), 1), "%")
else:
    print("CPU            : keine Daten (unter Windows nicht verfuegbar)")

if len(fps_werte) > 0:
    print("FPS            (Ø)  :", round(durchschnitt(fps_werte), 1))
    print("FPS            (min):", round(min(fps_werte), 1))
    print("FPS            (max):", round(max(fps_werte), 1))
    print("Frames              :", len(fps_werte))
else:
    print("FPS            : keine Simulations-Frames in der Aufnahme")
print()