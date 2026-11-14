"""Lays the game's own sound under a film of X Chara's H with the hack on, the terrain delete (2.133.0).
usage: [MUX_H_TICK=20] [MUX_H_LEAD=1] python3 -I tools/xchara/shots/mux_erase.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_knives.py, for the terrain delete (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its sounds are ones the game has: the panel's click as it is
pressed (xchara_jab_press, ErasePress.CUES); as its grounds are marked, the "!!!"'s warning (xchara_g_warn); the hack
coming on as its cubes rise; the boom, the wall and the pull's burst as they burst; the double's coming as the ground is
back (EraseClient).
The skill is not in the shot's stand.csv, so where it started is the tick its H was pressed in the shot list
(MUX_H_TICK, 20 unless given: "then=KEY_H@20"). The recording is placed by the panel's click, found in it by its own
waveform near where its log puts it and laid MUX_H_LEAD ticks after that press in the film: the tick the client starts
it (the cast heard the tick after the press, its click due a tick into its clock: at once). The first design's delete
slash was laid by the slam's knife lighting at the same tick.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its H was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_H_TICK', '20'))


FIRST = {'lead': int(os.environ.get('MUX_H_LEAD', '1')), 'file': 'xchara_jab_press.ogg', 'sound': 'celestialfall:xchara_jab_press', 'name': 'erase', 'start': started}

if __name__ == '__main__':
    main(FIRST)
