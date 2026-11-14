"""Lays the game's own sound under a film of X Chara's T with the hack off, the three small knives thrown (2.131.0).
usage: [MUX_T_TICK=20] [MUX_T_LEAD=1] python3 -I tools/xchara/shots/mux_knives.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_shot.py, for the small knives (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Their sounds are the throw's (xchara_knives, the user's own out
of their video: tools/xchara/extract_knives.py), played as the throw begins, and a knife's strike (xchara_shot_hit, the
knives shot's: the video plays that very sound). The skill is not in the shot's stand.csv, so where it started is the
tick its T was pressed in the shot list (MUX_T_TICK, 20 unless given: "then=KEY_T@20"). The recording is placed by the
throw's sound, found in it by its own waveform near where its log puts it and laid MUX_T_LEAD ticks after that press in
the film: the tick the client hears of the skill (one after the press), when it plays it.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its T was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_T_TICK', '20'))


FIRST = {'lead': int(os.environ.get('MUX_T_LEAD', '1')), 'file': 'xchara_knives.ogg', 'sound': 'celestialfall:xchara_knives', 'name': 'knives', 'start': started}

if __name__ == '__main__':
    main(FIRST)
