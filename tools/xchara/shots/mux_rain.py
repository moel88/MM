"""Lays the game's own sound under a film of X Chara's H with the hack off, the rain of knives (2.132.0).
usage: [MUX_H_TICK=20] [MUX_H_LEAD=14] python3 -I tools/xchara/shots/mux_rain.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/xchara/shots/mux_knives.py, for the rain of knives (AUDIO_DIR a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time, FILM_DIR/ENTRY the slowed film run's folder of the same entry, the
film showing ENTRY's clock from START to END seconds). Its sounds are ones the game has (the user's: "있는 소리 재사용"):
the jab's thrust as the knife goes in (xchara_jab), then the knives shot's coming and strike through the rain. The
skill is not in the shot's stand.csv, so where it started is the tick its H was pressed in the shot list (MUX_H_TICK,
20 unless given: "then=KEY_H@20"). The recording is placed by the thrust's sound, found in it by its own waveform near
where its log puts it and laid MUX_H_LEAD ticks after that press in the film: the tick the client starts it. That
depends on how long the run was before someone was caught (the shot's log has it: "xchara_jab hit 17, told N ticks in,
played M later", under the tick the stab was heard of, "knife hit 17 at T"): T - 20 + 5 ticks after the press: 6 with
no run, 14 after a run caught nine ticks in.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../stand/shots'))
from mux_heavy import main  # noqa: E402


def started(stand):
    """The tick of the shot's clock its H was pressed on (the shot list's)."""
    return int(os.environ.get('MUX_H_TICK', '20'))


FIRST = {'lead': int(os.environ.get('MUX_H_LEAD', '14')), 'file': 'xchara_jab.ogg', 'sound': 'celestialfall:xchara_jab', 'name': 'rain', 'start': started}

if __name__ == '__main__':
    main(FIRST)
