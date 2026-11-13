"""엑스 차라's Y with the hack off, the four knives shot: a knife's strike, lifted out of the user's recording (2.130.0).
usage: python3 -I tools/xchara/extract_shot.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used once, its sound under background music ("효과음은 영상 참고하고"). The
recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what is written is the
recording's own sound with what is not the strike taken down.

What is in the recording (seconds into its sound, which runs about 0.25 s behind its picture, as the pull's did):
  0.39-0.94    each knife's coming to be: the very sound the game has already as G's "!!!" (xchara_g_warn: its stepped
               tones, pitch for pitch; the game's file laid on the recording agrees 0.80 at 0.600 s), once for the
               first knife at 0.39 and again for the second at 0.60 (the picture's two knives, 0.14 and 0.40 s), and
               again under the strikes at about 1.45 and 1.52 for the third and the fourth. It is not lifted again:
               the knives play G's.
  1.230        a knife's strike (the picture's "1 Hit", 0.94 s): a burst of noise from 150 Hz to over 16 kHz that comes
               on within a hundredth of a second, 15 to 25 dB over the music: a crack of some 0.04 s, a dip, and a
               softer swell after it until about a quarter of a second on, then gone within a tenth more
  1.466, 1.614 the same again for the second and the third ("2 Hits", "3 Hits": 1.22 and 1.37 s), and a quieter one
               near 1.93 (the fourth knife, which missed the one it was sent at, on what was behind them)
  the music    a kick and bass under 150 Hz, held notes, wide (stereo) pads

The strike plays three times, each beginning as the one before dies away (0.234 and 0.382 s after the first), so the
first's last tenth lies under the second's crack. It is taken from the first: the recording from its onset on, with everything that is not it
taken down, by how much of what sounds at each moment and pitch is the first strike's:
  1. the music by its usual level by frequency where no effect plays (OVER times over), as for the pull's burst
     (extract_pull.py)
  2. the later strikes by the first's own shape: all three are the same sound, so what the second adds at a moment
     after its onset is what the first was that long after its own (and the third's likewise). Going along from the
     first's onset, the first's share of the recording's power is what is left of it with the later ones' (the
     first's own, earlier, smoothed) taken off: before the second begins all of it, after it less and less
  3. the low end is the music's kick and bass: taken off under 150 Hz
  4. cut to its time and faded out (its last tenths cannot be told from the fourth's)
What cannot be told apart stays: music at the strike's moment as loud as noise, and the knives' coming-to-be tones
under its tail where they are as loud (they are taken down with the music's held notes above 800 Hz: what holds one
pitch for 40 ms and stands out of the noise around it).

xchara_shot_hit starts LEAD before the strike's onset; the client begins it that long before a knife is seen to land
(BladeShot.HIT_SOUND_LEAD ticks) when the server's word comes in time, else as it comes. Mono 48 kHz, brought to its
loudness under a limiter that keeps peaks below -1 dB.
"""
import os
import sys

import numpy as np
from scipy import signal
from scipy.ndimage import median_filter, uniform_filter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_pull import SR, N, HOP, band, decode, finish, smooth, write  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# the first strike: its onset, how long before it its file starts, its end and where its fade out begins
ONSET, LEAD, END, FADE = 1.230, .050, 1.64, 1.50
# how long after the first the second and the third begin (found to the millisecond by their loudness over 1.5 kHz)
LATER = (.236, .384)
# where no effect plays (the music alone)
QUIET = [(.03, .36), (.97, 1.20), (2.10, 2.60)]
LUFS = -16.0
OVER = 2.0
LOW = 150.
# the tones under its tail (the knives' coming to be, the music's notes): from this pitch up, what holds one pitch this long
HELD_FROM, HELD_FOR = 800., .040


def onsets(m):
    """How long after the first strike the second and the third begin: where the loudness over 1.5 kHz jumps."""
    from scipy.signal import butter, sosfiltfilt
    y = sosfiltfilt(butter(4, [1500, 12000], 'bp', fs=SR, output='sos'), m)
    h = int(.002 * SR)
    e = np.array([10 * np.log10((y[i:i + h] ** 2).mean() + 1e-12) for i in range(0, len(y) - h, h)])
    jump = e[3:] - np.array([e[max(0, i - 6):i + 1].mean() for i in range(len(e) - 3)])
    out = []
    for a, b in [(ONSET - .02, ONSET + .02), (ONSET + .20, ONSET + .27), (ONSET + .36, ONSET + .41)]:
        i0, i1 = int(a / .002), int(b / .002)
        out.append((i0 + int(np.argmax(jump[i0:i1])) + 3) * .002)
    return out


