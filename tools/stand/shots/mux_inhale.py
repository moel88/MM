"""Lays the game's own sound under a film of Star Platinum's inhale (Y, 2.61.0), its sound where the game plays it.
usage: python3 tools/stand/shots/mux_inhale.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the inhale: its suction's sound (made for it, tools/stand/make_inhale_sounds.py)
starts StandInhale.LEAD (1) tick into it on each client; its first inhale in ENTRY is found in the recording by its own
waveform and laid where the film run's first inhale started (+1 tick). The catch's and the air let go's sounds come with
it as the sound run played them.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402


def start(stand):
    """The tick of the shot's clock the inhale started on, from the first row that shows it (as mux_heavy.start_tick)."""
    for r in stand:
        if r.get('inhale_age'):
            return math.ceil(float(r['t']) - float(r['inhale_age']) * 20 - 1e-3)
    return None


if __name__ == '__main__':
    main({'lead': 1, 'file': 'star_platinum_inhale.ogg', 'sound': 'celestialfall:star_platinum_inhale', 'name': 'inhale', 'start': start})
