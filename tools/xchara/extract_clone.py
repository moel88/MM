"""엑스 차라's R with the hack on, the double (2.125.0): its three sounds, lifted out of the user's recording.
usage: python3 -I tools/xchara/extract_clone.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used twice, its sound under background music ("효과음은 영상을 참고해 2번
재생할거야"). The recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what is
written is the recording's own sound with the music taken down.

What is in the recording (seconds into its sound, which runs about 0.24 s behind its picture), each use:
  1.05-1.38  the double coming: a glitching tone, 600 Hz to 6 kHz           (second use 5.2327 s later)
  1.775-2.14 the double's throw: a sharp burst from 1.5 to 10 kHz, dying away (second use 5.2327 s later: 0.70 s after
             the first sound both times)
  3.20-3.7   the one thrown going into the wall: a crash of noise, 150 Hz to 16 kHz (second use 4.87 s later: the
             throw lasted 1.42 s the first time and 1.06 s the second, so it is a sound of its own)
  the music  a kick and bass under 150 Hz, held notes, wide (stereo) pads

The first two play alike both times (the two plays line up to the sample and agree, 0.88 and 0.87), so they are laid
over each other as the knife's are (extract_slash.py): the plays' mean is the sound with the music under it halved, and
every time-frequency cell is turned down by what the plays' difference says is still music in it (a Wiener gain). The
crash is noise that does not repeat (its two plays agree 0.25), so it is taken from the first play alone as the jab's
thrust is (extract_jab.py): the music's share of the stereo middle taken off by its side, what holds one pitch taken
down, the low end (the music's kick) off, cut to its time and faded where it cannot be told from the music.

xchara_clone starts 10 ms before the first sound; the client plays it as the double comes (CloneRules.CLONE).
xchara_clone_throw and xchara_clone_wall start 50 ms before their onsets; the client plays each a tick before its
moment (the throw, the wall), so the onset is on it. Mono 48 kHz, each brought to its loudness under a limiter that
keeps peaks below -1 dB.
"""
import os
import sys

import numpy as np
from scipy import signal
from scipy.ndimage import uniform_filter
from scipy.signal import butter, correlate, sosfiltfilt

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import extract_jab  # noqa: E402
from extract_jab import SR, back, band, decode, finish, smooth, write  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# the two sounds that play alike both times, in the first use: from, to, where the fade out begins (seconds)
COME = (1.040, 1.43, 1.37)
THROW_ONSET, THROW_LEAD, THROW_END, THROW_FADE = 1.775, .050, 2.24, 2.10
# about how much later the second use plays them (found to the sample by the band above 1.5 kHz)
LATER = 5.23
# the crash, in the first use: its onset, how long before it its file starts, its end and where its fade out begins
WALL_ONSET, WALL_LEAD, WALL_END, WALL_FADE = 3.200, .050, 3.80, 3.52
# where no effect plays (the music alone): for the middle's usual ratio to the side
QUIET = [(.2, .9), (1.46, 1.72), (2.35, 3.1), (3.9, 4.6)]
LUFS_COME, LUFS_THROW, LUFS_WALL = -19.0, -16.0, -15.0
N, HOP = 1024, 128


def line_up(m):
    """How many samples later the second use plays the first two sounds, and how loud against the first."""
    hp = sosfiltfilt(butter(4, 1500, 'hp', fs=SR, output='sos'), m)
    a0, a1 = int(1.0 * SR), int(2.2 * SR)
    s0 = hp[a0:a1]
    b0 = int((1.0 + LATER - .1) * SR)
    s1 = hp[b0:b0 + len(s0) + int(.2 * SR)]
    c = correlate(s1, s0, 'valid')
    k = int(np.argmax(c))
    seg = s1[k:k + len(s0)]
    return b0 + k - a0, float(seg @ s0 / (s0 @ s0)), float(c[k] / np.sqrt((s0 @ s0) * (seg @ seg)))


