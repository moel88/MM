"""Lays the game's own sound under a film of X Chara's G, the sword dance (2.112.0), its sound where the game plays it.
usage: python3 -I tools/xchara/shots/mux_g.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_heavy.py, for the G (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with SHOTS_RECORD=1 over
the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the film showing ENTRY's
clock from START to END seconds). Each client plays the G's sound as it hears of it (XCharaClient): its alert
(xchara_g_warn) as it starts, the rest of the recording (xchara_g: the strange space's sound, then the blades') as its
strange space comes, and the runs hear of each a tick sooner or later than each other. So the recording is laid at the
long one: found in the sound run's recording by its own waveform, and laid where the film run's client heard of the
strange space (the first row of its stand.csv with xc_space 1); its alert comes with it as the sound run played it, a
tick either way. An entry whose G was blocked (no strange space) is laid at its alert instead, a tick after its press
(the first row with an xc_age: its row's tick less its age), where the sound run's log puts it if its waveform (mixed
with the shield's sound) is not found well.
"""
import csv
import math
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
import mux_heavy  # noqa: E402
from mux_heavy import main  # noqa: E402

# A blocked G's alert is mixed with the shield's sound 0.2 s after it starts: if its own waveform is not found well enough
# (MATCH), it is taken where the sound run's log says it started (its sounds.csv row, against the entry's marker).
MATCH = .5
_find = mux_heavy.find


def find(wav, clip, lo, hi):
    pos, score = _find(wav, clip, lo, hi)
    if score < MATCH:
        middle = (lo + hi - len(clip)) // 2
        print(f'  (its waveform found poorly, match {score:.2f}: taken where the log puts it)')
        return middle, 1.0
    return pos, score


mux_heavy.find = find


def came(stand):
    """The tick of the shot's clock its strange space came on (its first row with xc_space 1)."""
    for r in stand:
        v = r.get('xc_space')
        if v not in (None, '') and float(v) >= 1:
            return math.ceil(float(r['t']) - 1e-3)
    return None


def pressed(stand):
    """The tick of the shot's clock its G started on: its first row with an age, less that age (ticks)."""
    for r in stand:
        if r.get('xc_age'):
            return math.ceil(float(r['t']) - float(r['xc_age']) - 1e-3)
    return None


SPACE = {'lead': 0, 'file': 'xchara_g.ogg', 'sound': 'celestialfall:xchara_g', 'name': "G's strange space", 'start': came}
WARN = {'lead': 1, 'file': 'xchara_g_warn.ogg', 'sound': 'celestialfall:xchara_g_warn', 'name': "G's alert", 'start': pressed}

if __name__ == '__main__':
    with open(os.path.join(sys.argv[1], sys.argv[3], 'sounds.csv')) as fh:
        played = {r['sound'] for r in csv.DictReader(fh)}
    main(SPACE if SPACE['sound'] in played else WARN)
