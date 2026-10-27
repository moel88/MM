"""Lays the game's own sound under a film of Star Platinum called out and back (Q, 2.72.0), where the game plays it.
usage: python3 tools/stand/shots/mux_call.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the stand's call (client/stand/StandCall): its sound starts on the tick the
stand starts to come out on each client. The call ("Star Platinum!", star_platinum_summon) is found in the recording by
its own waveform and laid where the film run's stand started to come out; the sound of it going back
(star_platinum_dismiss) comes with it as the sound run played it, on its own tick. Where the stand started to come out
in each run: the first row of stand.csv with the call sounding, less the seconds since it started to come out, less a
tick (start).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402


def start(stand):
    """The tick the stand started to come out on: the first row with the call sounding, its t (a whole tick) less the
    time since it came out, less one: a tick's row is written before the stand's own tick (StandPresence, where it
    starts to come out and its call starts), so the row that first shows it is the next tick's (since 0 there; the
    frames after the tick it started on show it already, and the sound run's sounds.csv has the call starting on it)."""
    for r in stand:
        if r.get('call') == 'star_platinum_summon' and r.get('since') not in (None, '', '-1.000'):
            return math.ceil(float(r['t']) - float(r['since']) * 20 - 1e-3) - 1
    return None


if __name__ == '__main__':
    main({'lead': 0, 'file': 'star_platinum_summon.ogg', 'sound': 'celestialfall:star_platinum_summon', 'name': 'call', 'start': start})
