"""Lays the game's own sound under a film of the stand arrow (2.70.0; its recorded cues since 2.71.0), where the game plays it.
usage: python3 tools/stand/shots/mux_arrow.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the stand arrow: its sounds are cues laid on the stab's clock
(client/stand/StandArrowSounds), each on its tick on each client. The blow going in (stand_arrow_stab) sounds on the
tick the arrow goes in (StandArrowRules.HIT, 108 ticks into the stab): it is found in the recording by its own waveform
and laid where the film run's stab started (+108 ticks); every other cue (and the choice's, the awakening's or the
rejection's) comes with it as the sound run played it, each on its own tick of the same clock. The stab's start in each
run is the first row of stand.csv with the stab's age (seconds on its clock).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402

# the blow's tick on the stab's clock (StandArrowRules.HIT)
HIT = 108


def start(stand):
    """
    The tick the entry's stab started on: the first row with its age (t a whole tick, the age that many ticks in plus
    the frame's part of one) that started in this entry (a client keeps a stab a while after it: an entry right after
    another's awakening starts with that one's rows, its age 12 s and more).
    """
    for r in stand:
        if r.get('arrow_age'):
            tick = math.ceil(float(r['t']) - float(r['arrow_age']) * 20 - 1e-3)
            if tick >= 0:
                return tick
    return None


if __name__ == '__main__':
    main({'lead': HIT, 'file': 'stand_arrow_stab.ogg', 'sound': 'celestialfall:stand_arrow_stab', 'name': 'stand arrow', 'start': start})
