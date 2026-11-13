"""엑스 차라's Y with the hack on, the pull: its sound, lifted out of the user's recording (2.127.0).
usage: python3 -I tools/xchara/extract_pull.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used once, its sound under background music ("효과음은 영상에 있고"). The
recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what is written is the
recording's own sound with the music taken down.

What is in the recording (seconds into its sound, which runs about 0.24 s behind its picture, as the jab's did):
  0.335-0.66   the "!!!" warning: the very sound the game has already as G's (xchara_g_warn: three stepped tones, 1688,
               2250 and 3000 Hz, then 938, 1336, 1055, 1500, 1195 and 1688 Hz, pitch for pitch). It is not lifted
               again: the pull plays G's.
  0.665-1.14   the pull itself: a burst of noise from 150 Hz to over 16 kHz that comes on within a hundredth of a
               second, 10 to 14 dB over the music, at its fullest for a fifth of a second and dying away over three
               more tenths; 0.33 s after the warning starts (the one marked stands before its user 0.27 s after the
               "!!!" shows)
  the music    a bass under 150 Hz, a held note near 600 Hz, wide (stereo) pads

It plays once, so the plays cannot be laid over each other as for the knife's (extract_slash.py). The music is taken
down by what tells it from the effect, as for the jab's thrust (extract_jab.py):
  1. the music is about as loud all through: its usual level by frequency in the middle of the stereo picture, where
     no effect plays, is taken off the middle (twice over). Not its share by the side, as for the jab: the burst is a
     little wide itself, and that took a third of it away
  2. the music's notes hold one pitch: under 1 kHz, where its held notes are, what stays on one pitch for 75 ms and
     stands out of what is around it is taken off (not above: the burst has lasting pitches of its own, and taking
     those off took 5 dB of it)
  3. the low end is the music's bass: taken off under 150 Hz
  4. cut to its time and faded (its end over its last 0.14 s, where it cannot be told from the music)
What cannot be told apart stays: music in the middle, at the burst's moment, as loud as noise.

xchara_pull starts on the burst's onset (16 ms before it, faded in); the client begins it as it hears of the pull
(client/xchara/PullClient), so the onset is on the one put there whenever the server's word comes. Mono 48 kHz, brought to its loudness under a
limiter that keeps peaks below -1 dB.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile
from scipy.ndimage import median_filter, uniform_filter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import CEILING_DB, limit, loudness  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SR = 48000
N, HOP = 2048, 96
# the burst: its onset, how long before it its file starts, its end and where its fade out begins
ONSET, LEAD, S1, SFADE = .665, .016, 1.16, 1.02
# where no effect plays (the music alone): for the middle's usual ratio to the side
QUIET = [(.03, .31), (1.22, 2.20)]
LUFS = -16.0
# how many times the music's usual level (by frequency, where no effect plays) is taken off the middle
OVER = 2.0
# held pitches are the music's only under this (Hz): the burst's own, above, are kept
HELD_UNDER = 1000.


def decode(video):
    """The recording's sound: stereo, 48 kHz, as floats."""
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', video, '-vn', '-ac', '2', '-ar', str(SR), wav], check=True)
        _, x = wavfile.read(wav)
    return x.astype(float) / 32768


def smooth(v):
    v = np.clip(v, 0, 1)
    return v * v * (3 - 2 * v)


def middle(x):
    """The recording's middle with the music's share taken off by its side, and what holds one pitch taken down: (f, t, spectrum, the gain for held pitches)."""
    stft = lambda y: signal.stft(y, SR, nperseg=N, noverlap=N - HOP)
    f, t, left = stft(x[:, 0])
    right = stft(x[:, 1])[2]
    mid, side = (left + right) / 2, (left - right) / 2
    pm, ps = np.abs(mid) ** 2, np.abs(side) ** 2
    quiet = np.zeros_like(t, bool)
    for a, b in QUIET:
        quiet |= (t > a) & (t < b)
    # (the burst itself is a little wide: the side at its moment is not the music alone, and the music's share taken
    # from it would take a third of the burst with it. The music's level by frequency where no effect plays is used
    # instead, as for the double's sounds, extract_clone.py)
    level = median_filter(np.median(pm[:, quiet], 1), size=9, mode='nearest')
    music = OVER * level[:, None]
    c = np.maximum(1 - music / (pm + 1e-18), .05) * mid
    a = np.abs(c)
    held = median_filter(a, size=(1, int(round(.075 / (HOP / SR))) | 1), mode='nearest')
    around = median_filter(a, size=(int(round(330 / (SR / N))) | 1, 1), mode='nearest')
    g = 1 - np.clip((held / (around + 1e-12) - 1.6) / 1.4, 0, 1) * .97
    g = np.minimum(g, np.roll(g, 1, 0))
    g = np.minimum(g, np.roll(g, -1, 0))
    return f, t, c, g


def back(c, n):
    return signal.istft(c, SR, nperseg=N, noverlap=N - HOP)[1][:n]


def burst(x):
    """The pull's burst: the middle's noise from its onset, the music's held pitches and its low end taken down."""
    f, t, c, g = middle(x)
    # (the burst has pitches of its own that last: held pitches are taken down only where the music's held notes are, under HELD_UNDER)
    g = np.where(f[:, None] < HELD_UNDER, g, 1)
    c = c * g * (1 / (1 + (150 / np.maximum(f, 1)) ** 8))[:, None]
    y = back(c, len(x))
    s = np.arange(len(y)) / SR
    t0 = ONSET - LEAD
    # (nothing before its onset but what leads into it over its last hundredth of a second)
    y *= smooth((s - (ONSET - .012)) / .010) * (1 - smooth((s - SFADE) / (S1 - SFADE)))
    return y[int(t0 * SR):int(S1 * SR)]


def finish(e, lufs):
    ceiling = 10 ** (CEILING_DB / 20)
    for _ in range(4):
        e = limit(e * 10 ** ((lufs - loudness(e)) / 20), ceiling)
    return e


def band(y, a, b, lo, hi):
    f, t, z = signal.stft(y, SR, nperseg=N, noverlap=N - HOP)
    return 10 * np.log10((np.abs(z[(f >= lo) & (f < hi)][:, (t >= a) & (t < b)]) ** 2).mean() + 1e-18)


def write(y, out, name):
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        wavfile.write(wav, SR, (np.clip(y, -1, 1) * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '7', os.path.join(out, name + '.ogg')], check=True)
    print(f'{name}: {len(y) / SR:.3f} s, {loudness(y):.1f} LUFS, peak {20 * np.log10(np.abs(y).max()):.1f} dB')


def main():
    video = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    x = decode(video)
    y = burst(x)
    # what was kept and what was taken down, against the recording's middle (dB)
    m = x.mean(1)
    full = np.zeros_like(m)
    a = int((ONSET - LEAD) * SR)
    full[a:a + len(y)] = y
    for name, t0, t1, lo, hi in [("the burst, 150-600 Hz", .68, .86, 150, 600), ("the burst, 600 Hz-5 kHz", .68, .86, 600, 5000),
                                 ("the burst, over 5 kHz", .68, .86, 5000, 16000), ("the music's bass under it", .68, 1.0, 30, 120),
                                 ("the music's held note", .68, 1.0, 560, 660), ("the burst's tail", .96, 1.06, 600, 5000)]:
        print(f'{name:28s} {band(full, t0, t1, lo, hi) - band(m, t0, t1, lo, hi):+6.1f} dB')
    os.makedirs(out, exist_ok=True)
    write(finish(y, LUFS), out, 'xchara_pull')


if __name__ == '__main__':
    main()
