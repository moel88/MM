"""엑스 차라's E with the hack off, the three cuts: their sound, lifted out of the user's recording (2.129.0).
usage: python3 -I tools/xchara/extract_cutter.py RECORDING.mp4 [OUT]

The user sent a screen recording of the skill used twice, its sound under background music ("효과음은 영상에 2번
재생되고"). The recording is the user's and is not in the repository; only this tool is. Nothing is synthesised: what is
written is the recording's own sound with the music taken down.

What is in the recording (seconds into its sound, which runs some 0.45 s behind its picture, as the slam's did):
  0.540-1.47   the three cuts, first use: three rushes of noise from 150 Hz to over 16 kHz, each coming on within a
               hundredth of a second, at 0.54, 0.80 and 1.07 (a quarter of a second apart, as the three cuts of the
               picture), 7 to 20 dB over the music from 150 Hz up; the last dies away to 1.47
  2.77-3.70    the same, second use (2.250 s later)
  the music    a kick and bass under 150 Hz (as loud as the cuts there), held notes, wide (stereo) pads

The two uses are the same sound but not the same samples (noise the recording's codec has made anew each time: they
agree 0.3), so they cannot be laid over each other as the knife's swings were (extract_slash.py). The music is taken
down by what tells it from the effect, as for the slam's sounds (extract_slam.py):
  1. the music at each moment by its sides: the cuts are in the middle of the stereo picture only, the music is wide;
     what is at the sides, by the music's own middle-to-side ratio, is the music in the middle then (WIDE times over),
     and no less than its usual level by frequency where no effect plays (OVER times over)
  2. the second use tells what of the first is music: where a cell of the first use (smoothed) is louder than the
     same cell of the second, it is turned down to the second's (the cuts are as loud both times; the music is not)
  3. the music's notes hold one pitch and the rushes are noise: from 400 Hz to 3.6 kHz, where its held notes are, what
     stays on one pitch for 75 ms and stands out of what is around it is taken down
  4. the low end is the music's kick and bass: taken off under 150 Hz
  5. cut to its time and faded
What cannot be told apart stays: music in the middle, at a cut's moment, as loud both times.

xchara_cutter starts LEAD before the first rush's onset; the client begins it KnifeClient.CUTTER_SOUND_BEFORE ticks
before the first cut lands, so the first rush is at its fullest on that cut and the others on theirs (a quarter of a
second apart: CutterRules.EVERY). Mono 48 kHz, brought to its loudness under a limiter that keeps peaks below -1 dB.
"""
import os
import sys

import numpy as np
from scipy import signal
from scipy.ndimage import median_filter, uniform_filter
from scipy.signal import butter, correlate, sosfiltfilt

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract_pull import SR, N, HOP, band, decode, finish, smooth, write  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# the first use: its first onset, how long before it its file starts, its end and where its fade out begins
ONSET, LEAD, END, FADE = .540, .010, 1.47, 1.30
# about how much later the second use plays (found to the millisecond by the rushes' loudness over 4 kHz)
LATER = 2.25
# where no effect plays (the music alone)
QUIET = [(.03, .50), (1.55, 2.70)]
LUFS = -16.0
# how many times the music's usual level is taken off; how much of the sides' word of it
OVER, WIDE = 1.5, 1.6
# the least a cell louder in the first use than in the second is turned down to
LEAST = .3
LOW = 150.
# where the music's held notes are (Hz)
HELD_FROM, HELD_TO = 400., 3600.


def line_up(m):
    """How many samples later the second use plays, by the rushes' loudness over 4 kHz (their waveforms do not agree)."""
    e = np.abs(sosfiltfilt(butter(4, 4000, 'hp', fs=SR, output='sos'), m))
    w = int(.006 * SR)
    e = np.convolve(e, np.ones(w) / w, 'same')
    a0, a1 = int((ONSET - .06) * SR), int((END - .1) * SR)
    s0 = e[a0:a1] - e[a0:a1].mean()
    b0 = int((ONSET - .06 + LATER - .08) * SR)
    s1 = e[b0:b0 + len(s0) + int(.16 * SR)]
    c = correlate(s1 - s1.mean(), s0, 'valid')
    k = int(np.argmax(c))
    seg = s1[k:k + len(s0)]
    return b0 + k - a0, float(c[k] / np.sqrt((s0 @ s0) * (((seg - seg.mean()) ** 2).sum())))


