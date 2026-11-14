"""Lays the game's own sound under a film of X Chara's H with the hack off, the rain of knives (2.132.5).
usage: [MUX_H_TICK=20] [MUX_H_LEAD=5] python3 -I tools/xchara/shots/mux_rain.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_knives.py, for the rain of knives (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its sounds are ones the game has (the user's: "있는 소리 재사용"):
the slam's knife lighting as the rain is called (xchara_slam_lit, its burst on the call), then the knives shot's strike
each time a knife of the rain goes into a foe. The skill is not in the shot's stand.csv, so where it started is the
tick its H was pressed in the shot list (MUX_H_TICK, 20 unless given: "then=KEY_H@20"). The recording is placed by the
call's sound, found in it by its own waveform near where its log puts it and laid MUX_H_LEAD ticks after that press in
the film: the tick the client starts it (the call heard the tick after the press, "knife hit 16 at 21", and its sound
4 ticks into it, BladeRain.CALL_SOUND_AT). 2.132.0 to 2.132.4 laid it by the jab's thrust as the knife went in.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its H was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_H_TICK', '20'))


FIRST = {'lead': int(os.environ.get('MUX_H_LEAD', '5')), 'file': 'xchara_slam_lit.ogg', 'sound': 'celestialfall:xchara_slam_lit', 'name': 'rain', 'start': started}

if __name__ == '__main__':
    main(FIRST)
