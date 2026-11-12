"""Lays the game's own sound under a film of X Chara's basic attack (2.118.0), its sound where the game plays it.
usage: python3 -I tools/xchara/shots/mux_slash.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the knife's swings (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Every swing plays the one sound (xchara_slash, the user's own:
tools/xchara/extract_slash.py) KnifeClient.SOUND_BEFORE ticks before its blow, and the runs hear of a swing a tick
sooner or later than each other. So the recording is laid at the combo's first swing: its sound (a hiss of noise, found
well by its own waveform) is found in the sound run's recording near where its log puts it, and goes where the film
run's first swing started (the first row of its stand.csv with a swing in it, kc_hit 1 with the hack on or 5 with it
off: its tick less the swing's age) plus the ticks the clients wait before they play it (the first swing's blow is 4
ticks in: 2); the other swings' sounds come with it as the sound run played them, a tick either way.
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock the combo's first swing started on: its first row with one, less the swing's age (ticks)."""
    for r in stand:
        if r.get('kc_hit') in ('1', '5') and r.get('kc_age') not in (None, ''):
            return math.ceil(float(r['t']) - float(r['kc_age']) - 1e-3)
    return None


FIRST = {'lead': 2, 'file': 'xchara_slash.ogg', 'sound': 'celestialfall:xchara_slash', 'name': "knife's first swing", 'start': started}

if __name__ == '__main__':
    main(FIRST)
