"""Lays the game's own sound under a film of X Chara's Q, the hack (2.115.0), its sounds where the game plays them.
usage: python3 -I tools/xchara/shots/mux_q.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_m1.py (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with SHOTS_RECORD=1 over the same
shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the film showing ENTRY's clock
from START to END seconds). Each client plays the hack's sound the tick it sees the hack change (client/xchara/HackMode),
and the runs see it a tick sooner or later than each other. So the recording is laid at the hack's coming on: its sound
(xchara_q_on: a tick and a rising crushed tone, found well by its own waveform) is found in the sound run's recording
near where its log puts it, and goes where the film run saw the hack come on (the first row of its stand.csv with hk_on
1: its tick less the seconds since, hk_t); whatever else the entry plays (the hack going off, the knife's hits) comes
with it as the sound run played them, a tick either way.
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock the hack first came on: its first row with it on, less the seconds since it changed."""
    for r in stand:
        if r.get('hk_on') == '1' and r.get('hk_t') not in (None, ''):
            return math.ceil(float(r['t']) - float(r['hk_t']) * 20 - 1e-3)
    return None


FIRST = {'lead': 0, 'file': 'xchara_q_on.ogg', 'sound': 'celestialfall:xchara_q_on', 'name': "hack coming on", 'start': started}

if __name__ == '__main__':
    main(FIRST)