def agreed(m, lag, gain, t0, t1):
    """What the two plays agree on between t0 and t1 of the first: their mean, each cell turned down by their difference."""
    pad = .15
    a, b = int((t0 - pad) * SR), int((t1 + pad) * SR)
    p1, p2 = m[a:b], m[a + lag:b + lag] / gain
    f, _, z1 = signal.stft(p1, SR, nperseg=N, noverlap=N - HOP)
    z2 = signal.stft(p2, SR, nperseg=N, noverlap=N - HOP)[2]
    e = (z1 + z2) / 2
    # the mean's share of what the plays do not agree on (their difference is twice the music's spread)
    music = uniform_filter(np.abs(z1 - z2) ** 2 / 4, size=(3, 5), mode='nearest')
    own = uniform_filter(np.abs(e) ** 2, size=(3, 5), mode='nearest')
    # (above 8 kHz the recording's codec makes the plays agree less on what is the same sound: turned down less)
    hard = np.where(f < 8000, 1.5, .8)[:, None]
    g = np.clip(1 - hard * music / (own + 1e-18), .02, 1)
    y = signal.istft(e * g, SR, nperseg=N, noverlap=N - HOP)[1][:b - a]
    s = a / SR + np.arange(len(y)) / SR
    return y, s


def come(m, lag, gain):
    t0, t1, fade = COME
    y, s = agreed(m, lag, gain, t0, t1)
    y = sosfiltfilt(butter(4, 300, 'hp', fs=SR, output='sos'), y)
    y *= smooth((s - t0) / .008) * (1 - smooth((s - fade) / (t1 - fade)))
    return y[(s >= t0) & (s < t1)]


def throw(m, lag, gain):
    t0 = THROW_ONSET - THROW_LEAD
    y, s = agreed(m, lag, gain, t0, THROW_END)
    y = sosfiltfilt(butter(4, 500, 'hp', fs=SR, output='sos'), y)
    y *= smooth((s - (THROW_ONSET - .014)) / .010) * (1 - smooth((s - THROW_FADE) / (THROW_END - THROW_FADE)))
    return y[(s >= t0) & (s < THROW_END)]


def wall(x):
    """The crash: the first play's middle from its onset, the music's held pitches and its low end taken down."""
    extract_jab.QUIET = QUIET
    f, t, c, g = extract_jab.middle(x)
    c = c * g * (1 / (1 + (150 / np.maximum(f, 1)) ** 8))[:, None]
    y = back(c, len(x))
    s = np.arange(len(y)) / SR
    y *= smooth((s - (WALL_ONSET - .012)) / .010) * (1 - smooth((s - WALL_FADE) / (WALL_END - WALL_FADE)))
    return y[int((WALL_ONSET - WALL_LEAD) * SR):int(WALL_END * SR)]


def main():
    video = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    x = decode(video)
    m = x.mean(1)
    lag, gain, corr = line_up(m)
    print(f'the second use plays the first two sounds {lag / SR:.4f} s later, {gain:.2f} as loud; the plays agree {corr:.2f}')
    a, b, c = come(m, lag, gain), throw(m, lag, gain), wall(x)
    # what was kept and what was taken down, against the recording's middle (dB)
    full = np.zeros_like(m)
    for y, t0 in [(a, COME[0]), (b, THROW_ONSET - THROW_LEAD), (c, WALL_ONSET - WALL_LEAD)]:
        full[int(t0 * SR):int(t0 * SR) + len(y)] = y
    for name, t0, t1, lo, hi in [("the double coming, 600 Hz-3 kHz", 1.12, 1.34, 600, 3000), ("the same, 3-6 kHz", 1.12, 1.34, 3000, 6000),
                                 ("the music under it, under 300 Hz", 1.06, 1.36, 30, 300), ("the throw, 1.5-6 kHz", 1.79, 2.0, 1500, 6000),
                                 ("the throw, 6-10 kHz", 1.79, 1.95, 6000, 10000), ("the music under it, under 500 Hz", 1.79, 2.1, 30, 500),
                                 ("the crash, 150-600 Hz", 3.22, 3.45, 150, 600), ("the crash, 600 Hz-5 kHz", 3.22, 3.45, 600, 5000),
                                 ("the crash, over 5 kHz", 3.22, 3.45, 5000, 16000), ("the music's kick under it", 3.2, 3.5, 30, 120)]:
        print(f'{name:34s} {band(full, t0, t1, lo, hi) - band(m, t0, t1, lo, hi):+6.1f} dB')
    os.makedirs(out, exist_ok=True)
    write(finish(a, LUFS_COME), out, 'xchara_clone')
    write(finish(b, LUFS_THROW), out, 'xchara_clone_throw')
    write(finish(c, LUFS_WALL), out, 'xchara_clone_wall')


if __name__ == '__main__':
    main()
