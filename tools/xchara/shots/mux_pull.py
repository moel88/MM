"""Lays the game's own sound under a film of X Chara's Y with the hack on, the pull (2.127.0).
usage: [MUX_Y_TICK=30] python3 -I tools/xchara/shots/mux_pull.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_weaken.py, for the pull (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its sounds are the sword dance's alert (xchara_g_warn: the
video's own warning), played as the client hears of the "!!!", a tick after the press, and the pull's burst
(xchara_pull), begun as the client hears of the pull (PullClient). The pull is not in the shot's stand.csv, so where it
started is the tick its Y was pressed in the shot list (MUX_Y_TICK, 30 unless given: "then=KEY_Y@30"). The recording
is placed by the alert, found in it by its own waveform near where its log puts it and laid a tick after that press in
the film; the burst comes along with it, where the sound run had it.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its Y was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_Y_TICK', '30'))


FIRST = {'lead': 1, 'file': 'xchara_g_warn.ogg', 'sound': 'celestialfall:xchara_g_warn', 'name': 'pull', 'start': started}

if __name__ == '__main__':
    main(FIRST)
