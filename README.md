# Quadrant Wars

Quadrant Wars ist ein kleines rundenbasiertes Strategiespiel in Java. Die Idee ist aehnlich wie Risiko, aber einfacher:

- Spieler kontrollieren Felder.
- Jedes Feld hat Soldaten.
- Pro Runde bekommt ein Spieler neue Soldaten.
- Man kann nur direkte Nachbarfelder angreifen.
- Je mehr Angreifer gegenueber Verteidigern eingesetzt werden, desto hoeher ist die Chance auf Eroberung.
- Wer alle Felder kontrolliert, gewinnt.

## Aktueller Prototyp

Der aktuelle Prototyp ist ein kleines Swing-Fensterspiel als Simulation:

- Die Spieler heissen nach ihren Farben: `Blau`, `Rot`, `Gruen` und `Gelb`.
- Die Spielfeldgroesse ist einstellbar: `4 x 4`, `8 x 8`, `12 x 12` oder `16 x 16`.
- Die Standardgroesse ist `8 x 8`, die groesste Groesse ist `16 x 16`.
- Das Spielfeld ist sichtbar in vier Quadranten geteilt.
- Jeder Spieler startet in einem eigenen Quadranten.
- Jedes Feld zeigt Besitzer und Soldatenzahl.
- Felder sind nach Spielerfarbe markiert.
- Wer einen ganzen Quadranten kontrolliert, bekommt je nach Kartengroesse Bonus-Soldaten.
- Das Angriffslimit ist dynamisch: Wer mehr Felder kontrolliert, bekommt mehr moegliche Angriffe pro Zug.
- Die KI greift nur an, wenn die Chance sinnvoll ist oder ein Quadrant dadurch erobert werden kann.
- Felder im Hauptgebiet verteidigen normal mit `100%`.
- Abgeschnittene Neben-Gebiete verteidigen schwaecher: Ihre Soldaten zaehlen im Kampf nur zu `85%`.
- Einzelne isolierte Felder verteidigen noch schwaecher: Ihre Soldaten zaehlen im Kampf nur zu `75%`.
- Erfolgreich eroberte Felder behalten mindestens `2` Soldaten.
- Spieler mit deutlich weniger Feldern bekommen eine kleine Aufhol-Verstaerkung.
- In Runde 1 bekommen spaeter startende Farben kleine Bonus-Soldaten, damit der erste Zug weniger Vorteil bringt.
- Die KI verteilt Verstaerkungen auf mehrere sinnvolle Frontfelder, statt alles auf ein Feld zu stapeln.
- Nach der Angriffsphase kann ein Spieler einmal Soldaten zwischen verbundenen eigenen Feldern verschieben.
- Oben werden die Gesamt-Soldaten und die kontrollierten Felder aller Spieler angezeigt.
- Die Startreihenfolge rotiert pro Runde, damit nicht immer dieselbe Farbe den Anzugsvorteil hat.
- Das Spielfeld wird nicht mehr nach jeder einzelnen Verstaerkung neu gezeichnet, sondern nur nach Angriffen.
- Du kannst entweder nur zuschauen oder selbst `Blau`, `Rot`, `Gruen` oder `Gelb` uebernehmen.
- Im eigenen Zug setzt du Verstaerkungen per Klick, waehlt Start- und Zielfeld, greifst an, verschiebst einmal Soldaten oder beendest den Zug.
- Mit `Skip andere` kannst du alle Zuege der anderen Farben ueberspringen, bis du wieder am Zug bist.
- Mit `Start`, `Pause`, `Ein Schritt`, `Skip andere`, `Neu starten`, `Geschwindigkeit`, `Feldgroesse` und `Mitspielen` steuerst du die Simulation.
- Im Log steht nach jedem Schritt, was genau passiert ist.

## Starten

In IntelliJ kann `Main.java` direkt gestartet werden.

Alternativ im Terminal:

```bash
javac -d out src/*.java
java -cp out Main
```

## Gute naechste Schritte

1. Animationen fuer Verstaerkung und Angriff einbauen.
2. Speichern und Laden eines Spielstands ermoeglichen.