def stft(y):
    return signal.stft(y, SR, nperseg=N, noverlap=N - HOP)


def cuts(x, lag):
    """The three cuts: the first use's middle with the music taken off it, by its sides, its usual level and the second use."""
    f, t, left = stft(x[:, 0])
    right = stft(x[:, 1])[2]
    mid, side = (left + right) / 2, (left - right) / 2
    pm, ps = np.abs(mid) ** 2, np.abs(side) ** 2
    quiet = np.zeros_like(t, bool)
    for a, b in QUIET:
        quiet |= (t > a) & (t < b)
    level = median_filter(np.median(pm[:, quiet], 1), size=9, mode='nearest')
    ratio = median_filter(pm[:, quiet].mean(1) / (ps[:, quiet].mean(1) + 1e-18), size=15, mode='nearest')
    wide = uniform_filter(ps, size=(5, 9), mode='nearest') * np.minimum(ratio, 4.)[:, None]
    music = np.maximum(OVER * level[:, None], WIDE * wide)
    left_over = np.maximum(pm - music, 0)
    # the second use, cell for cell (the frames HOP apart: the lag to the nearest of them)
    shift = int(round(lag / HOP))
    later = np.roll(left_over, -shift, axis=1)
    blur = lambda p: uniform_filter(p, size=(9, 21), mode='nearest')
    same = np.sqrt(np.clip(blur(later) / (blur(left_over) + 1e-18), LEAST ** 2, 1))
    c = np.sqrt(np.maximum(1 - music / (pm + 1e-18), .03 ** 2)) * same * mid
    # the music's notes hold one pitch (the rushes are noise): between HELD_FROM and HELD_TO Hz, where its held notes
    # are, what stays on one pitch for 75 ms and stands out of what is around it is taken down
    mag = np.abs(c)
    held = median_filter(mag, size=(1, int(round(.075 / (HOP / SR))) | 1), mode='nearest')
    around = median_filter(mag, size=(int(round(330 / (SR / N))) | 1, 1), mode='nearest')
    g = 1 - np.clip((held / (around + 1e-12) - 1.6) / 1.4, 0, 1) * .9
    g = np.minimum(g, np.minimum(np.roll(g, 1, 0), np.roll(g, -1, 0)))
    c = c * np.where((f[:, None] > HELD_FROM) & (f[:, None] < HELD_TO), g, 1)
    c = c / (1 + (LOW / np.maximum(f, 1)[:, None]) ** 8)
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
    lag, corr = line_up(m)
    print(f'the second use plays {lag / SR:.4f} s later; the rushes\' loudness agrees {corr:.2f}')
    y = cuts(x, lag)
    full = np.zeros_like(m)
    i = int((ONSET - LEAD) * SR)
    full[i:i + len(y)] = y
    for what, a, b, lo, hi in [("the first cut, 150 Hz-2 kHz", .55, .70, 150, 2000), ("the first cut, 2-8 kHz", .55, .70, 2000, 8000),
                               ("the first cut, 8-16 kHz", .55, .70, 8000, 16000), ("the second cut, 150 Hz-2 kHz", .81, .98, 150, 2000),
                               ("the second cut, 2-16 kHz", .81, .98, 2000, 16000), ("the third cut, 150 Hz-2 kHz", 1.08, 1.28, 150, 2000),
                               ("the third cut, 2-16 kHz", 1.08, 1.28, 2000, 16000), ("between the cuts, 150 Hz-2 kHz", .72, .78, 150, 2000),
                               ("the kick under them", .55, 1.3, 30, 120), ("its tail, 150 Hz-4 kHz", 1.32, 1.45, 150, 4000)]:
        print(f'{what:32s} {band(full, a, b, lo, hi) - band(m, a, b, lo, hi):+6.1f} dB')
    os.makedirs(out, exist_ok=True)
    write(finish(y, LUFS), out, 'xchara_cutter')


if __name__ == '__main__':
    main()
