# Testbericht Lastenheft

Stand: 2026-07-13

Dieser Bericht verweist auf die vollständige
[Prüfliste](LASTENHEFT-PRUEFLISTE.md). Er trennt automatisierte Nachweise von den vorgesehenen
Hardware- und Abnahmedemos.

## Automatisierter Qualitätsnachweis

Der reguläre, plattformunabhängige Qualitätsnachweis ist der JVM-Testlauf (mit Konfiguration der Umgebungsvariablen mit Beispielpfad auf Windows):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat jvmTest --no-daemon
```

- Java 21 ist verbindlich; Java 25 führt bei diesem KorGE-Build bereits beim Start zu einem
  Gradle-Fehler.
- Der Workflow [`.github/workflows/gradle.yml`](../.github/workflows/gradle.yml) führt denselben
  JVM-Test auf Pushes nach `main` aus. Der Teamscale-Workflow führt ihn auf `main` und `dev` aus
  und lädt die JUnit- und JaCoCo-Ergebnisse hoch.
- Die einzelnen fachlichen Nachweise sind pro Anforderung in
  [`LASTENHEFT-PRUEFLISTE.md`](LASTENHEFT-PRUEFLISTE.md) verlinkt. Sie decken Modell,
  Validierung, Erkennung, Simulation, Persistenz, Rendering, UI und Netzwerkkontrakte ab.

## Teststand

- Einzelne lokale Testklassen lassen sich in der Standardumgebung ausführen.
- Der vollständige JVM-Testlauf benötigt weiterhin eine erreichbare Testumgebung für die
  externen Prüfpfade. Im aktuellen Lauf wurde nach fünf Minuten kein Ergebnis erreicht.
- Ein vollständiges grünes Gesamtergebnis wird erst nach einem erfolgreichen Durchlauf
  dokumentiert.
