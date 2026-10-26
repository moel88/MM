"""Lays the game's own sound under a film of the stand arrow (2.70.0), its sounds where the game plays them.
usage: python3 tools/stand/shots/mux_arrow.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the stand arrow: the stab's sound (stand_arrow_stab) starts
StandArrowClient.LEAD (1) tick into the stab on each client; the stab in ENTRY is found in the recording by its sound's
own waveform and laid where the film run's stab started (+1 tick). Its choice's sound (the awakening's or the
rejection's) comes with it as the sound run played it, as its choice was heard there (the server's tick REVEAL, a tick
either way). The stab's start in each run is the first row of stand.csv with the stab's age (seconds on its clock).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402


def start(stand):
    """The tick the stab started on: the first row with its age (t a whole tick, the age that many ticks in plus the frame's part of one)."""
    for r in stand:
        if r.get('arrow_age'):
            return math.ceil(float(r['t']) - float(r['arrow_age']) * 20 - 1e-3)
    return None


if __name__ == '__main__':
    main({'lead': 1, 'file': 'stand_arrow_stab.ogg', 'sound': 'celestialfall:stand_arrow_stab', 'name': 'stand arrow', 'start': start})
