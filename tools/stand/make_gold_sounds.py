"""Gold Experience's T (giving life), H (the tree of life) and basic attack: their sounds, laid together from recordings (2.153.0, 2.154.0).

usage: python3 -I tools/stand/make_gold_sounds.py RECORDINGS_DIR [OUT_DIR]

The user: "t스킬과 H스킬은 너가 창작해 ai소리로 넣지말고 어디서 다운로드 해서 넣던지". So nothing here is synthesized:
every sound is cut from real recordings, all public domain (CC0, BigSoundBank's: Joseph Sardin), and laid together as
the stand arrow's and X Chara's wall are (tools/stand/cut_arrow_sounds.py, tools/xchara/make_clone_wall.py).
RECORDINGS_DIR holds them as downloaded (bsb_<number>.wav or .flac, from https://bigsoundbank.com/UPLOAD/bwf-en/<number>.wav);
they are not in the repository (third_party/gold-experience-sounds/SOURCES.md says where each comes from). The script
only decodes them (ffmpeg) and cuts, turns, filters and lays them together: a cut is faded in and out; a layer may be
played faster or slower (resampled, its pitch with it), backwards, or under a high- or low-pass filter (to take off the
hum or wind under a recording made outdoors); each is set to its level among the others; the whole is set to its
loudness (over its loudest 100 ms, K-weighted as ITU-R BS.1770: most are a blow) under a look-ahead limiter that keeps
peaks below -1 dB, pressing them down 8 dB at most. Mono at 48 kHz.

What each is (the moments are the skill's: client/stand/GoldLife, GoldTree, GoldFlySounds):
  T, the frog (an item held up turns into a frog that rides its user's shoulder and turns blows back)
    gold_life_gather   light gathering into the item: chimes played backwards, swelling to the moment it turns
    gold_life_born     the item gone in a pop and a sparkle, the frog's first croak
    gold_life_hop      the frog's hop up onto the shoulder: a little rush of air, a soft pat as it lands
    gold_life_croak    a tree frog's croak (three of them, the game picks one): with every blow turned back
    gold_life_reflect  a blow turned back on whom struck: a glassy ting and the rush of it going back
    gold_life_frog_back  its time up: its hop back into the hand, the item back in a sparkle and a pop
    gold_life_frog_burst ended early: a pop and a burst of sparkle
  T, the fist (a part of whom it strikes flies off and becomes a fly hovering in front of them)
    gold_life_punch    the quick punch through the air
    gold_life_strike   it lands: the blow, the part popping off wetly, the rush of it flying away
    gold_life_fly_born the part becomes the fly: a small sparkle
    gold_life_fly_buzz a housefly's buzz, in a loop (it follows the fly in the world)
    gold_life_fly_back its time up untouched: it flies back into the body, a sparkle swelling backwards
    gold_life_swat     its owner swats it: a slap and a squelch
    gold_life_fall     its owner falls on their back
  the basic attack (2.154.0: Star Platinum's four punches, its fourth with the overdrive's shout, the user's recording)
    gold_punch_swing_1 the jab through the air: a quick swish
    gold_punch_swing_2 the cross: a swish with more weight
    gold_punch_swing_3 the hook: the arm swept round
    gold_punch_swing_4 the fourth, the whole body behind it
    gold_punch_hit_1   the jab landing: a sharp smack and the thump in a body
    gold_punch_hit_2   the cross landing
    gold_punch_hit_3   the hook landing: a meatier smack
    gold_punch_hit_4   the fourth landing: the crack, the thump, a body's weight, the air as they are thrown
  H, the tree
    gold_tree_touch    the stand's palm laid on the ground, the light running to its user's feet swelling to the burst
    gold_tree_burst    the tree bursting up: a thud in the ground, wood tearing and twigs snapping as it grows,
                       a creak, the leaves, the rush of its user flung up
    gold_tree_knock    a blow on it turned back: a thud on wood and its leaves (three of them, the game picks one)
    gold_tree_wither   it withers back into the ground: a groan of wood, twigs crackling, soil, leaves
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SR = 48000
CEILING_DB = -1.0


class L:
    """A layer: recording {number}, from {start} to {end} (seconds), its {at} in the sound (seconds: where its start
    lands, or its loudest moment with peak=True), its level (dB: its own peak brought to it), and how it is changed:
    {speed} (resampled: 2 an octave up and half as long), {rev} (backwards), {hp}/{lp} (Hz), fades (s)."""

    def __init__(self, number, start, end, at, level, speed=1.0, rev=False, hp=None, lp=None, fade_in=.003, fade_out=.012, peak=False):
        self.number, self.start, self.end, self.at, self.level = number, start, end, at, level
        self.speed, self.rev, self.hp, self.lp, self.fade_in, self.fade_out, self.peak = speed, rev, hp, lp, fade_in, fade_out, peak


# Every sound: its layers, its loudness (LUFS over its loudest 100 ms), its length (s: the end faded over TAIL), and
# for the buzz its loop's crossfade (s). The levels are each layer's peak; the loudness is the whole's.
TAIL = .06
SOUNDS = {
    # ---- T, the frog
    'gold_life_gather': dict(lufs=-19, length=.27, layers=[
        # a cluster of chimes from its strike, backwards: it swells into the strike at .25 s (as the item turns, 0.3 s in)
        L('2083', .178, .43, 0, -2, rev=True, fade_in=.12, fade_out=.004),
        L('2079', .45, .70, 0, -9, rev=True, fade_in=.15, fade_out=.004),
    ]),
    'gold_life_born': dict(lufs=-12, length=.75, layers=[
        L('0648', .215, .42, 0, -5),                       # a cork's pop: the item gone
        L('2083', .17, .80, .006, -11, fade_out=.3),       # a sparkle of chimes
        L('0819', 6.36, 6.78, .13, -3, hp=350, fade_out=.06),  # the frog's croak as it pops out (0.44 s in)
    ]),
    'gold_life_hop': dict(lufs=-17, length=.5, layers=[
        L('1802', .05, .37, 0, -9, speed=1.5),             # a little rush of air as it leaps
        L('0597', 2.025, 2.12, .30, -12, speed=1.7, hp=400),  # a soft pat as it lands on the shoulder (0.30 s on)
    ]),
    'gold_life_croak_1': dict(lufs=-14, length=.42, layers=[L('0819', 6.36, 6.78, 0, -1, hp=350, fade_out=.06)]),
    'gold_life_croak_2': dict(lufs=-14, length=.34, layers=[L('0819', 9.11, 9.45, 0, -1, hp=350, fade_out=.06)]),
    'gold_life_croak_3': dict(lufs=-14, length=.27, layers=[L('0819', 12.87, 13.14, 0, -1, hp=350, fade_out=.06)]),
    'gold_life_reflect': dict(lufs=-14, length=.6, layers=[
        L('2087', .825, 1.35, 0, -4, fade_out=.25),        # a glassy ting
        L('1799', .10, .44, .0, -13, speed=1.3, hp=300),   # the rush of it going back
    ]),
    'gold_life_frog_back': dict(lufs=-15, length=.85, layers=[
        L('1802', .05, .37, 0, -9, speed=1.5),             # its hop back into the hand
        L('2083', .178, .40, .085, -12, rev=True, fade_in=.08, fade_out=.004),  # light swelling to the moment it turns back (.31 s on)
        L('0547', .015, .20, .31, -9),                     # the item back with a pop
        L('2087', .05, .50, .31, -14, fade_out=.2),        # and a ting
    ]),
    'gold_life_frog_burst': dict(lufs=-14, length=.85, layers=[
        L('0648', .215, .42, 0, -5),
        L('2079', .44, 1.25, .01, -8, fade_out=.35),
    ]),
    # ---- T, the fist
    'gold_life_punch': dict(lufs=-14, length=.32, layers=[
        # the punch through the air, its loudest moment .13 s in (as the fist lands, 0.4 s into the T)
        L('1799', .08, .46, .13, -3, speed=1.35, hp=200, peak=True),
    ]),
    'gold_life_strike': dict(lufs=-11, length=.75, layers=[
        L('2460', .065, .55, 0, -2, fade_out=.1),          # the blow
        L('2453', .445, .75, .002, -5, fade_out=.12),      # its weight: a body's thud
        L('0648', .215, .42, .025, -7),                    # the part popping off
        L('2594', .165, .45, .02, -12, hp=300),            # wetly
        L('1797', .15, .50, .05, -15, speed=1.2, hp=300),  # the rush of it flying away
    ]),
    'gold_life_fly_born': dict(lufs=-15, length=.5, layers=[
        L('2081', .615, 1.08, 0, -6, fade_out=.25),
    ]),
    'gold_life_fly_buzz': dict(lufs=-18, length=2.5, loop=.25, layers=[
        # a housefly against a window: a stretch of its buzz with no knock on the glass in it
        L('0759', 30.45, 33.20, 0, -1, hp=150, fade_in=.0, fade_out=.0),
    ]),
    'gold_life_fly_back': dict(lufs=-19, length=.42, layers=[
        L('2083', .178, .58, 0, -4, rev=True, fade_in=.15, fade_out=.004),  # swelling backwards to the moment it is back in the body
        L('1797', .15, .50, .05, -16, speed=1.4, hp=300, rev=True, fade_in=.1, fade_out=.01),
    ]),
    'gold_life_swat': dict(lufs=-12, length=.6, layers=[
        L('0597', 2.025, 2.32, 0, -2),                     # a slap
        L('2596', .665, .95, .005, -6, hp=250),            # a squelch
        L('0495', .555, .90, .008, -4, hp=200, fade_out=.12),  # and mud's
    ]),
    'gold_life_fall': dict(lufs=-12, length=.75, layers=[
        L('2453', .445, 1.15, 0, -1, fade_out=.2),         # a body falling on its back (its thud .03 s in)
    ]),
    # ---- H, the tree
    'gold_tree_touch': dict(lufs=-16, length=.62, layers=[
        # light swelling backwards from 0 to the burst at .40 s (the client plays it 0.1 s in: the burst is 0.5 s in)
        L('2079', .45, .86, 0, -6, rev=True, fade_in=.25, fade_out=.004),
        L('2083', .178, .45, .13, -10, rev=True, fade_in=.15, fade_out=.004),
        # the palm laid on the ground at .25 s (the touch, 0.35 s in)
        L('1839', .855, 1.20, .25, -4, speed=1.25, fade_out=.08),
    ]),
    'gold_tree_burst': dict(lufs=-9, length=1.45, layers=[
        L('1837', .525, 1.10, 0, -1, fade_out=.25),        # a thud in the ground
        L('1838', .845, 1.40, .02, -6, fade_out=.2),
        L('1022', 1.716, 1.95, .05, -14, hp=200),          # stones knocked loose
        L('1022', 13.241, 13.50, .11, -16, hp=200),
        L('2750', 18.62, 19.95, .04, -4, hp=150, fade_out=.3),  # wood tearing as it grows
        L('1300', .165, .40, .10, -7, hp=300),             # twigs snapping, the trunk to the twigs
        L('1299', 1.115, 1.30, .17, -8, hp=300),
        L('1299', 3.725, 3.90, .26, -7, hp=300),
        L('1299', 3.945, 4.10, .33, -10, hp=300),
        L('1299', 2.755, 2.95, .42, -11, hp=300),
        L('1299', .235, .40, .50, -12, hp=300),
        L('0518', 2.19, 2.62, .08, -10, speed=.6, hp=120, fade_out=.15),  # a groan of wood
        L('1811', .78, 1.45, .30, -9, hp=300, fade_out=.25),  # its leaves
        L('1813', .10, .95, .45, -13, hp=300, fade_out=.3),
        L('1796', .15, .72, .12, -9, peak=True, hp=150),   # the rush of its user flung up
    ]),
    'gold_tree_knock_1': dict(lufs=-13, length=.55, layers=[
        L('0705', 12.665, 13.10, 0, -1, fade_out=.15),     # a thud on wood
        L('1841', .565, .95, .002, -2, fade_out=.15),      # its body: a hollow thud on wooden boards
        L('1812', 1.37, 1.75, .02, -14, hp=300, fade_out=.15),  # its leaves stirred
    ]),
    'gold_tree_knock_2': dict(lufs=-13, length=.5, layers=[
        L('0705', 10.945, 11.35, 0, -1, fade_out=.15),
        L('1841', .565, .95, .002, -2, fade_out=.15),      # its body: a hollow thud on wooden boards
        L('1812', 1.37, 1.75, .02, -14, hp=300, fade_out=.15),
    ]),
    'gold_tree_knock_3': dict(lufs=-13, length=.5, layers=[
        L('0705', 21.505, 21.90, 0, -1, fade_out=.15),
        L('1842', .605, .98, .002, -2, fade_out=.15),
        L('1813', .15, .50, .02, -15, hp=300, fade_out=.15),
    ]),
    'gold_tree_wither': dict(lufs=-13, length=1.3, layers=[
        L('0518', 4.0, 4.45, 0, -6, speed=.55, hp=120, fade_out=.2),  # a groan of wood
        L('1301', 7.30, 8.00, .08, -9, hp=300, fade_in=.05, fade_out=.2),  # twigs crackling
        L('2889', 9.05, 9.70, .2, -11, hp=300, fade_out=.25),  # dry leaves
        L('1305', 4.0, 4.6, .45, -13, hp=150, fade_in=.05, fade_out=.2),  # soil settling
        L('1839', .855, 1.20, .78, -12, speed=.9, fade_out=.1),  # the last of it into the ground
    ]),
    # ---- the basic attack (2.154.0): each punch's whoosh through the air (its loudest moment where client/stand/GoldCombo's
    # SWING_PEAK says: as the fist lands), and the blow on whom it lands (its crack at the start: played as the server's
    # word comes, on the punch's contact)
    'gold_punch_swing_1': dict(lufs=-17, length=.26, layers=[
        L('0128', .03, .30, .07, -1, speed=1.3, hp=250, peak=True, fade_out=.06),   # a quick swish
        L('1802', .08, .22, .07, -11, speed=1.4, hp=120, peak=True),                # a little weight under it
    ]),
    'gold_punch_swing_2': dict(lufs=-16, length=.3, layers=[
        L('1795', .22, .46, .07, -1, speed=1.45, hp=200, peak=True, fade_in=.02, fade_out=.06),
        L('1799', .12, .30, .075, -9, speed=1.3, hp=100, peak=True),
    ]),
    'gold_punch_swing_3': dict(lufs=-16, length=.38, layers=[
        L('1800', .24, .50, .10, -1, speed=1.3, hp=200, peak=True, fade_in=.03, fade_out=.08),  # the arm swept round
        L('1798', .15, .33, .10, -8, speed=1.15, hp=90, peak=True),
    ]),
    'gold_punch_swing_4': dict(lufs=-14.5, length=.5, layers=[
        L('1796', .18, .52, .12, -1, speed=1.1, hp=150, peak=True, fade_in=.05, fade_out=.1),  # the whole body behind it
        L('1797', .14, .36, .12, -6, speed=.9, hp=60, peak=True),
        L('1801', .30, .48, .125, -9, speed=1.2, hp=300, peak=True),
    ]),
    # (a blow alone is all crack, some 20 dB between its peak and its loudness over 100 ms: a body's thud under it gives
    # it weight, and brings it to about the loudness of the game's own strong attack, -14.6; the limiter presses its
    # crack down 3 dB at most and lets go of it in 12 ms, not 60, so the thud after it keeps its weight)
    'gold_punch_hit_1': dict(release=.012, lufs=-16, length=.35, layers=[
        L('2467', .07, .45, .006, -3, peak=True, fade_out=.12),                    # a sharp smack
        L('0806', 1.11, 1.30, .008, -3, peak=True, lp=2500, fade_out=.08),         # the thump in a body
        L('2453', .44, .62, .014, -9, peak=True, hp=60, fade_out=.08),             # its weight
    ]),
    'gold_punch_hit_2': dict(release=.012, lufs=-15.5, length=.4, layers=[
        L('2463', .045, .45, .006, -2, peak=True, fade_out=.12),
        L('0806', 2.17, 2.36, .009, -3, peak=True, lp=2500, fade_out=.08),
        L('2453', .44, .64, .015, -7, peak=True, hp=60, fade_out=.1),
    ]),
    'gold_punch_hit_3': dict(release=.012, lufs=-14.5, length=.42, layers=[
        L('2466', .075, .45, .006, -3, peak=True, fade_out=.12),                   # a meatier smack across the jaw
        L('0806', 3.21, 3.40, .009, -2, peak=True, lp=2500, fade_out=.08),
        L('2453', .44, .66, .016, -6, peak=True, hp=60, fade_out=.1),
        L('2461', .05, .30, .004, -12, peak=True, hp=1500),
    ]),
    'gold_punch_hit_4': dict(release=.012, lufs=-13.5, length=.8, layers=[
        L('2460', .065, .55, .006, -1, peak=True, fade_out=.15),                   # the crack
        L('0806', 4.40, 4.62, .009, -3, peak=True, lp=2500, fade_out=.1),          # the thump
        L('2453', .44, .75, .016, -4, peak=True, hp=50, fade_out=.15),             # a body's weight behind it
        L('1797', .15, .50, .12, -14, speed=1.2, hp=300, peak=True),               # the air as they are thrown
    ]),
}
# the sounds the game picks among (each a sound event with these files)
VARIANTS = {'gold_life_croak': 3, 'gold_tree_knock': 3}


def smooth(v):
    v = np.clip(v, 0, 1)
    return v * v * (3 - 2 * v)


_cache = {}


def load(folder, number):
    if number not in _cache:
        for ext in ('wav', 'flac'):
            path = os.path.join(folder, f'bsb_{number}.{ext}')
            if os.path.exists(path):
                break
        else:
            sys.exit(f'bsb_{number}.wav is not in {folder}: https://bigsoundbank.com/UPLOAD/bwf-en/{number}.wav')
        raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True, check=True).stdout
        _cache[number] = np.frombuffer(raw, np.float32).astype(float)
    return _cache[number]


def resample(y, speed):
    """Played {speed} times as fast (its pitch with it)."""
    if abs(speed - 1) < 1e-6:
        return y
    from fractions import Fraction
    f = Fraction(speed).limit_denominator(100)
    return signal.resample_poly(y, f.denominator, f.numerator)


def layer(folder, l):
    x = load(folder, l.number)
    # (a little more either side for the filters to settle on)
    pad = int(.05 * SR)
    a, b = int(l.start * SR), int(l.end * SR)
    y = x[max(0, a - pad):min(len(x), b + pad)].copy()
    lead = a - max(0, a - pad)
    if l.hp:
        y = signal.sosfiltfilt(signal.butter(4, l.hp, 'highpass', fs=SR, output='sos'), y)
    if l.lp:
        y = signal.sosfiltfilt(signal.butter(4, l.lp, 'lowpass', fs=SR, output='sos'), y)
    y = y[lead:lead + (b - a)]
    if l.rev:
        y = y[::-1].copy()
    y = resample(y, l.speed)
    s = np.arange(len(y)) / SR
    fi = max(l.fade_in, 1e-4)
    y *= smooth(s / fi)
    if l.fade_out > 0:
        y *= smooth((s[-1] - s) / l.fade_out)
    top = np.abs(y).max()
    if top <= 0:
        return 0, y
    y *= 10 ** (l.level / 20) / top
    at = l.at - (np.argmax(np.abs(y)) / SR if l.peak else 0)
    return int(round(at * SR)), y


# K-weighting (ITU-R BS.1770, at 48 kHz)
K1 = ([1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585])
K2 = ([1.0, -2.0, 1.0], [1, -1.99004745483398, 0.99007225036621])
# the window its loudness is taken over (s): the loudest 100 ms of it (these are short sounds, most of them a blow)
WINDOW = .1


def loudness(y):
    """Its loudness (LUFS, K-weighted as ITU-R BS.1770): over its loudest WINDOW."""
    z = signal.lfilter(*K2, signal.lfilter(*K1, y))
    n = int(WINDOW * SR)
    c = np.concatenate([[0], np.cumsum(z * z)])
    power = (c[n:] - c[:-n]).max() / n if len(z) > n else c[-1] / n
    return -0.691 + 10 * np.log10(power + 1e-20)


def limit(y, ceiling, release=.06):
    """Keeps the peaks under the ceiling: a gain that looks LOOK ahead and comes down over it, and recovers over
    {release} seconds (60 ms; a blow's own, its crack a few milliseconds long, sooner)."""
    from scipy.ndimage import minimum_filter1d, uniform_filter1d
    need = np.minimum(1, ceiling / np.maximum(np.abs(y), 1e-12))
    if need.min() >= 1:
        return y
    n = int(LOOK * SR)
    g = uniform_filter1d(minimum_filter1d(need, 2 * n + 1, mode='nearest'), 2 * n + 1, mode='nearest')
    rel = 1 - np.exp(-1 / (release * SR))
    out = np.empty_like(g)
    cur = 1.0
    for i, v in enumerate(g):
        cur = v if v < cur else cur + (v - cur) * rel
        out[i] = cur
    return y * out


# the limiter's look ahead (s), and the most it presses a sound's peaks down (dB)
LOOK = .0015
SQUEEZE = 8.0


def finish(y, lufs, squeeze=SQUEEZE, release=.06):
    ceiling = 10 ** (CEILING_DB / 20)
    gain = 10 ** ((lufs - loudness(y)) / 20)
    gain = min(gain, ceiling * 10 ** (squeeze / 20) / np.abs(y).max())
    return limit(y * gain, ceiling, release)


def loop(y, cross):
    """A loop with no seam: its last {cross} seconds laid over its first, the end then cut there."""
    n = int(cross * SR)
    head, body = y[:n], y[n:]
    w = smooth(np.arange(n) / n)
    body = body.copy()
    body[-n:] = body[-n:] * (1 - w) + head * w
    return body


def make(folder, name, spec):
    length = spec['length']
    mix = np.zeros(int((length + 1) * SR))
    for l in spec['layers']:
        a, y = layer(folder, l)
        if a < 0:
            y, a = y[-a:], 0
        y = y[:len(mix) - a]
        mix[a:a + len(y)] += y
    if 'loop' in spec:
        mix = loop(mix[:int((length + spec['loop']) * SR)], spec['loop'])
        return finish(mix, spec['lufs'])
    mix = mix[:int(length * SR)]
    s = np.arange(len(mix)) / SR
    mix *= smooth((length - s) / TAIL)
    return finish(mix, spec['lufs'], spec.get('squeeze', SQUEEZE), spec.get('release', .06))


def write(y, out, name):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        wavfile.write(wav, SR, (np.clip(y, -1, 1) * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '7',
                        os.path.join(out, name + '.ogg')], check=True)
    print(f'{name}: {len(y) / SR:.3f} s, {loudness(y):.1f} LUFS, peak {20 * np.log10(np.abs(y).max()):.1f} dB')


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    folder = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    os.makedirs(out, exist_ok=True)
    only = set(sys.argv[3:])
    for name, spec in SOUNDS.items():
        if only and name not in only:
            continue
        write(make(folder, name, spec), out, name)


if __name__ == '__main__':
    main()
