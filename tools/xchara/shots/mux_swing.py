"""Lays the game's own sound under a film of X Chara's plain swings (2.117.0, the hack off), its sounds where the game plays them.
usage: python3 -I tools/xchara/shots/mux_swing.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_m1.py for the hack's four hits (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds), laid at the combo's first swing: its sound (xchara_swing_1, a
knife through the air: noise, found well by its own waveform) is found in the sound run's recording near where its log
puts it, and goes where the film run's first swing started (the first row of its stand.csv with kc_hit 5, the plain
first: its tick less the swing's age) plus the tick the clients wait before they play it (KnifeClient.SOUND_LEAD: the
files start there, tools/xchara/make_m1_sounds.py); the other swings' sounds and the blows' come with it as the sound
run played them, a tick either way.
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock the combo's first plain swing started on: its first row with that hit, less the swing's age (ticks)."""
    for r in stand:
        if r.get('kc_hit') == '5' and r.get('kc_age') not in (None, ''):
            return math.ceil(float(r['t']) - float(r['kc_age']) - 1e-3)
    return None


FIRST = {'lead': 1, 'file': 'xchara_swing_1.ogg', 'sound': 'celestialfall:xchara_swing_1', 'name': "knife's first plain swing", 'start': started}

if __name__ == '__main__':
    main(FIRST)
