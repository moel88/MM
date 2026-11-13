"""Lays the game's own sound under a film of X Chara's G with the hack off, the slam (2.128.0).
usage: [MUX_G_TICK=30] python3 -I tools/xchara/shots/mux_slam.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_pull.py, for the slam (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its sounds are the user's own out of their video
(tools/xchara/extract_slam.py): the knife's lighting (xchara_slam_lit) as the client hears of the slam, a tick after
the press; the leap's and the landing's a tick before their moments (KnifeClient). The slam is not in the shot's
stand.csv, so where it started is the tick its G was pressed in the shot list (MUX_G_TICK, 30 unless given:
"then=KEY_G@30"). The recording is placed by the lighting's sound, found in it by its own waveform near where its log
puts it and laid a tick after that press in the film; the other two come along with it, where the sound run had them.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its G was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_G_TICK', '30'))


FIRST = {'lead': 1, 'file': 'xchara_slam_lit.ogg', 'sound': 'celestialfall:xchara_slam_lit', 'name': 'slam', 'start': started}

if __name__ == '__main__':
    main(FIRST)
