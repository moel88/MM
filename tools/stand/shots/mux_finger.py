"""Lays the game's own sound under a film of Star Platinum's Star Finger (T, 2.60.0), its sound where the game plays it.
usage: python3 tools/stand/shots/mux_finger.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the Star Finger: its sound (the user's recording, StandAttack) starts
StandAttack.FINGER_LEAD (1) tick into it on each client; its first Star Finger in ENTRY is found in the recording by its
own waveform and laid where the film run's first Star Finger started (+1 tick).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402

if __name__ == '__main__':
    main({'combo': '6', 'lead': 1, 'file': 'star_platinum_finger.ogg', 'sound': 'celestialfall:star_platinum_finger', 'name': 'Star Finger'})
