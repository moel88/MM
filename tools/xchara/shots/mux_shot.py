"""Lays the game's own sound under a film of X Chara's Y with the hack off, the four knives shot (2.130.0).
usage: [MUX_Y_TICK=20] [MUX_Y_LEAD=2] python3 -I tools/xchara/shots/mux_shot.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_cutter.py, for the knives shot (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Their sounds are the "!!!"'s own as each knife comes to be
(xchara_shot_appear: G's cut, louder; four of them three ticks apart) and a knife's strike (xchara_shot_hit, the user's own out of their
video: tools/xchara/extract_shot.py). The skill is not in the shot's stand.csv, so where it started is the tick its Y
was pressed in the shot list (MUX_Y_TICK, 20 unless given: "then=KEY_Y@20"). The recording is placed by the first
knife's sound, found in it by its own waveform near where its log puts it and laid MUX_Y_LEAD ticks after that press in
the film: the tick the client hears of the skill (one after the press) and the tick the first knife comes on
(ShotRules.APPEAR: one more).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its Y was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_Y_TICK', '20'))


FIRST = {'lead': int(os.environ.get('MUX_Y_LEAD', '2')), 'file': 'xchara_shot_appear.ogg', 'sound': 'celestialfall:xchara_shot_appear', 'name': 'knives', 'start': started}

if __name__ == '__main__':
    main(FIRST)
