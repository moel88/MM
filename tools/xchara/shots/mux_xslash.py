"""Lays the game's own sound under a film of X Chara's H with the hack off, the great X slash (2.134.0).
usage: [MUX_H_TICK=20] [MUX_H_LEAD=1] python3 -I tools/xchara/shots/mux_xslash.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_erase.py, for the great X slash (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its sounds are ones the game has (the user's "있는 소리 재사용"):
the slam's knife lighting as it starts (xchara_slam_lit), the knife's cut before each of its two cuts (xchara_slash),
the slam's leap as the X goes (xchara_slam_leap, XSlash.CUES), the knives shot's strike on each struck, the double's
glitch as its X on the ground is whole and the pull's burst as it bursts (XSlash).
The skill is not in the shot's stand.csv, so where it started is the tick its H was pressed in the shot list
(MUX_H_TICK, 20 unless given: "then=KEY_H@20"). The recording is placed by the knife lighting, found in it by its own
waveform near where its log puts it and laid MUX_H_LEAD ticks after that press in the film: the tick the client starts
it (the cast heard the tick after the press, its lighting due at once).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its H was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_H_TICK', '20'))


FIRST = {'lead': int(os.environ.get('MUX_H_LEAD', '1')), 'file': 'xchara_slam_lit.ogg', 'sound': 'celestialfall:xchara_slam_lit', 'name': 'xslash', 'start': started}

if __name__ == '__main__':
    main(FIRST)
