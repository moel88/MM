"""Lays the game's own sound under a film of X Chara's Z, the teleport (2.138.0).
usage: [MUX_Z_TICK=12] python3 -I tools/xchara/shots/mux_blink.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_pull.py, for the teleport (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its one sound is the pull's burst (xchara_pull: the user's
video's is that very file), begun as the client hears of the move (BlinkClient): BlinkRules.GO ticks after the server
takes the press, heard the tick after. The teleport is not in the shot's stand.csv, so where it started is the tick its
Z was pressed in the shot list (MUX_Z_TICK, 12 unless given: "then=KEY_Z@12"). The recording is placed by the burst,
found in it by its own waveform near where its log puts it and laid MUX_Z_LEAD ticks (5 unless given) after that press
in the film.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its Z was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_Z_TICK', '12'))


FIRST = {'lead': int(os.environ.get('MUX_Z_LEAD', '5')), 'file': 'xchara_pull.ogg', 'sound': 'celestialfall:xchara_pull', 'name': 'teleport', 'start': started}

if __name__ == '__main__':
    main(FIRST)
