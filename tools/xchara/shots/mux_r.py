"""Lays the game's own sound under a film of X Chara's R with the hack on, the cleave (2.121.0).
usage: python3 -I tools/xchara/shots/mux_r.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_slash.py, for the cleave (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its one sound (xchara_r, tools/xchara/make_r_sound.py) plays as
the client hears of the skill, and the runs hear of it a tick sooner or later than each other. So the sound is found in
the sound run's recording by its own waveform near where its log puts it, and goes where the film run's cleave started
(the first row of its stand.csv with the knife's hit 9 in it, kc_hit: its tick less the hit's age).
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock the cleave started on: its first row with it, less its age (ticks)."""
    for r in stand:
        if r.get('kc_hit') == '9' and r.get('kc_age') not in (None, ''):
            return math.ceil(float(r['t']) - float(r['kc_age']) - 1e-3)
    return None


FIRST = {'lead': 0, 'file': 'xchara_r.ogg', 'sound': 'celestialfall:xchara_r', 'name': 'cleave', 'start': started}

if __name__ == '__main__':
    main(FIRST)
