"""엑스 차라's R with the hack off, the jab: its two sounds, lifted out of the user's recording (2.124.0).
usage: python3 -I tools/xchara/extract_jab.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used once, its sound under background music ("효과음은 띡 소리 나고 찌르는
효과음이고"). The recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what
is written is the recording's own sound with the music taken down.

What is in the recording (seconds into its sound, which runs about 0.24 s behind its picture):
  0.313-0.385  the press, "띡": the same three stepped square tones as the restore's press (extract_heal.py), 1688 Hz,
               2250 Hz and 3000 Hz, each with its odd harmonics
  1.000-1.52   the thrust: a burst of noise from 150 Hz to 16 kHz, 10 to 28 dB over the music, at its fullest for a
               fifth of a second and dying away over three more tenths; 0.69 s after the press (the thrust lands 0.7 s
               after R is pressed: XCharaCombo.JAB's blow)
  the music    a kick and bass under 150 Hz (the rest ducked under each kick), held notes, wide (stereo) pads

Each plays once, so the plays cannot be laid over each other as for the knife's (extract_slash.py). The music is taken
down by what tells it from the effects:
  1. the effects are in the middle of the stereo picture, much of the music is not: the music's share of the middle is
     estimated from the side (their usual ratio by frequency where no effect plays) and taken off (Wiener)
  2. the music's notes hold one pitch; the press's tones are short steps and the thrust is noise: what stays on one
     pitch for 75 ms and stands out of what is around it is taken off (but the press's own tones and odd harmonics,
     kept whole; of the press's moment nothing else is kept: it is only those tones)
  3. the low end is the music's kick and bass: taken off (under 1.4 kHz for the press, under 150 Hz for the thrust)
  4. each cut to its time and faded (the thrust's end over its last 0.16 s, where it cannot be told from the music)
What cannot be told apart stays: music in the middle, at the thrust's moment, as loud as noise.

xchara_jab_press starts 10 ms before the press; the client plays it as the skill starts. xchara_jab starts 50 ms before
the thrust's onset; the client plays it a tick before the blow (client/xchara/KnifeClient.JAB_SOUND_BEFORE), so the onset
is on the blow. Mono 48 kHz, each brought to its loudness under a limiter that keeps peaks below -1 dB.
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
# the press: its time in the recording, where its fade out begins, and its three tones: from, to (seconds), pitch (Hz)
P0, P1, PFADE = .303, .41, .385
PRESS = [(.306, .336, 1688.), (.316, .352, 2250.), (.340, .392, 3000.)]
# the thrust: its onset, how long before it its file starts, its end and where its fade out begins
ONSET, LEAD, S1, SFADE = 1.000, .050, 1.56, 1.40
# where no effect plays (the music alone): for the middle's usual ratio to the side
QUIET = [(.04, .17), (.42, .66), (.70, .96), (1.60, 1.84)]
LUFS_PRESS, LUFS_THRUST = -19.0, -16.0


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
    ratio = np.median(pm[:, quiet], 1) / (np.median(ps[:, quiet], 1) + 1e-18)
    ratio = np.clip(median_filter(ratio, size=15, mode='nearest'), .1, 4.)
    music = ratio[:, None] * uniform_filter(ps, size=(5, 9), mode='nearest')
    c = np.maximum(1 - 1.6 * music / (pm + 1e-18), .03) * mid
    a = np.abs(c)
    held = median_filter(a, size=(1, int(round(.075 / (HOP / SR))) | 1), mode='nearest')
    around = median_filter(a, size=(int(round(330 / (SR / N))) | 1, 1), mode='nearest')
    g = 1 - np.clip((held / (around + 1e-12) - 1.6) / 1.4, 0, 1) * .97
    g = np.minimum(g, np.roll(g, 1, 0))
    g = np.minimum(g, np.roll(g, -1, 0))
    return f, t, c, g


def back(c, n):
    return signal.istft(c, SR, nperseg=N, noverlap=N - HOP)[1][:n]


def press(x):
    """The press: its three tones and their odd harmonics whole, nothing else of its moment."""
    f, t, c, _ = middle(x)
    keep = np.zeros(c.shape, bool)
    for a0, b0, f0 in PRESS:
        k = np.round(f[:, None] / f0)
        keep |= (k % 2 == 1) & (np.abs(f[:, None] - k * f0) < 150) & (t[None, :] > a0) & (t[None, :] < b0)
    c = np.where(keep, c, c * .02) * (1 / (1 + (1400 / np.maximum(f, 1)) ** 8))[:, None]
    y = back(c, len(x))
    s = np.arange(len(y)) / SR
    y *= smooth((s - P0) / .008) * (1 - smooth((s - PFADE) / (P1 - PFADE)))
    return y[int(P0 * SR):int(P1 * SR)]


def thrust(x):
    """The thrust: the middle's noise from its onset, the music's held pitches and its low end taken down."""
    f, t, c, g = middle(x)
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
    p, th = press(x), thrust(x)
    # what was kept and what was taken down, against the recording's middle (dB)
    m = x.mean(1)
    full = np.zeros_like(m)
    full[int(P0 * SR):int(P0 * SR) + len(p)] = p
    a = int((ONSET - LEAD) * SR)
    full[a:a + len(th)] = th
    for name, t0, t1, lo, hi in [("the press's first tone", .31, .33, 1600, 1800), ("its second", .32, .345, 2150, 2350), ("its third", .345, .385, 2900, 3100),
                                 ("the music under the press", .31, .385, 200, 1400), ("the thrust, 150-600 Hz", 1.02, 1.2, 150, 600),
                                 ("the thrust, 600 Hz-5 kHz", 1.02, 1.2, 600, 5000), ("the thrust, over 5 kHz", 1.02, 1.2, 5000, 16000),
                                 ("the music's kick under it", 1.02, 1.3, 30, 120), ("the thrust's tail", 1.3, 1.4, 600, 5000)]:
        print(f'{name:28s} {band(full, t0, t1, lo, hi) - band(m, t0, t1, lo, hi):+6.1f} dB')
    os.makedirs(out, exist_ok=True)
    write(finish(p, LUFS_PRESS), out, 'xchara_jab_press')
    write(finish(th, LUFS_THRUST), out, 'xchara_jab')


if __name__ == '__main__':
    main()
