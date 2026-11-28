"""Lays the game's own sound under a film of one of Gold Experience's skills with sounds of 2.153.0 (its R, its Y's two
healings, its T's frog and fist, its H's tree), where the game plays it.
usage: python3 -I tools/stand/shots/mux_gold.py AUDIO_DIR FILM_DIR ENTRY START END FILM.mp4 OUT.mp4 KIND

As tools/stand/shots/mux_gold_beatdown.py (mux_heavy's way): AUDIO_DIR is a run of tools/kageno/shots/run_shots.py with
SHOTS_RECORD=1 over the same shot list in real time (a small window), FILM_DIR the slowed film run; ENTRY the entry
(NAME.k: the k-th entry called NAME), shown in FILM.mp4 from its clock's START to END seconds (as given to film.py).
KIND says which of the skill's sounds holds the two runs together: it is found in the recording by its own waveform and
laid where the film run's skill began (from the run's log.txt: the skill's line, its tick less its age) + its ticks in;
the rest of the entry's sound comes with it as the sound run played it (a tick either way). A film of a later stretch
of the same entry (the frog's return, the fly's, the tree's withering) is laid by the same sound: give its START.
  over   the overdrive punch's shout (R), 8 ticks in (client/stand/GoldPunch.SOUND_AT)
  mend   the healing of another (Y tapped), 6 ticks in (GoldHeal.TAP_SOUND)
  self   the user's own healing (Y held), 4 ticks in (GoldHeal.HOLD_SOUND)
  frog   the frog's birth (T with an item), 6 ticks in (GoldLife.FROG_SOUNDS)
  punch  the punch's rush (T with the hand empty), 5 ticks in (GoldLife.PUNCH_SOUNDS)
  tree   the tree's burst (H), 10 ticks in (GoldTree.SOUNDS)
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mux_heavy import main  # noqa: E402

# what holds the runs together for each KIND: the sound's file and event, its ticks into the skill, and the skill's
# log line (its tick, and its age the first or second of its fields: 'NAME at T: labels A,B,...')
KINDS = {
    'over': ('gold_experience_overdrive', 8, r'overdrive punch at (\d+): \S+ ([0-9.]+),'),
    'mend': ('gold_experience_mend', 6, r'healing at (\d+): \S+ 0,([0-9.]+),'),
    'self': ('gold_experience_self_heal', 4, r'healing at (\d+): \S+ 1,([0-9.]+),'),
    'frog': ('gold_life_born', 6, r'giving life at (\d+): \S+ 1,([0-9.]+),'),
    'punch': ('gold_life_punch', 5, r'giving life at (\d+): \S+ 0,([0-9.]+),'),
    'tree': ('gold_tree_burst', 10, r'tree of life at (\d+): \S+ ([0-9.]+),'),
}


def begun(path, entry, pattern):
    """The tick of the shot's clock the skill began on, from the run's log: the entry's part of it, its first line of the skill."""
    name, _, k = entry.partition('.')
    with open(path, encoding='utf8') as fh:
        parts = re.split(r'(?m)^%s at \d+$' % re.escape(name), fh.read())
    part = parts[int(k) if k else 1]
    m = re.search(pattern, part)
    return None if m is None else math.ceil(int(m.group(1)) - float(m.group(2)) * 20 - 1e-3)


if __name__ == '__main__':
    if len(sys.argv) < 9 or sys.argv[8] not in KINDS:
        sys.exit(__doc__)
    sound, lead, pattern = KINDS[sys.argv[8]]
    # (mux_heavy asks for the sound run's start first, then the film run's)
    logs = [os.path.join(sys.argv[1], 'log.txt'), os.path.join(sys.argv[2], 'log.txt')]
    main({'lead': lead, 'file': sound + '.ogg', 'sound': 'celestialfall:' + sound, 'name': sound,
          'start': lambda stand: begun(logs.pop(0), sys.argv[3], pattern)})
