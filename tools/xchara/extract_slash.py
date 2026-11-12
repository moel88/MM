"""X Chara's basic attack's sound (2.118.0): the user's own, taken out of their video of it from under its music.
usage: python3 -I tools/xchara/extract_slash.py VIDEO.mp4 [OUT_DIR]

The user sent a screen recording of the knife's four hits done twice over music (user request: "이거야 평타 소리에
배경음악이 섞여있어 효과음은 2번 재생돼"; the video is not in the repository) and asked for the sound alone. All eight hits
play one sound, about half a second long: a hiss as the knife cuts and a tail falling in pitch after it (the user's
"피슝"). A separation model (BandIt v2) was tried first and took that falling tail for music (the user: "원본 효과음에는
평타를 때릴때마다 피슝 소리가 나는데 너가 추출한건 안 나"), so none is used. Instead, with nothing generated:

1. The plays are lined up to the sample on the first (the high band's cross-correlation), each with its own gain (the
   second combo is heard a little louder), and the recording is solved by least squares as that one unknown sound
   started at each of those times: where plays overlap (a hit's tail under the next hit) they come apart.
2. Each play alone (the recording less the other plays as solved) is lined up with the rest. The plays agree on the
   sound and disagree on the music under it, so every time-frequency cell is the plays' median, turned down by what
   their spread says is still music in it (a Wiener gain); twice over.
3. The bands the plays agree on a little less (the recording's codec, above 6 kHz) are brought back to the plays' own
   level, the end is faded out where the sound can no longer be told from the music (0.44 to 0.54 s), and it is set to
   its loudness.

Seven plays are used: the first three hits of both combos and the second combo's fourth. The first combo's fourth is
the same sound with its low band 21 ms early (two layers started apart), and is left out.

Writes xchara_slash.ogg (mono, 48 kHz) to OUT_DIR, the mod's sounds by default, and prints what it measured. Every
swing of the attack plays it, the hack on or off (client/xchara/KnifeClient).
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.ndimage import uniform_filter
from scipy.signal import butter, correlate, istft, sosfiltfilt, stft
from scipy.sparse import coo_matrix
from scipy.sparse.linalg import lsqr

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from synth import CEILING_DB, limit, loudness  # noqa: E402

SR = 48000
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# about when each play starts in the video (seconds): hits 1 to 3 of the first combo, 1 to 3 of the second, its fourth
PLAYS = [1.687, 2.071, 2.4123, 5.4957, 5.9437, 6.221, 6.9677]
# solved this long; kept whole to FADE0 and faded out by FADE1 (seconds)
SOLVED, FADE0, FADE1 = .66, .44, .54
LUFS = -17.0
NP, HOP, PRE = 2048, 256, 4096


def decode(video):
    raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', video, '-vn', '-ac', '2', '-ar', str(SR), '-f', 'f32le', '-'])
    return np.frombuffer(raw, '<f4').reshape(-1, 2).astype(np.float64).mean(1)


def line_up(y):
    """Each play's first sample, lined up on the first play's by the band above 2.5 kHz (the music is weak there)."""
    hp = sosfiltfilt(butter(4, 2500, 'hp', fs=SR, output='sos'), y)
    n, pad = int(.2 * SR), int(.03 * SR)
    a0 = int(PLAYS[0] * SR)
    ref = hp[a0:a0 + n]
    starts = [a0]
    for t in PLAYS[1:]:
        b = int(t * SR)
        starts.append(b - pad + int(np.argmax(correlate(hp[b - pad:b + n + pad], ref, 'valid'))))
    return starts


def solve(y, starts, gains, n):
    """The one sound whose copies, started at `starts` with `gains`, are nearest the recording (least squares)."""
    lo, hi = min(starts), min(len(y), max(starts) + n)
    rows, cols, vals = [], [], []
    for s, g in zip(starts, gains):
        m = min(n, len(y) - s)
        rows.append(np.arange(s - lo, s - lo + m)); cols.append(np.arange(m)); vals.append(np.full(m, g))
    a = coo_matrix((np.concatenate(vals), (np.concatenate(rows), np.concatenate(cols))), shape=(hi - lo, n)).tocsr()
    covered = np.asarray(a.sum(1)).ravel() > 0
    return lsqr(a[covered], y[lo:hi][covered], damp=1e-3, iter_lim=500)[0]


def alone(y, e, starts, gains, k):
    """The recording round play k, less every other play as `e` tells it."""
    n, s = len(e), starts[k]
    seg = y[s - PRE:s + n + PRE].copy()
    for j, (t, g) in enumerate(zip(starts, gains)):
        if j == k:
            continue
        a, b = max(t, s - PRE), min(t + n, s + n + PRE)
        if b > a:
            seg[a - (s - PRE):b - (s - PRE)] -= g * e[a - t:b - t]
    return seg


def extract(y):
    starts = line_up(y)
    n, k, m = int(SOLVED * SR), len(PLAYS), int(.25 * SR)
    gains = [1.0] * k
    e = solve(y, starts, gains, n)
    for _ in range(3):
        gains = [float((alone(y, e, starts, gains, i)[PRE:PRE + m] * e[:m]).sum() / (e[:m] ** 2).sum()) for i in range(k)]
        gains = [g / gains[0] for g in gains]
        e = solve(y, starts, gains, n)
    print('plays at', ', '.join(f'{s / SR:.4f}' for s in starts), 's; gains', ', '.join(f'{g:.2f}' for g in gains))
    for _ in range(2):
        z = np.array([stft(alone(y, e, starts, gains, i) / gains[i], SR, nperseg=NP, noverlap=NP - HOP, boundary=None, padded=False)[2] for i in range(k)])
        med = np.median(z.real, 0) + 1j * np.median(z.imag, 0)
        # a play's music power in the cell, by how the plays spread (robustly), along time only (its lines are narrow)
        spread = uniform_filter((1.4826 * np.median(np.abs(z - med[None]), 0)) ** 2 * 1.2, (1, 9))
        left = 1.57 * spread / k
        gain = np.clip(1 - 1.6 * left / (uniform_filter(np.abs(med) ** 2, (3, 7)) + 1e-20), .03, 1)
        out = istft(med * gain, SR, nperseg=NP, noverlap=NP - HOP, boundary=None)[1]
        full = np.zeros(PRE + n + PRE)
        full[:min(len(out), len(full))] = out[:len(full)]
        e = full[PRE:PRE + n]
    return e, starts, gains


def finish(e):
    # the bands the plays agree on less lose a little in the median: back to the plays' own level (dB by Hz)
    f = np.fft.rfft(e)
    hz = np.fft.rfftfreq(len(e), 1 / SR)
    f *= 10 ** (np.interp(hz, [0, 6000, 7000, 9000, 11000, 12750, 14000, 16000, 24000], [0, 0, .5, 1.0, 1.1, 1.7, 2.5, 3, 3]) / 20)
    e = np.fft.irfft(f, len(e))[:int(FADE1 * SR)].copy()
    a = int(FADE0 * SR)
    e[a:] *= .5 * (1 + np.cos(np.pi * np.arange(len(e) - a) / (len(e) - a)))
    e[:144] *= np.linspace(0, 1, 144)
    ceiling = 10 ** (CEILING_DB / 20)
    for _ in range(4):
        e = limit(e * 10 ** ((LUFS - loudness(e)) / 20), ceiling)
    return e


def main():
    video = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'src/main/resources/assets/celestialfall/sounds')
    y = decode(video)
    e, starts, gains = extract(y)
    # what the recording keeps once the sound is taken out of it at every play: as loud as its music alone
    n = int(FADE1 * SR)
    rest = y.copy()
    for s, g in zip(starts, gains):
        rest[s:s + n] -= g * e[:n]
    db = lambda v: 10 * np.log10(np.mean(v ** 2) + 1e-15)
    print(f'music alone {db(y[int(.1 * SR):int(1.6 * SR)]):.1f} dB; under the first three hits {db(y[starts[0]:starts[2] + n]):.1f} dB with the sound, {db(rest[starts[0]:starts[2] + n]):.1f} dB without it')
    w = int(.03 * SR)
    print('level by 30 ms:', ' '.join(f'{db(e[i:i + w]):.0f}' for i in range(0, n - w, w)))
    x = finish(e)
    os.makedirs(out, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, 'x.wav')
        wavfile.write(wav, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '7', os.path.join(out, 'xchara_slash.ogg')], check=True)
    print(f'xchara_slash: {len(x) / SR:.2f} s, {loudness(x):.1f} LUFS, peak {20 * np.log10(np.abs(x).max()):.1f} dB')


if __name__ == '__main__':
    main()
