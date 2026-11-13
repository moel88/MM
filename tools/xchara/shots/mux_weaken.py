"""Lays the game's own sound under a film of X Chara's T with the hack on, the weakening (2.126.1).
usage: [MUX_T_TICK=30] python3 -I tools/xchara/shots/mux_weaken.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_e.py, for the weakening (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its one sound is the sword dance's alert (xchara_g_warn: the
user, "기존 !!!효과음 사용"), played as the client hears of the "!!!" (WeakenClient), a tick after the press. The
weakening is not in the shot's stand.csv, so where it started is the tick its T was pressed in the shot list
(MUX_T_TICK, 30 unless given: "then=KEY_T@30"). The sound is found in the sound run's recording by its own waveform
near where its log puts it, and laid a tick after that press in the film.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its T was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_T_TICK', '30'))


FIRST = {'lead': 1, 'file': 'xchara_g_warn.ogg', 'sound': 'celestialfall:xchara_g_warn', 'name': 'weakening', 'start': started}

if __name__ == '__main__':
    main(FIRST)
