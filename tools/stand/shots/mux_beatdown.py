"""Lays the game's own sound under a film of Star Platinum's beatdown (G, 2.64.0; 2.65.0), its sound where the game plays it.
usage: python3 tools/stand/shots/mux_beatdown.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the beatdown: its blow's "ORA!" starts StarPlatinumRules.BEAT_OPENER_SOUND
(13, 2.65.0: its blow slower; 1 in 2.64.0) ticks into it and, if it struck, its cutscene's sound (the rest of the user's
recording, StandBeatdown) StarPlatinumRules.BEAT_SOUND (35; 23 in 2.64.0) ticks into it on each client. The cutscene's
sound is the long one, so the recording is laid at it: found in the recording by its own waveform and laid where the
film run's beatdown started (+35 ticks); the opener's ORA and the stand coming out come with it as the sound run played
them, a tick either way. An entry whose blow began no cutscene (its guard broken, or nobody struck) is laid at its ORA
instead (+13 ticks).
"""
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402

CUTSCENE = {'combo': '7', 'lead': 35, 'file': 'star_platinum_beatdown.ogg', 'sound': 'celestialfall:star_platinum_beatdown', 'name': 'beatdown'}
ORA = {'combo': '7', 'lead': 13, 'file': 'star_platinum_beatdown_ora.ogg', 'sound': 'celestialfall:star_platinum_beatdown_ora', 'name': "beatdown's ORA"}

if __name__ == '__main__':
    with open(os.path.join(sys.argv[1], sys.argv[3], 'sounds.csv')) as fh:
        played = {r['sound'] for r in csv.DictReader(fh)}
    main(CUTSCENE if CUTSCENE['sound'] in played else ORA)
