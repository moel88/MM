"""엑스 차라's double (R with the hack on): the sound of the one thrown going into the wall, laid together from recordings.
usage: python3 -I tools/xchara/make_clone_wall.py SOURCES_DIR [OUT]

2.125.0 lifted this sound out of the user's video, where it plays once under music, and the music could be heard in it
whatever was taken off (user: "마지막 삥 소리에 노래가 들림 그냥 벽에 박히는 소리만 창작해줘 ai로 만들지말고 자연스러운
소리로"). So it is made anew, and not synthesised: it is real recordings, all public domain (CC0), cut and laid
together as the stand arrow's are (tools/stand/cut_arrow_sounds.py). SOURCES_DIR holds them as downloaded from
BigSoundBank (bsb_<number>.flac); they are not in the repository (third_party/xchara-clone-sounds/SOURCES.md says
where each comes from). The script only decodes them (ffmpeg).

What it is: a body driven into a wall of rock that has just come out of the ground.
  the slam    a heavy door slammed shut: the weight of the blow
  the low end the first 60 ms of a blast, under it
  the rock    stones struck on stone, three of them a few ms apart: the crack as the wall takes it
  the rubble  a wheelbarrow of limestone tipped out, from where the stones are pouring: what breaks off, dying away
  the last of it  two single stones coming down after
Each is changed only as any cut is: mono at 48 kHz, faded where it is cut, set to its level among the others. The
whole is set to its loudness under a limiter that keeps peaks below -1 dB.

Its blow is 50 ms into it, as 2.125.0's was: the client plays it a tick before the wall (CloneClient).
"""
import os
import subprocess
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_jab import SR, finish, smooth, write  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LUFS = -15.0
# where the blow is in the file, and how long the file is (seconds)
LEAD, LENGTH = .050, 1.15
# the layers: recording, from where in it (s), how long (s), when in the sound after the blow (s), its level (dB under
# its own peak brought to 0), fade in (s), and how it dies away: (from when after its start, over how long) (s)
LAYERS = [
    # the slam (its bang is 0.175 s into the recording, the latch's click just before it left out)
    ('1501', .171, .60, -.004, -4.0, .003, (.22, .36)),
    # the low end
    ('1808', .000, .09, -.004, -9.0, .002, (.03, .06)),
    # the rock: stones struck on stone (each cut 2 ms before its strike)
    ('1022', 1.718, .30, -.002, -2.0, .0015, (.10, .20)),
    ('1022', 13.243, .30, .011, -5.0, .0015, (.10, .20)),
    ('1022', 14.963, .26, .027, -6.0, .0015, (.10, .16)),
    # the rubble: from where the stones are pouring, dying away
    ('1634', 6.060, .95, .010, -5.0, .010, (.06, .89)),
    # the last of it
    ('1022', 4.636, .30, .36, -14.0, .002, (.12, .18)),
    ('1022', 3.096, .28, .58, -18.0, .002, (.12, .16)),
]


def load(folder, number):
    path = os.path.join(folder, f'bsb_{number}.flac')
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).astype(float)


def layer(folder, number, start, length, at, level, fade_in, dies):
    y = load(folder, number)[int(start * SR):int((start + length) * SR)].copy()
    s = np.arange(len(y)) / SR
    y *= smooth(s / fade_in) * (1 - smooth((s - dies[0]) / dies[1])) ** 2
    y *= 10 ** (level / 20) / np.abs(y).max()
    return int(round((LEAD + at) * SR)), y


def main():
    folder = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    mix = np.zeros(int(LENGTH * SR))
    for spec in LAYERS:
        a, y = layer(folder, *spec)
        y = y[:len(mix) - a]
        mix[a:a + len(y)] += y
    s = np.arange(len(mix)) / SR
    mix *= 1 - smooth((s - (LENGTH - .12)) / .12)
    os.makedirs(out, exist_ok=True)
    write(finish(mix, LUFS), out, 'xchara_clone_wall')


if __name__ == '__main__':
    main()
