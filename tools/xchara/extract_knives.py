"""엑스 차라's T with the hack off, the three small knives thrown: the throw's sound, lifted out of the user's recording (2.131.0).
usage: python3 -I tools/xchara/extract_knives.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used twice, its sound under background music ("T스킬 효과음은 영상 참고하고
2번 재생 돼"). The recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what
is written is the recording's own sound with what is not the throw taken down.

What is in the recording (seconds into its sound):
  0.160-0.70   the throw, first use: a sharp onset and a rush that rises to about 2 kHz, steady lines near 2, 4.8 and
               11 kHz in it, noise from 150 Hz to over 12 kHz dying away over half a second; 15 to 25 dB over the music
  2.622-3.16   the same, second use, 2.462 s later: the very samples (the two laid on each other agree 0.60 with the
               music and the strikes under them)
  0.329, ...   the knives' strikes, first use (they landed at once: the picture's "1 Hit" a sixth of a second after the
               throw's onset), and 3.02, 3.14 and 3.73 of the second (0.40 s after its onset, and on). A strike is the
               very sound the game has already as the knives shot's (xchara_shot_hit laid on the one alone at 3.73
               agrees 0.60): it is not lifted again; the small knives strike with that one.
  the music    quiet here: a kick and bass under 150 Hz, a few held notes

The strikes lie over the throw's sound at different moments of it in the two uses (0.17 s into the first, 0.40 s into
the second), and the music under the two is different. So the throw is what the two uses agree on:
  1. the second use is laid on the first to the sample (by their waveforms)
  2. the music by its usual level by frequency where no effect plays (OVER times over) is taken off both
  3. the second use tells what of the first is not the throw: where a cell (a time and a pitch) of the first use,
     smoothed, is louder than the same cell of the second, it is turned down to the second's (the throw is the same
     both times; a strike or a note under it is not). The first use is some 3 dB the louder all through (its camera is
     nearer its user), measured where neither has a strike and allowed for. The first use's own samples otherwise
  4. the low end is the music's kick and bass: taken off under 150 Hz
  5. cut to its time and faded out (from 0.4 s on both uses have a strike's tail under it, which cannot be told from it)
What cannot be told apart stays: what sounds at the same moment of both uses.

xchara_knives starts LEAD before the onset; the client plays it as the throw begins (KnifeClient: the video's sound
begins with the press, a quarter of a second before the first knife is in flight). Mono 48 kHz, brought to its loudness
under a limiter that keeps peaks below -1 dB.
"""
import os
import sys

import numpy as np
from scipy import signal
from scipy.ndimage import median_filter, uniform_filter
from scipy.signal import correlate

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_pull import SR, N, HOP, band, decode, finish, smooth, write  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# the first use: its onset, how long before it its file starts, its end and where its fade out begins
ONSET, LEAD, END, FADE = .160, .010, .70, .52
# about how much later the second use plays (found to the sample by the waveforms)
LATER = 2.46
# where no effect plays (the music alone)
QUIET = [(.03, .14), (.90, 2.58), (3.35, 3.68)]
LUFS = -15.0
OVER = 1.5
# the least a cell louder in the first use than in the second is turned down to
LEAST = .2
# how long from its onset neither use has a strike under it (the first use's first is 0.169 s in)
CLEAR = .16
LOW = 150.


def line_up(m):
    """How many samples later the second use plays, and how well the two agree there."""
    a0, n = int((ONSET - .02) * SR), int(.42 * SR)
    seg = m[a0:a0 + n]
    b0 = int((ONSET - .02 + LATER - .05) * SR)
    s1 = m[b0:b0 + n + int(.10 * SR)]
    c = correlate(s1, seg, 'valid')
    k = int(np.argmax(c))
    other = s1[k:k + n]
    return b0 + k - a0, float(c[k] / np.sqrt((seg @ seg) * (other @ other)))


def throw(x, lag):
    """The throw: the first use's middle, the music's usual level and what the second use has not taken off it."""
    m = x.mean(1)
    later = np.concatenate([m[lag:], np.zeros(lag)])
    f, t, a = signal.stft(m, SR, nperseg=N, noverlap=N - HOP)
    b = signal.stft(later, SR, nperseg=N, noverlap=N - HOP)[2]
    pa, pb = np.abs(a) ** 2, np.abs(b) ** 2
    quiet = np.zeros_like(t, bool)
    for q0, q1 in QUIET:
        quiet |= (t > q0) & (t < q1)
    level = median_filter(np.median(pa[:, quiet], 1), size=9, mode='nearest')
    clean = lambda p: np.maximum(p - OVER * level[:, None], 0)
    blur = lambda p: uniform_filter(p, size=(7, 9), mode='nearest')
    ca, cb = blur(clean(pa)), blur(clean(pb))
    # the first use is the louder of the two all through (the camera nearer its user): by how much, over the throw's
    # first sixth of a second, where neither use has a strike yet
    clear = (t > ONSET + .01) & (t < ONSET + CLEAR)
    high = f > LOW
    louder = ca[high][:, clear].sum() / (cb[high][:, clear].sum() + 1e-18)
    print(f'the first use is {10 * np.log10(louder):.1f} dB louder than the second where neither has a strike')
    same = np.sqrt(np.clip(louder * cb / (ca + 1e-18), LEAST ** 2, 1))
    c = np.sqrt(np.maximum(1 - OVER * level[:, None] / (pa + 1e-18), .03 ** 2)) * same * a / (1 + (LOW / np.maximum(f, 1)[:, None]) ** 8)
    y = signal.istft(c, SR, nperseg=N, noverlap=N - HOP)[1][:len(m)]
    s = np.arange(len(y)) / SR
    y = y * smooth((s - (ONSET - .008)) / .006) * (1 - smooth((s - FADE) / (END - FADE)))
    return y[int((ONSET - LEAD) * SR):int(END * SR)]


def main():
    video = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    x = decode(video)
    m = x.mean(1)
    lag, corr = line_up(m)
    print(f'the second use plays {lag / SR:.4f} s later; the two agree {corr:.2f}')
    y = throw(x, lag)
    full = np.zeros_like(m)
    i = int((ONSET - LEAD) * SR)
    full[i:i + len(y)] = y
    for what, a, b, lo, hi in [("its first sixth, 150 Hz-1 kHz", .165, .32, 150, 1000), ("its first sixth, 1-4 kHz", .165, .32, 1000, 4000),
                               ("its first sixth, 4-12 kHz", .165, .32, 4000, 12000), ("under the first use's strikes, 1-12 kHz", .33, .50, 1000, 12000),
                               ("the kick under it", .165, .6, 30, 120)]:
        print(f'{what:40s} {band(full, a, b, lo, hi) - band(m, a, b, lo, hi):+6.1f} dB')
    # the same stretch of the second use, before its strikes begin: how near the written sound is to it
    second = np.concatenate([m[lag:], np.zeros(lag)])
    for what, a, b, lo, hi in [("against the second use, 0.17-0.38 s in, 1-12 kHz", .33, .54, 1000, 12000)]:
        print(f'{what:40s} {band(full, a, b, lo, hi) - band(second, a, b, lo, hi):+6.1f} dB')
    e = np.array([10 * np.log10((y[int(a * SR):int((a + .05) * SR)] ** 2).mean() + 1e-12) for a in np.arange(0, len(y) / SR - .05, .05)])
    print('its level every 50 ms: ' + ' '.join(f'{v:.0f}' for v in e))
    os.makedirs(out, exist_ok=True)
    write(finish(y, LUFS), out, 'xchara_knives')


if __name__ == '__main__':
    main()
