"""Lays the game's own sound under a film of Star Platinum's time stop (H, 2.67.0), its sound where the game plays it.
usage: python3 tools/stand/shots/mux_timestop.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the time stop: its call ("Star Platinum: The World!", the user's first recording,
client/stand/StandTimeStop) starts StarPlatinumRules.TS_SOUND (1) tick into it on each client; the first time stop in
ENTRY is found in the recording by its own waveform and laid where the film run's time stop started (+1 tick). Its end
("toki wa ugoki dasu", the user's second recording, as time moves again) and the rest of the entry's sound come with it
as the sound run played them, a tick either way. The one it stopped hears nothing else while time is stopped.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402


def start(stand):
    """The tick of the shot's clock the time stop started on, from the first row that shows it (as mux_heavy.start_tick)."""
    for r in stand:
        if r.get('ts_age'):
            return math.ceil(float(r['t']) - float(r['ts_age']) * 20 - 1e-3)
    return None


if __name__ == '__main__':
    main({'lead': 1, 'file': 'star_platinum_timestop.ogg', 'sound': 'celestialfall:star_platinum_timestop', 'name': 'time stop', 'start': start})