def strike(x):
    """The first strike: the recording's middle from its onset, the music and the later strikes taken down."""
    f, t, left = signal.stft(x[:, 0], SR, nperseg=N, noverlap=N - HOP)
    right = signal.stft(x[:, 1], SR, nperseg=N, noverlap=N - HOP)[2]
    mid = (left + right) / 2
    pm = np.abs(mid) ** 2
    quiet = np.zeros_like(t, bool)
    for a, b in QUIET:
        quiet |= (t > a) & (t < b)
    level = median_filter(np.median(pm[:, quiet], 1), size=9, mode='nearest')
    effect = np.maximum(pm - OVER * level[:, None], 0)
    # what holds one pitch (the tones under it): taken down from HELD_FROM up, before the shares are worked out
    mag = np.sqrt(effect)
    held = median_filter(mag, size=(1, int(round(HELD_FOR / (HOP / SR))) | 1), mode='nearest')
    around = median_filter(mag, size=(int(round(700 / (SR / N))) | 1, 1), mode='nearest')
    tone = np.clip((held / (around + 1e-12) - 1.8) / 1.6, 0, 1)
    tone = np.maximum(tone, np.maximum(np.roll(tone, 1, 0), np.roll(tone, -1, 0)))
    g_tone = np.where(f[:, None] >= HELD_FROM, 1 - .92 * tone, 1)
    effect = effect * g_tone ** 2
    # the first strike's own power, going along from its onset: what is left of the smoothed power with the later
    # strikes' (the first's own, that much earlier) taken off
    sm = np.maximum(uniform_filter(effect, size=(9, 11), mode='nearest'), 0)
    step = HOP / SR
    i0 = int(round(ONSET / step))
    n = int(round((END - ONSET) / step)) + 1
    lags = [int(round(d / step)) for d in LATER]
    own = np.zeros((len(f), n))
    for i in range(n):
        rest = sm[:, i0 + i].copy()
        for lag in lags:
            if i >= lag:
                rest -= own[:, i - lag]
        own[:, i] = np.maximum(rest, 0)
    share = np.zeros_like(pm)
    now = sm[:, i0:i0 + n]
    share[:, i0:i0 + n] = np.clip(np.where(now > 1e-16, own / np.maximum(now, 1e-16), 0), 0, 1)
    share = np.clip(uniform_filter(share, size=(5, 7), mode='nearest'), 0, 1)
    share[:, :i0 - 3] = 0
    gain = np.sqrt(np.maximum(1 - OVER * level[:, None] / (pm + 1e-18), .03 ** 2)) * g_tone * np.sqrt(share)
    c = gain * mid / (1 + (LOW / np.maximum(f, 1)[:, None]) ** 8)
    y = signal.istft(c, SR, nperseg=N, noverlap=N - HOP)[1][:len(x)]
    s = np.arange(len(y)) / SR
    t0 = ONSET - LEAD
    y = y * smooth((s - (ONSET - .012)) / .010) * (1 - smooth((s - FADE) / (END - FADE)))
    return y[int(t0 * SR):int(END * SR)]


def main():
    video = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    x = decode(video)
    m = x.mean(1)
    on = onsets(m)
    print('the strikes begin at ' + ', '.join(f'{v:.3f}' for v in on) + f' s (the later ones {on[1] - on[0]:.3f} and {on[2] - on[0]:.3f} s after the first)')
    y = strike(x)
    full = np.zeros_like(m)
    i = int((ONSET - LEAD) * SR)
    full[i:i + len(y)] = y
    for what, a, b, lo, hi in [("its first fifth, 150 Hz-2 kHz", 1.24, 1.44, 150, 2000), ("its first fifth, 2-8 kHz", 1.24, 1.44, 2000, 8000),
                               ("its first fifth, 8-16 kHz", 1.24, 1.44, 8000, 16000), ("as the second begins, 150 Hz-16 kHz", 1.47, 1.56, 150, 16000),
                               ("the kick under it", 1.24, 1.6, 30, 120)]:
        print(f'{what:38s} {band(full, a, b, lo, hi) - band(m, a, b, lo, hi):+6.1f} dB')
    e = np.array([10 * np.log10((y[int(a * SR):int((a + .05) * SR)] ** 2).mean() + 1e-12) for a in np.arange(0, len(y) / SR - .05, .05)])
    print('its level every 50 ms: ' + ' '.join(f'{v:.0f}' for v in e))
    os.makedirs(out, exist_ok=True)
    write(finish(y, LUFS), out, 'xchara_shot_hit')


if __name__ == '__main__':
    main()
