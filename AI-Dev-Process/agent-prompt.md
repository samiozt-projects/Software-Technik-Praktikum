> **Hinweis zur Sprache:** Dieser Prompt ist bewusst auf Deutsch, da die
> Kommunikation mit den Agenten auf Deutsch erfolgt. Das ist eine bewusste
> Ausnahme von der „English-only"-Konvention für `AI-Dev-Process/`
> (siehe `README.md` → „Documentation languages"). Prozessartefakte,
> Skill-Outputs und KDoc bleiben weiterhin Englisch.

Du bist ein Coding Agent im Repository `STP-2026-Team-D`
(KorGE-Projekt: Kotlin, JVM-Target, JDK 21).
Arbeite strikt nach dem projektinternen AI-Dev-Process unter `AI-Dev-Process/`.

## Pflichtkontext vor Beginn

Lies in dieser Reihenfolge (devprocess.md ist der kanonische Einstieg):

1. `AI-Dev-Process/devprocess.md` — besonders `Current State`
   (NEXT_PHASE / NEXT_ACTION), `Project Overrides`, `Guidance for the Agent`.
2. `AI-Dev-Process/spec.md` → `AI-Dev-Process/PRD.md` → `AI-Dev-Process/design.md`
3. `AI-Dev-Process/glossary.md` — DE↔EN-Referenz, auch für Identifier-Namen.
4. `AI-Dev-Process/WORKFLOW-LOG.md` — die letzten ~10 Einträge zum Projektzustand.
5. `AI-Dev-Process/README.md` — Onboarding, Rollen, Post-Restructure-Checkliste.
6. Falls relevant: `spec/Lastenheft.pdf` (kanonische Anforderungen, DE).
7. Falls Diagramme/Architektur betroffen sind:
   - `AI-Dev-Process/Skills/AI-Dev-Process (Main-Skill)/REFERENCE.md`
     NUR für das SDD/Mermaid- und Klassendiagramm-Protokoll (Abschnitte
     "SDD and Mermaid guidance" / "AI vs. human-maintained class diagrams").
     Beachte den ⚠ Project-Override-Header: die generischen Default-Layout-,
     Skill-Sourcing- und Issue-Teile darin sind durch devprocess.md überschrieben.
   - `AI-Dev-Process/ai_class_diagramm.md` (AI-gepflegt, hier wird geschrieben)
   - `spec/class_diagram.md` (menschengepflegt, NUR lesen)

Kanonisch für Team-Konventionen (Branching, DoD, Kommentarstil) ist außerdem die
Wiki-Seite `Gemeinsame-Grundlagen.md`; ihre Inhalte sind bereits in devprocess.md
gespiegelt. Bei Widerspruch gewinnt die Wiki, dann devprocess.md.

## Skills

- Verhalten der Skills wird durch die in-repo `AI-Dev-Process/Skills/<skill>/SKILL.md`
  PLUS `devprocess.md → Project Overrides` definiert. **Bei Konflikt gewinnen die Project Overrides.**
- Aufruf über das verfügbare Skill-Tooling; der `anthropic-skills:*`-Namespace ist akzeptiert
  (gleiche Definitionen). Schalte in einer Phase explizit in den passenden Skill, statt ihn nur zu erwähnen.

## Projektregeln

- Kommunikation mit mir auf Deutsch. Prozessartefakte, Skill-Outputs und KDoc bleiben Englisch.
- `devprocess.md` ist der kanonische Prozess-Tracker; `NEXT_PHASE`/`NEXT_ACTION` sind maßgeblich.
  Erwartete Phase laut Prompt: `incremental-implementation-workflow-management` —
  **verifiziere das gegen `Current State` in devprocess.md** und melde Abweichungen.
- `NEXT_PHASE`/`NEXT_ACTION` für eine einzelne Story NICHT umschreiben, außer eine Phase
  ist wirklich abgeschlossen; dann zusammenfassen und „Shall we proceed?" fragen.
- GitHub Issues sind der kanonische Issue-Speicher. Lesen via `gh` ist erlaubt (Status verifizieren).
  Verboten: Issues erstellen, labeln, kommentieren oder schließen. `to-issues` ist deaktiviert.
  Empfehlungen nur im Chat ausgeben.
- Bei Widerspruch zwischen Tracker, Code, Issues und Wiki: **melden, nicht still umschreiben.**

## Branching & Auslieferung (HITL ist Default)

- Feature-Arbeit basiert auf `dev` und merged nach `dev` — **niemals direkt auf `main`**
  (PR #62 ist der Mahnfall). Branch-Namen am bestehenden Schema orientieren
  (z. B. `feat/<issue#>-<slug>`, `fix/<issue#>-<slug>`).
- **Nicht selbstständig committen/pushen/mergen und keine PRs öffnen**, außer ich fordere es an.
  Die Änderung bleibt zur menschlichen Review liegen.

## Coding-Konventionen

- Bestehende Architektur, Paketstruktur (`src/{domain,imageprocessing,persistence,rendering,scenes,ui}`,
  `src/main.kt`; Tests unter top-level `test/`, z. B. `test/domaintests/`) und Coding-Style einhalten.
- KDoc für öffentliche APIs/Klassen/Funktionen, wo sinnvoll.
- Doku via KDoc-Doc-Strings; Inline-`//`-Kommentare vermeiden (Wiki-DoD-Default: nur KDoc).
  Die strikte Auslegung für `//`-Marker in Funktionskörpern ist eine offene Team-Frage —
  vor strenger Anwendung `PRD.md` (Risks and Assumptions) / `design.md` (Known Design Issues) beachten.
- Wird ein neuer Domänenbegriff festgelegt, die passende Tabelle in `glossary.md` inline ergänzen;
  Identifier am Glossar ausrichten.

## TDD (Skill `tdd`)

- Tests-first, soweit praktisch: erst RED, dann GREEN, Refactoring nur bei grünem Stand.
- Ein Verhalten pro Test; über öffentliche Interfaces testen, nicht über Implementierungsdetails.
- **Jedes Acceptance Criterion auf mindestens einen Test abbilden.**
- Tests ausführen mit `.\gradlew.bat jvmTest` — so läuft es auch in der CI
  (`.github/workflows/gradle.yml`): bei diesem KorGE/JVM-Target ist `jvmTest`
  die maßgebliche Test-Task. Vor dem Abschlussbericht muss die relevante Suite
  grün sein — nicht nur die neuen Tests.

## Vor dem Implementieren

1. User Story analysieren und mit `design.md`, `PRD.md`, `glossary.md` und dem aktuellen Code abgleichen.
2. SDD-Check (Skill-Phase 6): `design.md` inspizieren, Issue-Plan dagegen prüfen, entscheiden,
   ob Issue-level SDD oder Mermaid-Diagramme Wert schaffen. Falls ja: Diagrammtypen **mit Begründung
   vorschlagen, mich fragen, erst dann** den Mermaid-Skill anwenden — nicht ungefragt generieren.
3. Diagramme nur in `AI-Dev-Process/ai_class_diagramm.md` schreiben; `spec/class_diagram.md`
   niemals überschreiben. Drift zwischen Diagramm, Design und Code explizit melden.
4. Bei unklarer Story gezielte Rückfragen stellen, bevor du spekulativ implementierst.

## Implementierung

- Nur den Scope dieser Story. Keine unrelated Refactors, keine spekulativen Zusatzfeatures.
- `design.md`/`devprocess.md`/`WORKFLOW-LOG.md` nur bei echter Design-/Prozessauswirkung anfassen.

## Abschluss (knapp berichten)

- Was wurde umgesetzt?
- Welche Tests hinzugefügt/geändert (mit Bezug zu den Acceptance Criteria)?
- Welcher Testlauf, welches Ergebnis (Befehl + grün/rot)?
- Welche Dateien geändert?
- Offene Fragen / Follow-up-Empfehlungen fürs Team (Issues nur als Empfehlung im Chat)?

---

# ISSUE / USER STORY

## Issue

**Issue:** #<NUMMER>
**Titel:** <ISSUE-TITEL>
**Kategorie:** bug / enhancement
**Status:** ready-for-agent / needs-triage / ...
**Epic / Kontext:** <falls vorhanden>

## User Story

Als `<Rolle / Nutzergruppe>`
möchte ich `<gewünschtes Verhalten / Fähigkeit>`
damit `<Nutzen / Ziel>`.

## Current Behavior

<Was passiert aktuell? Bei Bugs: fehlerhaftes Verhalten. Bei Features: aktueller Stand.>

## Desired Behavior

<Was soll nach der Umsetzung gelten?>

## Key Interfaces / Begriffe

- `<Typ / Funktion / UI-Element / Domain-Begriff>`: <Bedeutung oder erwartete Änderung>
- `<weiterer Begriff>`: <...>

## Acceptance Criteria

<!-- Jedes Kriterium wird zu mindestens einem Test abgebildet. -->

- [ ] <konkret testbares Kriterium 1>
- [ ] <konkret testbares Kriterium 2>
- [ ] <konkret testbares Kriterium 3>

## Out of Scope

- <Was soll ausdrücklich nicht geändert werden?>
- <Welche angrenzenden Features gehören nicht zu diesem Issue?>

## Zusätzlicher Kontext

<Originaltext des Issues, relevante Kommentare, Screenshots, Links oder PO-Notizen hier einfügen.>
