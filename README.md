# Beatbox – eigene Musik-Discs mit Lichtshow

Fabric-Mod für **Minecraft Java 26.3**.

## Was die Mod macht

- Aus jeder `.ogg`-Datei im Ordner `discs/` wird automatisch eine **Musik-Disc**
  (Kreativ-Reiter „Werkzeuge & Hilfsmittel“, bei den normalen Discs).
- Legst du so eine Disc in eine Jukebox, startet rund um die Jukebox eine
  **Partikel-Show im Takt**: Helix, Noten-Regenbogen, Funken-Druckwelle,
  Feuerwerk beim „Drop“ und Licht-Säulen.
- Eine Demo-Disc („Demo Beat“) ist schon dabei – der Song ist selbst erzeugt.

## Eigene Disc hinzufügen

1. **Musik vorbereiten:** Die Datei muss **OGG Vorbis** sein, am besten **mono**.
   - Audacity: *Spur → Mischen → Stereo zu Mono*, dann *Datei → Exportieren → OGG Vorbis*
   - oder mit ffmpeg: `ffmpeg -i lied.mp3 -ac 1 -c:a libvorbis -q:a 4 Mein_Song.ogg`
2. **Dateiname:** `Mein_Song.ogg` wird zur Disc „Mein Song“.
   Mit Tempo im Namen läuft die Show genau im Takt: `Mein_Song_128bpm.ogg`
   (ohne Angabe: 120 BPM).
3. Datei in den Ordner **`discs/`** legen.
4. Mod neu bauen (siehe unten) und die neue `.jar` in den `mods`-Ordner kopieren.

## Welche Musik?

Musik von Spotify & Co. ist kopiergeschützt und darf nicht heruntergeladen oder
umgewandelt werden – das funktioniert hier also nicht. Nimm Musik, die du
**selbst gemacht** hast oder die **ausdrücklich frei verwendbar** ist (z. B. CC0).

## Installieren

- Minecraft 26.3 mit **Fabric Loader 0.20.1** (oder neuer) und **Java 25**
- **Fabric API 0.165.0+26.3** in den `mods`-Ordner
- `beatbox-1.0.0.jar` ebenfalls in den `mods`-Ordner
- Auf Servern brauchen **Server und alle Spieler** die Mod.

## Bauen

**Mit GitHub (ohne Installation):** Projekt in ein GitHub-Repository hochladen.
Unter *Actions* baut GitHub die Mod automatisch; die fertige `.jar` steht im
letzten Lauf unter *Artifacts* → `beatbox-mod`.

**Auf dem eigenen PC:** Java 25 und Gradle 9.6 installieren, dann:

```
python3 tools/generate_discs.py
gradle build
```

Die fertige Datei liegt in `build/libs/beatbox-1.0.0.jar`.

## Wichtig

Dieser Stand wurde **noch nicht in Minecraft getestet** (es konnte nur der Aufbau
und die Disc-Erzeugung geprüft werden). Falls der Build oder das Spiel eine
Fehlermeldung zeigt: Meldung kopieren und weitergeben, dann wird sie behoben.
