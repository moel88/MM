"""Lays the game's own sound under a film of X Chara's E with the hack off, the three cuts (2.129.0).
usage: [MUX_E_TICK=20] [MUX_E_LEAD=4] python3 -I tools/xchara/shots/mux_cutter.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_slam.py, for the three cuts (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Their sound is the user's own out of their video
(tools/xchara/extract_cutter.py): one sound, xchara_cutter, begun a tick before the first cut lands (KnifeClient). The
cuts are not in the shot's stand.csv, so where they started is the tick their E was pressed in the shot list
(MUX_E_TICK, 20 unless given: "then=KEY_E@20"). The recording is placed by that sound, found in it by its own waveform
near where its log puts it and laid MUX_E_LEAD ticks after that press in the film: the tick the client hears of the
cuts (one after the press) and the ticks to a tick before the first cut (XCharaCombo: 3 where they stand, the default
4 in all; 7 after a way forward, 8 in all).
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its E was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_E_TICK', '20'))


FIRST = {'lead': int(os.environ.get('MUX_E_LEAD', '4')), 'file': 'xchara_cutter.ogg', 'sound': 'celestialfall:xchara_cutter', 'name': 'cutter', 'start': started}

if __name__ == '__main__':
    main(FIRST)
