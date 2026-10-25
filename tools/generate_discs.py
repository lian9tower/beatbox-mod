#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Beatbox - Disc-Generator

Liest alle .ogg-Dateien im Ordner "discs/" und erzeugt daraus alles, was
Minecraft fuer eine Musik-Disc braucht (Sound, Jukebox-Song, Item, Name).

Benutzung:  python3 tools/generate_discs.py

Dateinamen:
  Mein_Song.ogg            -> Disc "Mein Song"  (Standard-Takt 120 BPM)
  Mein_Song_128bpm.ogg     -> Disc "Mein Song"  mit 128 BPM (Licht-Show im Takt)

Die Datei muss OGG Vorbis sein (am besten mono, 44100 Hz).
Nur Python-Standardbibliothek - es muss nichts installiert werden.
"""
import hashlib
import json
import math
import re
import shutil
import struct
import sys
import unicodedata
from pathlib import Path

MOD_ID = "beatbox"
ROOT = Path(__file__).resolve().parent.parent
DISCS_DIR = ROOT / "discs"
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / MOD_ID
DATA = RES / "data" / MOD_ID
TEXTURE_VARIANTS = 6  # textures/item/vinyl_0.png ... vinyl_5.png
DEFAULT_BPM = 120


def read_ogg_info(path):
    """Gibt (Kanaele, Samplerate, Laenge in Sekunden) einer OGG-Vorbis-Datei zurueck."""
    data = path.read_bytes()
    if not data.startswith(b"OggS"):
        raise ValueError("keine gueltige OGG-Datei")
    pos = data.find(b"\x01vorbis")
    if pos < 0:
        if b"OpusHead" in data[:512]:
            raise ValueError("das ist OGG/Opus - Minecraft braucht OGG Vorbis "
                             "(Export mit Audacity oder: ffmpeg -i in.mp3 -c:a libvorbis out.ogg)")
        raise ValueError("kein Vorbis-Audio gefunden")
    channels = data[pos + 11]
    rate = struct.unpack("<I", data[pos + 12:pos + 16])[0]
    granule = -1
    p = data.rfind(b"OggS")
    while p >= 0:
        if p + 14 <= len(data) and data[p + 4] == 0:
            g = struct.unpack("<q", data[p + 6:p + 14])[0]
            if g > 0:
                granule = g
                break
        p = data.rfind(b"OggS", 0, p)
    if granule <= 0 or rate <= 0:
        raise ValueError("Laenge der Datei konnte nicht ermittelt werden")
    return channels, rate, granule / rate


def slugify(text):
    for a, b in (("\u00e4", "ae"), ("\u00f6", "oe"), ("\u00fc", "ue"), ("\u00c4", "ae"),
                 ("\u00d6", "oe"), ("\u00dc", "ue"), ("\u00df", "ss")):
        text = text.replace(a, b)
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    DISCS_DIR.mkdir(exist_ok=True)
    files = sorted(DISCS_DIR.glob("*.ogg"), key=lambda f: f.name.lower())

    # Alte, automatisch erzeugte Dateien wegraeumen (Texturen bleiben!)
    for folder in (ASSETS / "sounds" / "records", ASSETS / "items", ASSETS / "models" / "item",
                   DATA / "jukebox_song", ASSETS / "lang"):
        if folder.exists():
            shutil.rmtree(folder)
    for f in (ASSETS / "sounds.json", ASSETS / "discs.txt"):
        if f.exists():
            f.unlink()

    sounds = {}
    index_lines = ["# id|titel|laenge_sekunden|bpm   (automatisch erzeugt - nicht von Hand aendern)"]
    lang = {}
    used_ids = set()
    problems = 0

    for number, path in enumerate(files):
        stem = path.stem
        bpm = DEFAULT_BPM
        m = re.search(r"[ _\-]+(\d{2,3})\s*bpm$", stem, re.IGNORECASE)
        if m:
            bpm = int(m.group(1))
            stem = stem[:m.start()]
        title = stem.replace("_", " ").replace("|", " ").strip() or "Disc %d" % (number + 1)
        disc_id = slugify(stem) or "disc_%d" % (number + 1)
        base = disc_id
        suffix = 2
        while disc_id in used_ids:
            disc_id = "%s_%d" % (base, suffix)
            suffix += 1
        used_ids.add(disc_id)

        try:
            channels, rate, length = read_ogg_info(path)
        except ValueError as err:
            print("  [FEHLER] %s: %s" % (path.name, err))
            problems += 1
            continue

        length = math.ceil(length * 10) / 10.0
        variant = int(hashlib.md5(disc_id.encode("utf-8")).hexdigest(), 16) % TEXTURE_VARIANTS

        # 1) Audiodatei + Sound-Eintrag
        target = ASSETS / "sounds" / "records" / (disc_id + ".ogg")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        sounds["record." + disc_id] = {"sounds": [{"name": "%s:records/%s" % (MOD_ID, disc_id), "stream": True}]}

        # 2) Jukebox-Song (Daten)
        write_json(DATA / "jukebox_song" / (disc_id + ".json"), {
            "comparator_output": (number % 15) + 1,
            "description": {"text": title},
            "length_in_seconds": length,
            "sound_event": {"sound_id": "%s:record.%s" % (MOD_ID, disc_id)},
        })

        # 3) Item-Aussehen
        write_json(ASSETS / "items" / ("disc_%s.json" % disc_id), {
            "model": {"type": "minecraft:model", "model": "%s:item/disc_%s" % (MOD_ID, disc_id)}})
        write_json(ASSETS / "models" / "item" / ("disc_%s.json" % disc_id), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/vinyl_%d" % (MOD_ID, variant)}})

        # 4) Name + Index fuer den Java-Code
        lang["item.%s.disc_%s" % (MOD_ID, disc_id)] = title
        index_lines.append("%s|%s|%s|%d" % (disc_id, title, length, bpm))

        note = "" if channels == 1 else "  (Stereo - Mono klingt wie echte Discs: leiser, je weiter weg)"
        print("  [OK] %-28s -> beatbox:disc_%s   %.1f s, %d BPM%s" % (path.name, disc_id, length, bpm, note))

    write_json(ASSETS / "sounds.json", sounds)
    write_json(ASSETS / "lang" / "en_us.json", lang)
    write_json(ASSETS / "lang" / "de_de.json", lang)
    (ASSETS / "discs.txt").write_text("\n".join(index_lines) + "\n", encoding="utf-8")

    print("%d Disc(s) erzeugt, %d Problem(e)." % (len(files) - problems, problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
