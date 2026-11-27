"""Lays the game's own sound under a film of Gold Experience's beatdown (G, 2.150.0), its cutscene's sound where the game plays it.
usage: python3 -I tools/stand/shots/mux_gold_beatdown.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4

As tools/stand/shots/mux_beatdown.py, for Gold Experience's: its cutscene's sound (the user's video's, from its second
shot on: client/stand/GoldBeatdown) starts GoldExperienceRules.BEAT_SOUND (16) ticks into the beatdown, as its opening
punch lands, on each client. The recording is laid at it: found in the recording by its own waveform and laid where the
film run's beatdown started (+16 ticks). Both runs film the one entry; each run's log.txt says on which tick of the
shot's clock the beatdown's punch began on the client ("gold beatdown at T (...): AGE,...": T less AGE ticks, up to
the whole tick).
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402

LEAD = 16


def begun(path):
    """The tick of the shot's clock the beatdown's punch began on, from the run's log."""
    with open(path, encoding='utf8') as fh:
        m = re.search(r'gold beatdown at (\d+) \([^)]*\): ([0-9.]+),', fh.read())
    return None if m is None else math.ceil(int(m.group(1)) - float(m.group(2)) * 20 - 1e-3)


if __name__ == '__main__':
    # (mux_heavy asks for the sound run's start first, then the film run's)
    logs = [os.path.join(sys.argv[1], 'log.txt'), os.path.join(sys.argv[2], 'log.txt')]
    main({'lead': LEAD, 'file': 'gold_experience_beatdown.ogg', 'sound': 'celestialfall:gold_experience_beatdown', 'name': "gold beatdown's cutscene",
          'start': lambda stand: begun(logs.pop(0))})
