"""Lays the game's own sound under a film of X Chara's R with the hack on, the double (2.125.0).
usage: python3 -I tools/xchara/shots/mux_clone.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_jab.py, for the double (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). The double plays three sounds (two the user's own,
tools/xchara/extract_clone.py; the wall's laid together from recordings, make_clone_wall.py): the double coming (xchara_clone, 3 ticks in), its throw (xchara_clone_throw, a tick
before the throw: 16 ticks in) and the wall (xchara_clone_wall, a tick before the one thrown is in it: 39 ticks in).
The runs hear of the skill a tick sooner or later than each other, so the recording is laid by the throw's sound (a
sharp burst, found well by its own waveform): found in the sound run's recording near where its log puts it, it goes
where the film run's double started (the first row of its stand.csv with the double in it, cl_age: its tick less its
age) plus those 16 ticks; the other two come with it as the sound run played them.
"""
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock the double started on: its first row with it, less its age (ticks)."""
    for r in stand:
        if r.get('cl_age') not in (None, ''):
            return math.ceil(float(r['t']) - float(r['cl_age']) - 1e-3)
    return None


FIRST = {'lead': 16, 'file': 'xchara_clone_throw.ogg', 'sound': 'celestialfall:xchara_clone_throw', 'name': 'double', 'start': started}

if __name__ == '__main__':
    main(FIRST)
